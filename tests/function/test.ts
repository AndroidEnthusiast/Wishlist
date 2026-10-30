(globalThis as any).__WL_TEST__ = true;
const mod = await import(Deno.args[0] ?? new URL("../../supabase/functions/fetch-page/index.ts", import.meta.url).href);
const { isPrivateIp, checkUrl, loadPage, handle } = mod;
let fails = 0, n = 0;
function check(name: string, cond: boolean) { n++; if (!cond) { fails++; console.log("FAIL " + name); } }
async function throwsCode(f: () => Promise<unknown> | unknown, code: string) {
  try { await f(); return false; } catch (e) { return (e as any).code === code || (e as Error).message === code; }
}

// --- addresses ---
const priv = ["127.0.0.1","10.1.2.3","172.16.0.1","172.31.255.255","192.168.1.1","169.254.169.254","0.0.0.0","100.64.0.1","224.0.0.1","255.255.255.255","198.18.0.1","::1","::","fe80::1","fc00::1","fd12:3456::1","::ffff:127.0.0.1","::ffff:10.0.0.1","::ffff:7f00:1","64:ff9b::a00:1","2002:7f00:1::","2002:c0a8:101::","ff02::1","2001:db8::1","not-an-ip","1.2.3","::7f00:1"];
const pub = ["8.8.8.8","1.1.1.1","93.184.216.34","172.32.0.1","172.15.255.255","100.63.0.1","100.128.0.1","2606:4700:4700::1111","2a00:1450:4001:81b::200e","::ffff:8.8.8.8","64:ff9b::808:808"];
for (const ip of priv) check("private: " + ip, isPrivateIp(ip) === true);
for (const ip of pub) check("public: " + ip, isPrivateIp(ip) === false);

// --- urls ---
const bad = ["ftp://shop.example/a","file:///etc/passwd","http://localhost/a","http://127.0.0.1/","http://[::1]/","http://10.0.0.1/","https://shop.example:8443/a","https://user:pw@shop.example/","http://intranet/a","http://foo.local/","http://foo.internal/","http://a.localhost/","http://0x7f.1/","http://2130706433/","http://017700000001/","javascript:alert(1)","not a url","http://169.254.169.254/latest/meta-data/","https://shop.example:22/"];
for (const u of bad) check("bad url: " + u, await throwsCode(() => checkUrl(u), "bad_url"));
for (const u of ["https://www.amazon.com/dp/B0ABCDEFGH","http://shop.example/a?b=1","https://shop.example:443/a","https://xn--bcher-kva.example/","https://Shop.Example./a"]) {
  let ok = true; try { checkUrl(u); } catch { ok = false; }
  check("good url: " + u, ok);
}

// --- loading ---
function html(body: string, extra: Record<string,string> = {}) {
  return new Response(body, { status: 200, headers: { "content-type": "text/html; charset=utf-8", ...extra } });
}
const publicDns = async (_h: string) => ["93.184.216.34"];
const okKey = async (k: string) => k === "ABCDEFGHJKLMNPQRSTUV";
function deps(fetchFn: (u: string, i: RequestInit) => Promise<Response>, resolve = publicDns): any {
  return { fetch: fetchFn, resolve, keyOk: okKey };
}
let r = await loadPage("https://shop.example/a", deps(async (u) => html("<html><title>Hi</title></html>")));
check("plain page", r.status === 200 && r.url === "https://shop.example/a" && r.html.includes("<title>Hi"));

// redirect followed and final url reported
const seen: string[] = [];
r = await loadPage("http://shop.example/a", deps(async (u) => {
  seen.push(u);
  if (u === "http://shop.example/a") return new Response(null, { status: 301, headers: { location: "https://www.shop.example/b" } });
  if (u === "https://www.shop.example/b") return new Response(null, { status: 302, headers: { location: "/c" } });
  return html("<title>C</title>");
}));
check("redirects followed", r.url === "https://www.shop.example/c" && seen.length === 3);

// redirect to a private address is refused
check("redirect to 127.0.0.1 refused", await throwsCode(() => loadPage("https://shop.example/a", deps(async (u) =>
  new Response(null, { status: 302, headers: { location: "http://127.0.0.1/admin" } }))), "bad_redirect"));
check("redirect to metadata refused", await throwsCode(() => loadPage("https://shop.example/a", deps(async (u) =>
  new Response(null, { status: 302, headers: { location: "http://169.254.169.254/" } }))), "bad_redirect"));
// a name that resolves to a private ip is refused
check("dns to private refused", await throwsCode(() => loadPage("https://evil.example/a", deps(async () => html("x"), async () => ["10.0.0.5"])), "bad_url"));
check("dns mixed public+private refused", await throwsCode(() => loadPage("https://evil.example/a", deps(async () => html("x"), async () => ["93.184.216.34", "127.0.0.1"])), "bad_url"));
check("dns empty refused", await throwsCode(() => loadPage("https://evil.example/a", deps(async () => html("x"), async () => [])), "bad_url"));
// a redirect to a name that resolves privately
let calls = 0;
check("redirect to private-dns name refused", await throwsCode(() => loadPage("https://ok.example/a", deps(async (u) => {
  calls++; return new Response(null, { status: 302, headers: { location: "https://rebind.example/" } });
}, async (h) => h === "rebind.example" ? ["192.168.0.9"] : ["93.184.216.34"])), "bad_url") && calls === 1);
// resolve unsupported (null) still loads
r = await loadPage("https://shop.example/a", deps(async () => html("<title>ok</title>"), async () => null));
check("no dns support: still loads", r.html.includes("ok"));
// too many redirects
check("redirect loop stops", await throwsCode(() => loadPage("https://shop.example/a", deps(async () =>
  new Response(null, { status: 302, headers: { location: "https://shop.example/a" } }))), "too_many_redirects"));
// size cap
const big = "x".repeat(3_000_000);
r = await loadPage("https://shop.example/a", deps(async () => html(big)));
check("size capped at 1.5 MB", r.html.length === 1_500_000);
// a never-ending stream is cut off
let pulled = 0;
const endless = new ReadableStream({ pull(c) { pulled++; c.enqueue(new TextEncoder().encode("y".repeat(65536))); } });
r = await loadPage("https://shop.example/a", deps(async () => new Response(endless, { status: 200, headers: { "content-type": "text/html" } })));
check("endless stream cut off", r.html.length === 1_500_000 && pulled < 100);
// not html
check("pdf refused", await throwsCode(() => loadPage("https://shop.example/a", deps(async () =>
  new Response("%PDF", { status: 200, headers: { "content-type": "application/pdf" } }))), "not_html"));
check("image refused", await throwsCode(() => loadPage("https://shop.example/a", deps(async () =>
  new Response("x", { status: 200, headers: { "content-type": "image/png" } }))), "not_html"));
// charset
const latin = new Uint8Array([...new TextEncoder().encode("<html><title>Caf"), 0xe9, ...new TextEncoder().encode("</title></html>")]);
r = await loadPage("https://shop.example/a", deps(async () => new Response(latin, { status: 200, headers: { "content-type": "text/html; charset=iso-8859-1" } })));
check("latin1 via header", r.html.includes("Café"));
const latin2 = new Uint8Array([...new TextEncoder().encode('<html><head><meta charset="windows-1252"><title>Caf'), 0xe9, ...new TextEncoder().encode("</title></head></html>")]);
r = await loadPage("https://shop.example/a", deps(async () => new Response(latin2, { status: 200, headers: { "content-type": "text/html" } })));
check("latin1 via meta", r.html.includes("Café"));
r = await loadPage("https://shop.example/a", deps(async () => new Response("<title>€ 5</title>", { status: 200, headers: { "content-type": "text/html" } })));
check("utf8 default", r.html.includes("€ 5"));
// status of the shop is passed on (403 page still returned)
r = await loadPage("https://shop.example/a", deps(async () => new Response("<title>Access denied</title>", { status: 403, headers: { "content-type": "text/html" } })));
check("403 passed on", r.status === 403 && r.html.includes("Access denied"));
// fetch error
check("network error", await throwsCode(() => loadPage("https://shop.example/a", deps(async () => { throw new TypeError("boom"); })), "fetch_failed"));
// timeout: a fetch that waits for the abort signal
const t0 = Date.now();
check("timeout", await throwsCode(() => loadPage("https://slow.example/a", deps((u, i) => new Promise((_, rej) => {
  i.signal!.addEventListener("abort", () => rej(new DOMException("aborted", "TimeoutError")));
}))), "timeout") && Date.now() - t0 < 14000);
// headers sent to the shop
let sent: any = null;
await loadPage("https://shop.example/a", deps(async (u, i) => { sent = i; return html("x"); }));
check("browser-like user agent", /Chrome\//.test((sent.headers as any)["User-Agent"]) && sent.redirect === "manual");

// --- the web request ---
const key = "ABCDEFGHJKLMNPQRSTUV";
function req(body: unknown, headers: Record<string,string> = { apikey: "sb_publishable_x" }, method = "POST") {
  return new Request("https://x.supabase.co/functions/v1/fetch-page", { method, headers: { "content-type": "application/json", ...headers }, body: method === "POST" ? JSON.stringify(body) : undefined });
}
let res = await handle(req({}, {}, "OPTIONS"), deps(async () => html("x")));
check("options 204 with cors", res.status === 204 && res.headers.get("access-control-allow-origin") === "*" && /apikey/.test(res.headers.get("access-control-allow-headers")!));
res = await handle(req({ key, url: "https://shop.example/a" }), deps(async () => html("<title>T</title>")));
let j = await res.json();
check("good request", res.status === 200 && j.status === 200 && j.html.includes("<title>T") && res.headers.get("access-control-allow-origin") === "*");
res = await handle(req({ key: "WRONGKEYWRONGKEYWRON", url: "https://shop.example/a" }), deps(async () => html("x")));
check("wrong key 403", res.status === 403 && (await res.json()).error === "not_allowed");
res = await handle(req({ key, url: "https://shop.example/a" }, {}), deps(async () => html("x")));
check("no apikey 401", res.status === 401);
res = await handle(req({ key: "short", url: "https://shop.example/a" }), deps(async () => html("x")));
check("short key 400", res.status === 400);
res = await handle(req({ key, url: "http://127.0.0.1/" }), deps(async () => html("x")));
check("private url 400, before any key check", res.status === 400);
let keyCalls = 0;
res = await handle(req({ key, url: "http://127.0.0.1/" }), { fetch: async () => html("x"), resolve: publicDns, keyOk: async () => { keyCalls++; return true; } });
check("no database call for a bad url", keyCalls === 0);
res = await handle(new Request("https://x/", { method: "POST", headers: { apikey: "a" }, body: "not json" }), deps(async () => html("x")));
check("bad json 400", res.status === 400);
res = await handle(req({ key, url: 12 }), deps(async () => html("x")));
check("url not a string 400", res.status === 400);
res = await handle(req({ key, url: "https://shop.example/" + "a".repeat(2100) }), deps(async () => html("x")));
check("huge url 400", res.status === 400);
res = await handle(new Request("https://x/", { method: "GET" }), deps(async () => html("x")));
check("get 405", res.status === 405);
res = await handle(req({ key, url: "https://shop.example/a" }), { fetch: async () => html("x"), resolve: publicDns, keyOk: async () => { throw new Error("db down"); } });
check("key check failure 502", res.status === 502);
res = await handle(req({ key, url: "https://shop.example/a" }), deps(async () => { throw new TypeError("x"); }));
check("shop down 502 with cors", res.status === 502 && res.headers.get("access-control-allow-origin") === "*");

// realKeyOk talks to the right place (fake global fetch)
const real = mod.realDeps;
(globalThis as any).Deno.env.set?.("SUPABASE_URL", "https://p.supabase.co");
const realFetch = globalThis.fetch;
let seenReq: any = null;
(globalThis as any).fetch = async (u: string, i: any) => { seenReq = { u, i }; return new Response(JSON.stringify({ name: "x", count: 1 }), { status: 200 }); };
check("realKeyOk true", (await real.keyOk(key, "sb_publishable_x")) === true && seenReq.u === "https://p.supabase.co/rest/v1/rpc/wl_sh_info" && JSON.parse(seenReq.i.body).p_key === key && seenReq.i.headers.apikey === "sb_publishable_x" && !seenReq.i.headers.Authorization);
(globalThis as any).fetch = async () => new Response("null", { status: 200 });
check("realKeyOk null → false", (await real.keyOk(key, "k")) === false);
(globalThis as any).fetch = async () => new Response("nope", { status: 401 });
check("realKeyOk 401 → false", (await real.keyOk(key, "k")) === false);
(globalThis as any).fetch = async (u: string, i: any) => { seenReq = { u, i }; return new Response("{\"a\":1}", { status: 200 }); };
await real.keyOk(key, "eyJabc");
check("old jwt keys also go in Authorization", seenReq.i.headers.Authorization === "Bearer eyJabc");
(globalThis as any).fetch = realFetch;

console.log(fails === 0 ? "ALL PASSED (" + n + " checks)" : fails + " FAILED of " + n);
Deno.exit(fails ? 1 : 0);
