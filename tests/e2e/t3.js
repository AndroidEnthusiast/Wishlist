const { boot, done, check, summary, KEY, CODE, sleep, drawerItem } = require("./lib.js");
const settle = (t) => t.page.waitForFunction(() => window.__wl.pending() === 0);
(async () => {
  // ---- reservations: the givers see them and reserve here ----
  let t = await boot();
  t.fake.res[CODE] = { 1001: "someone-else" };
  await t.page.waitForSelector(".card");
  await t.page.waitForFunction(() => /Reserved/.test(document.querySelector(".card[data-id='1001']").innerText));
  check("someone else's reservation is shown", /Reserved/.test(await t.page.locator(".card[data-id='1001']").innerText()) && (await t.page.locator(".card.dim[data-id='1001']").count()) === 1);
  check("no Reserve button on a wish someone else reserved", (await t.page.locator(".card[data-id='1001']").getByRole("button", { name: "Reserve", exact: true }).count()) === 0);
  await t.page.locator(".card[data-id='1002']").getByRole("button", { name: "Reserve", exact: true }).click();
  await t.page.waitForFunction(() => /Reserved by you/.test(document.querySelector(".card[data-id='1002']").innerText));
  const dev = t.fake.res[CODE][1002];
  check("reserved online with this browser's device id", !!dev && dev === await t.page.evaluate(() => localStorage.getItem("wl_device")));
  check("no wish change for a reservation", t.fake.calls("wl_sh_put_item").length === 0);
  await t.page.screenshot({ path: "shot_reserved.png" });
  await t.page.locator(".card[data-id='1002']").getByRole("button", { name: "Undo", exact: true }).click();
  await t.page.waitForFunction(() => !/Reserved/.test(document.querySelector(".card[data-id='1002']").innerText));
  check("undo reservation", !t.fake.res[CODE][1002]);
  // two givers at once: the second one is told
  t.fake.hooks.wl_reserve_item = async (b) => { if (b.p_item === 1003) t.fake.res[CODE][1003] = "fast-friend"; };
  await t.page.locator(".card[data-id='1003']").getByRole("button", { name: "Reserve", exact: true }).click();
  await t.page.waitForFunction(() => /Someone else just reserved/.test(document.getElementById("toast").textContent));
  await t.page.waitForFunction(() => /Reserved/.test(document.querySelector(".card[data-id='1003']").innerText) && !/by you/.test(document.querySelector(".card[data-id='1003']").innerText));
  check("someone was faster: shown as reserved by someone else", t.fake.res[CODE][1003] === "fast-friend");
  delete t.fake.hooks.wl_reserve_item;
  // offline: the reservation is undone with a message
  t.fake.hooks.wl_reserve_item = async () => { throw Object.assign(new Error("down"), { status: 500 }); };
  await t.page.locator(".card[data-id='1002']").getByRole("button", { name: "Reserve", exact: true }).click();
  await t.page.waitForFunction(() => /Couldn’t save/.test(document.getElementById("toast").textContent));
  check("failed reservation is undone", !/Reserved/.test(await t.page.locator(".card[data-id='1002']").innerText()));
  delete t.fake.hooks.wl_reserve_item;
  // the wish form has no "Most wanted" switch any more
  await t.page.locator("#fab").click();
  check("no Most wanted in the wish form", (await t.page.locator(".dlg").getByLabel("Most wanted").count()) === 0);
  await t.page.locator(".dlg").getByRole("button", { name: "Cancel", exact: true }).click();
  check("no Most wanted sort", !(await t.page.evaluate(() => document.body.innerText)).includes("Most wanted first"));
  await t.page.locator("#sortBtn").click();
  const sorts = (await t.page.locator(".menu .mitem").allInnerTexts()).map(x => x.trim());
  check("sort menu without Most wanted", JSON.stringify(sorts) === JSON.stringify(["Newest first", "Price: low to high", "Price: high to low", "Name A–Z"]), sorts);
  await t.page.keyboard.press("Escape");
  check("no js errors", t.errors.length === 0, t.errors);
  await t.ctx.close();

  // ---- delete with confirmation: the wish goes to the bin online at once (marked, not deleted); Undo takes it out ----
  const inBin = (t, id) => { const w = t.fake.lists[CODE].items[id]; return !!w && w.bin > 0; };
  const binCalls = t => t.fake.calls("wl_sh_put_item").map(c => c.body).filter(b => JSON.stringify(b.p_fields) === JSON.stringify(["bin", "binBy"]));
  const settle = async t => { await t.page.waitForFunction(() => window.__wl.pending() === 0); await sleep(100); };
  t = await boot({ clock: true });
  await t.page.waitForSelector(".card");
  await t.page.locator(".card[data-id='1001']").getByLabel("Delete", { exact: true }).click();
  check("asks first", (await t.page.locator(".dlg h2").innerText()) === "Delete this wish?");
  check("says it goes to the bin", /goes to the bin/.test(await t.page.locator(".dlg").innerText()));
  await t.page.screenshot({ path: "shot_delete.png" });
  await t.page.getByRole("button", { name: "Cancel" }).click();
  check("cancel keeps it", (await t.page.locator(".card[data-id='1001']").count()) === 1 && binCalls(t).length === 0);
  await t.page.locator(".card[data-id='1001']").getByLabel("Delete", { exact: true }).click();
  await t.page.locator(".dlg").getByRole("button", { name: "Delete" }).click();
  check("gone at once", (await t.page.locator(".card[data-id='1001']").count()) === 0);
  check("short message", (await t.page.locator("#snack .stext").innerText()) === "Wish moved to the bin");
  await settle(t);
  check("in the bin online at once", binCalls(t).length === 1 && inBin(t, 1001) && t.fake.calls("wl_sh_del_item").length === 0);
  check("bin: only the bin fields were sent", binCalls(t)[0].p_item.title === "Novel" && typeof binCalls(t)[0].p_item.bin === "number");
  check("bin: the rest of the wish is kept online", t.fake.lists[CODE].items[1001].title === "Novel");
  await t.page.screenshot({ path: "shot_undo.png" });
  await t.page.locator("#snack").getByRole("button", { name: "Undo" }).click();
  check("undo brings it back, same place", (await t.page.locator(".card").nth(2).getAttribute("data-id")) === "1001");
  await settle(t);
  check("undo: out of the bin online", binCalls(t).length === 2 && !inBin(t, 1001) && !("bin" in binCalls(t)[1].p_item));
  check("snack gone", await t.page.locator("#snack").isHidden());
  // the message goes after 4 seconds; the wish stays in the bin
  await t.page.locator(".card[data-id='1001']").getByLabel("Delete", { exact: true }).click();
  await t.page.locator(".dlg").getByRole("button", { name: "Delete" }).click();
  await t.page.clock.fastForward(3000);
  check("message still there at 3 s", !(await t.page.locator("#snack").isHidden()));
  await t.page.clock.fastForward(1500);
  check("message gone after 4 s", await t.page.locator("#snack").isHidden());
  await settle(t);
  check("still in the bin", inBin(t, 1001));
  await t.page.evaluate(() => window.__wl.refresh && window.__wl.refresh());
  await sleep(200);
  check("a wish in the bin stays off the list after a refresh", (await t.page.locator(".card[data-id='1001']").count()) === 0);
  // two deletes in a row: both in the bin, the message is for the last one
  await t.page.locator(".card[data-id='1002']").getByLabel("Delete", { exact: true }).click();
  await t.page.locator(".dlg").getByRole("button", { name: "Delete" }).click();
  await t.page.locator(".card[data-id='1003']").getByLabel("Delete", { exact: true }).click();
  await t.page.locator(".dlg").getByRole("button", { name: "Delete" }).click();
  await settle(t);
  check("both in the bin", inBin(t, 1002) && inBin(t, 1003));
  await t.page.locator("#snack").getByRole("button", { name: "Undo" }).click();
  await settle(t);
  check("undo takes out only the last one", inBin(t, 1002) && !inBin(t, 1003) && (await t.page.locator(".card[data-id='1003']").count()) === 1);
  check("no js errors", t.errors.length === 0, t.errors);
  await t.ctx.close();

  // instant delete from settings
  t = await boot({ clock: true });
  await t.page.waitForSelector(".card");
  await drawerItem(t, "#meBtn");
  await t.page.locator(".dlg").getByLabel("Instant delete").check();
  await t.page.getByRole("button", { name: "Save" }).click();
  await t.page.locator(".card[data-id='1001']").getByLabel("Delete", { exact: true }).click();
  check("instant: no question", (await t.page.locator(".dlg").count()) === 0);
  check("instant: gone and undo offered", (await t.page.locator(".card[data-id='1001']").count()) === 0 && !(await t.page.locator("#snack").isHidden()));
  await t.page.reload();
  await t.page.waitForSelector(".card");
  check("instant delete remembered", await t.page.evaluate(() => localStorage.getItem("wl_instant")) === "1");
  await t.ctx.close();

  const failed = summary(); await done(); process.exit(failed ? 1 : 0);
})().catch(async e => { console.log("CRASH", e); await done(); process.exit(1); });
