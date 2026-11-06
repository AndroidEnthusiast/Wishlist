// The friend link page web/friend/index.html (2.139) in Chromium: the name as text, the code, the app link, a broken link.
const path = require("path");
const { chromium } = require("playwright");
const { serveWeb } = require("../e2e/fake.js");
let fails = 0, total = 0;
const check = (n, c, x) => { total++; if (!c) { fails++; console.log("FAIL " + n + (x !== undefined ? " -> " + JSON.stringify(x) : "")); } };
(async () => {
  const web = await serveWeb(path.join(__dirname, "..", "..", "web"));
  const b = await chromium.launch();
  const p = await b.newPage();
  await p.goto(web.base + "friend/?f=abc234&n=" + encodeURIComponent("<b>Jorai</b>"));
  check("name shown as text", (await p.locator("#title").innerText()) === "<b>Jorai</b> wants to be friends on Wishlist 🎁");
  check("no HTML from the link", (await p.locator("#title b").count()) === 0);
  check("code shown", (await p.locator("#code").innerText()) === "ABC-234");
  check("app link", (await p.locator("#open").getAttribute("href")) === "wishlist://friend?f=ABC234&n=%3Cb%3EJorai%3C%2Fb%3E");
  await p.goto(web.base + "friend/?f=ab");
  check("broken link says so", /isn't complete/.test(await p.locator("#lead").innerText()));
  await b.close(); web.srv.close();
  console.log(fails ? fails + " FAILED of " + total : "ALL PASSED (" + total + " checks)");
  process.exit(fails ? 1 : 0);
})();
