const { boot, done, check, summary, KEY, CODE, sleep, drawerItem, catItems, pickCat, pickSort, search, listItem } = require("./lib.js");
const { Fake } = require("./fake.js");
const path = require("path");
const settle = (t) => t.page.waitForFunction(() => window.__wl.pending() === 0);
const q = (t, sel) => t.page.locator(sel);
(async () => {
  // ---- categories ----
  let t = await boot();
  await t.page.waitForSelector(".card");
  await pickCat(t, "Edit categories");
  check("categories dialog", (await q(t, ".dlg h2").innerText()) === "Categories" && (await q(t, ".dlg .catrow").count()) === 3);
  await t.page.getByRole("button", { name: "New category" }).click();
  await q(t, ".dlg input").last().fill("  Garden   tools ");
  await t.page.getByRole("button", { name: "Add", exact: true }).click();
  await settle(t);
  check("category added (cleaned) online", JSON.stringify(t.fake.lists[CODE].cats) === JSON.stringify(["Home", "Fashion", "Books", "Garden tools"]), t.fake.lists[CODE].cats);
  check("dialog list shows it", (await q(t, ".catrow").count()) === 4);
  // rejected names
  await t.page.getByRole("button", { name: "New category" }).click();
  await q(t, ".dlg input").last().fill("all");
  await t.page.getByRole("button", { name: "Add", exact: true }).click();
  check("All is not allowed", /another name/.test(await q(t, ".dlg .err").last().innerText()));
  await q(t, ".dlg input").last().fill("books");
  await t.page.getByRole("button", { name: "Add", exact: true }).click();
  await settle(t);
  check("same name in other capitals: no duplicate", t.fake.lists[CODE].cats.length === 4);
  // rename
  await t.page.getByLabel("Rename Fashion").click();
  await q(t, ".dlg input").last().fill("Clothes");
  await t.page.getByRole("button", { name: "Rename", exact: true }).click();
  await settle(t);
  check("renamed online with its wishes", t.fake.lists[CODE].cats[1] === "Clothes" && t.fake.lists[CODE].items[1002].cat === "Clothes");
  check("rename call", JSON.stringify(t.fake.calls("wl_sh_cat_rename").pop().body) === JSON.stringify({ p_key: KEY, p_old: "Fashion", p_new: "Clothes" }));
  await t.page.getByLabel("Rename Clothes").click();
  await q(t, ".dlg input").last().fill("home");
  await t.page.getByRole("button", { name: "Rename", exact: true }).click();
  check("rename to a taken name refused", /already used/.test(await q(t, ".dlg .err").last().innerText()));
  await t.page.getByRole("button", { name: "Cancel", exact: true }).last().click();
  // delete
  await t.page.getByLabel("Delete Books").click();
  await t.page.locator(".dlg").last().getByRole("button", { name: "Delete", exact: true }).click();
  await settle(t);
  check("category deleted online", !t.fake.lists[CODE].cats.includes("Books"));
  await t.page.getByRole("button", { name: "Done", exact: true }).click();
  const cats = await catItems(t);
  check("drop-down follows", JSON.stringify(cats) === JSON.stringify(["All (3)", "Home (1)", "Clothes (1)", "Garden tools (0)", "New category…", "Edit categories"]), cats);
  // filtering by category
  await pickCat(t, "Clothes");
  check("filter by category", (await q(t, ".card").count()) === 1 && (await q(t, "#catBtn").innerText()).trim() === "Clothes");
  // delete the category you are filtering: back to All
  await pickCat(t, "Edit categories");
  await t.page.getByLabel("Delete Clothes").click();
  await t.page.locator(".dlg").last().getByRole("button", { name: "Delete", exact: true }).click();
  await settle(t);
  await t.page.getByRole("button", { name: "Done", exact: true }).click();
  check("deleting the shown category goes back to All", (await q(t, ".card").count()) === 3 && t.fake.lists[CODE].items[1002].cat === "");
  // new category from inside the wish form gets selected
  await q(t, "#fab").click();
  await q(t, ".dlg").getByRole("button", { name: "＋ New" }).click();
  await q(t, ".dlg input").last().fill("Toys");
  await t.page.getByRole("button", { name: "Add", exact: true }).click();
  check("new category is selected in the form", (await q(t, ".dlg .chip.on").innerText()) === "Toys");
  await q(t, ".dlg").first().getByLabel("Title", { exact: true }).fill("Robot");
  await t.page.getByRole("button", { name: "Save", exact: true }).click();
  await settle(t);
  check("saved with the new category", t.fake.items(CODE).find(i => i.title === "Robot").cat === "Toys");
  check("no js errors", t.errors.length === 0, t.errors);
  await t.ctx.close();

  // ---- list name, note, share ----
  t = await boot();
  await t.page.waitForSelector(".card");
  await listItem(t, "Rename list");
  await q(t, ".dlg input").fill("Family wishes");
  await t.page.getByRole("button", { name: "Save", exact: true }).click();
  await settle(t);
  check("name saved", t.fake.lists[CODE].name === "Family wishes" && (await q(t, "#name").innerText()) === "Family wishes");
  check("only the name is sent", t.fake.calls("wl_sh_set_name").length === 1 && t.fake.calls("wl_sh_set_meta").length === 0);
  await listItem(t, "Note for friends");
  await q(t, ".dlg textarea").fill("Please no gift wrap.");
  check("counter", /20\/300/.test(await q(t, ".dlg .hint").innerText()));
  await t.page.getByRole("button", { name: "Save", exact: true }).click();
  await settle(t);
  check("note online as item 0", t.fake.lists[CODE].items[0] && t.fake.lists[CODE].items[0].about === "Please no gift wrap." && /gift wrap/.test(await q(t, "#about").textContent()));
  await listItem(t, "Note for friends");
  await q(t, ".dlg textarea").fill("");
  await t.page.getByRole("button", { name: "Save", exact: true }).click();
  await settle(t);
  check("empty note removes it", !t.fake.lists[CODE].items[0] && (await q(t, "#about").isHidden()));
  await q(t, "#shareBtn").click();
  const links = await q(t, ".shareblock input").evaluateAll(a => a.map(i => i.value));
  check("invite link (through join/ for its preview)", links[0] === t.base + "join/?j=" + KEY, links);
  check("friends link has the list code (through list/)", links[1] === t.base + "list/?c=" + CODE, links);
  await q(t, ".shareblock").first().getByRole("button", { name: "Copy link" }).click();
  check("copied to clipboard", (await t.page.evaluate(() => navigator.clipboard.readText())) === t.base + "join/?j=" + KEY);
  await t.page.screenshot({ path: "shot_share.png" });
  // sharing sends a picture with the list's name (link previews can't show it)
  const heads = await t.page.evaluate(() => [
    window.__wl.cardHeadline("invite", "Jakim's B'day"), window.__wl.cardHeadline("friends", "Jakim's B'day"),
    window.__wl.cardHeadline("invite", "Family wishlist"), window.__wl.cardHeadline("invite", "  ")]);
  check("picture words with the list name", JSON.stringify(heads) === JSON.stringify(["Join Jakim's B'day wishlist", "Check out Jakim's B'day wishlist", "Join Family wishlist", "Join our wishlist"]), heads);
  const png = await t.page.evaluate(async () => {
    const f = await window.__wl.cardFile("invite", "A very long name for a wishlist of a whole family at Christmas");
    const b = new Uint8Array(await f.arrayBuffer());
    const fr = new FileReader(); const url = await new Promise(r => { fr.onload = () => r(fr.result); fr.readAsDataURL(f); });
    return { type: f.type, size: f.size, head: Array.from(b.slice(1, 4)).map(c => String.fromCharCode(c)).join(""), url };
  });
  check("the picture is a PNG", png.type === "image/png" && png.head === "PNG" && png.size > 5000, { type: png.type, size: png.size });
  require("fs").writeFileSync("shot_card.png", Buffer.from(png.url.split(",")[1], "base64"));
  await t.page.getByRole("button", { name: "Done", exact: true }).click();
  await t.ctx.close();

  // ---- a failed save is undone with a message ----
  t = await boot();
  await t.page.waitForSelector(".card");
  t.fake.hooks.wl_sh_put_item = async () => { throw Object.assign(new Error("down"), { status: 500 }); };
  await q(t, ".card[data-id='1002']").getByLabel("Edit", { exact: true }).click();
  await q(t, ".dlg").getByLabel("Title", { exact: true }).fill("Red shirt");
  await t.page.getByRole("button", { name: "Save", exact: true }).click();
  await t.page.waitForFunction(() => /Couldn’t save/.test(document.getElementById("toast").textContent));
  await settle(t);
  check("change was undone", /Blue shirt/.test(await q(t, ".card[data-id='1002']").innerText()) && t.fake.lists[CODE].items[1002].title === "Blue shirt");
  delete t.fake.hooks.wl_sh_put_item;
  t.fake.hooks.wl_sh_cat_add = async () => { throw Object.assign(new Error("down"), { status: 500 }); };
  await pickCat(t, "Edit categories");
  await t.page.getByRole("button", { name: "New category" }).click();
  await q(t, ".dlg input").last().fill("Nope");
  await t.page.getByRole("button", { name: "Add", exact: true }).click();
  await settle(t);
  check("category add undone", !(await t.page.evaluate(() => window.__wl.content().cats)).includes("Nope"));
  await t.page.getByRole("button", { name: "Done", exact: true }).click();
  // a failing move to the bin brings the wish back
  t = await boot({ clock: true });
  await t.page.waitForSelector(".card");
  t.fake.hooks.wl_sh_put_item = async () => { throw Object.assign(new Error("down"), { status: 500 }); };
  await q(t, ".card[data-id='1001']").getByLabel("Delete", { exact: true }).click();
  await q(t, ".dlg").getByRole("button", { name: "Delete", exact: true }).click();
  await settle(t);
  await sleep(100);
  check("failed delete: wish is back with a message", (await q(t, ".card[data-id='1001']").count()) === 1 && /Couldn’t delete/.test(await q(t, "#toast").innerText()));
  await t.ctx.close();

  // ---- sorting and searching ----
  t = await boot();
  await t.page.waitForSelector(".card");
  const order = async () => (await q(t, ".card").evaluateAll(a => a.map(c => c.getAttribute("data-id")))).join(",");
  check("newest first", (await order()) === "1003,1002,1001");
  await pickSort(t, "Price: low to high");
  check("price low to high, no price last", (await order()) === "1002,1003,1001", await order());
  await pickSort(t, "Price: high to low");
  check("price high to low, no price last", (await order()) === "1003,1002,1001", await order());
  await pickSort(t, "Name A–Z");
  check("A-Z (Blue shirt, Desk lamp, Novel)", (await order()) === "1002,1003,1001", await order());
  await pickSort(t, "Price: low to high");
  check("sort button shows the sort", (await q(t, "#sortBtn").innerText()).trim() === "Price: low" && (await q(t, "#sortBtn.on").count()) === 1);
  await search(t, "shirt");
  check("search", (await order()) === "1002");
  await search(t, "shop.example");
  check("search by shop (still sorted by price)", (await order()) === "1002,1003", await order());
  await search(t, "zzz");
  check("nothing matches", /No wishes match/.test(await q(t, "#view").innerText()));
  await q(t, "#view").getByRole("button", { name: "Clear search" }).click();
  check("clear search", (await order()) !== "" && (await q(t, ".card").count()) === 3);
  await t.page.reload();
  await t.page.waitForSelector(".card");
  check("sort remembered", (await q(t, "#sortBtn").innerText()).trim() === "Price: low");
  await t.ctx.close();
  const failed = summary(); await done(); process.exit(failed ? 1 : 0);
})().catch(async e => { console.log("CRASH", e); await done(); process.exit(1); });
