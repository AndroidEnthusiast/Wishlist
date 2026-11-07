// Reactions and "Is it still being bought?" on the friends' page (web/index.html + friends.js + together.js,
// 7 November 2026) in a real Chromium, against the REAL Supabase scripts in a local PostgreSQL (like group/test.js:
// each rpc call runs as `select fn(...)` with the role anon). pg_net and pg_cron are pretended: net.http_post only
// writes down its calls, and the 2-day timer is run by hand.
// Needs WL_PG (see tests/README.md); without it this says SKIPPED.
const { spawnSync } = require("child_process");
const fs = require("fs");
const path = require("path");
const { chromium } = require("playwright");
const { serveWeb, SUPA } = require("../e2e/fake.js");

const ROOT = path.join(__dirname, "..", "..");
const PG = process.env.WL_PG;
if (!PG) { console.log("SKIPPED (set WL_PG to run it against PostgreSQL)"); process.exit(0); }
const DB = "wl_together_test";
const pgArgs = PG.split(/\s+/).filter(Boolean);

function psql(sql, db) {
  const r = spawnSync("psql", [...pgArgs, "-d", db || DB, "-v", "ON_ERROR_STOP=1", "-q", "-At", "-c", sql], { encoding: "utf8" });
  if (r.status !== 0) { const e = new Error((r.stderr || "").trim()); e.sql = sql; throw e; }
  return r.stdout.replace(/\n$/, "");
}
function psqlText(text, name) {
  const r = spawnSync("psql", [...pgArgs, "-d", DB, "-v", "ON_ERROR_STOP=1", "-q"], { input: text, encoding: "utf8" });
  if (r.status !== 0) throw new Error(name + ": " + r.stderr);
}
const q = v => v === null || v === undefined ? "NULL" : "'" + (typeof v === "object" ? JSON.stringify(v) : String(v)).replace(/'/g, "''") + "'";

psql("drop database if exists " + DB, "postgres");
psql("create database " + DB, "postgres");
try { psql("create role anon", "postgres"); } catch (e) { /* already there */ }
psqlText("create schema if not exists net; create table net.calls(url text, body jsonb);" +
  "create function net.http_post(url text, body jsonb, headers jsonb) returns bigint language sql as 'insert into net.calls values (url, body) returning 1::bigint';", "net");
for (const f of ["setup", "shared", "friends", "chat", "push", "groups", "features", "together"]) {
  const text = fs.readFileSync(path.join(ROOT, "supabase-" + f + ".sql"), "utf8")
    .split("\n").filter(l => !/create extension|cron\.schedule/.test(l)).join("\n");
  psqlText(text, f);
}
const types = {};
psql("select proname || '=' || prorettype::regtype from pg_proc where proname like 'wl_%'").split("\n").forEach(l => { const [n, t] = l.split("="); types[n] = t; });

function rpc(fn, body) {
  if (!/^wl_[a-z_]+$/.test(fn) || !types[fn]) throw Object.assign(new Error("unknown " + fn), { status: 404 });
  const args = Object.keys(body).map(k => k + " => " + q(body[k])).join(", ");
  const out = psql("set role anon; select " + fn + "(" + args + ")" + (types[fn] === "void" ? " is null" : "") + ";");
  const t = types[fn];
  if (t === "void" || out === "") return null;
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
}

(async () => {
  // Zoe's wishlist; Ben (an app user) already reserved the lamp.
  rpc("wl_create_list", { p_code: "ZQELSTW2", p_token: "ownerZ_ownerZ_ownerZ", p_name: "Zoe’s birthday" });
  rpc("wl_sync_list", { p_code: "ZQELSTW2", p_token: "ownerZ_ownerZ_ownerZ", p_name: "Zoe’s birthday", p_cats: [], p_items: [
    { id: 1, url: "", title: "Lamp <b>x</b>", desc: "", image: "", price: 30, old: null, cur: "EUR", cat: "", got: false },
    { id: 2, url: "", title: "Scarf", desc: "", image: "", price: 12, old: null, cur: "EUR", cat: "", got: false },
  ] });
  rpc("wl_pf_save", { p_id: "BBBBBBBBBBBBBBBBBBBB", p_token: "tokB_tokB_tokB_tokB_", p_name: "Ben" });
  rpc("wl_dev_link", { p_id: "BBBBBBBBBBBBBBBBBBBB", p_token: "tokB_tokB_tokB_tokB_", p_device: "benphone1" });
  rpc("wl_push_set", { p_id: "BBBBBBBBBBBBBBBBBBBB", p_token: "tokB_tokB_tokB_tokB_", p_push: "pushB" });
  rpc("wl_reserve_item", { p_code: "ZQELSTW2", p_item: 1, p_device: "benphone1" });
  rpc("wl_rx_set", { p_code: "ZQELSTW2", p_item: 2, p_id: "BBBBBBBBBBBBBBBBBBBB", p_token: "tokB_tokB_tokB_tokB_", p_device: "", p_name: "", p_emoji: "😍" });

  const browser = await chromium.launch();
  const web = await serveWeb(path.join(ROOT, "web"));
  async function open() {
    const ctx = await browser.newContext({ viewport: { width: 390, height: 844 }, locale: "en-GB" });
    await attach(ctx);
    const page = await ctx.newPage();
    const errors = [];
    page.on("pageerror", e => errors.push(String(e)));
    page.on("console", m => { if (m.type() === "error" && !/Failed to load resource/.test(m.text())) errors.push("console: " + m.text()); });
    await page.goto(web.base + "?c=ZQELSTW2");
    try { await page.waitForSelector(".card", { timeout: 8000 }); }
    catch (e) { console.log("PAGE:", await page.locator("body").innerText(), errors); throw e; }
    return { ctx, page, errors };
  }

  // ---- Sam opens Zoe's list in a browser ----
  const s = await open();
  const scarf = s.page.locator(".card", { hasText: "Scarf" });
  check("Ben's reaction shows as 😍 1", (await scarf.locator(".wlt-chip").innerText()).replace(/\s+/g, "") === "😍1");
  check("+ react pill", (await scarf.locator(".wlt-add").count()) === 1);
  check("the title stays text", (await s.page.locator(".card", { hasText: "Lamp <b>x</b>" }).count()) === 1);
  // React: a first name is asked once.
  await scarf.locator(".wlt-add").click();
  await s.page.waitForSelector(".wlt-box");
  check("a first name is asked", (await s.page.locator(".wlt-h").innerText()) === "Your first name");
  await s.page.locator(".wlt-form button[type=submit]").click();
  check("the name is needed", (await s.page.locator(".wlt-box").count()) === 1);
  await s.page.locator(".wlt-form input").fill("Sam");
  await s.page.locator(".wlt-form button[type=submit]").click();
  await s.page.waitForSelector(".wlt-picker .wlt-e");
  check("the picker has groups and emoji", (await s.page.locator(".wlt-tab").count()) >= 8 && (await s.page.locator(".wlt-picker .wlt-e").count()) > 1000);
  await s.page.locator(".wlt-search").fill("party popper");
  check("search by English name", (await s.page.locator(".wlt-picker .wlt-e").count()) === 1 && (await s.page.locator(".wlt-picker .wlt-e").innerText()) === "🎉");
  await s.page.locator(".wlt-search").fill("zzzz");
  check("nothing found says so", (await s.page.locator(".wlt-picker .wlt-p").innerText()) === "No emoji found.");
  await s.page.locator(".wlt-search").fill("party popper");
  await s.page.locator(".wlt-picker .wlt-e").click();
  await sleep(300);
  check("my reaction shows at once", (await scarf.locator(".wlt-chip.mine").innerText()).replace(/\s+/g, "") === "🎉1");
  const saved = rpc("wl_rx_get", { p_code: "ZQELSTW2", p_id: "", p_token: "", p_device: "" });
  check("saved with the name", saved.some(r => r.e === "🎉" && r.n === "Sam"), saved);
  // One per person: tapping Ben's 😍 replaces mine.
  await scarf.locator(".wlt-chip", { hasText: "😍" }).click();
  await sleep(300);
  check("a new one replaces mine", (await scarf.locator(".wlt-chip").count()) === 1 && (await scarf.locator(".wlt-chip.mine").innerText()).replace(/\s+/g, "") === "😍2");
  check("no second name question", (await s.page.locator(".wlt-box").count()) === 0);
  // Long press shows who.
  await scarf.locator(".wlt-chip").click({ button: "right" });
  await s.page.waitForSelector(".wlt-box");
  check("who reacted", (await s.page.locator(".wlt-box .wlt-p").innerText()) === "Ben, You");
  await s.page.keyboard.press("Escape");
  // Tap yours to take it away.
  await scarf.locator(".wlt-chip.mine").click();
  await sleep(300);
  check("tap yours to remove", (await scarf.locator(".wlt-chip").innerText()).replace(/\s+/g, "") === "😍1" && (await scarf.locator(".wlt-chip.mine").count()) === 0);
  // The picker remembers what you used.
  await scarf.locator(".wlt-add").click();
  await s.page.waitForSelector(".wlt-picker .wlt-e");
  check("recently used first", (await s.page.locator(".wlt-gh").first().innerText()) === "Recently used" && (await s.page.locator(".wlt-picker .wlt-e").first().innerText()) === "😍");
  await s.page.keyboard.press("Escape");

  // ---- Sam wants the lamp that Ben reserved: ask ----
  const lamp = s.page.locator(".card", { hasText: "Lamp" });
  check("Reserved is a button", (await lamp.locator("button.badge.wlt-ask").innerText()) === "Reserved");
  await lamp.locator("button.badge.wlt-ask").click();
  await s.page.waitForSelector(".wlt-box");
  check("asking explains the 2 days", (await s.page.locator(".wlt-box .wlt-p").innerText()).includes("2 days"));
  psql("delete from net.calls");
  await s.page.getByRole("button", { name: "Ask if it’s still being bought" }).click();
  await s.page.waitForFunction(() => document.getElementById("toast").className === "show");
  check("confirmation on screen", (await s.page.locator("#toast").innerText()).startsWith("Asked."));
  check("Ben is pushed at once", psql("select count(*) from net.calls") === "1");
  check("Ben's question", rpc("wl_ask_mine", { p_device: "benphone1", p_code: "" }).length === 1);

  // A second visitor asks too: someone already asked.
  const t = await open();
  await t.page.locator(".card", { hasText: "Lamp" }).locator("button.badge.wlt-ask").click();
  await t.page.getByRole("button", { name: "Ask if it’s still being bought" }).click();
  await t.page.waitForFunction(() => document.getElementById("toast").className === "show");
  check("only the first ask counts", (await t.page.locator("#toast").innerText()).startsWith("Someone already asked"));

  // Ben lets go in the app (here: the same function); Sam's page says it's free and the lamp can be reserved.
  const ask = rpc("wl_ask_mine", { p_device: "benphone1", p_code: "" })[0].ask;
  rpc("wl_ask_answer", { p_device: "benphone1", p_ask: ask, p_yes: false });
  await s.page.evaluate(() => document.dispatchEvent(new Event("visibilitychange")));
  await s.page.waitForSelector(".wlt-panel");
  check("Sam sees it's free again", (await s.page.locator(".wlt-panel").innerText()).includes("“Lamp <b>x</b>” is free again"));
  check("and can reserve it", (await lamp.getByRole("button", { name: "Reserve" }).count()) === 1);
  await s.page.locator(".wlt-panel button", { hasText: "OK" }).click();
  check("OK closes the note", (await s.page.locator(".wlt-panel").count()) === 0);

  // ---- Sam reserves; a browser reserver gets the question on the page and answers ----
  await lamp.getByRole("button", { name: "Reserve" }).click();
  await sleep(400);
  rpc("wl_ask", { p_code: "ZQELSTW2", p_item: 1, p_device: "otherdevice1", p_id: "", p_token: "" });
  await s.page.evaluate(() => document.dispatchEvent(new Event("visibilitychange")));
  await s.page.waitForSelector(".wlt-q strong");
  check("the question waits on the page", (await s.page.locator(".wlt-q strong").innerText()) === "Still buying “Lamp <b>x</b>”?");
  check("with the time left", /1 day 23 hours left|2 days left/.test(await s.page.locator(".wlt-q .wlt-p").innerText()));
  await s.page.getByRole("button", { name: "Yes, I’m buying it" }).click();
  await s.page.waitForFunction(() => document.getElementById("toast").className === "show");
  check("answered", (await s.page.locator("#toast").innerText()).startsWith("Thanks!"));
  check("still reserved by Sam", (await lamp.getByRole("button", { name: "Undo" }).count()) === 1);
  check("the asker hears it's still being bought", rpc("wl_news_web", { p_device: "otherdevice1", p_code: "ZQELSTW2" })[0].kind === "ask_yes");

  // ---- no answer within 2 days: the reservation ends; Sam sees it on the next visit ----
  psql("update wl_asks set answered_at = now() - interval '8 days'");
  rpc("wl_ask", { p_code: "ZQELSTW2", p_item: 1, p_device: "otherdevice1", p_id: "", p_token: "" });
  psql("update wl_asks set created_at = now() - interval '3 days' where state = 'open'; select wl_ask_expire();");
  await s.page.reload();
  await s.page.waitForSelector(".wlt-panel");
  check("Sam: your reservation has ended", (await s.page.locator(".wlt-panel").innerText()).includes("Your reservation of “Lamp <b>x</b>” has ended"));
  check("the lamp is free", (await s.page.locator(".card", { hasText: "Lamp" }).getByRole("button", { name: "Reserve" }).count()) === 1);
  check("no errors", s.errors.length === 0 && t.errors.length === 0, s.errors.concat(t.errors));

  await browser.close();
  web.srv.close();
  psql("drop database if exists " + DB, "postgres");
  console.log(fails ? "FAILED " + fails + " of " + total : "ALL PASSED (" + total + " checks)");
  process.exit(fails ? 1 : 0);
})().catch(e => { console.error(e); process.exit(1); });
