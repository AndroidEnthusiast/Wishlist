// Checks the shop browser's page scripts in Chromium on made-up pages (it cannot load real shops from here):
//  - CONSENT_JS (app/src/main/java/com/wishlist/app/Shops.kt): refuse cookie questions, or open their settings and save.
//  - GUARD_JS (same file, 2.125): empty dark layers are hidden and kept away (bol.com), without breaking the shop.
// Run from tests/: node shop/consent_test.js
const fs = require("fs");
const path = require("path");
const { chromium } = require("playwright");
const src = fs.readFileSync(process.argv[2] || path.join(__dirname, "..", "..", "app/src/main/java/com/wishlist/app/Shops.kt"), "utf8");
const take = name => src.split("private const val " + name + ' = """')[1].split('"""')[0];
const CONSENT = take("CONSENT_JS"), GUARD = take("GUARD_JS");
let fails = 0, total = 0;
const check = (n, c, x) => { total++; console.log((c ? "PASS " : "FAIL ") + n + (c ? "" : " -> " + JSON.stringify(x))); if (!c) fails++; };
const sleep = ms => new Promise(r => setTimeout(r, ms));
const VEIL = `position:fixed;inset:0;background:rgba(0,0,0,.45);z-index:99`;
const PRODUCTS = `<div style="height:3000px"><p>Products</p><button id="buy" onclick="window.bought=1">Buy</button></div>`;
const PAGES = {
  refuse: `<div id="cookie-banner" style="position:fixed;bottom:0;left:0;right:0;background:#fff">We use cookies <button onclick="window.r='acc'">Alles accepteren</button><button onclick="window.r='ref'">Alleen noodzakelijke cookies</button></div>`,
  twostep: `<div role="dialog" aria-modal="true" style="position:fixed;inset:20px;background:#fff">Bol gebruikt cookies. <button onclick="window.r='acc'">Accepteren</button><button onclick="window.r='set';document.getElementById('s').hidden=false">Instellingen aanpassen</button><div id="s" hidden><button onclick="window.r='saved'">Voorkeuren opslaan</button></div></div>`,
  notcookie: `<p>Return policy</p><button onclick="window.r='ref'">Weigeren</button>`,
  shadow: `<div id="host"></div><script>const r=document.getElementById('host').attachShadow({mode:'open'});r.innerHTML='<div class="consent-layer" style="position:fixed;bottom:0">Cookies? <button id=b>Reject all</button></div>';r.getElementById('b').onclick=()=>window.r='ref';</script>`,
  // bol.com in the owner's recording: a grey layer with nothing on it, back on every page.
  veil: `<style>body{overflow:hidden}</style>${PRODUCTS}<div id="veil" style="${VEIL}"></div>`,
  fixedbody: `<style>body{position:fixed;top:-200px;width:100%}</style>${PRODUCTS}<div id="veil" style="${VEIL}"></div>`,
  late: `${PRODUCTS}`,
  modal: `${PRODUCTS}<div id="veil" style="${VEIL}"></div><div id="m" style="position:fixed;top:20%;left:5%;right:5%;height:60%;background:#fff;z-index:100">Choose a size <button>M</button></div>`,
  sheet: `${PRODUCTS}<div id="veil" style="${VEIL}"></div><div id="m" style="position:fixed;bottom:0;left:0;right:0;height:40%;background:#fff;z-index:100">Delivery <button>OK</button></div>`,
  drawer: `${PRODUCTS}<div id="veil" style="${VEIL}"></div><nav id="m" style="position:fixed;top:0;bottom:0;left:0;width:75%;background:#fff;z-index:100">Menu <a href="/x">Toys</a></nav>`,
  invisible: `${PRODUCTS}<div id="veil" style="position:fixed;inset:0;z-index:99"></div>`,
  veilWithText: `${PRODUCTS}<div id="veil" style="${VEIL};color:#fff">Loading your basket…</div>`,
};
(async () => {
  const b = await chromium.launch();
  let p;
  const open = async html => { if (p) await p.close(); p = await b.newPage({ viewport: { width: 400, height: 800 } }); await p.setContent(html); };
  const consent = () => p.evaluate(CONSENT);
  const guard = () => p.evaluate(GUARD);
  const shown = sel => p.evaluate(s => { const e = document.querySelector(s); return !!e && getComputedStyle(e).display !== "none"; }, sel);

  // ---- cookie questions ----
  await open(PAGES.refuse);
  check("refuses with the necessary-only button", (await consent()) === "refused" && (await p.evaluate(() => window.r)) === "ref");
  await open(PAGES.twostep);
  const a1 = await consent(), a2 = await consent();
  check("no refuse button: settings, then save", a1 === "settings" && a2 === "saved" && (await p.evaluate(() => window.r)) === "saved", [a1, a2]);
  await open(PAGES.notcookie);
  check("'Weigeren' outside a cookie question not tapped", (await consent()) === "" && (await p.evaluate(() => window.r)) === undefined);
  await open(PAGES.shadow);
  check("cookie question in a shadow root", (await consent()) === "refused" && (await p.evaluate(() => window.r)) === "ref");
  await open(PAGES.refuse);
  for (let i = 0; i < 6; i++) await consent();
  check("at most four taps per page", (await p.evaluate(() => window.__wlc.n)) === 4);

  // ---- empty layers ----
  await open(PAGES.veil);
  check("guard installs once", (await guard()) === "on" && (await guard()) === "on");
  check("empty grey layer hidden at once", !(await shown("#veil")));
  check("scrolling back on", (await p.evaluate(() => getComputedStyle(document.body).overflow)) === "auto");
  await p.click("#buy");
  check("the page can be tapped", (await p.evaluate(() => window.bought)) === 1);
  // the shop shows its layer again (as bol.com did): hidden again before it can flicker
  await p.evaluate(() => { document.getElementById("veil").style.display = "block"; });
  await sleep(250);
  check("layer shown again by the shop: hidden again", !(await shown("#veil")));
  await p.evaluate(() => { const v = document.createElement("div"); v.id = "veil2"; v.style.cssText = "position:fixed;inset:0;background:rgba(0,0,0,.45);z-index:99"; document.body.appendChild(v); });
  await sleep(250);
  check("a new layer added later: hidden too", !(await shown("#veil2")));
  // the layer turns into a real pop-up: shown again, and scrolling goes back to how the shop had it
  await p.evaluate(() => { document.getElementById("veil2").remove(); document.getElementById("veil").innerHTML = '<div style="background:#fff;margin:40% 10%">Your basket <button>Close</button></div>'; });
  await sleep(250);
  check("a layer that gets content is shown again", await shown("#veil"));
  check("scrolling back to how the shop had it", (await p.evaluate(() => getComputedStyle(document.body).overflow)) === "hidden");

  await open(PAGES.fixedbody);
  await guard();
  check("body pinned by the shop: unpinned", !(await shown("#veil")) && (await p.evaluate(() => getComputedStyle(document.body).position)) === "static");

  await open(PAGES.late);
  await guard();
  await sleep(300);
  await p.evaluate(() => { const v = document.createElement("div"); v.id = "veil"; v.style.cssText = "position:fixed;inset:0;background:rgba(80,80,80,.5);z-index:99"; document.body.appendChild(v); });
  await sleep(250);
  check("layer that appears after loading: hidden", !(await shown("#veil")));

  for (const [k, what] of [["modal", "a pop-up in the middle"], ["sheet", "a sheet at the bottom"], ["drawer", "a side menu"]]) {
    await open(PAGES[k]);
    await guard();
    await sleep(150);
    check(what + ": its layer is left alone", (await shown("#veil")) && (await shown("#m")));
  }
  await open(PAGES.invisible);
  await guard();
  check("invisible full-screen helper left alone", await shown("#veil"));
  await open(PAGES.veilWithText);
  await guard();
  check("a layer with text on it is left alone", await shown("#veil"));

  await b.close();
  console.log(fails ? fails + " FAILED of " + total : "ALL SHOP SCRIPT CHECKS OK (" + total + " checks)");
  process.exit(fails ? 1 : 0);
})();
