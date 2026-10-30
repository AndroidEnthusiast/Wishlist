const { chromium } = require("playwright");
const { Fake, serveWeb } = require("./fake.js");
const path = require("path");
const WEB = path.join(__dirname, "..", "..", "web"); // the real web pages of this repo
const KEY = "ABCDEFGHJKLMNPQRSTUV", CODE = "KQ4M7XZ2";

let fails = 0, total = 0;
function check(name, cond, extra) { total++; if (!cond) { fails++; console.log("FAIL " + name + (extra !== undefined ? "  -> " + JSON.stringify(extra) : "")); } }
function summary() { console.log(fails === 0 ? "ALL PASSED (" + total + " checks)" : fails + " FAILED of " + total); return fails; }

function sample() {
  return [
    { id: 1003, url: "https://shop.example/lamp", title: "Desk lamp", desc: "Warm light", image: "https://img.example/lamp.png", price: 39.5, old: 49.9, cur: "USD", cat: "Home", got: false, star: true, note: "White, please", size: "", by: "Mia" },
    { id: 1002, url: "https://shop.example/shirt", title: "Blue shirt", desc: "", image: "", price: 25, old: null, cur: "EUR", cat: "Fashion", got: false, note: "", size: "M", by: "" },
    { id: 1001, url: "", title: "Novel", desc: "", image: "https://img.example/book.png", price: null, old: null, cur: "USD", cat: "", got: true, by: "Sam" },
  ];
}

async function boot(opts) {
  opts = opts || {};
  if (!boot.browser) boot.browser = await chromium.launch();
  if (!boot.web) boot.web = await serveWeb(WEB);
  const fake = opts.fake || new Fake();
  if (!opts.fake) fake.makeList(CODE, KEY, "Our home", ["Home", "Fashion", "Books"], opts.items || sample());
  if (opts.about) fake.lists[CODE].items[0] = { id: 0, kind: "about", about: opts.about, url: "", title: "" };
  const ctx = await boot.browser.newContext({
    viewport: opts.viewport || { width: 390, height: 844 }, deviceScaleFactor: 2,
    userAgent: opts.ua, locale: opts.locale, colorScheme: opts.dark ? "dark" : "light", reducedMotion: opts.reduced ? "reduce" : "no-preference",
    permissions: ["clipboard-read", "clipboard-write"],
  });
  await fake.attach(ctx);
  const known = opts.name === undefined ? "Sam" : opts.name;
  await ctx.addInitScript(([name]) => {
    try { if (name !== null && !localStorage.getItem("wl_asked")) { localStorage.setItem("wl_name", name); localStorage.setItem("wl_asked", "1"); } } catch (e) {}
  }, [known]);
  const page = await ctx.newPage();
  const errors = [];
  page.on("pageerror", e => errors.push(String(e)));
  page.on("console", m => { if (m.type() === "error" && !/Failed to load resource/.test(m.text())) errors.push("console: " + m.text()); });
  if (opts.clock) await page.clock.install();
  const base = boot.web.base;
  const url = base + "edit.html?j=" + (opts.key || KEY);
  if (opts.goto !== false) await page.goto(url);
  return { fake, ctx, page, errors, base, url };
}
async function done() { if (boot.browser) await boot.browser.close(); if (boot.web) boot.web.srv.close(); }
const sleep = ms => new Promise(r => setTimeout(r, ms));
// The editor is laid out like the app: a side menu, a category drop-down and a sort menu.
async function openDrawer(t) { await t.page.locator("#menuBtn").click(); await t.page.locator("#drawer").waitFor({ state: "visible" }); }
async function drawerItem(t, id) { await openDrawer(t); await t.page.locator(id).click(); }
// The ⚙ in the top bar: the settings of this list (Rename list, Note for friends, Categories, Share list).
async function listItem(t, label) { await t.page.locator("#listBtn").click(); await t.page.locator(".menu .mitem", { hasText: label }).click(); }
async function catItems(t) {
  await t.page.locator("#catBtn").click();
  const items = await t.page.locator(".menu .mitem").allInnerTexts();
  await t.page.keyboard.press("Escape");
  return items.map(x => x.trim());
}
async function pickCat(t, label) { await t.page.locator("#catBtn").click(); await t.page.locator(".menu .mitem", { hasText: label }).first().click(); }
async function pickSort(t, label) { await t.page.locator("#sortBtn").click(); await t.page.locator(".menu .mitem", { hasText: label }).click(); }
async function search(t, text) { if (await t.page.locator("#search").isHidden()) await t.page.locator("#searchBtn").click(); await t.page.locator("#search").fill(text); }
module.exports = { boot, done, check, summary, KEY, CODE, sample, sleep, WEB, openDrawer, drawerItem, listItem, catItems, pickCat, pickSort, search };
