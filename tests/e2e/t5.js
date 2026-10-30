const { boot, done, check, summary, KEY, CODE, sleep, drawerItem } = require("./lib.js");
const { SUPA } = require("./fake.js");
const path = require("path");
const settle = (t) => t.page.waitForFunction(() => window.__wl.pending() === 0);
const card = (t, id) => t.page.locator(".card[data-id='" + id + "']");
const dlg = (t) => t.page.locator(".dlg").last();
const lab = (t, l) => dlg(t).getByLabel(l, { exact: true });
const delayPuts = (fake, ms) => { fake.hooks.wl_sh_put_item = async () => { await sleep(ms); }; };
(async () => {
  // ---- two people at the same time: price vs note on the same wish ----
  let a = await boot({ name: "Sam" });
  let b = await boot({ fake: a.fake, name: "Mia" });
  await a.page.waitForSelector(".card"); await b.page.waitForSelector(".card");
  delayPuts(a.fake, 400);
  await card(a, 1003).getByLabel("Edit", { exact: true }).click();
  await card(b, 1003).getByLabel("Edit", { exact: true }).click();
  await lab(a, "Price").fill("30");
  await lab(b, "Note for givers (colour, details, …)").fill("Black, please");
  await a.page.getByRole("button", { name: "Save", exact: true }).click();
  await b.page.getByRole("button", { name: "Save", exact: true }).click();
  await settle(a); await settle(b);
  const lamp = a.fake.lists[CODE].items[1003];
  check("both edits kept online (price + note)", lamp.price === 30 && lamp.note === "Black, please", lamp);
  await a.page.evaluate(() => window.__wl.refresh()); await b.page.evaluate(() => window.__wl.refresh());
  await sleep(300);
  check("A sees B's note", /Black, please/.test(await card(a, 1003).innerText()));
  check("B sees A's price", /30/.test(await card(b, 1003).innerText()) && !/39\.50/.test(await card(b, 1003).innerText()));
  check("no js errors (two people)", a.errors.length === 0 && b.errors.length === 0, [a.errors, b.errors]);
  await a.ctx.close(); await b.ctx.close();

  // ---- add vs delete, and two people changing different fields of one wish ----
  a = await boot({ name: "Sam", clock: false });
  b = await boot({ fake: a.fake, name: "Mia" });
  await a.page.waitForSelector(".card"); await b.page.waitForSelector(".card");
  delayPuts(a.fake, 300);
  await a.page.locator("#fab").click();
  await lab(a, "Title").fill("Robot");
  await a.page.getByRole("button", { name: "Save", exact: true }).click();
  // B turns on instant delete and deletes the novel
  await drawerItem(b, "#meBtn");
  await dlg(b).getByLabel("Instant delete").check();
  await b.page.getByRole("button", { name: "Save", exact: true }).click();
  await card(b, 1001).getByLabel("Delete", { exact: true }).click();
  await settle(a); await settle(b);
  await b.page.evaluate(() => document.dispatchEvent(new Event("pagehide")));
  await b.page.evaluate(() => window.dispatchEvent(new Event("pagehide")));
  await sleep(600);
  const titles = a.fake.items(CODE).filter(i => i.id > 0).map(i => i.title).sort();
  check("added wish and deleted wish: both applied", JSON.stringify(titles) === JSON.stringify(["Blue shirt", "Desk lamp", "Robot"]), titles);
  a.fake.hooks = {};
  delayPuts(a.fake, 300);
  await card(a, 1002).getByLabel("Edit", { exact: true }).click();
  await card(b, 1002).getByLabel("Edit", { exact: true }).click();
  await lab(a, "Title").fill("Green shirt");
  await dlg(b).getByLabel("Note for givers").fill("Cotton");
  await a.page.getByRole("button", { name: "Save", exact: true }).click();
  await b.page.getByRole("button", { name: "Save", exact: true }).click();
  await settle(a); await settle(b);
  await sleep(300);
  const sh = a.fake.lists[CODE].items[1002];
  check("title and note on the same wish both kept", sh.title === "Green shirt" && sh.note === "Cotton", sh);
  await a.ctx.close(); await b.ctx.close();

  // ---- a slow, old answer from the server must not undo what you just did ----
  let t = await boot();
  await t.page.waitForSelector(".card");
  const orig = t.fake.rpc.bind(t.fake);
  t.fake.slowGet = 700;
  t.fake.rpc = async (fn, body) => { const r = await orig(fn, body); if (fn === "wl_sh_get" && t.fake.slowGet) await sleep(t.fake.slowGet); return r; };
  t.page.evaluate(() => window.__wl.refresh());
  await sleep(150);
  await card(t, 1002).getByLabel("Edit", { exact: true }).click();
  await lab(t, "Title").fill("Plain shirt");
  await t.page.getByRole("button", { name: "Save", exact: true }).click();
  await settle(t);
  await sleep(1000);
  t.fake.slowGet = 0;
  check("the stale answer was ignored: change still there", /Plain shirt/.test(await card(t, 1002).innerText()) && t.fake.lists[CODE].items[1002].title === "Plain shirt");
  await t.ctx.close();

  // ---- the list refreshes by itself ----
  t = await boot({ clock: true });
  await t.page.waitForSelector(".card");
  t.fake.lists[CODE].items[1002].title = "Red shirt";
  await t.page.clock.fastForward(21000);
  await t.page.waitForFunction(() => /Red shirt/.test(document.body.innerText));
  check("refresh after 20 s shows other people's change", true);
  t.fake.lists[CODE].items[1002].title = "Green shirt";
  await t.page.evaluate(() => { Object.defineProperty(document, "visibilityState", { value: "visible", configurable: true }); document.dispatchEvent(new Event("visibilitychange")); });
  await t.page.waitForFunction(() => /Green shirt/.test(document.body.innerText));
  check("refresh when coming back to the page", true);
  t.fake.lists[CODE].items[1002].title = "Pink shirt";
  await t.page.evaluate(() => window.dispatchEvent(new Event("online")));
  await t.page.waitForFunction(() => /Pink shirt/.test(document.body.innerText));
  check("refresh when the connection returns", true);
  await t.ctx.close();

  // ---- pictures ----
  t = await boot();
  await t.page.waitForSelector(".card");
  await t.page.locator("#fab").click();
  await lab(t, "Title").fill("Poster");
  await dlg(t).locator("input[type=file]").setInputFiles(path.join(__dirname, "big.jpg"));
  await t.page.waitForFunction(() => document.querySelector(".dlg .picbox img"));
  await t.page.getByRole("button", { name: "Save", exact: true }).click();
  await settle(t);
  let up = t.fake.log.filter(x => x.kind === "storage" && x.fn === "POST");
  check("one upload", up.length === 1, up.length);
  check("upload is a small JPEG into the list's folder", up[0] && up[0].head === "ffd8ff" && up[0].type === "image/jpeg" && up[0].rel.startsWith(CODE + "/") && up[0].size > 1000 && up[0].size < 300000, up[0]);
  const poster = t.fake.items(CODE).find(i => i.title === "Poster");
  check("wish stores the public address", poster.image === SUPA + "/storage/v1/object/public/wish-photos/" + up[0].rel, poster.image);
  check("card shows it", (await t.page.locator(".card", { hasText: "Poster" }).locator("img").getAttribute("src")) === poster.image);
  check("not denied", !t.fake.log.some(x => x.denied));
  // dimensions of what was uploaded
  // remove it
  await t.page.locator(".card", { hasText: "Poster" }).getByLabel("Edit", { exact: true }).click();
  await dlg(t).getByRole("button", { name: "Remove", exact: true }).click();
  await t.page.getByRole("button", { name: "Save", exact: true }).click();
  await settle(t);
  const dels = t.fake.log.filter(x => x.kind === "storage" && x.fn === "DELETE");
  check("removed picture is deleted online", dels.length === 1 && dels[0].rel === up[0].rel && !(up[0].rel in t.fake.photos), dels);
  check("wish has no image now", !t.fake.items(CODE).find(i => i.title === "Poster").image);
  // replace: add one, then replace by another
  await t.page.locator(".card", { hasText: "Poster" }).getByLabel("Edit", { exact: true }).click();
  await dlg(t).locator("input[type=file]").setInputFiles(path.join(__dirname, "trans.png"));
  await t.page.waitForFunction(() => document.querySelector(".dlg .picbox img"));
  await t.page.getByRole("button", { name: "Save", exact: true }).click();
  await settle(t);
  const first = Object.keys(t.fake.photos);
  check("png became a jpeg upload", first.length === 1 && t.fake.log.filter(x => x.fn === "POST").pop().head === "ffd8ff");
  await t.page.locator(".card", { hasText: "Poster" }).getByLabel("Edit", { exact: true }).click();
  await dlg(t).locator("input[type=file]").setInputFiles(path.join(__dirname, "big.jpg"));
  await t.page.waitForFunction(() => document.querySelector(".dlg .picbox img"));
  await t.page.getByRole("button", { name: "Save", exact: true }).click();
  await settle(t);
  const second = Object.keys(t.fake.photos);
  check("replaced picture: old one deleted, new one there", second.length === 1 && second[0] !== first[0], { first, second });
  // cancel after choosing: nothing uploaded
  const before = t.fake.log.filter(x => x.fn === "POST").length;
  await t.page.locator("#fab").click();
  await lab(t, "Title").fill("Never saved");
  await dlg(t).locator("input[type=file]").setInputFiles(path.join(__dirname, "big.jpg"));
  await t.page.waitForFunction(() => document.querySelector(".dlg .picbox img"));
  await dlg(t).getByRole("button", { name: "Cancel", exact: true }).click();
  await sleep(200);
  check("cancel: nothing uploaded", t.fake.log.filter(x => x.fn === "POST").length === before);
  // delete the wish with the picture: picture goes at the end
  await drawerItem(t, "#meBtn");
  await dlg(t).getByLabel("Instant delete").check();
  await t.page.getByRole("button", { name: "Save", exact: true }).click();
  await t.page.locator(".card", { hasText: "Poster" }).getByLabel("Delete", { exact: true }).click();
  check("picture kept during undo time", Object.keys(t.fake.photos).length === 1);
  await t.page.evaluate(() => window.dispatchEvent(new Event("pagehide")));
  await sleep(800);
  check("deleting the wish deletes its picture online", Object.keys(t.fake.photos).length === 0 && !t.fake.items(CODE).some(i => i.title === "Poster"), t.fake.photos);
  check("no js errors (pictures)", t.errors.length === 0, t.errors);
  await t.ctx.close();

  // ---- a picture that cannot be uploaded ----
  t = await boot();
  t.fake.storageCors = false;
  await t.page.waitForSelector(".card");
  await t.page.locator("#fab").click();
  await lab(t, "Title").fill("Rug");
  await dlg(t).locator("input[type=file]").setInputFiles(path.join(__dirname, "big.jpg"));
  await t.page.waitForFunction(() => document.querySelector(".dlg .picbox img"));
  await t.page.getByRole("button", { name: "Save", exact: true }).click();
  await settle(t);
  const rug = t.fake.items(CODE).find(i => i.title === "Rug");
  check("failed upload: wish saved without a picture", rug && !rug.image && /Saved without the picture/.test(await t.page.locator("#toast").innerText()), rug);
  check("failed upload: no broken image on the card", (await t.page.locator(".card", { hasText: "Rug" }).locator("img[src^=blob]").count()) === 0);
  await t.page.locator(".card", { hasText: "Desk lamp" }).getByLabel("Edit", { exact: true }).click();
  await dlg(t).locator("input[type=file]").setInputFiles(path.join(__dirname, "big.jpg"));
  await t.page.waitForFunction(() => document.querySelector(".dlg .picbox img"));
  await t.page.getByRole("button", { name: "Save", exact: true }).click();
  await settle(t);
  check("failed upload on an edit keeps the old picture", t.fake.lists[CODE].items[1003].image === "https://img.example/lamp.png");
  await t.ctx.close();

  // ---- a picture of a picture that is not an image ----
  t = await boot();
  await t.page.waitForSelector(".card");
  await t.page.locator("#fab").click();
  const notImg = path.join(require("os").tmpdir(), "wl-notimg.jpg");
  require("fs").writeFileSync(notImg, "this is not a picture");
  await dlg(t).locator("input[type=file]").setInputFiles(notImg);
  await t.page.waitForFunction(() => /Couldn’t use that picture/.test(document.querySelector(".dlg").innerText));
  check("a file that is not a picture is refused kindly", true);
  await t.ctx.close();

  // ---- offline ----
  t = await boot();
  await t.page.waitForSelector(".card");
  t.fake.hooks.wl_sh_get = async () => { throw Object.assign(new Error("down"), { status: 500 }); };
  await t.page.reload();
  await t.page.waitForSelector(".card");
  await t.page.waitForFunction(() => /No connection/.test(document.getElementById("toast").textContent));
  check("offline: last copy shown with a message", (await t.page.locator(".card").count()) === 3);
  await t.ctx.close();
  t = await boot({ goto: false });
  t.fake.hooks.wl_sh_get = async () => { throw Object.assign(new Error("down"), { status: 500 }); };
  await t.page.goto(t.url);
  await t.page.waitForFunction(() => /Couldn’t load the list/.test(document.body.innerText));
  t.fake.hooks = {};
  await t.page.getByRole("button", { name: "Try again" }).click();
  await t.page.waitForSelector(".card");
  check("offline with no copy: try again works", (await t.page.locator(".card").count()) === 3);
  await t.ctx.close();

  // ---- iPhone tip ----
  const IOS = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1";
  t = await boot({ ua: IOS });
  await t.page.waitForSelector(".card");
  check("iPhone: tip shown", await t.page.locator("#tip").isVisible() && /Add to Home Screen/.test(await t.page.locator("#tip").innerText()));
  await t.page.getByRole("button", { name: "OK" }).click();
  await t.page.reload(); await t.page.waitForSelector(".card");
  check("iPhone: tip gone for good after Got it", await t.page.locator("#tip").isHidden());
  await t.ctx.close();
  t = await boot();
  await t.page.waitForSelector(".card");
  check("Android/computer: no tip", await t.page.locator("#tip").isHidden());

  // ---- settings: name ----
  await drawerItem(t, "#meBtn");
  await lab(t, "Your name").fill("Jo");
  await t.page.getByRole("button", { name: "Save", exact: true }).click();
  await t.page.locator("#fab").click();
  await lab(t, "Title").fill("Mug");
  await t.page.getByRole("button", { name: "Save", exact: true }).click();
  await settle(t);
  check("new wishes carry the new name", t.fake.items(CODE).find(i => i.title === "Mug").by === "Jo");
  check("side menu shows the name", /Jo/.test(await t.page.locator("#who").textContent()));
  await t.ctx.close();
  const failed = summary(); await done(); process.exit(failed ? 1 : 0);
})().catch(async e => { console.log("CRASH", e); await done(); process.exit(1); });
