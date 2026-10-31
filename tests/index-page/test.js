const { JSDOM } = require("jsdom");
const fs = require("fs");
const path = require("path");
const WEB = path.join(__dirname, "..", "..", "web");
// jsdom doesn't load script files here: put nl.js and i18n.js into the page, as a browser would load them.
const html = fs.readFileSync(process.argv[2] || path.join(WEB, "index.html"), "utf8")
  .replace('<script src="nl.js"></script>', () => "<script>" + fs.readFileSync(path.join(WEB, "nl.js"), "utf8") + "</script>")
  .replace('<script src="i18n.js"></script>', () => "<script>" + fs.readFileSync(path.join(WEB, "i18n.js"), "utf8") + "</script>");

const items = [
  {id: 1, url: "https://www.amazon.com/dp/B000000001", title: "Headphones <b>X</b>", desc: "Nice", image: "https://img.example/a.jpg", price: 80, old: 100, cur: "USD", cat: "Tech", got: false},
  {id: 2, url: "javascript:alert(1)", title: "Evil link", desc: "", image: "javascript:alert(2)", price: null, old: null, cur: "USD", cat: "Home", got: false},
  {id: 3, url: "https://shop.example/c", title: "Bought already", desc: "", image: "", price: 20, old: null, cur: "EUR", cat: "Books", got: true},
];
const store = { reservations: { 2: "someone-else" }, calls: [] };

function fakeFetch(url, opts) {
  const fn = url.split("/rpc/")[1];
  const body = JSON.parse(opts.body);
  store.calls.push([fn, body, opts.headers]);
  let out;
  if (fn === "wl_get_list") out = body.p_code === "ZZZZZZZZ" ? null : {name: "Sam's wishlist", cats: store.noCats ? [] : ["Tech","Home","Books"], items};
  else if (fn === "wl_get_reservations")
    out = Object.entries(store.reservations).map(([id, d]) => ({id: Number(id), mine: d === body.p_device}));
  else if (fn === "wl_sh_info") out = body.p_key === "ABCDEFGHJKLMNPQRSTUV" ? {name: store.inviteName || "Baby registry", count: store.inviteCount === undefined ? 3 : store.inviteCount} : null;
  else if (fn === "wl_reserve_item") {
    if (store.reservations[body.p_item]) out = false; else { store.reservations[body.p_item] = body.p_device; out = true; }
  } else if (fn === "wl_unreserve_item") {
    if (store.reservations[body.p_item] === body.p_device) delete store.reservations[body.p_item];
    out = null;
  }
  const text = fn === "wl_unreserve_item" ? "" : JSON.stringify(out);
  return Promise.resolve({ ok: true, status: 200, text: () => Promise.resolve(text) });
}

const ANDROID_UA = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36";
const IPHONE_UA = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1";
async function load(search, pre, ua) {
  const vc = new (require("jsdom").VirtualConsole)();
  const navs = [];
  vc.on("jsdomError", e => { if (/navigation/i.test(String(e.message))) navs.push(String(e.message)); });
  const dom = new JSDOM(html, {
    url: "https://androidenthusiast.github.io/Wishlist/" + search,
    runScripts: "dangerously", pretendToBeVisual: true, virtualConsole: vc,
    beforeParse(w) {
      w.fetch = fakeFetch;
      Object.defineProperty(w.navigator, "userAgent", { configurable: true, value: ua || ANDROID_UA });
      if (pre) pre(w);
    }
  });
  dom.navs = navs;
  await new Promise(r => setTimeout(r, 100));
  return dom;
}
const sleep = ms => new Promise(r => setTimeout(r, ms));
// The category drop-down: open it and pick a category (as a visitor would tap it).
function pickCat(doc, name) {
  doc.getElementById("catBtn").click();
  [...doc.querySelectorAll(".menu .mitem")].find(b => b.getAttribute("data-cat") === name).click();
}
let fails = 0;
function check(name, cond) { console.log((cond ? "PASS " : "FAIL ") + name); if (!cond) fails++; }

(async () => {
  let d = await load("");
  check("no code shows entry form", !!d.window.document.querySelector("form.code"));

  d = await load("?c=abcd2345");
  let doc = d.window.document;
  check("title is list name", doc.title === "Sam's wishlist" && doc.getElementById("name").textContent === "Sam's wishlist");
  check("3 cards rendered", doc.querySelectorAll(".card").length === 3);
  check("HTML in title is not injected", !doc.querySelector(".title b") && doc.body.innerHTML.includes("&lt;b&gt;X"));
  check("javascript: link not used", ![...doc.querySelectorAll("a")].some(a => a.href.startsWith("javascript")));
  check("javascript: image not used", ![...doc.querySelectorAll("img")].some(i => i.src.startsWith("javascript")));
  const cards = [...doc.querySelectorAll(".card")];
  check("item1 Reserve button + 20% badge", cards[0].querySelector("button.act").textContent === "Reserve" && cards[0].querySelector(".badge.drop").textContent.includes("20%"));
  check("item2 Reserved by someone else, no button", cards[1].querySelector(".badge.status").textContent === "Reserved" && !cards[1].querySelector("button.act"));
  check("item3 Got it, no button", cards[2].querySelector(".badge.status").textContent === "Got it" && !cards[2].querySelector("button.act"));
  const noLink = {id: 9, url: "", title: "Custom gift", desc: "", image: "", price: null, old: null, cur: "USD", cat: "Home", got: false};
  items.push(noLink);
  const dn = await load("?c=abcd2345");
  const nl = [...dn.window.document.querySelectorAll(".card")].find(c => c.textContent.includes("Custom gift"));
  check("no-link item: shown, meta has no leading dot", !!nl && nl.querySelector(".meta").textContent === "Home");
  check("no-link item: no store link", !!nl && !nl.querySelector("a"));
  items.pop();
  // A wish in the owner's bin (deleted, kept 10 days) travels along marked "bin": friends never see it.
  items.push({id: 10, url: "", title: "Binned gift", desc: "", image: "", price: 5, old: null, cur: "USD", cat: "Home", got: false, bin: Date.now(), binBy: "Ann"});
  const db = await load("?c=abcd2345");
  check("a wish in the bin is not shown to friends", ![...db.window.document.querySelectorAll(".card")].some(c => c.textContent.includes("Binned gift")) && db.window.document.querySelectorAll(".card").length === 3);
  items.pop();
  check("category drop-down: All + 3", (doc.getElementById("catBtn").click(), doc.querySelectorAll(".menu .mitem").length === 4));
  doc.body.click();
  check("apikey header, no Authorization for sb_publishable", store.calls[0][2].apikey.startsWith("sb_publishable_") && !store.calls[0][2].Authorization);
  check("code uppercased", store.calls[0][1].p_code === "ABCD2345");

  cards[0].querySelector("button.act").click();
  await sleep(100);
  let c1 = d.window.document.querySelectorAll(".card")[0];
  check("after Reserve: Reserved by you + Undo", c1.querySelector(".badge.status").textContent === "Reserved by you" && c1.querySelector("button.act").textContent === "Undo");
  const myDevice = d.window.localStorage.getItem("wl_device");
  check("device id stored and used", !!myDevice && store.reservations[1] === myDevice);

  const d2 = await load("?c=ABCD2345");
  const c1b = d2.window.document.querySelectorAll(".card")[0];
  check("other visitor sees Reserved, no button", c1b.querySelector(".badge.status").textContent === "Reserved" && !c1b.querySelector("button.act"));

  c1.querySelector("button.act").click();
  await sleep(100);
  c1 = d.window.document.querySelectorAll(".card")[0];
  check("after Undo: Reserve again", c1.querySelector("button.act").textContent === "Reserve" && !store.reservations[1]);

  store.reservations[1] = "other";
  d.window.document.querySelectorAll(".card")[0].querySelector("button.act").click();
  await sleep(100);
  check("race: toast shown", d.window.document.getElementById("toast").textContent.includes("Someone else"));
  check("race: item now Reserved", d.window.document.querySelectorAll(".card")[0].querySelector(".badge.status").textContent === "Reserved");

  pickCat(d.window.document, "Books");
  check("filter Books -> 1 card", d.window.document.querySelectorAll(".card").length === 1);

  const g = await load("?c=ZZZZZZZZ");
  check("unshared list message", g.window.document.getElementById("view").textContent.includes("isn’t shared any more"));

  const h = await load("#WL-ABCD2345");
  check("hash with WL- prefix works", h.window.document.querySelectorAll(".card").length === 3);

  // ---------- view switcher ----------
  store.reservations = { 2: "someone-else" };
  const D = x => x.window.document;
  let v = await load("?c=ABCD2345");
  check("bar visible with 2 view buttons", !D(v).getElementById("bar").hidden && D(v).querySelectorAll("#views .vbtn").length === 2);
  check("default view: large cards selected", D(v).querySelectorAll("#views .vbtn")[0].classList.contains("on") && D(v).querySelector(".grid").className === "grid");
  check("view buttons have labels", [...D(v).querySelectorAll("#views .vbtn")].map(b => b.getAttribute("aria-label")).join("|") === "Large cards|Two per row");
  check("default cards are not compact, keep desc/meta", !D(v).querySelector(".card.compact") && !!D(v).querySelector(".card .meta"));

  D(v).querySelectorAll("#views .vbtn")[1].click();
  check("two per row: .grid.two with 3 compact cards", !!D(v).querySelector(".grid.two") && D(v).querySelectorAll(".card.compact").length === 3);
  check("two per row: button now selected", D(v).querySelectorAll("#views .vbtn")[1].classList.contains("on") && D(v).querySelectorAll("#views .vbtn")[1].getAttribute("aria-pressed") === "true");
  const cc = [...D(v).querySelectorAll(".card.compact")];
  check("compact: no desc, meta or store link", !D(v).querySelector(".card.compact .desc") && !D(v).querySelector(".card.compact .meta") && !D(v).querySelector(".card.compact .store"));
  check("compact: Reserve button, price and 20% badge kept", cc[0].querySelector("button.act").textContent === "Reserve" && cc[0].querySelector(".price").textContent.includes("80") && cc[0].querySelector(".badge.drop").textContent.includes("20%"));
  check("compact: statuses kept", cc[1].querySelector(".badge.status").textContent === "Reserved" && cc[2].querySelector(".badge.status").textContent === "Got it");
  check("compact: HTML still not injected", !D(v).querySelector(".title b"));
  check("choice saved in localStorage", v.window.localStorage.getItem("wl_view") === "grid");
  cc[0].querySelector("button.act").click();
  await sleep(100);
  check("compact: Reserve works and view stays", D(v).querySelectorAll(".card.compact")[0].querySelector("button.act").textContent === "Undo" && !!D(v).querySelector(".grid.two"));
  pickCat(D(v), "Books");
  check("compact: category filter keeps the view", D(v).querySelectorAll(".card.compact").length === 1 && !!D(v).querySelector(".grid.two"));
  pickCat(D(v), "All");

  // remembered view on the next visit
  let rem = await load("?c=ABCD2345", w => w.localStorage.setItem("wl_view", "grid"));
  check("remembered: opens in two per row", !!D(rem).querySelector(".grid.two") && D(rem).querySelectorAll("#views .vbtn")[1].classList.contains("on"));
  let junk = await load("?c=ABCD2345", w => w.localStorage.setItem("wl_view", "<b>x</b>"));
  check("junk stored value falls back to large cards", D(junk).querySelector(".grid").className === "grid");

  // the removed "images only" view falls back to large cards
  let g2 = await load("?c=ABCD2345", w => w.localStorage.setItem("wl_view", "gallery"));
  check("old images-only choice opens as large cards", D(g2).querySelector(".grid").className === "grid" && D(g2).querySelectorAll(".card").length === 3 && !D(g2).querySelector(".tile"));

  // no categories: drop-down hidden, view buttons still there
  store.noCats = true;
  let nc = await load("?c=ABCD2345");
  store.noCats = false;
  check("no categories: drop-down hidden, view buttons visible", D(nc).getElementById("chips").hidden && !D(nc).getElementById("bar").hidden && D(nc).querySelectorAll("#views .vbtn").length === 2);

  // entry form / unshared list: no view buttons
  let ef = await load("");
  check("entry form: bar hidden", D(ef).getElementById("bar").hidden);
  let gone = await load("?c=ZZZZZZZZ");
  check("unshared list: bar hidden", D(gone).getElementById("bar").hidden);

  // browser with storage blocked
  let blocked = await load("?c=ABCD2345", w => {
    const boom = function () { throw new Error("blocked"); };
    w.Storage.prototype.getItem = boom; w.Storage.prototype.setItem = boom;
  });
  D(blocked).querySelectorAll("#views .vbtn")[1].click();
  check("storage blocked: page still works and view still switches", D(blocked).querySelectorAll(".card.compact").length === 3);

  // empty list
  items.length = 0;
  let em = await load("?c=ABCD2345", w => w.localStorage.setItem("wl_view", "grid"));
  check("empty list: friendly message", D(em).getElementById("view").textContent.includes("Nothing here yet"));

  // ---------- most wanted, notes, sort and filters ----------
  const saved = items.slice();
  items.length = 0;
  items.push(
    {id: 11, url: "https://shop.example/mug", title: "Cheap mug", desc: "Ceramic", image: "https://img.example/m.jpg", price: 12, old: null, cur: "EUR", cat: "Home", got: false, note: "blue one please", size: "M"},
    {id: 12, url: "https://shop.example/watch", title: "Fancy watch", desc: "", image: "https://img.example/w.jpg", price: 300, old: null, cur: "EUR", cat: "Tech", got: false, star: true},
    {id: 13, url: "https://shop.example/book", title: "Book", desc: "", image: "https://img.example/b.jpg", price: 25, old: null, cur: "EUR", cat: "Books", got: false, star: true, note: "<b>hardcover</b> <img src=x onerror=alert(1)>", size: "<b>XL</b>"},
    {id: 14, url: "", title: "Mystery", desc: "", image: "", price: null, old: null, cur: "EUR", cat: "Home", got: false},
    {id: 15, url: "https://shop.example/lamp", title: "Reserved lamp", desc: "", image: "", price: 40, old: null, cur: "EUR", cat: "Home", got: false},
    {id: 16, url: "https://shop.example/old", title: "Old star got", desc: "", image: "", price: 10, old: null, cur: "EUR", cat: "Tech", got: true, star: true},
    {id: 0, kind: "about", about: "Size M\nNo candles <b>please</b>", url: "", title: ""}
  );
  store.reservations = { 15: "other", 12: "other" };
  const titles = x => [...D(x).querySelectorAll(".card .title")].map(e => e.textContent);
  const fire = (x, el, type) => el.dispatchEvent(new x.window.Event(type, {bubbles: true}));

  let ms = await load("?c=ABCD2345");
  check("about item is not shown as a wish", D(ms).querySelectorAll(".card").length === 6 && !titles(ms).includes(""));
  check("note for friends shown with line breaks, as text", D(ms).getElementById("about").textContent === "Size M\nNo candles <b>please</b>" && !D(ms).getElementById("about").hidden && !D(ms).querySelector("#about b"));
  check("default order: most wanted first, then as listed", titles(ms).join("|") === "Fancy watch|Book|Cheap mug|Mystery|Reserved lamp|Old star got");
  const mc = [...D(ms).querySelectorAll(".card")];
  check("star badge on starred, not on others or on got ones", !!mc[0].querySelector(".badge.star") && !!mc[1].querySelector(".badge.star") && !mc[2].querySelector(".badge.star") && !mc[5].querySelector(".badge.star"));
  check("star label text", mc[0].querySelector(".badge.star .lbl").textContent === "Most wanted" && mc[0].querySelector(".badge.star").getAttribute("aria-label") === "Most wanted");
  check("star + reserved: both badges in one corner stack, star first", mc[0].querySelectorAll(".badges > .badge").length === 2 && mc[0].querySelector(".badges").firstChild.classList.contains("star") && mc[0].querySelector(".badge.status").textContent === "Reserved");
  check("reserved without star: status badge only", mc[4].querySelectorAll(".badges > .badge").length === 1 && mc[4].querySelector(".badge.status").textContent === "Reserved");
  check("no badges container when nothing to show", !mc[3].querySelector(".badges"));
  check("note shown on card", [...D(ms).querySelectorAll(".wnote")].some(n => n.textContent === "Note: blue one please"));
  check("note with html stays text", !D(ms).querySelector(".wnote b") && !D(ms).querySelector(".wnote img"));

  check("filter panel hidden at first", D(ms).getElementById("tools").hidden && D(ms).getElementById("filterBtn").getAttribute("aria-expanded") === "false");
  D(ms).getElementById("filterBtn").click();
  check("filter button opens panel", !D(ms).getElementById("tools").hidden && D(ms).getElementById("filterBtn").getAttribute("aria-expanded") === "true");
  check("no dot while everything is default", D(ms).getElementById("filterDot").hidden);

  const sel = D(ms).getElementById("sort");
  sel.value = "asis"; fire(ms, sel, "change");
  check("sort: as listed", titles(ms).join("|") === "Cheap mug|Fancy watch|Book|Mystery|Reserved lamp|Old star got");
  check("sort choice saved", ms.window.localStorage.getItem("wl_sort") === "asis" && !D(ms).getElementById("filterDot").hidden);
  sel.value = "low"; fire(ms, sel, "change");
  check("sort: price low to high, no price last", titles(ms).join("|") === "Old star got|Cheap mug|Book|Reserved lamp|Fancy watch|Mystery");
  sel.value = "high"; fire(ms, sel, "change");
  check("sort: price high to low, no price last", titles(ms).join("|") === "Fancy watch|Reserved lamp|Book|Cheap mug|Old star got|Mystery");
  sel.value = "star"; fire(ms, sel, "change");

  const mp = D(ms).getElementById("maxp");
  mp.value = "30"; fire(ms, mp, "input");
  check("max price 30: dearer ones hidden, no-price ones stay", titles(ms).join("|") === "Book|Cheap mug|Mystery|Old star got");
  check("dot shows when a filter is on", !D(ms).getElementById("filterDot").hidden);
  mp.value = "abc"; fire(ms, mp, "input");
  check("junk in max price = no limit", titles(ms).length === 6);
  mp.value = "30"; fire(ms, mp, "input");
  const av = D(ms).getElementById("avail");
  av.click();
  check("only available: reserved by others and got are hidden", titles(ms).join("|") === "Book|Cheap mug|Mystery");
  check("filter keeps the panel focus (input not rebuilt)", D(ms).getElementById("maxp") === mp && mp.value === "30");
  pickCat(D(ms), "Tech");
  check("filters leaving nothing: message and clear button", D(ms).getElementById("view").textContent.includes("No wishes match your filters") && !!D(ms).querySelector("#view button.act"));
  D(ms).querySelector("#view button.act").click();
  check("clear filters resets budget and availability", mp.value === "" && !av.checked && titles(ms).length === 2 /* Tech: watch + old */);
  pickCat(D(ms), "All");
  D(ms).getElementById("filterBtn").click();
  check("panel closes again", D(ms).getElementById("tools").hidden);

  // sort is remembered on the next visit; junk is ignored
  let sr = await load("?c=ABCD2345", w => w.localStorage.setItem("wl_sort", "low"));
  check("remembered sort applies", D(sr).getElementById("sort").value === "low" && titles(sr)[0] === "Old star got");
  let sj = await load("?c=ABCD2345", w => w.localStorage.setItem("wl_sort", "<x>"));
  check("junk sort falls back to most wanted first", titles(sj)[0] === "Fancy watch");

  // other views
  let cv = await load("?c=ABCD2345", w => w.localStorage.setItem("wl_view", "grid"));
  const cc2 = [...D(cv).querySelectorAll(".card.compact")];
  check("compact: star badge + note shown", !!cc2[0].querySelector(".badge.star") && [...D(cv).querySelectorAll(".card.compact .wnote")].length === 2);

  // no note, no about
  items.splice(items.findIndex(i => i.kind === "about"), 1);
  let na = await load("?c=ABCD2345");
  check("no note: box hidden", D(na).getElementById("about").hidden && D(na).getElementById("about").textContent === "");

  // entry form hides the new controls
  let ef2 = await load("");
  check("entry form: tools and about hidden", D(ef2).getElementById("tools").hidden && D(ef2).getElementById("about").hidden && D(ef2).getElementById("bar").hidden);

  // ---------- size ----------
  items.length = 0;
  items.push(
    {id: 21, url: "https://shop.example/a", title: "Shirt", desc: "", image: "", price: 30, old: null, cur: "EUR", cat: "Home", got: false, size: "M", note: "navy"},
    {id: 22, url: "https://shop.example/b", title: "Hat", desc: "", image: "", price: 15, old: null, cur: "EUR", cat: "Home", got: false, size: "<b>XL</b>"},
    {id: 23, url: "https://shop.example/c", title: "Mug", desc: "", image: "", price: 9, old: null, cur: "EUR", cat: "Home", got: false}
  );
  store.reservations = {};
  let sz = await load("?c=ABCD2345");
  const szc = [...D(sz).querySelectorAll(".card")];
  check("size tag shown with the size", szc.length === 3 && szc[0].querySelector(".sizetag").textContent === "Size M");
  check("no size, no tag", !szc[2].querySelector(".sizetag"));
  check("size is shown as text only", szc[1].querySelector(".sizetag").textContent === "Size <b>XL</b" && !D(sz).querySelector(".sizetag b") && !D(sz).querySelector(".sizetag *"));
  check("size tag comes before the note", szc[0].querySelector(".sizetag").nextElementSibling.classList.contains("wnote"));
  let szg = await load("?c=ABCD2345", w => w.localStorage.setItem("wl_view", "grid"));
  check("two per row: size tag shown too", [...D(szg).querySelectorAll(".card.compact .sizetag")].length === 2);

  // ---------- invitation page (?j=KEY) ----------
  {
    const KEY = "ABCDEFGHJKLMNPQRSTUV";
    store.calls.length = 0;
    let iv = await load("?j=" + KEY.toLowerCase());
    let idoc = iv.window.document;
    check("invite: asks wl_sh_info with the upper-cased key", store.calls.length === 1 && store.calls[0][0] === "wl_sh_info" && store.calls[0][1].p_key === KEY);
    check("invite: no list is loaded and nothing is polled", !store.calls.some(c => c[0] === "wl_get_list" || c[0] === "wl_get_reservations"));
    check("invite: heading, then the list name with a gift", idoc.querySelector(".inv h2").textContent === "You’re invited to add gifts for:" && idoc.querySelector(".inv .listname").textContent === "Baby registry 🎁");
    check("invite: wish count shown", [...idoc.querySelectorAll(".inv p")].some(p => p.textContent === "3 wishes so far"));
    const openBtn = idoc.querySelector(".inv a.act");
    check("invite: Open in the app goes to wishlist://join?j=KEY", !!openBtn && openBtn.getAttribute("href") === "wishlist://join?j=" + KEY);
    const dl = idoc.querySelector(".inv a.act.outline");
    check("invite: Download app is a plain link to the latest release file", !!dl && dl.textContent === "Download app" && dl.getAttribute("href") === "https://github.com/AndroidEnthusiast/Wishlist/releases/download/latest/Wishlist.apk");
    check("invite: Download app asks for Wishlist.apk and opens no new page", dl.getAttribute("download") === "Wishlist.apk" && !dl.getAttribute("target"));
    check("invite: the Copy invite link button is gone", !idoc.querySelector(".inv button") && ![...idoc.querySelectorAll(".inv *")].some(e => /copy invite/i.test(e.textContent)));
    const webBtn = [...idoc.querySelectorAll(".inv a.act")].find(a => a.textContent === "Edit in the browser");
    check("invite (Android): Edit in the browser opens edit.html with the same key", !!webBtn && webBtn.getAttribute("href") === "https://androidenthusiast.github.io/Wishlist/edit.html?j=" + KEY, webBtn && webBtn.getAttribute("href"));
    check("invite (Android): the page does not jump away", iv.navs.length === 0);
    check("invite: the old 'not available yet' text is gone", !/not available yet/i.test(idoc.body.textContent));
    check("invite: three buttons (app, download, browser), no wish UI", idoc.querySelectorAll(".inv .btns > a.act").length === 3 && idoc.getElementById("bar").hidden && !idoc.querySelector(".card") && !idoc.querySelector("form.code"));
    check("invite: title and header updated", idoc.title === "Invitation: Baby registry" && idoc.getElementById("name").textContent === "Wishlist invitation");
    check("invite: no long help text at the bottom", !idoc.querySelector(".steps") && !/Already have the Wishlist app/.test(idoc.body.textContent));
    check("invite: nothing on the page mentions Join with a link", !/join with a link/i.test(idoc.body.textContent));
    check("invite: the 'copied' message is hidden until Download app is tapped", idoc.querySelector(".inv .copied").hidden === true);

    // ----- tapping Download app puts the invitation on the clipboard, and never blocks the download -----
    const URLX = "https://androidenthusiast.github.io/Wishlist/?j=" + KEY;
    async function tap(win) {
      const doc = win.document;
      let prevented = null;
      // stands in for the browser's own "start the download": it must still be allowed to happen
      doc.addEventListener("click", e => { prevented = e.defaultPrevented; e.preventDefault(); });
      doc.querySelector(".inv a.act.outline").dispatchEvent(new win.MouseEvent("click", { bubbles: true, cancelable: true }));
      await sleep(30);
      return prevented;
    }
    const clipOK = w => { w.__copied = []; Object.defineProperty(w.navigator, "clipboard", { configurable: true, value: { writeText: t => { w.__copied.push(t); return Promise.resolve(); } } }); };
    let c1 = await load("?j=" + KEY, clipOK);
    check("invite: nothing is copied before the tap", c1.window.__copied.length === 0);
    const pv1 = await tap(c1.window);
    check("invite: tap copies the invite link (the one the app understands)", c1.window.__copied.length === 1 && c1.window.__copied[0] === URLX);
    check("invite: the tap does not block the download", pv1 === false);
    const msg1 = c1.window.document.querySelector(".inv .copied");
    check("invite: a message says the invitation was copied and names the list", msg1.hidden === false && msg1.textContent.includes("Invitation copied") && msg1.textContent.includes("“Baby registry”"));
    check("invite: copied text parses back to the same key (same rule as the app)", /[?&]J=([A-HJ-NP-Z2-9]{20})(?![A-Z0-9])/.exec(c1.window.__copied[0].toUpperCase())[1] === KEY);
    await tap(c1.window);
    check("invite: a second tap copies again (still one link)", c1.window.__copied.length === 2 && c1.window.__copied[1] === URLX);

    // clipboard API refuses (for example no permission): the old way is used
    let c2 = await load("?j=" + KEY, w => {
      w.__exec = [];
      Object.defineProperty(w.navigator, "clipboard", { configurable: true, value: { writeText: () => Promise.reject(new Error("denied")) } });
      w.document.execCommand = cmd => { const t = w.document.querySelector("textarea"); w.__exec.push([cmd, t && t.value]); return true; };
    });
    const pv2 = await tap(c2.window);
    check("invite: clipboard API refused -> falls back to the old copy command", c2.window.__exec.length === 1 && c2.window.__exec[0][0] === "copy" && c2.window.__exec[0][1] === URLX);
    check("invite: ...and leaves no stray text box behind, download not blocked", !c2.window.document.querySelector("textarea") && pv2 === false);

    // no clipboard API at all
    let c3 = await load("?j=" + KEY, w => { w.__exec = []; w.document.execCommand = cmd => { const t = w.document.querySelector("textarea"); w.__exec.push([cmd, t && t.value]); return true; }; });
    await tap(c3.window);
    check("invite: no clipboard API -> old copy command with the link", c3.window.__exec.length === 1 && c3.window.__exec[0][1] === URLX);

    // everything throws: the tap still works and the download is not blocked
    let c4 = await load("?j=" + KEY, w => {
      Object.defineProperty(w.navigator, "clipboard", { configurable: true, value: { writeText: () => { throw new Error("boom"); } } });
      w.document.execCommand = () => { throw new Error("boom"); };
    });
    const pv4 = await tap(c4.window);
    check("invite: copying fails completely -> no crash, download not blocked", pv4 === false && c4.window.document.querySelector(".inv a.act.outline") !== null);

    // "Open in the app" does not copy anything
    let c5 = await load("?j=" + KEY, clipOK);
    c5.window.document.addEventListener("click", e => e.preventDefault());
    c5.window.document.querySelector(".inv a.act").dispatchEvent(new c5.window.MouseEvent("click", { bubbles: true, cancelable: true }));
    await sleep(30);
    check("invite: Open in the app copies nothing", c5.window.__copied.length === 0);

    store.inviteName = "<img src=x onerror=alert(1)> Our <b>list</b>";
    store.inviteCount = 1;
    let ih = await load("?j=" + KEY);
    check("invite: list name is text, never HTML", !ih.window.document.querySelector(".inv img, .inv b") && ih.window.document.querySelector(".inv .listname").textContent.includes("<img src=x"));
    check("invite: one wish wording", [...ih.window.document.querySelectorAll(".inv p")].some(p => p.textContent === "1 wish so far"));
    await tap(ih.window);
    check("invite: hostile list name stays text in the 'copied' message and help too", !ih.window.document.querySelector(".inv img, .inv b") && ih.window.document.querySelector(".inv .copied").textContent.includes("<img src=x"));
    delete store.inviteName; delete store.inviteCount;

    store.calls.length = 0;
    let iw = await load("?j=" + "ZYXWVUTSRQPNMLKJHGFE");
    check("invite: unknown key says it doesn't work", iw.window.document.querySelector(".inv h2").textContent === "This invitation doesn’t work" && !iw.window.document.querySelector(".inv a.act"));
    store.calls.length = 0;
    let ib = await load("?j=short");
    check("invite: malformed key is refused without asking the server", ib.window.document.querySelector(".inv h2").textContent === "This invitation doesn’t work" && store.calls.length === 0);
    let ie = await load("?j=");
    check("invite: empty key is refused", ie.window.document.querySelector(".inv h2").textContent === "This invitation doesn’t work");
    // iPhone / computer: the invitation page, with only "Edit in the browser" (no app for them)
    store.calls.length = 0;
    let ip = await load("?j=" + KEY, null, IPHONE_UA);
    await sleep(100);
    const ipBtns = [...ip.window.document.querySelectorAll(".inv a.act")];
    check("invite (iPhone): shows the invitation, not redirected", ip.navs.length === 0 && ip.window.document.querySelector(".inv h2").textContent === "You’re invited to add gifts for:" && /🎁/.test(ip.window.document.querySelector(".inv .listname").textContent), ip.navs);
    check("invite (iPhone): only the Edit in the browser button, to the editor", ipBtns.length === 1 && ipBtns[0].textContent === "Edit in the browser" && ipBtns[0].getAttribute("href") === "https://androidenthusiast.github.io/Wishlist/edit.html?j=" + KEY, ipBtns.map(b => b.textContent));
    check("invite: editorUrl points at edit.html next to this page", ip.window.editorUrl(KEY) === "https://androidenthusiast.github.io/Wishlist/edit.html?j=" + KEY);
    let ic = await load("?j=" + KEY, null, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36");
    await sleep(100);
    check("invite (computer): invitation with only Edit in the browser", ic.navs.length === 0 && [...ic.window.document.querySelectorAll(".inv a.act")].map(b => b.textContent).join() === "Edit in the browser");
    let ibad = await load("?j=short", null, IPHONE_UA);
    check("invite (iPhone): a broken link is explained, not redirected", ibad.navs.length === 0 && ibad.window.document.querySelector(".inv h2").textContent === "This invitation doesn’t work");
    // a friends link keeps working next to it
    let fl = await load("?c=ABCD2345");
    check("friends link still opens the list", fl.window.document.querySelectorAll(".card").length >= 1 && !fl.window.document.querySelector(".inv"));
    let both = await load("?c=ABCD2345&j=" + KEY);
    check("both given: the invitation wins", !!both.window.document.querySelector(".inv h2") && !both.window.document.querySelector(".card"));
  }

  items.length = 0; saved.forEach(i => items.push(i));
  store.reservations = {};

  console.log(fails ? fails + " FAILED" : "ALL PASSED");
  process.exit(fails ? 1 : 0);
})();
