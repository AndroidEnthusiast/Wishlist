const { boot, done, check, summary, KEY, CODE, sleep, openDrawer } = require("./lib.js");
const ANDROID = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36";
const IOS = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1";
(async () => {
  // friends page still fine with the shared stylesheet
  let t = await boot({ goto: false });
  await t.page.goto(t.base + "?c=" + CODE);
  await t.page.waitForSelector(".card");
  check("friends page: 3 cards", (await t.page.locator(".card").count()) === 3);
  check("friends page: styled (card has a radius from wishlist.css)", parseFloat(await t.page.locator(".card").first().evaluate(e => getComputedStyle(e).borderRadius)) > 8);
  await t.page.screenshot({ path: "s_friends.png" });
  check("friends page: no js errors", t.errors.length === 0, t.errors);
  await t.ctx.close();

  // 2.100: new visitors start with two per row, and a photo sits on a blur of itself (Blur stage) in both pages
  t = await boot({ goto: false, view: null });
  await t.page.goto(t.base + "?c=" + CODE);
  await t.page.waitForSelector(".card");
  check("friends page: two per row by default", (await t.page.locator(".grid.two .card.compact").count()) === 3);
  await t.page.goto(t.url);
  await t.page.waitForSelector(".card");
  check("editor: two per row by default", (await t.page.locator(".grid.two .card.compact").count()) === 3);
  const stage = await t.page.evaluate(() => [...document.querySelectorAll(".pic")].filter(p => p.querySelector("img:not(.back)")).map(p => {
    const b = p.querySelector("img.back"), f = p.querySelector("img:not(.back)"), r = p.getBoundingClientRect();
    return { back: !!b && b.getAttribute("aria-hidden") === "true" && b.alt === "" && /blur/.test(getComputedStyle(b).filter) && b.src === f.src,
             square: Math.abs(r.width - r.height) < 2, front: +getComputedStyle(f).zIndex > 0 };
  }));
  check("blur stage: every photo has its hidden blurred copy behind it", stage.length > 0 && stage.every(x => x.back && x.front), stage);
  check("blur stage: square pictures when two per row", stage.every(x => x.square), stage);
  await t.ctx.close();

  // iPhone: the invitation page (so it's clear what they're invited to), with only "Edit in the browser"
  t = await boot({ goto: false, ua: IOS });
  await t.page.goto(t.base + "?j=" + KEY);
  await t.page.waitForSelector(".inv .btns");
  check("iPhone: invitation page with the list name", (await t.page.locator(".inv .listname").innerText()).trim() === "Our home 🎁");
  check("iPhone: only Edit in the browser", JSON.stringify(await t.page.locator(".inv .btns a").allInnerTexts()) === JSON.stringify(["Edit in the browser"]));
  check("iPhone: confetti too", (await t.page.locator(".inv canvas.confetti").count()) === 1);
  await t.page.screenshot({ path: "s_invite_iphone.png" });
  check("iPhone: the invitation fits on one screen (no scrolling), language and theme included", await t.page.evaluate(() => document.documentElement.scrollHeight <= innerHeight && document.documentElement.scrollWidth <= innerWidth) && await t.page.locator("#langs .themes").isVisible());
  // light/dark picked here goes along to the editor, also into a browser that can't see it (English only since 4 November 2026)
  await t.page.locator("#langs button", { hasText: "Dark" }).click();
  check("no language switch any more", (await t.page.locator("#langs button", { hasText: "Nederlands" }).count()) === 0);
  await t.page.locator(".inv .btns a").click();
  await t.page.waitForURL(u => /edit\.html\?j=/.test(u.toString()));
  await t.page.waitForSelector(".card");
  check("iPhone: the button opens the editor with the list, in dark", (await t.page.locator("#name").innerText()) === "Our home" && t.page.url() === t.base + "edit.html?j=" + KEY + "&t=dark" && await t.page.evaluate(() => document.documentElement.getAttribute("data-theme") === "dark"));
  check("iPhone: no js errors", t.errors.length === 0, t.errors);
  await t.ctx.close();

  // a browser that never saw the invitation (another app's browser, a home-screen icon) follows the link
  t = await boot({ goto: false, ua: IOS });
  await t.page.goto(t.base + "edit.html?j=" + KEY + "&l=nl&t=dark"); // an old link with l=nl: just English
  await t.page.waitForSelector(".card");
  check("scripts and styles carry ?v= so a release reloads them", (await t.page.evaluate(() => [...document.querySelectorAll("script[src], link[rel=stylesheet]")].filter(e => !/fonts\.googleapis/.test(e.src || e.href)).every(e => /\?v=/.test(e.src || e.href)))));
  check("fresh browser: editor dark from the link, and remembered; an old l=nl shows English", await t.page.evaluate(() => document.documentElement.getAttribute("data-theme") === "dark" && localStorage.getItem("wl_theme") === "dark") && /Add wish/.test(await t.page.locator("body").innerText()));
  await t.ctx.close();

  // a browser that still has an old saved i18n.js (without prefsQuery) next to the new page: the invitation still shows
  t = await boot({ goto: false, ua: ANDROID });
  await t.page.route(/i18n\.js/, r => r.fulfill({ contentType: "text/javascript", body: require("fs").readFileSync(require("path").join(__dirname, "old_i18n.js"), "utf8") }));
  await t.page.goto(t.base + "?j=" + KEY);
  await t.page.waitForSelector(".inv .btns");
  check("old saved i18n.js: invitation still complete", (await t.page.locator(".inv .btns a").count()) === 3 && t.errors.length === 0, t.errors);
  await t.ctx.close();

  // a small Android phone: the invitation with three buttons still fits on one screen
  t = await boot({ goto: false, ua: ANDROID, viewport: { width: 360, height: 640 } });
  await t.page.goto(t.base + "?j=" + KEY);
  await t.page.waitForSelector(".inv .btns");
  check("small phone: invitation fits on one screen", await t.page.evaluate(() => document.documentElement.scrollHeight <= innerHeight && document.documentElement.scrollWidth <= innerWidth));
  await t.ctx.close();

  // Android: invitation page with the three buttons; the browser one opens the editor
  t = await boot({ goto: false, ua: ANDROID });
  await t.page.goto(t.base + "?j=" + KEY);
  await t.page.waitForSelector(".inv .btns");
  check("Android: three buttons", JSON.stringify(await t.page.locator(".inv .btns a").allInnerTexts()) === JSON.stringify(["Open in the app", "Download app", "Edit in the browser"]));
  const before = await t.page.locator(".inv h2").boundingBox();
  check("invite: confetti bursts above the heading", (await t.page.locator(".inv canvas.confetti").count()) === 1);
  check("invite: the confetti may use the whole screen, up to the top", await t.page.locator(".inv canvas.confetti").evaluate(c => { const r = c.getBoundingClientRect(); return r.top === 0 && r.height === innerHeight && getComputedStyle(c).position === "fixed"; }));
  const vh = t.page.viewportSize().height;
  check("invite: heading and buttons in the middle of the screen", before.y > vh * 0.25 && before.y < vh * 0.6, before);
  await sleep(350);
  await t.page.screenshot({ path: "s_confetti.png" });
  check("invite: heading, then the list name in bigger letters, no long help text", (await t.page.locator(".inv h2").innerText()).trim() === "You’re invited to add gifts for:" && (await t.page.locator(".inv .listname").innerText()).trim() === "Our home 🎁" && (await t.page.locator(".inv .listname").evaluate(e => getComputedStyle(e).fontSize)) === (await t.page.locator(".inv h2").evaluate(e => getComputedStyle(e).fontSize)) && Number(await t.page.locator(".inv .listname").evaluate(e => getComputedStyle(e).fontWeight)) > Number(await t.page.locator(".inv h2").evaluate(e => getComputedStyle(e).fontWeight)) && !/Already have/.test(await t.page.locator(".inv").innerText()));
  const during = await t.page.locator(".inv h2").boundingBox();
  check("invite: the confetti doesn't move the words", Math.abs(during.y - before.y) < 1, [before.y, during.y]);
  await sleep(2100);
  check("invite: confetti is gone afterwards", (await t.page.locator(".inv canvas.confetti").count()) === 0);
  const after = await t.page.locator(".inv h2").boundingBox();
  check("invite: nothing jumps when it's gone", Math.abs(after.y - before.y) < 1, [before.y, after.y]);
  await t.page.screenshot({ path: "s_invite.png" });
  await t.page.getByRole("link", { name: "Edit in the browser" }).click();
  await t.page.waitForSelector(".card");
  check("Android: Edit in the browser opens the editor", /edit\.html\?j=/.test(t.page.url()) && (await t.page.locator(".card").count()) === 3);
  await t.ctx.close();

  // ---- the three kinds of links each have their own preview ----
  const fs = require("fs"), path = require("path"), { WEB } = require("./lib.js");
  const meta = (file, prop) => { const m = fs.readFileSync(path.join(WEB, file), "utf8").match(new RegExp('<meta property="' + prop + '" content="([^"]*)"')); return m && m[1]; };
  check("preview: own list says my wishes", /my wishes/.test(meta("index.html", "og:title")) && /\/og\.png$/.test(meta("index.html", "og:image")));
  check("preview: shared list's friends link", /this wishlist/.test(meta("list/index.html", "og:title")) && /\/og-list\.png$/.test(meta("list/index.html", "og:image")) && !/my wishes/.test(meta("list/index.html", "og:title")));
  check("preview: invite link is about putting the list together", /Join our wishlist/.test(meta("join/index.html", "og:title")) && /\/og-join\.png$/.test(meta("join/index.html", "og:image")) && !/Reserve on a gift/.test(meta("join/index.html", "og:description")));
  check("preview pictures exist", fs.statSync(path.join(WEB, "og-list.png")).size > 5000 && fs.statSync(path.join(WEB, "og-join.png")).size > 5000);

  // join/ goes on to the invite page (Android: the three buttons)
  t = await boot({ goto: false, ua: ANDROID });
  await t.page.goto(t.base + "join/?j=" + KEY);
  await t.page.waitForSelector(".inv .btns");
  check("join/: opens the invitation", t.page.url() === t.base + "?j=" + KEY && /Accept the invitation to add gift ideas/.test(await t.page.locator(".inv").innerText()));
  await t.ctx.close();

  // list/ goes on to the friends' page of a shared list: no most wanted, wording for givers
  t = await boot({ goto: false });
  await t.page.goto(t.base + "list/?c=" + CODE);
  await t.page.waitForSelector(".card");
  check("list/: friends' page with the list", t.page.url() === t.base + "?c=" + CODE + "&s=1" && (await t.page.locator(".card").count()) === 3);
  check("list/: no most wanted label (even on a wish starred earlier)", (await t.page.locator(".badge.star").count()) === 0);
  check("list/: no most wanted sort", (await t.page.locator('#sort option[value="star"]').count()) === 0 && (await t.page.locator("#sort").inputValue()) === "asis");
  check("list/: givers see reservations", /Everyone who puts this list together sees/.test(await t.page.locator("#note").innerText()) && !/owner/.test(await t.page.locator("#note").innerText()));
  check("list/: no js errors", t.errors.length === 0, t.errors);
  await t.ctx.close();
  // the own list still has its most wanted
  t = await boot({ goto: false });
  await t.page.goto(t.base + "?c=" + CODE);
  await t.page.waitForSelector(".card");
  await t.page.screenshot({ path: "s_topwish.png", clip: await t.page.locator(".card").first().boundingBox() });
  check("Top wish label: gold, bold words", await t.page.locator(".badge.star").evaluate(b => getComputedStyle(b).backgroundColor === "rgb(255, 200, 61)" && Number(getComputedStyle(b).fontWeight) >= 700) && (await t.page.locator(".badge.star .lbl").innerText()) === "Top wish");
  check("own list's friends page keeps most wanted", (await t.page.locator(".badge.star").count()) === 1 && (await t.page.locator('#sort option[value="star"]').count()) === 1);
  await t.ctx.close();

  // less motion: no confetti
  t = await boot({ goto: false, ua: ANDROID, reduced: true });
  await t.page.goto(t.base + "?j=" + KEY);
  await t.page.waitForSelector(".inv .btns");
  check("invite: no confetti when the phone asks for less motion", (await t.page.locator(".inv canvas.confetti").count()) === 0);
  await t.ctx.close();

  // ---- a price that went up shows "+20%" in red, the opposite of a drop ("−20%", plus and minus since 2.83) ----
  const risen = [
    { id: 2002, url: "https://shop.example/a", title: "Kettle", desc: "", image: "", price: 60, old: 50, cur: "EUR", cat: "", got: false },
    { id: 2001, url: "https://shop.example/b", title: "Mug", desc: "", image: "", price: 8, old: 10, cur: "EUR", cat: "", got: false },
  ];
  t = await boot({ items: risen });
  await t.page.waitForSelector(".card");
  const k = t.page.locator(".card[data-id='2002']"), m = t.page.locator(".card[data-id='2001']");
  check("editor: price increase badge", (await k.locator(".badge.rise").innerText()).trim() === "+20%" && (await k.locator(".price.rise").count()) === 1 && (await k.locator(".old").count()) === 1);
  check("editor: drop still green with minus", /−20%/.test(await m.locator(".badge.drop").innerText()) && (await m.locator(".badge.rise").count()) === 0);
  await t.ctx.close();
  t = await boot({ goto: false, items: risen });
  await t.page.goto(t.base + "?c=" + CODE);
  await t.page.waitForSelector(".card");
  check("friends page: price increase badge", /^\+20%/.test(await t.page.locator(".card", { hasText: "Kettle" }).locator(".badge").first().innerText()));
  await t.ctx.close();

  // ---- a Dutch browser still gets English (Dutch removed, 4 November 2026) ----
  t = await boot({ locale: "nl-NL" });
  await t.page.waitForSelector(".card");
  check("Dutch browser: English editor", (await t.page.locator("#fab").innerText()).trim() === "Add wish" && t.errors.length === 0, t.errors);
  await t.ctx.close();

  // ---- light or dark, chosen at the bottom of the page (or the device decides) ----
  t = await boot({ goto: false });
  await t.page.goto(t.base + "?c=" + CODE);
  await t.page.waitForSelector(".card");
  const bg = () => t.page.evaluate(() => getComputedStyle(document.body).backgroundColor);
  check("light by default on a light device", (await bg()) === "rgb(251, 248, 255)");
  await t.page.getByRole("button", { name: "Dark", exact: true }).click();
  check("Dark: dark page at once", (await bg()) === "rgb(18, 19, 24)" && (await t.page.locator("html").getAttribute("data-theme")) === "dark");
  await t.page.reload(); await t.page.waitForSelector(".card");
  check("Dark is remembered", (await bg()) === "rgb(18, 19, 24)");
  await t.page.getByRole("button", { name: "System", exact: true }).click();
  check("System: back to the device", (await bg()) === "rgb(251, 248, 255)" && (await t.page.locator("html").getAttribute("data-theme")) === null);
  await t.ctx.close();
  t = await boot({ dark: true });
  await t.page.waitForSelector(".card");
  await openDrawer(t);
  await t.page.locator("#drawer").getByRole("button", { name: "Light", exact: true }).click();
  check("editor: Light on a dark device", (await t.page.evaluate(() => getComputedStyle(document.body).backgroundColor)) === "rgb(251, 248, 255)");
  check("theme: no js errors", t.errors.length === 0, t.errors);
  await t.ctx.close();

  // a link to the editor with a broken key
  t = await boot({ goto: false });
  await t.page.goto(t.base + "edit.html?j=NOTAKEY");
  await t.page.waitForFunction(() => /doesn’t work|does not work/i.test(document.body.innerText));
  check("editor: a bad link says so", true);
  await t.ctx.close();
  const failed = summary(); await done(); process.exit(failed ? 1 : 0);
})().catch(async e => { console.log("CRASH", e); await done(); process.exit(1); });
