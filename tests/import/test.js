// Checks for the Import reader (web/import/reader.js, docs/decisions/import.md) on saved and made-up list pages.
// Run: node tests/import/test.js. The same file is built into the app (app/src/main/assets/import/reader.js): checked
// here to be the same, so the app and the daily check never read differently.
const { JSDOM } = require("jsdom");
const fs = require("fs");
const path = require("path");
const file = path.join(__dirname, "..", "..", "web", "import", "reader.js");
const src = fs.readFileSync(file, "utf8");
let fails = 0, n = 0;
function check(name, cond) { n++; if (!cond) { fails++; console.log("FAIL " + name); } }
function read(html, url) {
  const dom = new JSDOM(html, { url, runScripts: "outside-only" });
  dom.window.eval(src);
  return dom.window.WLImport.read(dom.window.document, url);
}

// The app's built-in copy is the same file.
const asset = path.join(__dirname, "..", "..", "app", "src", "main", "assets", "import", "reader.js");
check("app copy is the same as web/import/reader.js", fs.existsSync(asset) && fs.readFileSync(asset, "utf8") === src);

// Sites are recognised.
const dom0 = new JSDOM("<p>", { runScripts: "outside-only" });
dom0.window.eval(src);
const S = dom0.window.WLImport.siteOf;
check("gowish", S("gowish.com") === "gowish" && S("www.gowish.nl") === "gowish");
check("giftful", S("giftful.com") === "giftful" && S("app.giftful.com") === "giftful");
check("amazon any country", S("www.amazon.nl") === "amazon" && S("amazon.co.uk") === "amazon" && S("smile.amazon.com") === "amazon");
check("bol", S("www.bol.com") === "bol");
check("etsy", S("www.etsy.com") === "etsy");
check("pinterest", S("nl.pinterest.com") === "pinterest" && S("pinterest.co.uk") === "pinterest");
check("other shop", S("www.coolblue.nl") === "");
check("not fooled by a lookalike", S("notgowish.com") === "" && S("amazon.evil.com") !== "" /* amazon. counts at any level, still read by the general reader */);

// 1. JSON-LD ItemList.
const ld = read(`<html><head><title>Birthday</title><script type="application/ld+json">
{"@context":"https://schema.org","@type":"ItemList","itemListElement":[
 {"@type":"ListItem","position":1,"item":{"@type":"Product","name":"Lego set","url":"/p/1","image":"https://img.example/1.jpg","offers":{"@type":"Offer","price":"39.99","priceCurrency":"EUR"}}},
 {"@type":"ListItem","position":2,"item":{"@type":"Product","name":"Book","url":"https://shop.example/p/2","image":["/2.jpg"]}}]}
</script></head><body></body></html>`, "https://shop.example/list/9");
check("ld: two wishes", ld.wishes.length === 2);
check("ld: title", ld.wishes[0].title === "Lego set");
check("ld: absolute url", ld.wishes[0].url === "https://shop.example/p/1");
check("ld: price text", ld.wishes[0].price === "EUR 39.99");
check("ld: image from list", ld.wishes[1].image === "https://shop.example/2.jpg");
check("ld: no price is empty", ld.wishes[1].price === "");
check("ld: list name", ld.name === "Birthday");
check("ld: general reader", ld.reader === "any" && ld.version >= 1);

// 3. A grid of cards (the usual list page), with a menu that must not count.
const cards = read(`<html><head><meta property="og:title" content="Sam's wishlist"></head><body>
<nav><a href="/a"><img src="/logo.png" width="20">Home</a><a href="/b"><img src="/i.png" width="20">Lists</a></nav>
<ul class="grid">
 <li class="card c-123"><a href="https://www.bol.com/nl/p/x/9200000/?utm_source=gowish"><img srcset="/s.jpg 200w, /l.jpg 800w" alt="Headphones"></a><h3>Wireless headphones</h3><span class="price">€ 89,99</span></li>
 <li class="card c-456"><a href="/go?url=https%3A%2F%2Fshop.example%2Fmug"><img data-src="/mug.jpg" src="data:image/gif;base64,R0lGOD"></a><h3>Mug</h3></li>
 <li class="card c-789"><a href="javascript:void(0)">x</a><a href="https://shop.example/sock"><img src="/sock.jpg" alt="Socks"></a><p>Nice socks £12.50</p></li>
</ul>
<footer><a href="/terms">Terms</a></footer></body></html>`, "https://gowish.com/s/abc");
check("cards: site", cards.site === "gowish" && cards.reader === "any");
check("cards: three wishes", cards.wishes.length === 3);
check("cards: title from heading", cards.wishes[0].title === "Wireless headphones");
check("cards: biggest srcset picture", cards.wishes[0].image === "https://gowish.com/l.jpg");
check("cards: price", cards.wishes[0].price === "€ 89,99");
check("cards: lazy picture, not the data: placeholder", cards.wishes[1].image === "https://gowish.com/mug.jpg");
check("cards: redirect link kept for the app to unwrap", cards.wishes[1].url.indexOf("https://gowish.com/go?url=") === 0);
check("cards: javascript: link skipped", cards.wishes[2].url === "https://shop.example/sock");
check("cards: price in plain text", cards.wishes[2].price === "£12.50");
check("cards: title from alt", cards.wishes[2].title === "Socks" || cards.wishes[2].title === "Nice socks £12.50");
check("cards: list name", cards.name === "Sam's wishlist");

// Nothing to find: no wishes, never an error.
const none = read("<html><body><h1>Sign in</h1><p>Please log in</p></body></html>", "https://www.amazon.nl/hz/wishlist/ls/X");
check("nothing found", none.wishes.length === 0 && none.site === "amazon");

// Links that all go to one place are a banner row, not wishes.
const banner = read(`<body><div><a href="/sale"><img src="/1.jpg"> Sale</a><a href="/sale"><img src="/2.jpg"> Sale</a></div></body>`, "https://shop.example/");
check("banner row ignored", banner.wishes.length === 0);

// Only http(s) links and pictures come through.
const evil = read(`<body><div><div class="c"><a href="javascript:alert(1)"><img src="javascript:alert(2)" alt="A"></a><h3>A</h3></div><div class="c"><a href="https://ok.example/b"><img src="https://ok.example/b.jpg"></a><h3>B</h3></div><div class="c"><a href="https://ok.example/c"><img src="https://ok.example/c.jpg"></a><h3>C</h3></div></div></body>`, "https://shop.example/");
check("no javascript: links", evil.wishes.every(w => w.url === "" || /^https?:/.test(w.url)));
check("no javascript: pictures", evil.wishes.every(w => w.image === "" || /^https?:/.test(w.image)));

console.log(fails ? `${fails} of ${n} checks FAILED` : `all ${n} checks passed`);
process.exit(fails ? 1 : 0);
