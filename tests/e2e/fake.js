// A pretend Supabase for the browser tests. It follows supabase-shared.sql (merge rules, categories, keys) and the
// storage rules (x-edit-key), and serves a "helper function" that returns product pages from a table of fixtures.
const http = require("http");
const fs = require("fs");
const path = require("path");

const SUPA = "https://aspnmvmsvwbezdltykgx.supabase.co";
// 1x1 png
const PNG = Buffer.from("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==", "base64");

class Fake {
  constructor() {
    this.lists = {};            // code -> {name, cats, key, items: {id: data}}
    this.photos = {};           // "code/name" -> bytes length
    this.log = [];              // every call: {kind, fn, body}
    this.pages = {};            // url -> {status, html, contentType}
    this.hooks = {};            // fn -> async (body) => may throw {status} or delay
    this.helperUp = true;
    this.storageCors = true;
    this.res = {};              // code -> {itemId: device}
  }
  makeList(code, key, name, cats, items) {
    const l = { name, cats: cats || [], key, items: {} };
    (items || []).forEach(i => { l.items[i.id] = JSON.parse(JSON.stringify(i)); });
    this.lists[code] = l;
    return l;
  }
  byKey(key) { for (const c in this.lists) if (this.lists[c].key === key) return [c, this.lists[c]]; return null; }
  need(key) { const f = this.byKey(key); if (!f) { const e = new Error("not allowed"); e.status = 400; throw e; } return f; }
  items(code) { return Object.values(this.lists[code].items); }
  calls(fn) { return this.log.filter(x => x.fn === fn); }

  async rpc(fn, b) {
    if (this.hooks[fn]) await this.hooks[fn](b);
    switch (fn) {
      case "wl_sh_get": {
        const f = this.byKey(b.p_key); if (!f) return null;
        const [code, l] = f;
        const items = Object.values(l.items).sort((a, c) => c.id - a.id);
        return { code, name: l.name, cats: l.cats, items: JSON.parse(JSON.stringify(items)) };
      }
      case "wl_sh_info": { const f = this.byKey(b.p_key); return f ? { name: f[1].name, count: Object.values(f[1].items).filter(i => i.id > 0).length } : null; }
      case "wl_sh_put_item": {
        const [code, l] = this.need(b.p_key);
        const it = b.p_item; const id = Number(it.id);
        if (!(id > 0)) throw Object.assign(new Error("bad id"), { status: 400 });
        const cur = l.items[id];
        if (!cur || b.p_fields === undefined || b.p_fields === null) { l.items[id] = JSON.parse(JSON.stringify(it)); }
        else {
          for (const f of b.p_fields) {
            if (f === "id" || f === "kind") continue;
            if (Object.prototype.hasOwnProperty.call(it, f)) cur[f] = it[f]; else delete cur[f];
          }
        }
        return null;
      }
      case "wl_sh_del_item": { const [code, l] = this.need(b.p_key); delete l.items[b.p_id]; return null; }
      case "wl_sh_set_name": { const [, l] = this.need(b.p_key); l.name = b.p_name; return null; }
      case "wl_sh_set_about": {
        const [, l] = this.need(b.p_key);
        if (!b.p_about) delete l.items[0]; else l.items[0] = { id: 0, kind: "about", about: b.p_about.slice(0, 300), url: "", title: "" };
        return null;
      }
      case "wl_sh_cat_add": { const [, l] = this.need(b.p_key); if (!b.p_name.trim()) throw Object.assign(new Error("bad name"), { status: 400 }); if (!l.cats.includes(b.p_name)) l.cats.push(b.p_name); return null; }
      case "wl_sh_cat_rename": {
        const [, l] = this.need(b.p_key);
        const seen = new Set(); const out = [];
        l.cats.forEach(c => { const e = c === b.p_old ? b.p_new : c; if (!seen.has(e)) { seen.add(e); out.push(e); } });
        l.cats = out;
        Object.values(l.items).forEach(i => { if (i.id > 0 && i.cat === b.p_old) i.cat = b.p_new; });
        return null;
      }
      case "wl_sh_cat_remove": {
        const [, l] = this.need(b.p_key);
        l.cats = l.cats.filter(c => c !== b.p_name);
        Object.values(l.items).forEach(i => { if (i.id > 0 && i.cat === b.p_name) i.cat = ""; });
        return null;
      }
      case "wl_get_list": {
        const l = this.lists[b.p_code]; if (!l) return null;
        return { name: l.name, cats: l.cats, items: Object.values(l.items).sort((a, c) => c.id - a.id) };
      }
      // Reservations, as in supabase-setup.sql: one per wish, "mine" when the device matches.
      case "wl_get_reservations": {
        const r = this.res[b.p_code] || {};
        return Object.keys(r).map(id => ({ id: Number(id), mine: r[id] === b.p_device }));
      }
      case "wl_reserve_item": {
        const l = this.lists[b.p_code]; if (!l || !l.items[b.p_item]) return false;
        const r = this.res[b.p_code] = this.res[b.p_code] || {};
        if (r[b.p_item]) return false;
        r[b.p_item] = b.p_device; return true;
      }
      case "wl_unreserve_item": {
        const r = this.res[b.p_code] || {};
        if (r[b.p_item] === b.p_device) delete r[b.p_item];
        return null;
      }
    }
    throw Object.assign(new Error("unknown " + fn), { status: 404 });
  }

  // Wires everything into a Playwright browser context.
  async attach(context) {
    await context.route(SUPA + "/**", async (route, request) => {
      const url = new URL(request.url());
      const cors = { "access-control-allow-origin": "*", "access-control-allow-headers": "*", "access-control-allow-methods": "*" };
      if (request.method() === "OPTIONS") {
        if (url.pathname.startsWith("/storage/") && !this.storageCors) return route.fulfill({ status: 200, headers: { "access-control-allow-origin": "*", "access-control-allow-headers": "apikey, content-type" } });
        return route.fulfill({ status: 204, headers: cors });
      }
      const json = (status, obj) => route.fulfill({ status, headers: { ...cors, "content-type": "application/json" }, body: JSON.stringify(obj) });
      try {
        if (url.pathname.startsWith("/rest/v1/rpc/")) {
          const fn = url.pathname.split("/").pop();
          const body = JSON.parse(request.postData() || "{}");
          this.log.push({ kind: "rpc", fn, body, keepalive: false });
          const r = await this.rpc(fn, body);
          return json(200, r);
        }
        if (url.pathname === "/functions/v1/fetch-page") {
          const body = JSON.parse(request.postData() || "{}");
          this.log.push({ kind: "helper", fn: "fetch-page", body });
          if (!this.helperUp) return route.fulfill({ status: 404, body: "not found" }); // no cors headers, like a missing function
          if (!this.byKey(body.key)) return json(403, { error: "not_allowed" });
          const p = this.pages[body.url];
          if (!p) return json(502, { error: "fetch_failed" });
          return json(200, { status: p.status || 200, url: p.finalUrl || body.url, html: p.html });
        }
        const m = /^\/storage\/v1\/object\/(public\/)?wish-photos\/(.+)$/.exec(url.pathname);
        if (m) {
          const rel = m[2];
          const key = request.headers()["x-edit-key"];
          if (request.method() === "GET" || request.method() === "HEAD") {
            return route.fulfill({ status: 200, headers: { ...cors, "content-type": "image/png" }, body: PNG });
          }
          if (!this.storageCors && request.method() === "POST") return route.abort("failed"); // like a browser refusing a cross-site upload
          const code = rel.split("/")[0];
          const l = this.lists[code];
          if (!l || l.key !== key) { this.log.push({ kind: "storage", fn: request.method(), rel, denied: true }); return json(403, { error: "denied" }); }
          if (request.method() === "POST") {
            const buf = request.postDataBuffer();
            this.log.push({ kind: "storage", fn: "POST", rel, size: buf ? buf.length : 0, type: request.headers()["content-type"], head: buf ? buf.subarray(0, 3).toString("hex") : "" });
            this.photos[rel] = buf ? buf.length : 0;
            return json(200, { Key: rel });
          }
          if (request.method() === "DELETE") { this.log.push({ kind: "storage", fn: "DELETE", rel }); delete this.photos[rel]; return json(200, {}); }
        }
        return json(404, { error: "unknown " + url.pathname });
      } catch (e) {
        return json(e.status || 500, { message: String(e.message) });
      }
    });
    // The editor loads the app's font from Google Fonts; the tests never go online.
    await context.route("https://fonts.googleapis.com/**", route => route.fulfill({ status: 200, headers: { "content-type": "text/css" }, body: "" }));
    await context.route("https://fonts.gstatic.com/**", route => route.fulfill({ status: 404, body: "" }));
    await context.route("https://img.example/**", route => route.fulfill({ status: 200, headers: { "content-type": "image/png" }, body: PNG }));
    await context.route("https://shop.example/**", route => route.fulfill({ status: 200, headers: { "content-type": "text/html" }, body: "<title>shop</title>" }));
  }
}

function serveWeb(dir) {
  const types = { ".html": "text/html", ".js": "text/javascript", ".css": "text/css", ".png": "image/png" };
  const srv = http.createServer((req, res) => {
    let p = decodeURIComponent(req.url.split("?")[0]);
    if (p.endsWith("/")) p += "index.html"; // like GitHub Pages: a folder serves its index.html
    const f = path.join(dir, p);
    if (!f.startsWith(dir) || !fs.existsSync(f)) { res.writeHead(404); return res.end("nope"); }
    res.writeHead(200, { "content-type": types[path.extname(f)] || "application/octet-stream", "cache-control": "no-store" });
    fs.createReadStream(f).pipe(res);
  });
  return new Promise(r => srv.listen(0, "127.0.0.1", () => r({ srv, base: "http://127.0.0.1:" + srv.address().port + "/" })));
}

module.exports = { Fake, serveWeb, SUPA, PNG };
