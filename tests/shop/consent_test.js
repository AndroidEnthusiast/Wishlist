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
  // bol.com after 2.125: a grey layer plus an invisible one on top that waits for a tap to close the pop-up.
  catcher: `<style>body{overflow:hidden}</style>${PRODUCTS}<div id="veil" style="${VEIL}"></div><div id="catch" style="position:fixed;inset:0;z-index:100"></div><script>document.getElementById('catch').addEventListener('click',function(){window.closedBySite=1;document.getElementById('veil').remove();this.remove();document.body.style.overflow='auto';});</script>`,
  deafCatcher: `${PRODUCTS}<div id="veil" style="${VEIL}"></div><div id="catch" style="position:fixed;inset:0;z-index:100"></div>`,
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
  await sleep(300);
  check("empty grey layer hidden (after one tap on it)", !(await shown("#veil")));
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
  await sleep(300);
  check("body pinned by the shop: unpinned", !(await shown("#veil")) && (await p.evaluate(() => getComputedStyle(document.body).position)) === "static");

  await open(PAGES.late);
  await guard();
  await sleep(300);
  await p.evaluate(() => { const v = document.createElement("div"); v.id = "veil"; v.style.cssText = "position:fixed;inset:0;background:rgba(80,80,80,.5);z-index:99"; document.body.appendChild(v); });
  await sleep(250);
  check("layer that appears after loading: hidden", !(await shown("#veil")));

  // the invisible layer that waits for a tap: tapped by the guard, so the shop closes its pop-up itself
  await open(PAGES.catcher);
  await guard();
  await sleep(500);
  check("invisible layer over a grey one: tapped once, the shop closes its pop-up", (await p.evaluate(() => window.closedBySite)) === 1);
  let tapped = true;
  try { await p.click("#buy", { timeout: 1500 }); } catch (e) { tapped = false; }
  check("then the first real tap reaches the page", tapped && (await p.evaluate(() => window.bought)) === 1);
  await open(PAGES.deafCatcher);
  await guard();
  await sleep(600);
  tapped = true;
  try { await p.click("#buy", { timeout: 1500 }); } catch (e) { tapped = false; }
  check("an invisible layer that ignores the tap: hidden, the page can be tapped", tapped && !(await shown("#catch")));

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

  // ---- 2.130: no flicker on a new page (owner, bol.com) ----
  // The guard runs at the start of the page (as addDocumentStartJavaScript does in the app). A dark empty layer that the
  // shop adds while the page loads, or later as a new "page" in the same tab, must never be drawn: every frame is
  // sampled with requestAnimationFrame from the very start.
  const SAMPLER = `window.__seen=[];(function f(){var v=document.getElementById('veil');if(v){var s=getComputedStyle(v);if(s.display!=='none'&&s.visibility!=='hidden'&&+s.opacity>0.05)window.__seen.push(performance.now());}requestAnimationFrame(f);})();`;
  async function startPage(body) {
    if (p) await p.close();
    p = await b.newPage({ viewport: { width: 400, height: 800 } });
    await p.addInitScript(GUARD);
    await p.addInitScript(SAMPLER);
    await p.route("https://shop.test/**", r => r.fulfill({ status: 200, headers: { "content-type": "text/html" }, body }));
    await p.goto("https://shop.test/page");
  }
  await startPage(`<html><body>${PRODUCTS}<div id="veil" style="${VEIL}"></div></body></html>`);
  await sleep(400);
  check("a layer in the page itself is never drawn", (await p.evaluate(() => window.__seen.length)) === 0, await p.evaluate(() => window.__seen.length));
  check("...and hidden for good", !(await shown("#veil")));
  await startPage(`<html><body>${PRODUCTS}<script>setTimeout(function(){var v=document.createElement('div');v.id='veil';v.setAttribute('style','${VEIL}');document.body.appendChild(v);},120);</script></body></html>`);
  await sleep(500);
  check("a layer added a moment later (a new page in the shop) is never drawn", (await p.evaluate(() => window.__seen.length)) === 0, await p.evaluate(() => window.__seen.length));
  await startPage(`<html><body>${PRODUCTS}<div id="veil" style="display:none;${VEIL}"></div><script>setTimeout(function(){document.getElementById('veil').style.display='block';},120);</script></body></html>`);
  await sleep(500);
  check("a hidden layer that is switched on is never drawn", (await p.evaluate(() => window.__seen.length)) === 0, await p.evaluate(() => window.__seen.length));
  let tapped2 = true;
  try { await p.click("#buy", { timeout: 1500 }); } catch (e) { tapped2 = false; }
  check("...and the page can be tapped", tapped2);
  await startPage(`<html><body>${PRODUCTS}<script>setTimeout(function(){var v=document.createElement('div');v.id='veil';v.setAttribute('style','${VEIL}');document.body.appendChild(v);var m=document.createElement('div');m.id='m';m.setAttribute('style','position:fixed;top:20%;left:5%;right:5%;height:60%;background:#fff;z-index:100');m.textContent='Choose a size';document.body.appendChild(m);},120);</script></body></html>`);
  await sleep(500);
  check("a real pop-up keeps its dark background", (await shown("#veil")) && (await p.evaluate(() => +getComputedStyle(document.getElementById('veil')).opacity)) === 1 && (await shown("#m")));
  await startPage(`<html><body>${PRODUCTS}<script>setTimeout(function(){var v=document.createElement('div');v.id='veil';v.setAttribute('style','${VEIL}');document.body.appendChild(v);setTimeout(function(){v.textContent='Loading your basket…';},150);},100);</script></body></html>`);
  await sleep(600);
  check("a layer that gets text a moment later shows again", (await shown("#veil")) && (await p.evaluate(() => +getComputedStyle(document.getElementById('veil')).opacity)) === 1);
  // 2.131 (owner: bol.com could scroll but nothing could be tapped after 2.130): bol.com's set-up from the start of a
  // page: a grey layer plus an invisible one on top that closes the pop-up (and unlocks the page) when tapped, and a
  // page that stays locked (pointer-events off) until then. The shop must close it itself and every button must work.
  for (const delay of [0, 120]) {
    const add = `var v=document.createElement('div');v.id='veil';v.setAttribute('style','${VEIL}');var c=document.createElement('div');c.id='catch';c.setAttribute('style','position:fixed;inset:0;z-index:100');c.addEventListener('click',function(){window.closedBySite=1;v.remove();c.remove();document.getElementById('main').style.pointerEvents='';});document.getElementById('main').style.pointerEvents='none';document.body.appendChild(v);document.body.appendChild(c);`;
    await startPage(`<html><body><div id="main">${PRODUCTS}</div><script>${delay ? "setTimeout(function(){" + add + "}," + delay + ");" : add}</script></body></html>`);
    await sleep(700);
    check("bol.com set-up (" + (delay ? "a new page" : "page start") + "): the grey layer is never drawn", (await p.evaluate(() => window.__seen.length)) === 0, await p.evaluate(() => window.__seen.length));
    check("bol.com set-up (" + (delay ? "a new page" : "page start") + "): the shop closed its own pop-up", (await p.evaluate(() => window.closedBySite)) === 1);
    let ok = true;
    try { await p.click("#buy", { timeout: 1500 }); } catch (e) { ok = false; }
    check("bol.com set-up (" + (delay ? "a new page" : "page start") + "): buttons work", ok && (await p.evaluate(() => window.bought)) === 1);
  }

  // 2.131: never inside a frame of the page (ads, payments, cookie frames), only the page itself.
  await startPage(`<html><body>${PRODUCTS}<iframe id="f" srcdoc="<div id=v style='position:fixed;inset:0;background:rgba(0,0,0,.45)'></div>"></iframe></body></html>`);
  await sleep(400);
  const fr = p.frames().find(f => f !== p.mainFrame());
  check("the guard leaves frames inside the page alone", !!fr && (await fr.evaluate(() => !window.__wlg && getComputedStyle(document.getElementById('v')).opacity === '1')));

  await b.close();
  console.log(fails ? fails + " FAILED of " + total : "ALL SHOP SCRIPT CHECKS OK (" + total + " checks)");
  process.exit(fails ? 1 : 0);
})();
