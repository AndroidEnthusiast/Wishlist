// The gift exchange page (web/exchange.html + exchange.js, 2.129) in a real Chromium, against the REAL Supabase scripts
// in a local PostgreSQL (like group/test.js): every call to /rest/v1/rpc/<fn> runs as `select <fn>(...)` with role anon.
// Needs PostgreSQL: set WL_PG to the psql connection options, for example
//   WL_PG="-h /path/to/socket -p 5499 -U postgres" node exchange/test.js
// Without WL_PG it says SKIPPED. See tests/README.md.
const { spawnSync } = require("child_process");
const path = require("path");
const { chromium } = require("playwright");
const { serveWeb, SUPA, PNG } = require("../e2e/fake.js");

const ROOT = path.join(__dirname, "..", "..");
const PG = process.env.WL_PG;
if (!PG) { console.log("SKIPPED (set WL_PG to run it against PostgreSQL)"); process.exit(0); }
const DB = "wl_exchange_test";
const pgArgs = PG.split(/\s+/).filter(Boolean);

function psql(sql, db) {
  const r = spawnSync("psql", [...pgArgs, "-d", db || DB, "-v", "ON_ERROR_STOP=1", "-q", "-At", "-c", sql], { encoding: "utf8" });
  if (r.status !== 0) { const e = new Error((r.stderr || "").trim()); e.sql = sql; throw e; }
  return r.stdout.replace(/\n$/, "");
}
function psqlText(text) {
  const r = spawnSync("psql", [...pgArgs, "-d", DB, "-v", "ON_ERROR_STOP=1", "-q"], { input: text, encoding: "utf8" });
  if (r.status !== 0) throw new Error(r.stderr);
}
const fs = require("fs");
const q = v => v === null || v === undefined ? "NULL" : "'" + (typeof v === "object" ? JSON.stringify(v) : String(v)).replace(/'/g, "''") + "'";

psql("drop database if exists " + DB, "postgres");
psql("create database " + DB, "postgres");
try { psql("create role anon", "postgres"); } catch (e) { /* already there */ }
// pg_net doesn't exist locally: a pretend net.http_post that only remembers the call.
psql("create schema net; create table net.calls(url text, body jsonb); create function net.http_post(url text, body jsonb, headers jsonb) returns bigint language sql as 'insert into net.calls values (url, body) returning 1'; grant usage on schema net to anon; grant insert on net.calls to anon;");
for (const f of ["supabase-setup.sql", "supabase-shared.sql", "supabase-friends.sql", "supabase-chat.sql", "supabase-push.sql", "supabase-groups.sql", "supabase-features.sql", "supabase-exchange.sql"])
  psqlText(fs.readFileSync(path.join(ROOT, f), "utf8").replace(/create extension if not exists pg_net;/g, ""));
const types = {};
psql("select proname || '=' || prorettype::regtype from pg_proc where proname like 'wl_%'").split("\n").forEach(l => { const [n, t] = l.split("="); types[n] = t; });

function rpc(fn, body) {
  if (!/^wl_[a-z_]+$/.test(fn) || !types[fn]) throw Object.assign(new Error("unknown " + fn), { status: 404 });
  const args = Object.keys(body).map(k => k + " => " + q(body[k])).join(", ");
  const out = psql("set role anon; select " + fn + "(" + args + ")" + (types[fn] === "void" ? " is null" : "") + ";");
  const t = types[fn];
  if (t === "void") return null;
  if (out === "") return null;
  if (t === "jsonb" || t === "json") return JSON.parse(out);
  if (t === "boolean") return out === "t";
  return out;
}

let fails = 0, total = 0;
function check(name, cond, extra) { total++; if (!cond) { fails++; console.log("FAIL " + name + (extra !== undefined ? "  -> " + JSON.stringify(extra) : "")); } }
const sleep = ms => new Promise(r => setTimeout(r, ms));

async function attach(ctx) {
  await ctx.route(SUPA + "/**", async (route, request) => {
    const url = new URL(request.url());
    const cors = { "access-control-allow-origin": "*", "access-control-allow-headers": "*", "access-control-allow-methods": "*" };
    if (request.method() === "OPTIONS") return route.fulfill({ status: 204, headers: cors });
    const json = (status, obj) => route.fulfill({ status, headers: { ...cors, "content-type": "application/json" }, body: JSON.stringify(obj) });
    try {
      if (url.pathname.startsWith("/rest/v1/rpc/")) {
        const fn = url.pathname.split("/").pop();
        return json(200, rpc(fn, JSON.parse(request.postData() || "{}")));
      }
      return json(404, { error: "unknown" });
    } catch (e) {
      return json(e.status || 400, { message: String(e.message) });
    }
  });
  await ctx.route("https://fonts.googleapis.com/**", r => r.fulfill({ status: 200, headers: { "content-type": "text/css" }, body: "" }));
  await ctx.route("https://fonts.gstatic.com/**", r => r.fulfill({ status: 404, body: "" }));
  await ctx.route("https://img.example/**", r => r.fulfill({ status: 200, headers: { "content-type": "image/png" }, body: PNG }));
}

const ANDROID = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36";
const A = "AAAAAAAAAAAAAAAAAAAA", AT = "tokA_tokA_tokA_tokA_";
const B = "BBBBBBBBBBBBBBBBBBBB", BT = "tokB_tokB_tokB_tokB_";
const C = "CCCCCCCCCCCCCCCCCCCC", CT = "tokC_tokC_tokC_tokC_";
const LINK = "LNKXABCDEFGHJKLMNPQR", GX = "GXGXGXGXGXGXGXGXGXGX";
const TOK = { [A]: AT, [B]: BT, [C]: CT };

(async () => {
  // Anna organises in the app; Ben and Cas joined with their lists. Ben's list has a wish above the budget and one
  // whose title tries to be HTML.
  for (const [id, tok, n] of [[A, AT, "Anna"], [B, BT, "Ben"], [C, CT, "Cas"]]) rpc("wl_pf_save", { p_id: id, p_token: tok, p_name: n });
  for (const [code, tok, n] of [["ANNALIST", "ownerA_ownerA_ownerA", "Anna wishes"], ["BENLIST1", "ownerB_ownerB_ownerB", "Ben wishes"], ["CASLIST1", "ownerC_ownerC_ownerC", "Cas wishes"]]) {
    rpc("wl_create_list", { p_code: code, p_token: tok, p_name: n });
    rpc("wl_sync_list", { p_code: code, p_token: tok, p_name: n, p_cats: [], p_items: [
      { id: 21, url: "https://shop.example/scarf", title: "Scarf", desc: "", image: "https://img.example/scarf.png", price: 19.5, old: null, cur: "EUR", cat: "", got: false },
      { id: 22, url: "javascript:alert(1)", title: "<img src=x onerror=alert(1)>Watch", desc: "", image: "", price: 120, old: null, cur: "EUR", cat: "", got: false },
      { id: 23, url: "", title: "Binned", desc: "", image: "", price: 1, old: null, cur: "EUR", cat: "", got: false, bin: 1700000000000 },
    ] });
  }
  rpc("wl_gx_create", { p_id: A, p_token: AT, p_gx: GX, p_link: LINK, p_name: "Family Xmas", p_list: "ANNALIST", p_list_token: "ownerA_ownerA_ownerA",
    p_budget: 25, p_currency: "EUR", p_day: null, p_theme: "xmas", p_message: "Bring it wrapped!" });
  rpc("wl_gx_join", { p_id: B, p_token: BT, p_link: LINK, p_gx: "", p_list: "BENLIST1", p_list_token: "ownerB_ownerB_ownerB" });
  rpc("wl_gx_join", { p_id: C, p_token: CT, p_link: LINK, p_gx: "", p_list: "CASLIST1", p_list_token: "ownerC_ownerC_ownerC" });

  const browser = await chromium.launch();
  const web = await serveWeb(path.join(ROOT, "web"));
  async function open(opts) {
    const ctx = await browser.newContext({ viewport: { width: 390, height: 844 }, userAgent: opts.ua, locale: "en-GB", permissions: ["clipboard-read", "clipboard-write"] });
    await attach(ctx);
    const page = await ctx.newPage();
    const errors = [];
    page.on("pageerror", e => errors.push(String(e)));
    page.on("dialog", d => { errors.push("dialog: " + d.message()); d.dismiss(); });
    page.on("console", m => { if (m.type() === "error" && !/Failed to load resource/.test(m.text())) errors.push("console: " + m.text()); });
    return { ctx, page, errors };
  }

  // ---- Wendy has no app: she opens the exchange link (through the preview page exchange/) ----
  let w = await open({ ua: ANDROID });
  await w.page.goto(web.base + "exchange/?x=" + LINK.toLowerCase());
  await w.page.waitForSelector(".gjoin");
  await w.page.screenshot({ path: "s_exchange_join.png" });
  check("preview page goes on to exchange.html with the key", /exchange\.html\?x=/i.test(w.page.url()), w.page.url());
  check("join screen names the exchange", (await w.page.locator(".gtitle").innerText()).includes("Family Xmas"));
  check("join screen: organiser, people, budget", (await w.page.locator(".gjoin .sub").innerText()) === "Organised by Anna · 3 people · Budget: €25.00", await w.page.locator(".gjoin .sub").innerText());
  check("join screen: open in the app (Android)", (await w.page.locator(".gjoin a[href^='wishlist://exchange?x=']").count()) === 1);
  check("join screen: the theme's cover", (await w.page.locator(".xcover.t-xmas").count()) === 1);
  await w.page.getByRole("button", { name: "Join", exact: true }).click();
  check("a name is needed", (await w.page.locator(".gjoin .err").innerText()) === "What is your name?");
  await w.page.getByLabel("Your name*").fill("Wendy");
  await w.page.getByLabel("Name your wishlist*").fill("Wendy wishes");
  await w.page.getByRole("button", { name: "Join", exact: true }).click();
  await w.page.waitForSelector(".xpeople");
  const wendy = psql("select id from wl_profiles where name = 'Wendy'");
  const WT = psql("select token from wl_profiles where id = " + q(wendy));
  TOK[wendy] = WT;
  check("a browser profile was made and joined", psql("select count(*) from wl_gx_members where profile = " + q(wendy)) === "1");
  check("before the draw: waiting for Anna", (await w.page.locator(".xnote").innerText()).includes("Waiting for Anna to draw names"));
  check("before the draw: 4 people, Wendy is You", (await w.page.locator(".xpeople li").count()) === 4 && (await w.page.locator(".xpeople li .xpn").allInnerTexts()).includes("You"));
  check("the welcome message shows", (await w.page.locator(".xmsg").innerText()).includes("Bring it wrapped!"));
  check("no draw button in the browser", (await w.page.getByRole("button", { name: /Draw/ }).count()) === 0);

  // ---- Wendy adds a wish to her own list ----
  await w.page.getByRole("button", { name: "Edit my wishlist" }).click();
  await w.page.getByRole("button", { name: "Add wish" }).click();
  await w.page.getByLabel("Name*").fill("Teapot");
  await w.page.getByLabel("Link to the shop").fill("javascript:alert(2)");
  await w.page.getByRole("button", { name: "Save" }).click();
  check("a link that isn't http(s) is refused", (await w.page.locator(".dlg .err").innerText()).includes("https://"));
  await w.page.getByLabel("Link to the shop").fill("https://shop.example/teapot");
  await w.page.getByLabel("Price").fill("12,50");
  await w.page.getByRole("button", { name: "Save" }).click();
  await w.page.waitForSelector(".card");
  const wl = psql("select web_list from wl_profiles where id = " + q(wendy));
  check("her wish is online", psql("select data->>'title' || '|' || (data->>'price') from wl_items where list_code = " + q(wl) + " and id <> 0") === "Teapot|12.5");
  await w.page.locator("#backBtn").click();
  await w.page.waitForSelector(".xpeople");

  // ---- Anna draws (in the app) ----
  check("draw ok", rpc("wl_gx_draw", { p_id: A, p_token: AT, p_gx: GX, p_without: true }).ok === true);
  await w.page.evaluate(() => window.WLExchange.start());
  await w.page.waitForSelector(".xwrap");
  check("after the draw: the wrapped gift", (await w.page.locator(".xwrap").innerText()).includes("Your name is ready"));
  const gives = psql("select gives_to from wl_gx_members where gx_id = " + q(GX) + " and profile = " + q(wendy));
  const giftee = psql("select name from wl_profiles where id = " + q(gives));
  check("nobody's pairs are in the page before opening", !(await w.page.content()).includes(gives));
  await w.page.locator(".xwrap").click();
  await w.page.waitForSelector(".xgiftee");
  check("confetti", (await w.page.locator(".confetti i").count()) > 10);
  await w.page.screenshot({ path: "s_exchange_name.png" });
  check("she sees the name she drew", (await w.page.locator(".xgname").innerText()) === giftee, giftee);
  check("opened is saved", psql("select seen from wl_gx_members where gx_id = " + q(GX) + " and profile = " + q(wendy)) === "t");
  check("budget on the name card", (await w.page.locator(".xgiftee").innerText()).includes("Budget: €25.00"));

  // ---- their wishlist: reserve, over budget, safety ----
  await w.page.getByRole("button", { name: "See their wishlist" }).click();
  await w.page.waitForSelector(".card");
  const gcode = psql("select list_code from wl_gx_members where gx_id = " + q(GX) + " and profile = " + q(gives));
  const cards = await w.page.locator(".card .title").allInnerTexts();
  check("their list: two wishes, the binned one hidden", cards.length === 2, cards);
  check("an HTML title stays text", cards.some(t => t.includes("<img")) && (await w.page.locator(".card .title img").count()) === 0);
  check("a javascript: link isn't a link", (await w.page.locator("a[href^='javascript']").count()) === 0);
  check("over budget label", (await w.page.locator(".card", { hasText: "Watch" }).locator(".badge.status").innerText()) === "Over budget");
  check("within budget: no label", (await w.page.locator(".card", { hasText: "Scarf" }).locator(".badge.status").count()) === 0);
  await w.page.locator(".card", { hasText: "Scarf" }).getByRole("button", { name: "Reserve" }).click();
  await w.page.waitForSelector(".card .badge.status:has-text('Reserved by you')");
  const dev = await w.page.evaluate(() => localStorage.getItem("wl_device"));
  check("the reservation is online with this browser's device", psql("select device from wl_reservations where list_code = " + q(gcode) + " and item_id = 21") === dev);
  check("everyone else sees it as reserved (any friend's device)", rpc("wl_get_reservations", { p_code: gcode, p_device: "x" }).some(r => r.id === 21 && !r.mine));
  await w.page.locator("#backBtn").click();
  await w.page.waitForSelector(".xgiftee");

  // ---- the anonymous chats ----
  await w.page.getByRole("button", { name: "Ask " + giftee + " anonymously" }).click();
  await w.page.locator(".xin").fill("Which size?");
  await w.page.getByRole("button", { name: "Send" }).click();
  await w.page.waitForSelector(".xbub.mine");
  check("her question is online, from the Santa", psql("select from_santa::text || '|' || body from wl_gx_msgs where santa = " + q(wendy)) === "true|Which size?");
  const reply = rpc("wl_gx_chat", { p_id: gives, p_token: TOK[gives], p_gx: GX, p_side: "santa", p_after: 0 });
  check("the giftee reads it without her name", reply.length === 1 && !JSON.stringify(reply).includes(wendy) && !JSON.stringify(reply).includes("Wendy"));
  rpc("wl_gx_send", { p_id: gives, p_token: TOK[gives], p_gx: GX, p_side: "santa", p_body: "M, <b>thanks</b>" });
  await w.page.waitForSelector(".xbub:not(.mine)", { timeout: 8000 });
  check("the answer arrives in the open chat, as text", (await w.page.locator(".xbub:not(.mine)").innerText()) === "M, <b>thanks</b>");
  await w.page.getByRole("button", { name: "Close" }).click();
  const santa = psql("select profile from wl_gx_members where gx_id = " + q(GX) + " and gives_to = " + q(wendy));
  check("a Santa can't write before opening their name", rpc("wl_gx_send", { p_id: santa, p_token: TOK[santa], p_gx: GX, p_side: "giftee", p_body: "too early" }) === "0" || santa === wendy);
  rpc("wl_gx_open", { p_id: santa, p_token: TOK[santa], p_gx: GX });
  rpc("wl_gx_send", { p_id: santa, p_token: TOK[santa], p_gx: GX, p_side: "giftee", p_body: "Hello from your Santa" });
  await w.page.evaluate(() => window.WLExchange.start());
  await w.page.waitForSelector(".xsanta .xdot");
  check("unread dot on Your Secret Santa", (await w.page.locator(".xsanta").innerText()).includes("Wrote to you"));
  await w.page.locator(".xsanta").click();
  await w.page.waitForSelector(".xbub");
  check("the Santa's message, no name", (await w.page.locator(".xbub").first().innerText()) === "Hello from your Santa" && !(await w.page.locator(".dlg").innerText()).includes(psql("select name from wl_profiles where id = " + q(santa))));
  await w.page.getByRole("button", { name: "Close" }).click();
  check("read after opening", psql("select count(*) from wl_gx_msgs where giftee = " + q(wendy) + " and read_at is null") === "0");

  // ---- My link on another device ----
  await w.page.getByRole("button", { name: "Copy my link" }).click();
  const myLink = await w.page.evaluate(() => navigator.clipboard.readText());
  check("my link has id, token and device", new RegExp("#me=" + wendy + "\\." + WT + "\\.").test(myLink), myLink);
  const w2 = await open({ ua: "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/126 Safari/537.36" });
  await w2.page.goto(myLink.replace(/^https?:\/\/[^/]+\//, web.base));
  await w2.page.waitForSelector(".xgiftee");
  check("other device: same exchange, same name", (await w2.page.locator(".xgname").innerText()) === giftee);
  check("other device: no JS errors", w2.errors.length === 0, w2.errors);
  await w2.ctx.close();

  // ---- leaving repairs the draw ----
  await w.page.getByRole("button", { name: "Leave exchange" }).click();
  await w.page.locator(".dlg").getByRole("button", { name: "Leave" }).click();
  await w.page.waitForSelector(".center");
  check("after leaving: not in it", psql("select count(*) from wl_gx_members where profile = " + q(wendy)) === "0");
  check("the draw is still valid", psql("select count(distinct gives_to) || '|' || count(*) || '|' || bool_or(gives_to = profile)::text from wl_gx_members where gx_id = " + q(GX)) === "3|3|false");
  check("her Santa now has her giftee", psql("select gives_to from wl_gx_members where gx_id = " + q(GX) + " and profile = " + q(santa)) === (santa === gives ? psql("select gives_to from wl_gx_members where gx_id = " + q(GX) + " and profile = " + q(santa)) : gives));
  check("first browser: no JS errors", w.errors.length === 0, w.errors);
  await w.ctx.close();

  // ---- a broken link ----
  const w3 = await open({ ua: ANDROID });
  await w3.page.goto(web.base + "exchange.html?x=abc");
  await w3.page.waitForSelector(".center");
  check("a broken link says so", (await w3.page.locator(".center").innerText()).includes("isn’t complete"));
  await w3.ctx.close();

  await browser.close();
  web.srv.close();
  psql("drop database if exists " + DB, "postgres");
  console.log(fails === 0 ? "ALL PASSED (" + total + " checks)" : fails + " FAILED of " + total);
  process.exit(fails ? 1 : 0);
})().catch(e => { console.log("FAIL crashed: " + (e.stack || e)); process.exit(1); });
