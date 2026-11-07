// The wishlist group page (web/group.html + group.js, 2.108) in a real Chromium, against the REAL Supabase scripts
// running in a local PostgreSQL: every call the page makes to /rest/v1/rpc/<fn> is run as `select <fn>(...)` with the
// role anon. Storage and the helper fetch-page are pretended (with the same key rules).
// Needs PostgreSQL: set WL_PG to the psql connection options, for example
//   WL_PG="-h /path/to/socket -p 5499 -U postgres" node group/test.js
// Without WL_PG it says SKIPPED (run-all.sh then doesn't count it as failed). See tests/README.md.
const { spawnSync } = require("child_process");
const path = require("path");
const { chromium } = require("playwright");
const { serveWeb, SUPA, PNG } = require("../e2e/fake.js");

const ROOT = path.join(__dirname, "..", "..");
const PG = process.env.WL_PG;
if (!PG) { console.log("SKIPPED (set WL_PG to run it against PostgreSQL)"); process.exit(0); }
const DB = "wl_group_test";
const pgArgs = PG.split(/\s+/).filter(Boolean);

function psql(sql, db) {
  const r = spawnSync("psql", [...pgArgs, "-d", db || DB, "-v", "ON_ERROR_STOP=1", "-q", "-At", "-c", sql], { encoding: "utf8" });
  if (r.status !== 0) { const e = new Error((r.stderr || "").trim()); e.sql = sql; throw e; }
  return r.stdout.replace(/\n$/, "");
}
function psqlFile(f) {
  const r = spawnSync("psql", [...pgArgs, "-d", DB, "-v", "ON_ERROR_STOP=1", "-q", "-f", path.join(ROOT, f)], { encoding: "utf8" });
  if (r.status !== 0) throw new Error(f + ": " + r.stderr);
}
const q = v => v === null || v === undefined ? "NULL" : "'" + (typeof v === "object" ? JSON.stringify(v) : String(v)).replace(/'/g, "''") + "'";

// A fresh database with the real scripts.
psql("drop database if exists " + DB, "postgres");
psql("create database " + DB, "postgres");
try { psql("create role anon", "postgres"); } catch (e) { /* already there */ }
for (const f of ["supabase-setup.sql", "supabase-shared.sql", "supabase-friends.sql", "supabase-chat.sql", "supabase-groups.sql"]) psqlFile(f);
const types = {};
psql("select proname || '=' || prorettype::regtype from pg_proc where proname like 'wl_%'").split("\n").forEach(l => { const [n, t] = l.split("="); types[n] = t; });

// What the page calls, run as anon.
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
const log = [];
const PAGES = {
  "https://shop.example/teapot": "<html><head><title>Blue teapot</title><meta property=\"og:title\" content=\"Blue teapot\"><meta property=\"og:image\" content=\"https://img.example/teapot.png\"><meta property=\"product:price:amount\" content=\"24.95\"><meta property=\"product:price:currency\" content=\"EUR\"></head><body></body></html>",
};

async function attach(ctx) {
  await ctx.route(SUPA + "/**", async (route, request) => {
    const url = new URL(request.url());
    const cors = { "access-control-allow-origin": "*", "access-control-allow-headers": "*", "access-control-allow-methods": "*" };
    if (request.method() === "OPTIONS") return route.fulfill({ status: 204, headers: cors });
    const json = (status, obj) => route.fulfill({ status, headers: { ...cors, "content-type": "application/json" }, body: JSON.stringify(obj) });
    try {
      if (url.pathname.startsWith("/rest/v1/rpc/")) {
        const fn = url.pathname.split("/").pop();
        const body = JSON.parse(request.postData() || "{}");
        log.push({ fn, body });
        return json(200, rpc(fn, body));
      }
      if (url.pathname === "/functions/v1/fetch-page") {
        const body = JSON.parse(request.postData() || "{}");
        log.push({ fn: "fetch-page", body });
        // The real helper's rule (2.108): an edit key of a gift list, or a group link.
        if (!rpc("wl_gr_info", { p_link: body.key }) && !rpc("wl_sh_info", { p_key: body.key })) return json(403, { error: "not_allowed" });
        const html = PAGES[body.url];
        return html ? json(200, { status: 200, url: body.url, html }) : json(502, { error: "fetch_failed" });
      }
      const m = /^\/storage\/v1\/object\/(public\/)?wish-photos\/(.+)$/.exec(url.pathname);
      if (m) {
        if (request.method() === "GET") return route.fulfill({ status: 200, headers: { ...cors, "content-type": "image/png" }, body: PNG });
        const code = m[2].split("/")[0];
        const token = request.headers()["x-owner-token"] || "";
        // Like the storage rule (supabase-photos.sql): the owner token of the list in the folder.
        const ok = psql("select exists(select 1 from wl_lists where code = " + q(code) + " and owner_token = " + q(token) + ")") === "t";
        log.push({ fn: "storage", path: m[2], ok, size: (request.postDataBuffer() || []).length });
        return ok ? json(200, { Key: m[2] }) : json(403, { error: "denied" });
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
const A = "AAAAAAAAAAAAAAAAAAAA", AT = "tokA_tokA_tokA_tokA_", LINK = "LNKABCDEFGHJKLMNPQRS", GID = "GROUPGROUPGROUPGROUP";

(async () => {
  // Anna made the group in the app and brought her list: one wish in her bin, one top wish.
  rpc("wl_pf_save", { p_id: A, p_token: AT, p_name: "Anna" });
  rpc("wl_create_list", { p_code: "ANNALIST", p_token: "ownerA_ownerA_ownerA", p_name: "Anna birthday" });
  rpc("wl_sync_list", { p_code: "ANNALIST", p_token: "ownerA_ownerA_ownerA", p_name: "Anna birthday", p_cats: [], p_items: [
    { id: 11, url: "https://shop.example/book", title: "Poetry book", desc: "", image: "https://img.example/book.png", price: 12.5, old: null, cur: "EUR", cat: "", got: false, star: true },
    { id: 12, url: "", title: "Socks", desc: "", image: "", price: 5, old: null, cur: "EUR", cat: "", got: false },
    { id: 13, url: "", title: "Old wish", desc: "", image: "", price: 1, old: null, cur: "EUR", cat: "", got: false, bin: 1700000000000 },
  ] });
  rpc("wl_set_link", { p_code: "ANNALIST", p_token: "ownerA_ownerA_ownerA", p_on: false });
  rpc("wl_gr_create", { p_id: A, p_token: AT, p_group: GID, p_link: LINK, p_name: "Family", p_list: "ANNALIST", p_list_token: "ownerA_ownerA_ownerA" });

  const browser = await chromium.launch();
  const web = await serveWeb(path.join(ROOT, "web"));
  async function open(opts) {
    const ctx = await browser.newContext({ viewport: { width: 390, height: 844 }, userAgent: opts.ua, locale: opts.locale || "en-GB", permissions: ["clipboard-read", "clipboard-write"] });
    await attach(ctx);
    const page = await ctx.newPage();
    const errors = [];
    page.on("pageerror", e => errors.push(String(e)));
    page.on("console", m => { if (m.type() === "error" && !/Failed to load resource/.test(m.text())) errors.push("console: " + m.text()); });
    return { ctx, page, errors };
  }

  // ---- Wendy has no app: she opens the group link (through the preview page group/) ----
  let w = await open({ ua: ANDROID });
  await w.page.goto(web.base + "group/?g=" + LINK.toLowerCase());
  await w.page.waitForSelector(".gjoin");
  await w.page.screenshot({ path: "s_group_join.png" });
  check("preview page goes on to group.html with the key", /group\.html\?g=/i.test(w.page.url()), w.page.url());
  check("join screen names the group", (await w.page.locator(".gtitle").innerText()).includes("Family"));
  check("join screen: organiser and members", (await w.page.locator(".gjoin .sub").innerText()) === "Organised by Anna · 1 member");
  check("join screen: open in the app (Android)", (await w.page.locator(".gjoin a[href^='wishlist://group?g=']").count()) === 1);
  await w.page.getByRole("button", { name: "Join" }).click();
  check("a name is needed", (await w.page.locator(".gjoin .err").innerText()) === "What is your name?");
  await w.page.getByLabel("Your name*").fill("Wendy");
  await w.page.getByRole("button", { name: "Join" }).click();
  check("a wishlist name is needed", (await w.page.locator(".gjoin .err").innerText()) === "What is your wishlist called?");
  await w.page.getByLabel("Name your wishlist*").fill("Wendy wishes");
  await w.page.getByRole("button", { name: "Join" }).click();
  await w.page.waitForSelector(".gblocks");
  await sleep(300);
  await w.page.screenshot({ path: "s_group_page.png", fullPage: true });
  const blocks = await w.page.locator(".gblock .gname").allInnerTexts();
  check("group page: You first, then Anna", JSON.stringify(blocks) === JSON.stringify(["You", "Anna"]), blocks);
  check("group page: 2 members", (await w.page.locator("main .sub").first().innerText()) === "Organised by Anna · 2 members");
  check("Anna's block counts 2 wishes (bin not counted)", (await w.page.locator(".gblock").nth(1).locator(".gcount").innerText()) === "2");
  check("Anna's block shows her newest picture", (await w.page.locator(".gblock").nth(1).locator(".pic img:not(.back)").count()) === 1);
  check("Anna is the organiser", (await w.page.locator(".gblock").nth(1).locator(".gsub").innerText()) === "Organiser · Anna birthday");
  const wendy = psql("select id from wl_profiles where name = 'Wendy'");
  check("a browser profile was made", psql("select web from wl_profiles where id = " + q(wendy)) === "t");
  check("Wendy is in the group with her list", psql("select l.name from wl_group_members m join wl_lists l on l.code = m.list_code where m.profile = " + q(wendy)) === "Wendy wishes");
  check("not the gift-list invite link in group/ (no share box for members)", (await w.page.locator("text=Copy group link").count()) === 0);
  check("My link box with Get the app (Android)", (await w.page.locator(".gbox a.act", { hasText: "Get the app" }).count()) === 1 && (await w.page.locator(".gbox a[href^='wishlist://me?me=']").count()) === 1);

  // ---- Anna's list: reserve ----
  await w.page.locator(".gblock").nth(1).click();
  await w.page.waitForSelector(".card");
  check("Anna's list: 2 wishes, the binned one hidden", (await w.page.locator(".card").count()) === 2);
  await sleep(200);
  await w.page.screenshot({ path: "s_group_member.png" });
  check("Anna's list: Owned by Anna", (await w.page.locator(".owner").innerText()) === "Owned by Anna");
  check("Anna's list: top wish first with its label", (await w.page.locator(".card").first().locator(".badge.star").count()) === 1 && (await w.page.locator(".card .title").first().innerText()) === "Poetry book");
  await w.page.locator(".card").first().getByRole("button", { name: "Reserve" }).click();
  await w.page.waitForSelector(".badge.status");
  check("reserved by you", (await w.page.locator(".card").first().locator(".badge.status").innerText()) === "Reserved by you");
  const dev = await w.page.evaluate(() => localStorage.getItem("wl_device"));
  check("the reservation is online with this browser's device", psql("select device from wl_reservations where list_code = 'ANNALIST' and item_id = 11") === dev);
  rpc("wl_reserve_item", { p_code: "ANNALIST", p_item: 12, p_device: "someone-else" });
  await w.page.locator("#backBtn").click();
  await w.page.locator(".gblock").nth(1).click();
  await w.page.waitForSelector(".card .badge.status >> nth=1");
  check("someone else's reservation shows as Reserved", (await w.page.locator(".card").nth(1).locator(".badge.status").innerText()) === "Reserved");
  check("the Reserved label asks if it is still being bought (together.js)", (await w.page.locator(".card").nth(1).locator("button.badge.wlt-ask").count()) === 1);
  check("no Reserve button on a reserved gift", (await w.page.locator(".card").nth(1).getByRole("button", { name: "Reserve", exact: true }).count()) === 0);
  await w.page.locator("#backBtn").click();

  // ---- her own list ----
  await w.page.locator(".gblock").first().click();
  await w.page.waitForSelector("text=Your wishlist is empty");
  check("own list: Add wish button", await w.page.locator("#fab").isVisible());
  const resCalls = log.filter(x => x.fn === "wl_get_reservations" && x.body.p_code !== "ANNALIST").length;
  check("own list never loads its reservations", resCalls === 0);
  await w.page.locator("#fab").click();
  await w.page.getByLabel("Product link (optional)").fill("https://shop.example/teapot");
  await w.page.getByRole("button", { name: "Fetch details" }).click();
  await w.page.waitForFunction(() => document.querySelector(".dlg input[id^=gf]") && [...document.querySelectorAll(".dlg input")].some(i => i.value === "Blue teapot"));
  const helper = log.filter(x => x.fn === "fetch-page").pop();
  check("the helper is asked with the group link as key", helper && helper.body.key === LINK);
  check("price filled in", await w.page.getByLabel("Price").inputValue() === "24.95");
  await w.page.getByLabel("Note for givers").fill("Any colour");
  await w.page.locator(".dlg").getByRole("button", { name: "Save" }).click();
  await w.page.waitForSelector(".card");
  const saved = JSON.parse(psql("select data from wl_items where list_code = (select web_list from wl_profiles where id = " + q(wendy) + ")"));
  check("wish saved online with its fields", saved.title === "Blue teapot" && saved.price === 24.95 && saved.cur === "EUR" && saved.note === "Any colour" && saved.image === "https://img.example/teapot.png" && saved.cat === "" && saved.got === false, saved);
  // Top wish
  await w.page.locator(".card .ibtn").first().click();
  await sleep(300);
  check("top wish saved", JSON.parse(psql("select data from wl_items where id = " + saved.id)).star === true);
  check("top wish label on the card", (await w.page.locator(".card .badge.star").count()) === 1);
  await w.page.screenshot({ path: "s_group_mine.png" });
  // A picture from this device
  await w.page.getByRole("button", { name: "Edit" }).click();
  await w.page.locator(".dlg input[type=file]").setInputFiles(path.join(__dirname, "..", "e2e", "big.jpg"));
  await w.page.waitForSelector(".dlg .picbox img[src^='blob:']");
  await w.page.getByLabel("Title*").fill("Blue teapot, large");
  await w.page.locator(".dlg").getByRole("button", { name: "Save" }).click();
  await w.page.waitForFunction(() => [...document.querySelectorAll(".card .title")].some(t => t.textContent === "Blue teapot, large"));
  await sleep(300);
  const up = log.filter(x => x.fn === "storage").pop();
  const edited = JSON.parse(psql("select data from wl_items where id = " + saved.id));
  check("picture uploaded with the list's owner token", up && up.ok && up.size > 1000 && up.size < 200000, up);
  check("edited wish keeps id and star, gets the uploaded picture", edited.star === true && edited.title === "Blue teapot, large" && edited.image.indexOf("/storage/v1/object/public/wish-photos/" + up.path) > 0, edited);
  // Delete and Undo
  await w.page.getByRole("button", { name: "Delete" }).click();
  await w.page.waitForSelector("#snack:not([hidden])");
  check("deleted online", psql("select count(*) from wl_items where id = " + saved.id) === "0");
  await w.page.locator("#snack .sbtn").click();
  await w.page.waitForSelector(".card");
  await sleep(300);
  check("Undo puts it back online", psql("select count(*) from wl_items where id = " + saved.id) === "1");
  // Anna (app) sees Wendy's list through the group
  check("Anna can open Wendy's list in the group", rpc("wl_gr_list", { p_id: A, p_token: AT, p_group: GID, p_code: rpc("wl_gr_web_me", { p_id: wendy, p_token: psql("select token from wl_profiles where id = " + q(wendy)) }).list }).items.length === 1);
  await w.page.locator("#backBtn").click();

  // ---- My link on another device ----
  await w.page.locator(".gbox").filter({ hasText: "Your private link" }).getByRole("button", { name: "Copy my link" }).click();
  const my = await w.page.evaluate(() => navigator.clipboard.readText());
  check("My link has the profile and the device", new RegExp("group\\.html#me=" + wendy + "\\.[A-Z2-9]{20}\\." + dev + "$").test(my), my);
  check("no JS errors so far", w.errors.length === 0, w.errors);
  let w2 = await open({});
  await w2.page.goto(my);
  await w2.page.waitForSelector(".gblocks");
  check("other device: recognised, in the group", (await w2.page.locator(".gblock .gname").first().innerText()) === "You");
  check("other device: the secret left the address bar", !/#me=/.test(w2.page.url()));
  await w2.page.locator(".gblock").nth(1).click();
  await w2.page.waitForSelector(".card .badge.status");
  check("other device: her reservation is hers there too", (await w2.page.locator(".card").first().locator(".badge.status").innerText()) === "Reserved by you");
  await w2.page.locator("#backBtn").click();

  // ---- an organiser setting: members may invite (share box) ----
  rpc("wl_gr_settings", { p_id: A, p_token: AT, p_group: GID, p_name: "Family 2026", p_invite_all: true });
  await w2.page.reload();
  await w2.page.waitForSelector(".gblocks");
  check("renamed group shows its new name", (await w2.page.locator("#name").innerText()) === "Family 2026");
  check("with Members can invite too: Copy group link", (await w2.page.getByRole("button", { name: "Copy group link" }).count()) === 1);
  await w2.page.getByRole("button", { name: "Copy group link" }).click();
  check("the group link goes through group/", /\/group\/\?g=LNKABCDEFGHJKLMNPQRS$/.test(await w2.page.evaluate(() => navigator.clipboard.readText())));

  // ---- a link that doesn't work ----
  let x = await open({});
  await x.page.goto(web.base + "group.html?g=ZZZZZZZZZZZZZZZZZZZZ");
  await x.page.waitForSelector(".center");
  check("unknown link: clear message", (await x.page.locator(".center").innerText()) === "This group link doesn’t work any more. Ask for a new one.");
  await x.page.goto(web.base + "group.html?g=abc");
  await x.page.waitForSelector(".center");
  check("broken link: clear message", (await x.page.locator(".center").innerText()) === "This group link isn’t complete. Ask for the link again.");
  await x.ctx.close();

  // ---- leaving ----
  await w2.page.getByRole("button", { name: "Leave group" }).click();
  await w2.page.locator(".dlg").getByRole("button", { name: "Leave" }).click();
  await w2.page.waitForSelector(".center");
  check("after leaving: not in a group", (await w2.page.locator(".center").innerText()).length > 0 && psql("select count(*) from wl_group_members where profile = " + q(wendy)) === "0");
  check("her list stays", psql("select count(*) from wl_lists where name = 'Wendy wishes'") === "1");
  check("other device: no JS errors", w2.errors.length === 0, w2.errors);
  await w2.ctx.close();

  // ---- she joins again in the first browser, with the wishlist she has ----
  await w.page.reload();
  await w.page.waitForSelector(".gjoin");
  check("known browser: Join with her wishlist", (await w.page.getByRole("button", { name: "Join with “Wendy wishes”" }).count()) === 1);
  await w.page.getByRole("button", { name: "Join with “Wendy wishes”" }).click();
  await w.page.waitForSelector(".gblocks");
  check("joined again with the same list", psql("select l.name from wl_group_members m join wl_lists l on l.code = m.list_code where m.profile = " + q(wendy)) === "Wendy wishes");

  // ---- she moves to the app (what the app does with My link) ----
  rpc("wl_pf_save", { p_id: "XXXXXXXXXXXXXXXXXXXX", p_token: "tokX_tokX_tokX_tokX_", p_name: "" });
  const wtok = psql("select token from wl_profiles where id = " + q(wendy));
  check("takeover gives the list", rpc("wl_gr_takeover", { p_web: wendy, p_web_token: wtok, p_web_device: dev, p_id: "XXXXXXXXXXXXXXXXXXXX", p_token: "tokX_tokX_tokX_tokX_", p_device: "app-device" }) !== "");
  check("her reservation moved to the app", psql("select device from wl_reservations where list_code = 'ANNALIST' and item_id = 11") === "app-device");
  await w.page.reload();
  await w.page.waitForSelector(".gjoin");
  check("the browser starts again after the move", (await w.page.locator(".gjoin input").count()) === 2);
  check("first browser: no JS errors", w.errors.length === 0, w.errors);
  await w.ctx.close();

  await browser.close();
  web.srv.close();
  psql("drop database if exists " + DB, "postgres");
  console.log(fails === 0 ? "ALL PASSED (" + total + " checks)" : fails + " FAILED of " + total);
  process.exit(fails ? 1 : 0);
})().catch(e => { console.log("FAIL crashed: " + (e.stack || e)); process.exit(1); });
