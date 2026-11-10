// The web pages' half of tests/shared/same-answers.json: web/parse.js and web/occasion.js must give the answers
// listed there. The app checks the same file (app/src/test/java/com/wishlist/app/SameAnswersTest.kt), so the app
// and the web pages can't drift apart.
const { JSDOM } = require("jsdom");
const fs = require("fs");
const path = require("path");
const web = f => fs.readFileSync(path.join(__dirname, "..", "..", "web", f), "utf8");
const cases = JSON.parse(fs.readFileSync(path.join(__dirname, "same-answers.json"), "utf8"));
const dom = new JSDOM("<!doctype html><html><body></body></html>", { runScripts: "outside-only" });
const w = dom.window;
w.eval(web("i18n.js"));
w.eval(web("parse.js"));
w.eval(web("occasion.js"));
let fails = 0, n = 0;
function check(name, cond) { n++; if (!cond) { fails++; console.log("FAIL " + name); } }
for (const [text, want] of cases.toNum) {
  const got = w.WLParse.toNum(text);
  check("toNum " + JSON.stringify(text) + " = " + want + " (got " + got + ")", got === want);
}
for (const [text, host, want] of cases.currency) {
  const got = w.WLParse.currencyOf(text, host);
  check("currency " + text + " on " + host + " = " + want + " (got " + got + ")", got === want);
}
for (const [o, today, want] of cases.occasion) {
  const [y, m, d] = today.split("-").map(Number);
  const got = w.occasionLine(o, new w.Date(y, m - 1, d));
  check("occasion " + JSON.stringify(o) + " on " + today + " = " + JSON.stringify(want) + " (got " + JSON.stringify(got) + ")", got === want);
}
// Whole product pages (2.178): the same page, the same price, currency and "the page named the currency itself".
for (const pg of cases.pages) {
  const d = new w.DOMParser().parseFromString(pg.html, "text/html");
  const got = w.WLParse.parsePage(d, pg.host, pg.status, pg.url);
  check("page: " + pg.name + " price = " + pg.price + " (got " + (got && got.price) + ")", !!got && got.price === pg.price);
  if (got && pg.currency !== undefined) check("page: " + pg.name + " currency = " + pg.currency + " (got " + got.currency + ")", got.currency === pg.currency);
  if (got && pg.sure !== undefined) check("page: " + pg.name + " sure = " + pg.sure + " (got " + got.currencySure + ")", got.currencySure === pg.sure);
  if (got && pg.product !== undefined) check("page: " + pg.name + " product = " + pg.product + " (got " + got.product + ")", got.product === pg.product);
}
console.log((fails ? fails + " of " : "all ") + n + " shared checks " + (fails ? "FAILED" : "passed"));
process.exit(fails ? 1 : 0);
