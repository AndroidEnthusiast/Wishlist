const { boot, done, check, summary, KEY, CODE, sleep } = require("./lib.js");
const CHAIR = `<html><head><title>Chair - Shop</title><meta property="og:title" content="Ergonomic chair"><meta property="og:image" content="https://img.example/chair.png"><meta property="product:price:amount" content="149.99"><meta property="product:price:currency" content="EUR"><meta property="og:description" content="Comfy for long days"></head><body></body></html>`;
(async () => {
  // ---- add a wish by pasting a link ----
  let t = await boot();
  t.fake.pages["https://www.shop.example/p/chair?utm=1"] = { html: CHAIR };
  t.fake.pages["https://www.shop.example/p/blocked"] = { status: 403, html: "<title>Access denied</title>" };
  t.fake.pages["https://www.shop.example/p/noprice"] = { html: "<html><head><title>Plain thing</title></head><body>hi</body></html>" };
  await t.page.waitForSelector(".card");
  await t.page.locator("#fab").click();
  await t.page.waitForSelector(".dlg");
  check("form title", (await t.page.locator(".dlg h2").innerText()) === "Add a wish");
  await t.page.locator(".dlg input[type=url]").fill("https://www.shop.example/p/chair?utm=1");
  await t.page.waitForFunction(() => document.querySelector(".dlg input[placeholder='']") || true);
  await t.page.waitForFunction(() => { const i = [...document.querySelectorAll(".dlg input")].find(x => x.value === "Ergonomic chair"); return !!i; });
  const get = async (label) => t.page.locator(".dlg").getByLabel(label, { exact: true }).inputValue();
  check("title filled", (await get("Title")) === "Ergonomic chair");
  check("description filled", (await get("Description")) === "Comfy for long days");
  check("price filled", (await get("Price")) === "149.99");
  check("currency EUR", (await get("Currency")) === "EUR");
  check("picture shown", (await t.page.locator(".dlg .picbox img").count()) === 1);
  check("helper called with the key and the link", t.fake.calls("fetch-page").length === 1 && t.fake.calls("fetch-page")[0].body.key === KEY && t.fake.calls("fetch-page")[0].body.url === "https://www.shop.example/p/chair?utm=1");
  await t.page.screenshot({ path: "shot_form.png" });
  // pick category, size, note
  await t.page.locator(".dlg .chips .chip", { hasText: "Home" }).click();
  await t.page.locator(".dlg").getByLabel("Size", { exact: true }).selectOption("L");
  await t.page.locator(".dlg").getByLabel("Note for givers", { exact: false }).fill("  Black  ");
  await t.page.getByRole("button", { name: "Save" }).click();
  await t.page.waitForFunction(() => window.__wl.pending() === 0);
  check("dialog closed", (await t.page.locator(".dlg").count()) === 0);
  const first = t.page.locator(".card").first();
  check("new wish on top", /Ergonomic chair/.test(await first.innerText()) && /€149\.99/.test(await first.innerText()));
  const put = t.fake.calls("wl_sh_put_item").pop();
  check("new wish: all of it is sent (no p_fields)", put && put.body.p_fields === undefined);
  const saved = t.fake.items(CODE).find(i => i.title === "Ergonomic chair");
  check("saved json", saved && saved.price === 149.99 && saved.cur === "EUR" && saved.cat === "Home" && saved.size === "L" && saved.note === "Black" && !saved.star && saved.by === "Sam" && saved.got === false && saved.old === null && saved.image === "https://img.example/chair.png" && saved.url === "https://www.shop.example/p/chair?utm=1" && saved.desc === "Comfy for long days", saved);
  check("saved id positive number", saved && Number.isInteger(saved.id) && saved.id > 0);
  check("no js errors", t.errors.length === 0, t.errors);

  // ---- blocked / no price / helper missing / manual ----
  await t.page.locator("#fab").click();
  await t.page.locator(".dlg input[type=url]").fill("https://www.shop.example/p/blocked");
  await t.page.waitForFunction(() => /blocked/.test(document.querySelector(".dlg .err").textContent));
  check("blocked message", /blocked the automatic read/.test(await t.page.locator(".dlg .err").innerText()));
  await t.page.evaluate(() => { const i = document.querySelector(".dlg input[type=url]"); i.value = "https://www.shop.example/p/noprice"; i.dispatchEvent(new ClipboardEvent("paste")); });
  await t.page.waitForFunction(() => /No price found/.test(document.querySelector(".dlg .err").textContent));
  check("title still filled with no price", (await get("Title")) === "Plain thing" && (await get("Price")) === "");
  t.fake.helperUp = false;
  await t.page.evaluate(() => { const i = document.querySelector(".dlg input[type=url]"); i.value = "https://www.shop.example/p/other"; i.dispatchEvent(new ClipboardEvent("paste")); });
  await t.page.waitForFunction(() => /Couldn’t read/.test(document.querySelector(".dlg .err").textContent));
  check("helper missing: friendly message", /Fill in the details yourself/.test(await t.page.locator(".dlg .err").innerText()));
  check("typing is still possible", true);
  await t.page.locator(".dlg").getByLabel("Title", { exact: true }).fill("My own thing");
  await t.page.locator(".dlg").getByLabel("Price", { exact: true }).fill("12,50");
  await t.page.locator(".dlg").getByLabel("Currency", { exact: true }).selectOption("GBP");
  await t.page.getByRole("button", { name: "Save" }).click();
  await t.page.waitForFunction(() => window.__wl.pending() === 0);
  const mine = t.fake.items(CODE).find(i => i.title === "My own thing");
  check("typed wish saved with comma price", mine && mine.price === 12.5 && mine.cur === "GBP" && mine.image === "" && !("star" in mine) && !("note" in mine) && !("size" in mine), mine);
  // save needs a title
  await t.page.locator("#fab").click();
  await t.page.getByRole("button", { name: "Save" }).click();
  check("title required", /title/i.test(await t.page.locator(".dlg .err").innerText()) && (await t.page.locator(".dlg").count()) === 1);
  await t.page.getByRole("button", { name: "Cancel" }).click();
  check("cancel closes, nothing saved", (await t.page.locator(".dlg").count()) === 0);
  check("bad link text", true);
  await t.page.locator("#fab").click();
  await t.page.locator(".dlg input[type=url]").fill("hello");
  await t.page.getByRole("button", { name: "Fetch details" }).click();
  check("not a link message", /full product link/.test(await t.page.locator(".dlg .err").innerText()));
  check("click outside does not close", await (async () => { await t.page.mouse.click(5, 5); return (await t.page.locator(".dlg").count()) === 1; })());
  await t.page.getByRole("button", { name: "Cancel" }).click();
  check("no js errors 2", t.errors.length === 0, t.errors);
  await t.ctx.close();

  // ---- edit only the changed field ----
  t = await boot();
  await t.page.waitForSelector(".card");
  const shirt = t.page.locator(".card", { hasText: "Blue shirt" });
  await shirt.getByLabel("Edit", { exact: true }).click();
  check("edit title", (await t.page.locator(".dlg h2").innerText()) === "Edit wish");
  check("prefilled", (await t.page.locator(".dlg").getByLabel("Title", { exact: true }).inputValue()) === "Blue shirt" && (await t.page.locator(".dlg").getByLabel("Size", { exact: true }).inputValue()) === "M");
  await t.page.locator(".dlg").getByLabel("Price", { exact: true }).fill("27.5");
  await t.page.getByRole("button", { name: "Save" }).click();
  await t.page.waitForFunction(() => window.__wl.pending() === 0);
  let p = t.fake.calls("wl_sh_put_item").pop();
  check("only price (and the old price) are sent", JSON.stringify(p.body.p_fields) === JSON.stringify(["price"]), p.body.p_fields);
  check("server price changed, rest untouched", (() => { const i = t.fake.lists[CODE].items[1002]; return i.price === 27.5 && i.title === "Blue shirt" && i.size === "M" && i.cur === "EUR"; })());
  // lamp has an old price: changing its price clears old too
  await t.page.locator(".card", { hasText: "Desk lamp" }).getByLabel("Edit", { exact: true }).click();
  await t.page.locator(".dlg").getByLabel("Price", { exact: true }).fill("35");
  await t.page.getByRole("button", { name: "Save" }).click();
  await t.page.waitForFunction(() => window.__wl.pending() === 0);
  p = t.fake.calls("wl_sh_put_item").pop();
  check("price + old", JSON.stringify(p.body.p_fields.sort()) === JSON.stringify(["old", "price"]), p.body.p_fields);
  check("no change = no call", await (async () => {
    const n = t.fake.calls("wl_sh_put_item").length;
    await t.page.locator(".card", { hasText: "Desk lamp" }).getByLabel("Edit", { exact: true }).click();
    await t.page.getByRole("button", { name: "Save" }).click();
    await sleep(200);
    return t.fake.calls("wl_sh_put_item").length === n;
  })());
  // clear the note and the size -> sent as missing
  await t.page.locator(".card", { hasText: "Blue shirt" }).getByLabel("Edit", { exact: true }).click();
  await t.page.locator(".dlg").getByLabel("Size", { exact: true }).selectOption("");
  await t.page.getByRole("button", { name: "Save" }).click();
  await t.page.waitForFunction(() => window.__wl.pending() === 0);
  check("cleared size is removed online", !("size" in t.fake.lists[CODE].items[1002]));
  check("no js errors 3", t.errors.length === 0, t.errors);
  await t.ctx.close();
  const failed = summary(); await done(); process.exit(failed ? 1 : 0);
})().catch(async e => { console.log("CRASH", e); await done(); process.exit(1); });
