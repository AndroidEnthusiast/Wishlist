// The daily Import check (docs/decisions/import.md; .github/workflows/import-check.yml): opens each of the owner's
// public test lists (tests/import/lists.json) in a real Chromium like a phone, runs the Import reader
// (web/import/reader.js) on it, and fails when a site no longer gives the wishes it should. The failures are also
// written to import-failures.md for the GitHub issue that tells the owner.
// Run: node tests/import/live.js   (needs Playwright's Chromium and the internet; not part of run-all.sh)
const fs = require("fs");
const path = require("path");
const { chromium, devices } = require("playwright");
const reader = fs.readFileSync(path.join(__dirname, "..", "..", "web", "import", "reader.js"), "utf8");
const lists = JSON.parse(fs.readFileSync(path.join(__dirname, "lists.json"), "utf8")).lists || [];
const out = path.join(__dirname, "..", "..", "import-failures.md");

(async () => {
  if (!lists.length) { console.log("No test lists yet (tests/import/lists.json): nothing to check online."); return; }
  const browser = await chromium.launch();
  const failures = [];
  for (const l of lists) {
    const ctx = await browser.newContext({ ...devices["Pixel 7"], locale: "en-GB" });
    const page = await ctx.newPage();
    let best = null, why = "";
    try {
      await page.goto(l.url, { waitUntil: "domcontentloaded", timeout: 45000 });
      for (let i = 0; i < 20; i++) {
        await page.waitForTimeout(1500);
        const r = await page.evaluate((src) => { eval(src); return window.WLImport.read(document, location.href); }, reader);
        if (!best || r.wishes.length > best.wishes.length) best = r;
        if (best.wishes.length >= (l.min || 1) && i >= 3) break;
        await page.evaluate(() => window.scrollTo(0, document.body ? document.body.scrollHeight : 0));
      }
    } catch (e) { why = String(e.message || e).split("\n")[0]; }
    const found = best ? best.wishes : [];
    const missing = (l.titles || []).filter(t => !found.some(w => w.title.toLowerCase().includes(t.toLowerCase())));
    const ok = !why && found.length >= (l.min || 1) && missing.length === 0;
    console.log(`${ok ? "OK  " : "FAIL"} ${l.site}: ${found.length} wishes (need ${l.min || 1}), reader "${best ? best.reader : "-"}"` +
      (missing.length ? `, missing: ${missing.join(", ")}` : "") + (why ? `, error: ${why}` : ""));
    if (!ok) failures.push(`- **${l.site}**: found ${found.length} of at least ${l.min || 1} wishes` +
      (missing.length ? `; missing ${missing.join(", ")}` : "") + (why ? `; page error: ${why}` : "") + ` (${l.url})`);
    await ctx.close();
  }
  await browser.close();
  if (failures.length) {
    fs.writeFileSync(out, "The daily Import check could not read these test lists:\n\n" + failures.join("\n") +
      "\n\nThe site probably changed its page. Start a Claude Code session and say: \"The Import check failed, please fix the reader.\"\n");
    process.exit(1);
  }
})().catch(e => { console.error(e); process.exit(1); });
