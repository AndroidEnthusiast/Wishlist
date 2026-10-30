// fetch-page: lets the Wishlist web editor read a product page.
//
// A web page may not load another shop's page itself (the browser forbids it), so the editor asks this function to
// load it and send the HTML back. The page then reads the title, picture and price out of it (web/parse.js).
//
// Safety:
//  * Only people who hold a valid invite (edit) key of a shared list can use it. The key is checked in the database.
//  * It only loads public web addresses: no localhost, no private or internal network addresses, and every redirect
//    is checked again. Only ports 80 and 443. At most 1.5 MB is read, and it gives up after 12 seconds.
//
// Setup (once): Supabase dashboard -> Edge Functions -> Deploy a new function -> Via Editor -> name it exactly
// "fetch-page" -> paste this whole file -> Deploy. Then open the function's details and switch OFF "Verify JWT"
// (the key check is done here instead: Supabase's built-in check only understands the older kind of key).

const UA =
  "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";
const MAX_BYTES = 1_500_000;
const MAX_REDIRECTS = 5;
const TIMEOUT_MS = 12_000;

const CORS: Record<string, string> = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Access-Control-Max-Age": "86400",
};

class Fail extends Error {
  constructor(public status: number, public code: string) {
    super(code);
  }
}

// ---- which addresses may be loaded ----

function v4Parts(ip: string): number[] | null {
  const m = /^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$/.exec(ip);
  if (!m) return null;
  const p = [m[1], m[2], m[3], m[4]].map(Number);
  return p.every((x) => x >= 0 && x <= 255) ? p : null;
}

function isPrivateV4(p: number[]): boolean {
  const [a, b, c] = p;
  return (
    a === 0 || a === 10 || a === 127 || a >= 224 ||
    (a === 100 && b >= 64 && b <= 127) ||
    (a === 169 && b === 254) ||
    (a === 172 && b >= 16 && b <= 31) ||
    (a === 192 && b === 0 && c === 0) ||
    (a === 192 && b === 0 && c === 2) ||
    (a === 192 && b === 168) ||
    (a === 198 && (b === 18 || b === 19)) ||
    (a === 198 && b === 51 && c === 100) ||
    (a === 203 && b === 0 && c === 113)
  );
}

// Turns an IPv6 address into its eight 16-bit groups, or null if it isn't one.
function v6Groups(ip: string): number[] | null {
  let s = ip.toLowerCase();
  if (s.startsWith("[") && s.endsWith("]")) s = s.slice(1, -1);
  const z = s.indexOf("%");
  if (z >= 0) s = s.slice(0, z);
  // an embedded IPv4 tail (::ffff:1.2.3.4)
  const tail = /(\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3})$/.exec(s);
  if (tail) {
    const p = v4Parts(tail[1]);
    if (!p) return null;
    s = s.slice(0, s.length - tail[1].length) +
      ((p[0] << 8) | p[1]).toString(16) + ":" + ((p[2] << 8) | p[3]).toString(16);
  }
  if (!/^[0-9a-f:]+$/.test(s) || (s.match(/::/g) || []).length > 1) return null;
  const halves = s.split("::");
  const head = halves[0] ? halves[0].split(":") : [];
  const rest = halves.length > 1 && halves[1] ? halves[1].split(":") : [];
  let groups: string[];
  if (halves.length === 1) {
    groups = head;
  } else {
    const fill = 8 - head.length - rest.length;
    if (fill < 1) return null;
    groups = head.concat(new Array(fill).fill("0"), rest);
  }
  if (groups.length !== 8) return null;
  const out = groups.map((g) => (g.length >= 1 && g.length <= 4 ? parseInt(g, 16) : NaN));
  return out.some((x) => Number.isNaN(x)) ? null : out;
}

/** True for any address that is not an ordinary public internet address (or isn't an IP address at all). */
export function isPrivateIp(ip: string): boolean {
  const p4 = v4Parts(ip);
  if (p4) return isPrivateV4(p4);
  const g = v6Groups(ip);
  if (!g) return true; // not an address we understand: treat as unsafe
  const allZeroTo = (n: number) => g.slice(0, n).every((x) => x === 0);
  if (allZeroTo(8) || (allZeroTo(7) && g[7] === 1)) return true; // :: and ::1
  if ((g[0] & 0xfe00) === 0xfc00) return true; // fc00::/7 private
  if ((g[0] & 0xffc0) === 0xfe80) return true; // fe80::/10 link-local
  if ((g[0] & 0xff00) === 0xff00) return true; // multicast
  if (g[0] === 0x2001 && g[1] === 0x0db8) return true; // documentation
  // IPv4 inside IPv6: ::ffff:a.b.c.d, ::a.b.c.d, 64:ff9b::/96 (NAT64), 2002::/16 (6to4)
  const v4of = (hi: number, lo: number) => [hi >> 8, hi & 255, lo >> 8, lo & 255];
  if (allZeroTo(5) && (g[5] === 0xffff || g[5] === 0)) return isPrivateV4(v4of(g[6], g[7]));
  if (g[0] === 0x64 && g[1] === 0xff9b && g.slice(2, 6).every((x) => x === 0)) return isPrivateV4(v4of(g[6], g[7]));
  if (g[0] === 0x2002) return isPrivateV4(v4of(g[1], g[2]));
  return false;
}

/** The address as a URL object, or a Fail when it is not a plain public web address. */
export function checkUrl(raw: string): URL {
  let u: URL;
  try {
    u = new URL(raw);
  } catch {
    throw new Fail(400, "bad_url");
  }
  if (u.protocol !== "http:" && u.protocol !== "https:") throw new Fail(400, "bad_url");
  if (u.username || u.password) throw new Fail(400, "bad_url");
  if (u.port && u.port !== "80" && u.port !== "443") throw new Fail(400, "bad_url");
  const h = u.hostname.toLowerCase().replace(/\.$/, "");
  if (!h || h.startsWith("[") || v4Parts(h) || !h.includes(".")) throw new Fail(400, "bad_url"); // no IP numbers, no bare names
  if (
    h === "localhost" || h.endsWith(".localhost") || h.endsWith(".local") || h.endsWith(".internal") ||
    h.endsWith(".lan") || h.endsWith(".home") || h.endsWith(".corp") || h.endsWith(".intranet")
  ) throw new Fail(400, "bad_url");
  return u;
}

// ---- what the function needs from the outside (replaced in the tests) ----

export interface Deps {
  fetch: (url: string, init: RequestInit) => Promise<Response>;
  /** The IP addresses of a host name; null when this runtime can't look them up. */
  resolve: (host: string) => Promise<string[] | null>;
  /** Is this a valid edit key of a shared list? */
  keyOk: (key: string, apikey: string) => Promise<boolean>;
}

async function realResolve(host: string): Promise<string[] | null> {
  // deno-lint-ignore no-explicit-any
  const d = (globalThis as any).Deno;
  if (!d || typeof d.resolveDns !== "function") return null;
  const found: string[] = [];
  let looked = false;
  for (const type of ["A", "AAAA"]) {
    try {
      const r: string[] = await d.resolveDns(host, type);
      looked = true;
      found.push(...r);
    } catch (e) {
      const name = String((e && (e as Error).name) || "");
      // "no such record" is normal (a host often has no AAAA). Anything else means DNS isn't usable here.
      if (name === "NotFound" || /not\s*found|no (data|records?)|NXDOMAIN|ENODATA|ENOTFOUND/i.test(String((e as Error)?.message))) {
        looked = true;
        continue;
      }
      return null;
    }
  }
  return looked ? found : null;
}

async function realKeyOk(key: string, apikey: string): Promise<boolean> {
  // deno-lint-ignore no-explicit-any
  const base = (globalThis as any).Deno?.env?.get("SUPABASE_URL");
  if (!base) return false;
  const headers: Record<string, string> = { "Content-Type": "application/json", apikey };
  if (apikey.startsWith("eyJ")) headers["Authorization"] = "Bearer " + apikey;
  const r = await fetch(base.replace(/\/$/, "") + "/rest/v1/rpc/wl_sh_info", {
    method: "POST", headers, body: JSON.stringify({ p_key: key }),
  });
  if (!r.ok) return false;
  const t = (await r.text()).trim();
  return t !== "" && t !== "null";
}

export const realDeps: Deps = { fetch: (u, i) => fetch(u, i), resolve: realResolve, keyOk: realKeyOk };

// ---- loading the page ----

async function assertPublic(u: URL, deps: Deps) {
  const ips = await deps.resolve(u.hostname.replace(/\.$/, ""));
  if (ips === null) return; // can't look up here; the checks on the name above still apply
  if (ips.length === 0 || ips.some(isPrivateIp)) throw new Fail(400, "bad_url");
}

function charsetOf(contentType: string, head: Uint8Array): string {
  const m = /charset\s*=\s*["']?([\w-]+)/i.exec(contentType);
  if (m) return m[1];
  const first = new TextDecoder("latin1").decode(head.subarray(0, 4096));
  const n = /<meta[^>]+charset\s*=\s*["']?\s*([\w-]+)/i.exec(first);
  return n ? n[1] : "utf-8";
}

async function readLimited(res: Response, limit: number): Promise<Uint8Array> {
  if (!res.body) return new Uint8Array(0);
  const reader = res.body.getReader();
  const chunks: Uint8Array[] = [];
  let total = 0;
  while (total < limit) {
    const { done, value } = await reader.read();
    if (done) break;
    chunks.push(value);
    total += value.length;
  }
  try { await reader.cancel(); } catch { /* already finished */ }
  const all = new Uint8Array(Math.min(total, limit));
  let at = 0;
  for (const c of chunks) {
    const take = Math.min(c.length, all.length - at);
    all.set(c.subarray(0, take), at);
    at += take;
    if (at >= all.length) break;
  }
  return all;
}

export async function loadPage(startUrl: string, deps: Deps): Promise<{ status: number; url: string; html: string }> {
  let u = checkUrl(startUrl);
  const stop = AbortSignal.timeout(TIMEOUT_MS);
  for (let hop = 0; hop <= MAX_REDIRECTS; hop++) {
    await assertPublic(u, deps);
    let res: Response;
    try {
      res = await deps.fetch(u.href, {
        redirect: "manual",
        signal: stop,
        headers: {
          "User-Agent": UA,
          "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
          "Accept-Language": "en-US,en;q=0.9",
        },
      });
    } catch (e) {
      if (stop.aborted || (e as Error)?.name === "TimeoutError" || (e as Error)?.name === "AbortError") {
        throw new Fail(504, "timeout");
      }
      throw new Fail(502, "fetch_failed");
    }
    const loc = res.headers.get("location");
    if (res.status >= 300 && res.status < 400 && loc) {
      try { await res.body?.cancel(); } catch { /* ignore */ }
      try {
        u = checkUrl(new URL(loc, u).href);
      } catch {
        throw new Fail(502, "bad_redirect");
      }
      continue;
    }
    const ct = res.headers.get("content-type") || "";
    if (ct && !/html|xml|text\/plain/i.test(ct)) {
      try { await res.body?.cancel(); } catch { /* ignore */ }
      throw new Fail(415, "not_html");
    }
    let bytes: Uint8Array;
    try {
      bytes = await readLimited(res, MAX_BYTES);
    } catch {
      if (stop.aborted) throw new Fail(504, "timeout");
      throw new Fail(502, "fetch_failed");
    }
    let html: string;
    try {
      html = new TextDecoder(charsetOf(ct, bytes), { fatal: false }).decode(bytes);
    } catch {
      html = new TextDecoder("utf-8", { fatal: false }).decode(bytes);
    }
    return { status: res.status, url: u.href, html };
  }
  throw new Fail(502, "too_many_redirects");
}

// ---- the web request ----

function reply(status: number, body: unknown): Response {
  return new Response(body === null ? null : JSON.stringify(body), {
    status,
    headers: { ...CORS, "Content-Type": "application/json", "Cache-Control": "no-store" },
  });
}

export async function handle(req: Request, deps: Deps = realDeps): Promise<Response> {
  if (req.method === "OPTIONS") return reply(204, null);
  if (req.method !== "POST") return reply(405, { error: "post_only" });
  try {
    const apikey = req.headers.get("apikey") || "";
    if (!apikey) throw new Fail(401, "no_apikey");
    let body: { key?: unknown; url?: unknown };
    try {
      body = await req.json();
    } catch {
      throw new Fail(400, "bad_request");
    }
    const key = typeof body.key === "string" ? body.key : "";
    const url = typeof body.url === "string" ? body.url : "";
    if (!/^[A-Za-z0-9]{16,64}$/.test(key) || !url || url.length > 2000) throw new Fail(400, "bad_request");
    checkUrl(url); // refuse bad addresses before spending a database call
    let ok = false;
    try {
      ok = await deps.keyOk(key, apikey);
    } catch {
      throw new Fail(502, "key_check_failed");
    }
    if (!ok) throw new Fail(403, "not_allowed");
    return reply(200, await loadPage(url, deps));
  } catch (e) {
    if (e instanceof Fail) return reply(e.status, { error: e.code });
    return reply(500, { error: "failed" });
  }
}

// deno-lint-ignore no-explicit-any
if (!(globalThis as any).__WL_TEST__) Deno.serve((req: Request) => handle(req));
