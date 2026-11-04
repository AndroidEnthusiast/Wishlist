// The bin in the browser editor (menu > Bin, 4 November 2026): restore only, 30 days, shared with the app.
const { boot, done, check, summary, CODE, sleep, drawerItem, sample } = require("./lib.js");
const settle = async t => { await t.page.waitForFunction(() => window.__wl.pending() === 0); await sleep(100); };
const dlg = t => t.page.locator(".dlg").last();
(async () => {
  const DAY = 24 * 60 * 60 * 1000;
  const items = sample();
  items.push({ id: 1004, url: "", title: "Old mug", desc: "", image: "", price: null, old: null, cur: "EUR", cat: "", got: false, bin: Date.now() - 31 * DAY, binBy: "Mia" });
  items.push({ id: 1005, url: "", title: "Scarf", desc: "", image: "https://img.example/scarf.png", price: 12, old: null, cur: "EUR", cat: "", got: false, bin: Date.now() - 2 * DAY, binBy: "Mia" });
  const a = await boot({ name: "Sam", items });
  const b = await boot({ fake: a.fake, name: "Jo" });
  await a.page.waitForSelector(".card"); await b.page.waitForSelector(".card");
  check("wishes in the bin are not on the list", (await a.page.locator(".card[data-id='1005']").count()) === 0 && (await a.page.locator(".card[data-id='1004']").count()) === 0);

  await drawerItem(a, "#binBtn");
  let txt = await dlg(a).innerText();
  check("bin: title and the 30 days", /^Bin/.test(txt) && /Deleted wishes stay here for 30 days\. Then they are deleted for good\./.test(txt), txt);
  check("bin: shows a recent wish with who and days left", /Scarf/.test(txt) && /Deleted by Mia · 28 days left/.test(txt), txt);
  check("bin: older than 30 days is not shown", !/Old mug/.test(txt));
  check("bin: restore only (no delete for good, no empty bin)", JSON.stringify(await dlg(a).locator("button").allInnerTexts()) === JSON.stringify(["Restore", "Close"]), await dlg(a).locator("button").allInnerTexts());
  check("bin: picture shown", (await dlg(a).locator(".binpic img").count()) === 1);
  await a.page.screenshot({ path: "shot_bin.png" });

  // A deletes the lamp: it shows in B's bin after a refresh, with A's name
  await dlg(a).getByRole("button", { name: "Close" }).click();
  await a.page.locator(".card[data-id='1003']").getByLabel("Delete", { exact: true }).click();
  await a.page.locator(".dlg").getByRole("button", { name: "Delete" }).click();
  await settle(a);
  await drawerItem(a, "#binBtn");
  check("own delete shows in own bin at once", /Desk lamp/.test(await dlg(a).innerText()) && /Deleted by Sam/.test(await dlg(a).innerText()));
  await dlg(a).getByRole("button", { name: "Close" }).click();
  await b.page.evaluate(() => window.__wl.refresh()); await sleep(300);
  await drawerItem(b, "#binBtn");
  txt = await dlg(b).innerText();
  check("someone else's delete shows in the bin", /Desk lamp/.test(txt) && /Deleted by Sam · 30 days left/.test(txt), txt);

  // B restores it: back on the list for both, out of the bin online, only the bin fields written
  await dlg(b).locator(".binrow", { hasText: "Desk lamp" }).getByRole("button", { name: "Restore" }).click();
  check("restore: back on the list at once", (await b.page.locator(".card[data-id='1003']").count()) === 1);
  check("restore: says so", /“Desk lamp” is back on the list\./.test(await b.page.locator("#toast").innerText()));
  await settle(b);
  const w = a.fake.lists[CODE].items[1003];
  const last = a.fake.calls("wl_sh_put_item").map(c => c.body).pop();
  check("restore: out of the bin online", !w.bin && w.title === "Desk lamp" && w.note === "White, please", w);
  check("restore: only the bin fields sent", JSON.stringify(last.p_fields) === JSON.stringify(["bin", "binBy"]));
  await a.page.evaluate(() => window.__wl.refresh()); await sleep(300);
  check("restore: the other person sees it back", (await a.page.locator(".card[data-id='1003']").count()) === 1);
  await drawerItem(b, "#binBtn");
  check("restore: gone from the bin", !/Desk lamp/.test(await dlg(b).innerText()));
  await dlg(b).getByRole("button", { name: "Close" }).click();

  // a failed restore is undone with a message
  a.fake.hooks.wl_sh_put_item = async () => { throw new Error("offline"); };
  await drawerItem(b, "#binBtn");
  await dlg(b).locator(".binrow", { hasText: "Scarf" }).getByRole("button", { name: "Restore" }).click();
  await settle(b);
  check("failed restore: off the list again", (await b.page.locator(".card[data-id='1005']").count()) === 0);
  check("failed restore: says so", /Couldn’t save that/.test(await b.page.locator("#toast").innerText()));
  delete a.fake.hooks.wl_sh_put_item;

  // empty bin
  const c = await boot({ name: "Sam" });
  await c.page.waitForSelector(".card");
  await drawerItem(c, "#binBtn");
  check("empty bin says so", /The bin is empty\./.test(await dlg(c).innerText()));

  check("no js errors", a.errors.length === 0 && b.errors.filter(e => !/offline/.test(e)).length === 0 && c.errors.length === 0, [a.errors, b.errors, c.errors]);
  await done();
  process.exit(summary() ? 1 : 0);
})().catch(e => { console.log("FAIL crashed: " + (e.stack || e)); process.exit(1); });
