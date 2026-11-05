// Checks the shop browser's cookie script (CONSENT_JS in app/src/main/java/com/wishlist/app/Shops.kt) in Chromium on
// made-up pages: refuse, settings then save, the empty dark layer (bol.com), and what must be left alone.
// Run from tests/: node shop/consent_test.js
const fs = require("fs");
const { chromium } = require("playwright");
const src = fs.readFileSync(process.argv[2] || require("path").join(__dirname, "..", "..", "app/src/main/java/com/wishlist/app/Shops.kt"), "utf8");
const JS = src.split('private const val CONSENT_JS = """')[1].split('"""')[0];
let fails = 0;
const check = (n, c, x) => { console.log((c ? "PASS " : "FAIL ") + n + (c ? "" : " -> " + JSON.stringify(x))); if (!c) fails++; };
const PAGES = {
  refuse: `<div id="cookie-banner" style="position:fixed;bottom:0;left:0;right:0;background:#fff">We use cookies <button id="acc" onclick="window.r='acc'">Alles accepteren</button><button id="ref" onclick="window.r='ref'">Alleen noodzakelijke cookies</button></div>`,
  twostep: `<div role="dialog" aria-modal="true" style="position:fixed;inset:20px;background:#fff">Bol gebruikt cookies. <button onclick="window.r='acc'">Accepteren</button><button onclick="window.r='set';document.getElementById('s').hidden=false">Instellingen aanpassen</button><div id="s" hidden><button onclick="window.r='saved'">Voorkeuren opslaan</button></div></div>`,
  veil: `<style>body{overflow:hidden}</style><p>Products</p><div id="veil" style="position:fixed;inset:0;background:rgba(0,0,0,.6);z-index:99"></div>`,
  working: `<p>Products</p><div style="position:fixed;inset:0;background:rgba(0,0,0,.6);z-index:99"></div><div id="m" style="position:fixed;top:20%;left:5%;right:5%;height:60%;background:#fff;z-index:100">Choose a size <button>M</button></div>`,
  notcookie: `<p>Return policy</p><button onclick="window.r='ref'">Weigeren</button>`,
  shadow: `<div id="host"></div><script>const r=document.getElementById('host').attachShadow({mode:'open'});r.innerHTML='<div class="consent-layer" style="position:fixed;bottom:0">Cookies? <button id=b>Reject all</button></div>';r.getElementById('b').onclick=()=>window.r='ref';</script>`,
  invisible: `<p>Products</p><div style="position:fixed;inset:0;z-index:99"></div>`,
};
(async () => {
  const b = await chromium.launch();
  let p;
  const run = (veil) => p.evaluate(JS.replace("__VEIL__", veil ? "true" : "false"));
  p && await p.close(); p = await b.newPage({ viewport: { width: 400, height: 800 } }); await p.setContent(PAGES.refuse);
  check("refuses with the necessary-only button", (await run(false)) === "refused" && (await p.evaluate(() => window.r)) === "ref");
  p && await p.close(); p = await b.newPage({ viewport: { width: 400, height: 800 } }); await p.setContent(PAGES.twostep);
  const a1 = await run(false), a2 = await run(false);
  check("no refuse button: settings, then save", a1 === "settings" && a2 === "saved" && (await p.evaluate(() => window.r)) === "saved", [a1, a2]);
  p && await p.close(); p = await b.newPage({ viewport: { width: 400, height: 800 } }); await p.setContent(PAGES.veil);
  check("veil left alone before the wait", (await run(false)) === "" && await p.isVisible("#veil"));
  check("empty dark veil removed", (await run(true)) === "veil" && !(await p.isVisible("#veil")) && (await p.evaluate(() => getComputedStyle(document.body).overflow)) === "auto");
  p && await p.close(); p = await b.newPage({ viewport: { width: 400, height: 800 } }); await p.setContent(PAGES.working);
  check("a working pop-up is left alone", (await run(true)) === "" && await p.isVisible("#m"));
  p && await p.close(); p = await b.newPage({ viewport: { width: 400, height: 800 } }); await p.setContent(PAGES.notcookie);
  const nc = await run(true); check("'Weigeren' outside a cookie question not tapped", nc === "" && (await p.evaluate(() => window.r)) === undefined, [nc, await p.evaluate(() => JSON.stringify(window.__wlc))]);
  p && await p.close(); p = await b.newPage({ viewport: { width: 400, height: 800 } }); await p.setContent(PAGES.shadow);
  const sh = await run(false);
  check("cookie question in a shadow root", sh === "refused" && (await p.evaluate(() => window.r)) === "ref", sh);
  p && await p.close(); p = await b.newPage({ viewport: { width: 400, height: 800 } }); await p.setContent(PAGES.invisible);
  check("invisible full-screen helper left alone", (await run(true)) === "");
  p && await p.close(); p = await b.newPage({ viewport: { width: 400, height: 800 } }); await p.setContent(PAGES.refuse);
  for (let i = 0; i < 6; i++) await run(true);
  check("at most four taps per page", (await p.evaluate(() => window.__wlc.n)) === 4);
  await b.close();
  console.log(fails ? fails + " FAILED" : "ALL CONSENT CHECKS OK");
  process.exit(fails ? 1 : 0);
})();
