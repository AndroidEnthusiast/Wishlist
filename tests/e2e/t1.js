const { boot, done, check, summary, KEY, CODE, sleep, openDrawer, catItems } = require("./lib.js");
(async () => {
  // A. bad / missing key
  let t = await boot({ goto: false });
  await t.page.goto(t.base + "edit.html");
  check("no key: says it does not work", /doesn’t work/.test(await t.page.locator("#view").innerText()));
  await t.page.goto(t.base + "edit.html?j=SHORT");
  check("short key: does not work", /doesn’t work/.test(await t.page.locator("#view").innerText()));
  check("no calls for a bad link", t.fake.log.length === 0);
  await t.ctx.close();

  // B. valid-looking key the server does not know
  t = await boot({ key: "ZZZZZZZZZZZZZZZZZZZZ" });
  await t.page.waitForSelector(".inv");
  check("unknown key: gone screen", /doesn’t work any more/.test(await t.page.locator("#view").innerText()));
  check("unknown key: no editing buttons", await t.page.locator("#fab").isHidden());
  await t.ctx.close();

  // C. loads the list
  t = await boot({ about: "Sizes are small. Thanks!" });
  await t.page.waitForSelector(".card");
  check("title", (await t.page.locator("#name").innerText()) === "Our home");
  check("3 cards", (await t.page.locator(".card").count()) === 3);
  check("about in the side menu", /Sizes are small/.test(await t.page.locator("#about").textContent()));
  check("editing as Sam", /Editing as Sam/.test(await t.page.locator("#who").textContent()));
  check("top bar like the app", await t.page.locator("#menuBtn").isVisible() && await t.page.getByLabel("Search", { exact: true }).isVisible() && await t.page.getByLabel("Wishlist settings").isVisible() && (await t.page.locator("#refreshBtn").count()) === 0 && await t.page.getByLabel("Invite or share").isVisible());
  check("side menu closed at first", await t.page.locator("#drawer").isHidden());
  await openDrawer(t);
  check("side menu: your name, the note and Settings (no list settings)", /Sizes are small/.test(await t.page.locator("#drawer").innerText()) && !/Rename list/.test(await t.page.locator("#drawer").innerText()) && /Settings/.test(await t.page.locator("#drawer").innerText()));
  await t.page.screenshot({ path: "shot_menu.png" });
  await t.page.keyboard.press("Escape");
  await t.page.locator("#drawer").waitFor({ state: "hidden", timeout: 2000 }).catch(() => {});
  check("escape closes the side menu", await t.page.locator("#drawer").isHidden());
  check("Add wish button", (await t.page.locator("#fab").innerText()).trim() === "Add wish");
  await t.page.locator("#listBtn").click();
  const lmenu = (await t.page.locator(".menu .mitem").allInnerTexts()).map(x => x.trim());
  check("⚙ opens the list's settings", JSON.stringify(lmenu) === JSON.stringify(["Rename list", "Note for friends", "Categories", "Share list"]), lmenu);
  await t.page.keyboard.press("Escape");
  const chips = await catItems(t);
  check("category drop-down", JSON.stringify(chips) === JSON.stringify(["All (3)", "Home (1)", "Fashion (1)", "Books (0)", "New category…", "Edit categories"]), chips);
  check("category button says All", (await t.page.locator("#catBtn").innerText()).trim() === "All");
  check("no most wanted on a shared list (not even for a wish starred earlier)", (await t.page.locator(".card .badge.star, .card .starbtn, [data-star]").count()) === 0);
  check("no Got it on a shared list", !/Got it/.test(await t.page.locator("#view").innerText()) && (await t.page.getByLabel("Mark as got").count()) === 0);
  check("Reserve buttons", (await t.page.locator(".card").getByRole("button", { name: "Reserve", exact: true }).count()) === 3);
  check("price drop badge", /% off/.test(await t.page.locator(".card").first().innerText()));
  check("Added by", /Added by Mia/.test(await t.page.locator(".card").first().innerText()));
  check("note", /White, please/.test(await t.page.locator(".card").first().innerText()));
  check("size tag", /Size M/.test(await t.page.locator(".card").nth(1).innerText()));
  check("reservations are read for the givers", t.fake.calls("wl_get_reservations").length >= 1 && t.fake.calls("wl_get_reservations")[0].body.p_code === CODE);
  check("no js errors", t.errors.length === 0, t.errors);
  await t.page.screenshot({ path: "shot_list.png" });
  // views
  await t.page.getByLabel("Two per row").click();
  check("grid compact cards", (await t.page.locator(".grid.two .card.compact").count()) === 3);
  check("grid: no star buttons", (await t.page.locator(".card.compact .starbtn").count()) === 0);
  await t.page.locator(".card.compact[data-id='1002']").getByLabel("More").click();
  const more = (await t.page.locator(".menu .mitem").allInnerTexts()).map(x => x.trim());
  check("grid: ⋮ menu (Edit, Reserve, Delete)", JSON.stringify(more) === JSON.stringify(["Edit", "Reserve", "Delete"]), more);
  await t.page.locator(".menu .mitem", { hasText: "Reserve" }).click();
  await t.page.waitForFunction(() => /Reserved by you/.test(document.querySelector(".card.compact[data-id='1002']").innerText));
  check("grid: ⋮ Reserve works", Object.keys(t.fake.res[CODE] || {}).join() === "1002");
  await t.page.screenshot({ path: "shot_grid.png" });
  await t.page.getByLabel("Images only").click();
  check("tiles", (await t.page.locator(".tile").count()) === 3);
  await t.page.locator(".tile").first().click();
  check("modal opens with actions", (await t.page.locator("#modal .card .actions .ibtn").count()) === 2 && (await t.page.locator("#modal .card .actions button.act").count()) === 1);
  await t.page.screenshot({ path: "shot_modal.png" });
  await t.page.keyboard.press("Escape");
  check("escape closes modal", (await t.page.locator("#modal").count()) === 0);
  await t.page.screenshot({ path: "shot_tiles.png" });
  await t.ctx.close();

  // D. first-run name dialog
  t = await boot({ name: null });
  await t.page.waitForSelector(".dlg");
  check("welcome dialog", /What’s your name/.test(await t.page.locator(".dlg h2").innerText()));
  await t.page.screenshot({ path: "shot_welcome.png" });
  await t.page.keyboard.press("Escape");
  check("welcome can't be escaped", (await t.page.locator(".dlg").count()) === 1);
  await t.page.locator(".dlg input").fill("Ana");
  await t.page.getByRole("button", { name: "Save" }).click();
  check("name shown", /Editing as Ana/.test(await t.page.locator("#who").textContent()));
  await t.page.reload();
  await t.page.waitForSelector(".card");
  check("name remembered, no dialog again", (await t.page.locator(".dlg").count()) === 0 && /Editing as Ana/.test(await t.page.locator("#who").textContent()));
  await t.ctx.close();
  const failed = summary(); await done(); process.exit(failed ? 1 : 0);
})().catch(async e => { console.log("CRASH", e); await done(); process.exit(1); });
