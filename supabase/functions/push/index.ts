// push: wakes a friend's phone when a message is sent to them (instant message notifications, version 2.105).
//
// The database calls this function after a message is saved (supabase-push.sql, trigger wl_messages_push) with only
// the message's number. This function then:
//  1. marks that message as pushed (so each message is pushed once, however often this is called),
//  2. looks up the receiver's push address (table wl_push),
//  3. asks Firebase Cloud Messaging to wake that phone. The push carries no text: messages stay end-to-end encrypted.
//     The phone fetches and unlocks its new messages itself and shows the notification.
//
// Safety: callers can't choose who gets a push or what it says; a message older than 10 minutes, or one that was
// already pushed, is skipped. The push address is only read here, with the server's own key.
//
// Setup (once):
//  * Supabase dashboard -> Edge Functions -> Secrets: add FCM_SERVICE_ACCOUNT with the whole content of the Firebase
//    service account file (Firebase console -> Project settings -> Service accounts -> Generate new private key).
//  * Edge Functions -> Deploy a new function -> Via Editor -> name it exactly "push" -> paste this whole file ->
//    Deploy. Then open the function's details and switch OFF "Verify JWT" (the database calls it without a key).

export interface Deps {
  fetch: (url: string, init: RequestInit) => Promise<Response>;
  env: (name: string) => string | undefined;
  now: () => number;
}

const realDeps: Deps = {
  fetch: (u, i) => fetch(u, i),
  env: (n) => Deno.env.get(n),
  now: () => Date.now(),
};

const MAX_AGE_MS = 10 * 60 * 1000;

function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json" } });
}

function b64url(bytes: Uint8Array | string): string {
  const s = typeof bytes === "string" ? bytes : String.fromCharCode(...bytes);
  return btoa(s).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function pemToDer(pem: string): ArrayBuffer {
  const body = pem.replace(/-----[^-]+-----/g, "").replace(/\s+/g, "");
  const raw = atob(body);
  const out = new ArrayBuffer(raw.length);
  const view = new Uint8Array(out);
  for (let i = 0; i < raw.length; i++) view[i] = raw.charCodeAt(i);
  return out;
}

/** A signed request for a Google access token (OAuth 2.0 for service accounts, RS256). */
export async function signedJwt(email: string, privateKeyPem: string, nowMs: number): Promise<string> {
  const key = await crypto.subtle.importKey(
    "pkcs8", pemToDer(privateKeyPem), { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["sign"],
  );
  const iat = Math.floor(nowMs / 1000);
  const head = b64url(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const claims = b64url(JSON.stringify({
    iss: email,
    scope: "https://www.googleapis.com/auth/firebase.messaging",
    aud: "https://oauth2.googleapis.com/token",
    iat,
    exp: iat + 3600,
  }));
  const sig = new Uint8Array(
    await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, new TextEncoder().encode(head + "." + claims)),
  );
  return head + "." + claims + "." + b64url(sig);
}

let cached: { token: string; until: number } | null = null;

async function accessToken(sa: { client_email: string; private_key: string }, deps: Deps): Promise<string> {
  if (cached && cached.until > deps.now() + 60_000) return cached.token;
  const jwt = await signedJwt(sa.client_email, sa.private_key, deps.now());
  const r = await deps.fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body: "grant_type=" + encodeURIComponent("urn:ietf:params:oauth:grant-type:jwt-bearer") + "&assertion=" + jwt,
  });
  if (!r.ok) throw new Error("token " + r.status);
  const o = await r.json();
  cached = { token: String(o.access_token), until: deps.now() + (Number(o.expires_in) || 3600) * 1000 };
  return cached.token;
}

/** Talks to our own database with the server's key (it may read wl_push; nobody else can). */
function db(deps: Deps) {
  const base = (deps.env("SUPABASE_URL") || "").replace(/\/+$/, "") + "/rest/v1/";
  const key = deps.env("SUPABASE_SERVICE_ROLE_KEY") || deps.env("SUPABASE_SECRET_KEY") || "";
  const headers: Record<string, string> = { apikey: key, "content-type": "application/json" };
  if (key.startsWith("eyJ")) headers.authorization = "Bearer " + key;
  return (path: string, init: RequestInit = {}) =>
    deps.fetch(base + path, { ...init, headers: { ...headers, ...(init.headers as Record<string, string> || {}) } });
}

export async function handle(req: Request, deps: Deps = realDeps): Promise<Response> {
  if (req.method !== "POST") return json(405, { error: "post_only" });
  let id = 0;
  try {
    id = Number((await req.json()).id);
  } catch {
    return json(400, { error: "bad_request" });
  }
  if (!Number.isSafeInteger(id) || id <= 0) return json(400, { error: "bad_request" });

  const saRaw = deps.env("FCM_SERVICE_ACCOUNT") || "";
  let sa: { client_email: string; private_key: string; project_id: string };
  try {
    sa = JSON.parse(saRaw);
    if (!sa.client_email || !sa.private_key || !sa.project_id) throw new Error("incomplete");
  } catch {
    return json(500, { error: "no_service_account" });
  }
  const q = db(deps);

  // 1. Take the message: only once, and only while it is new.
  const took = await q("wl_messages?id=eq." + id + "&pushed=eq.false&select=b,created_at", {
    method: "PATCH",
    headers: { prefer: "return=representation" },
    body: JSON.stringify({ pushed: true }),
  });
  if (!took.ok) return json(502, { error: "db " + took.status });
  const rows = await took.json();
  if (!Array.isArray(rows) || rows.length === 0) return json(200, { skipped: "done_or_gone" });
  const to = String(rows[0].b || "");
  const at = Date.parse(String(rows[0].created_at || ""));
  if (!to || !(at > deps.now() - MAX_AGE_MS)) return json(200, { skipped: "old" });

  // 2. Their push address.
  const pr = await q("wl_push?id=eq." + encodeURIComponent(to) + "&select=token");
  if (!pr.ok) return json(502, { error: "db " + pr.status });
  const pa = await pr.json();
  const token = Array.isArray(pa) && pa[0] ? String(pa[0].token || "") : "";
  if (!token) return json(200, { skipped: "no_address" });

  // 3. Wake the phone. A data message with high priority: it fetches its messages itself.
  const access = await accessToken(sa, deps);
  const r = await deps.fetch("https://fcm.googleapis.com/v1/projects/" + encodeURIComponent(sa.project_id) + "/messages:send", {
    method: "POST",
    headers: { authorization: "Bearer " + access, "content-type": "application/json" },
    body: JSON.stringify({
      message: { token, data: { t: "msg" }, android: { priority: "HIGH", ttl: "3600s" } },
    }),
  });
  if (r.ok) return json(200, { sent: true });
  const text = await r.text();
  // The app was uninstalled or the address changed: forget it (the phone sends a new one when it starts).
  if (r.status === 404 || text.includes("UNREGISTERED")) {
    await q("wl_push?id=eq." + encodeURIComponent(to) + "&token=eq." + encodeURIComponent(token), { method: "DELETE" });
    return json(200, { skipped: "unregistered" });
  }
  return json(502, { error: "fcm " + r.status });
}

if (!(globalThis as any).__WL_TEST__) Deno.serve((req: Request) => handle(req));
