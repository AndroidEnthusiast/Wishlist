// Every text in the web pages and in the app has a Dutch version, with the same placeholders.
// Web: T("…") in edit.js / index.html, and the fixed texts in the HTML, against web/nl.js.
// App: tr("…") in the Kotlin files, against NlStrings.kt.
const fs = require("fs"), path = require("path");
const ROOT = path.join(__dirname, "..", "..");
let fails = 0, total = 0;
function check(name, cond, extra) { total++; if (!cond) { fails++; console.log("FAIL " + name + (extra !== undefined ? "  -> " + JSON.stringify(extra) : "")); } }

// web
const win = {}; new Function("window", fs.readFileSync(path.join(ROOT, "web", "nl.js"), "utf8"))(win);
const NLW = win.WL_NL;
const webKeys = new Set();
for (const f of ["web/edit.js", "web/index.html"]) {
  const s = fs.readFileSync(path.join(ROOT, f), "utf8");
  for (const m of s.matchAll(/(?<![A-Za-z_.])T\("((?:[^"\\]|\\.)*)"/g)) webKeys.add(m[1]);
}
for (const k of ["Newest first", "Price: low to high", "Price: high to low", "Name A–Z", "Price: low", "Price: high", "A–Z", "Large cards", "Two per row", "Images only", "All"]) webKeys.add(k);
const missingWeb = [...webKeys].filter(k => !(k in NLW));
check("web: every text has a Dutch version", missingWeb.length === 0, missingWeb);
const ph = s => (s.match(/\{\d\}/g) || []).sort().join();
const badWeb = Object.keys(NLW).filter(k => ph(k) !== ph(NLW[k]));
check("web: same placeholders in Dutch", badWeb.length === 0, badWeb);

// app
const dir = path.join(ROOT, "app", "src", "main", "java", "com", "wishlist", "app");
const nlKt = fs.readFileSync(path.join(dir, "NlStrings.kt"), "utf8");
const NLA = {};
for (const m of nlKt.matchAll(/^\s*"((?:[^"\\]|\\.)*)" to "((?:[^"\\]|\\.)*)",?$/gm)) NLA[m[1]] = m[2];
const appKeys = new Set();
for (const f of fs.readdirSync(dir)) {
  if (!f.endsWith(".kt") || f === "NlStrings.kt" || f === "I18n.kt") continue;
  const s = fs.readFileSync(path.join(dir, f), "utf8");
  for (const m of s.matchAll(/(?<![A-Za-z_.])tr\("((?:[^"\\]|\\.)*)"/g)) appKeys.add(m[1]);
}
const missingApp = [...appKeys].filter(k => !(k in NLA));
check("app: every text has a Dutch version", missingApp.length === 0, missingApp);
const pha = s => (s.match(/%(\d\\\$)?[sd%]/g) || []).sort().join();
const badApp = Object.keys(NLA).filter(k => pha(k) !== pha(NLA[k]));
check("app: same placeholders in Dutch", badApp.length === 0, badApp);
check("app: the dictionary was read", Object.keys(NLA).length > 200, Object.keys(NLA).length);

console.log(fails === 0 ? "ALL PASSED (" + total + " checks)" : fails + " FAILED of " + total);
process.exit(fails ? 1 : 0);
