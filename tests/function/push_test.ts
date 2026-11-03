// Checks supabase/functions/push/index.ts with a fake database and a fake Firebase (no network).
(globalThis as any).__WL_TEST__ = true;
const mod = await import(new URL("../../supabase/functions/push/index.ts", import.meta.url).href);
const { handle, signedJwt } = mod;
let fails = 0, n = 0;
function check(name: string, cond: boolean) { n++; if (!cond) { fails++; console.log("FAIL " + name); } }

// A real RSA key made here, to check the signature as Google would.
const pair = await crypto.subtle.generateKey(
  { name: "RSASSA-PKCS1-v1_5", modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256" },
  true, ["sign", "verify"],
) as CryptoKeyPair;
const der = new Uint8Array(await crypto.subtle.exportKey("pkcs8", pair.privateKey));
const pem = "-----BEGIN PRIVATE KEY-----\n" + btoa(String.fromCharCode(...der)).replace(/(.{64})/g, "$1\n") + "\n-----END PRIVATE KEY-----\n";
const SA = JSON.stringify({ client_email: "push@proj.iam.gserviceaccount.com", private_key: pem, project_id: "wish-proj" });

const jwt = await signedJwt("a@b", pem, 1_700_000_000_000);
const [h, c, s] = jwt.split(".");
const unb = (x: string) => Uint8Array.from(atob(x.replace(/-/g, "+").replace(/_/g, "/")), (ch) => ch.charCodeAt(0));
check("jwt signature verifies", await crypto.subtle.verify("RSASSA-PKCS1-v1_5", pair.publicKey, unb(s), new TextEncoder().encode(h + "." + c)));
const claims = JSON.parse(new TextDecoder().decode(unb(c)));
check("jwt claims", claims.iss === "a@b" && claims.aud === "https://oauth2.googleapis.com/token" && claims.exp - claims.iat === 3600 && claims.scope.includes("firebase.messaging"));

const NOW = Date.parse("2026-11-03T12:00:00Z");
function world(opts: { pushed?: boolean; age?: number; token?: string; fcmStatus?: number; fcmBody?: string } = {}) {
  const calls: { url: string; init: RequestInit }[] = [];
  const msg = { id: 7, b: "BOB", pushed: opts.pushed ?? false, created_at: new Date(NOW - (opts.age ?? 1000)).toISOString() };
  let tokenRow: string | null = opts.token === undefined ? "TOKEN-BOB" : opts.token;
  const deps = {
    now: () => NOW,
    env: (k: string) => ({ SUPABASE_URL: "https://x.supabase.co/", SUPABASE_SERVICE_ROLE_KEY: "eyJsecret", FCM_SERVICE_ACCOUNT: SA } as any)[k],
    fetch: async (url: string, init: RequestInit) => {
      calls.push({ url, init });
      if (url.startsWith("https://x.supabase.co/rest/v1/wl_messages")) {
        const h = init.headers as Record<string, string>;
        if (h.apikey !== "eyJsecret" || h.authorization !== "Bearer eyJsecret") return new Response("no", { status: 401 });
        if (!url.includes("id=eq.7") || msg.pushed) return new Response("[]", { status: 200 });
        msg.pushed = true;
        return new Response(JSON.stringify([{ b: msg.b, created_at: msg.created_at }]), { status: 200 });
      }
      if (url.startsWith("https://x.supabase.co/rest/v1/wl_push")) {
        if (init.method === "DELETE") { tokenRow = null; return new Response(null, { status: 204 }); }
        return new Response(JSON.stringify(tokenRow ? [{ token: tokenRow }] : []), { status: 200 });
      }
      if (url === "https://oauth2.googleapis.com/token") return new Response(JSON.stringify({ access_token: "ACCESS", expires_in: 3600 }), { status: 200 });
      if (url.startsWith("https://fcm.googleapis.com/v1/projects/wish-proj/messages:send")) {
        return new Response(opts.fcmBody ?? "{}", { status: opts.fcmStatus ?? 200 });
      }
      return new Response("unexpected " + url, { status: 500 });
    },
  };
  return { deps, calls, msg, get tokenRow() { return tokenRow; } };
}
const post = (body: unknown) => new Request("https://f/push", { method: "POST", body: JSON.stringify(body) });

let w = world();
let r = await handle(post({ id: 7 }), w.deps);
let body = await r.json();
check("sends a push", r.status === 200 && body.sent === true);
const send = w.calls.find((x) => x.url.includes("messages:send"))!;
const sent = JSON.parse(String(send.init.body));
check("push to the receiver's address, no text", sent.message.token === "TOKEN-BOB" && sent.message.data.t === "msg" && !JSON.stringify(sent).includes("body") && sent.message.android.priority === "HIGH");
check("bearer from token exchange", (send.init.headers as any).authorization === "Bearer ACCESS");
r = await handle(post({ id: 7 }), w.deps);
check("only once per message", (await r.json()).skipped === "done_or_gone" && w.calls.filter((x) => x.url.includes("messages:send")).length === 1);

w = world({ age: 11 * 60 * 1000 });
r = await handle(post({ id: 7 }), w.deps);
check("old message skipped", (await r.json()).skipped === "old" && !w.calls.some((x) => x.url.includes("fcm")));

w = world({ token: "" });
r = await handle(post({ id: 7 }), w.deps);
check("no address: nothing sent", (await r.json()).skipped === "no_address");

w = world({ fcmStatus: 404, fcmBody: '{"error":{"status":"NOT_FOUND","details":[{"errorCode":"UNREGISTERED"}]}}' });
r = await handle(post({ id: 7 }), w.deps);
check("unregistered address is forgotten", (await r.json()).skipped === "unregistered" && w.tokenRow === null);

for (const bad of [{}, { id: "x" }, { id: -1 }, { id: 1.5 }, { id: "7; drop" }]) {
  w = world();
  r = await handle(post(bad), w.deps);
  check("bad id rejected: " + JSON.stringify(bad), r.status === 400 && w.calls.length === 0);
}
w = world();
r = await handle(new Request("https://f/push", { method: "GET" }), w.deps);
check("GET refused", r.status === 405);
w = world();
const noSa = { ...w.deps, env: (k: string) => (k === "FCM_SERVICE_ACCOUNT" ? "" : w.deps.env(k)) };
r = await handle(post({ id: 7 }), noSa);
check("no service account: error, nothing sent", r.status === 500 && w.calls.length === 0);

console.log(fails ? `${fails} of ${n} push checks FAILED` : `push function: all ${n} checks passed`);
if (fails) Deno.exit(1);
