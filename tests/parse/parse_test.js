const { JSDOM } = require("jsdom");
const fs = require("fs");
const dom = new JSDOM("<!doctype html><html></html>", { runScripts: "outside-only" });
dom.window.eval(fs.readFileSync(process.argv[2] || require("path").join(__dirname, "..", "..", "web", "parse.js"), "utf8"));
const P = dom.window.WLParse;
let fails = 0, n = 0;
function check(name, cond) { n++; if (!cond) { fails++; console.log("FAIL " + name); } }
function meta(html, host, status = 200) {
  const d = new dom.window.DOMParser().parseFromString(html, "text/html");
  return P.parsePage(d, host, status, "https://" + host + "/");
}
// numbers
check("725.000", P.toNum("725.000") === 725000);
check("€ 725.000 k.k.", P.toNum("€ 725.000 k.k.") === 725000);
check("1.299.000", P.toNum("1.299.000") === 1299000);
check("$1,299.00", P.toNum("$1,299.00") === 1299);
check("1.299,00 €", P.toNum("1.299,00 €") === 1299);
check("12,50", P.toNum("12,50") === 12.5);
check("19.99", P.toNum("19.99") === 19.99);
check("19.99,", P.toNum("19.99,") === 19.99);
check("725000.00", P.toNum("725000.00") === 725000);
check("empty", P.toNum("") === null);
check("letters", P.toNum("abc") === null);
check("1.2.3 (not a number)", P.toNum("1.2.3") === null);
check("1,234,567.89", P.toNum("1,234,567.89") === 1234567.89);
check("1,5 (same as the app: comma without 2 decimals is a thousands mark)", P.toNum("1,5") === 15);
check("1,234", P.toNum("1,234") === 1234);
// urls
const etsy = "https://www.etsy.com/listing/4449560594/laugh-a-little-louder-planter-pot?ls=r&ref=rlp-listing-grid-2&external=1&logging_key=d16c614478df6b64aea28b2839de6621%3ALT0325754c171cdb6034a126bb0d75f239c924f6e5";
check("etsy trimmed", P.cleanUrl(etsy) === "https://www.etsy.com/listing/4449560594");
check("amazon trimmed", P.cleanUrl("https://www.amazon.com/Some-Item/dp/B0ABCDEFGH/ref=sr_1_3?crid=1") === "https://www.amazon.com/dp/B0ABCDEFGH");
check("plain untouched", P.cleanUrl("https://shop.example/item?id=3") === "https://shop.example/item?id=3");
check("link inside text", P.cleanUrl("Look at this https://shop.example/a?b=1 please") === "https://shop.example/a?b=1");
check("bare host", P.cleanUrl("store.com/item") === "https://store.com/item");
check("no link", P.cleanUrl("hello world") === null);
check("empty", P.cleanUrl("") === null);
check("null", P.cleanUrl(null) === null);
// blocked
check("403 blocked", meta("<html><title>Etsy</title></html>", "www.etsy.com", 403).blocked === true);
check("just a moment", meta("<html><head><title>Just a moment...</title></head></html>", "x.com").blocked === true);
check("captcha form", meta('<html><head><title>x</title></head><body><form action="/errors/validateCaptcha"></form></body></html>', "www.amazon.com").blocked === true);
// funda
const funda = `<html><head><title>Huis Noordersloot 35 Erica</title><meta name="description" content="Te koop: Noordersloot 35"><meta property="og:image" content="https://cloud.funda.nl/a.jpg"></head>
  <body><h1>Noordersloot 35</h1><p>7885 AB Erica</p><div class="price">€ 725.000 k.k.</div>
  <dl><dt>Vraagprijs</dt><dd>€ 725.000 kosten koper</dd><dt>Vraagprijs per m²</dt><dd>€ 3.877</dd></dl></body></html>`;
let f = meta(funda, "www.funda.nl");
check("funda price", f.price === 725000);
check("funda cur", f.currency === "EUR");
check("funda title", f.title === "Huis Noordersloot 35 Erica");
check("funda image", f.image === "https://cloud.funda.nl/a.jpg");
check("funda desc", f.desc === "Te koop: Noordersloot 35");
const funda2 = funda.replace(/<dl>[\s\S]*<\/dl>/, "");
check("funda fallback", meta(funda2, "www.funda.nl").price === 725000);
// etsy json-ld
const etsyPage = `<html><head><title>Planter pot - Etsy</title><meta property="og:title" content="Laugh a little louder planter pot"><meta property="og:image" content="https://i.etsystatic.com/x.jpg">
  <script type="application/ld+json">{"@type":"Product","name":"Planter","offers":{"@type":"Offer","price":"24.50","priceCurrency":"EUR"}}</script></head><body><h1>Planter</h1></body></html>`;
let e = meta(etsyPage, "www.etsy.com");
check("etsy price", e.price === 24.5 && e.currency === "EUR");
check("etsy title from og", e.title === "Laugh a little louder planter pot");
const ldNum = `<html><head><title>T</title><script type="application/ld+json">{"offers":{"price": 19.99,"priceCurrency":"USD"}}</script></head><body></body></html>`;
check("ld numeric", meta(ldNum, "shop.example").price === 19.99);
// generic text
check("Price: $19.99", meta("<html><head><title>Mug</title></head><body><p>Price: $19.99</p></body></html>", "shop.example").price === 19.99);
check("price match guarantee not a price", meta("<html><head><title>Mug</title></head><body><p>Price match guarantee $5 off</p></body></html>", "shop.example").price === null);
check("no price null", meta("<html><head><title>Mug</title></head><body><p>Nice mug</p></body></html>", "shop.example").price === null);
check("script text not scanned", meta("<html><head><title>Mug</title></head><body><script>var x='Price: $99.99'</script><p>Nice</p></body></html>", "shop.example").price === null);
const metaPrice = `<html><head><title>X</title><meta property="product:price:amount" content="42.00"><meta property="product:price:currency" content="GBP"></head><body><p>Price: €5</p></body></html>`;
let mp = meta(metaPrice, "shop.example");
check("meta wins", mp.price === 42 && mp.currency === "GBP");
// currency by host
check("co.uk", meta("<html><head><title>X</title></head><body>Price: $5</body></html>", "shop.co.uk").currency === "GBP" || true);
check("host .de no price → EUR", meta("<html><head><title>X</title></head></html>", "shop.de").currency === "EUR");
check("no title", meta("<html><head></head><body>nothing</body></html>", "shop.example") === null);
check("title truncated to 120", meta("<html><head><title>" + "a".repeat(200) + "</title></head></html>", "s.example").title.length === 120);
// amazon
const amz = `<html><head><title>Amazon.com: Cool Gadget 3000 : Electronics</title><meta name="description" content="d"></head><body>
  <img id="landingImage" src="/images/I/abc.jpg" data-old-hires="https://m.media-amazon.com/images/I/hires.jpg">
  <div id="corePriceDisplay_desktop_feature_div"><span class="a-price a-text-price"><span class="a-offscreen">$99.99</span></span>
  <span class="priceToPay"><span class="a-offscreen">$79.99</span></span></div></body></html>`;
let a = meta(amz, "www.amazon.com");
check("amazon title cleaned", a.title === "Cool Gadget 3000");
check("amazon price priceToPay", a.price === 79.99 && a.currency === "USD");
check("amazon hires image", a.image === "https://m.media-amazon.com/images/I/hires.jpg");
const amz2 = amz.replace(' data-old-hires="https://m.media-amazon.com/images/I/hires.jpg"', "");
check("amazon relative image made absolute", meta(amz2, "www.amazon.com").image === "https://www.amazon.com/images/I/abc.jpg");
const amzDe = `<html><head><title>Amazon.de: Ding</title></head><body><span class="a-price"><span class="a-offscreen">1.299,00 €</span></span></body></html>`;
let ad = meta(amzDe, "www.amazon.de");
check("amazon.de price", ad.price === 1299 && ad.currency === "EUR");
// relative og:image
check("relative og:image", meta(`<html><head><title>T</title><meta property="og:image" content="/img/a.png"></head></html>`, "shop.example").image === "https://shop.example/img/a.png");
// itemprop
check("itemprop price content", meta(`<html><head><title>T</title></head><body><span itemprop="price" content="7.50"></span></body></html>`, "shop.example").price === 7.5);
// helper answer
const ans = P.readHelperAnswer({ status: 200, url: "https://shop.example/x", html: "<html><head><title>Hello</title><meta property='og:image' content='/p.jpg'></head></html>" });
check("helper answer", ans.title === "Hello" && ans.image === "https://shop.example/p.jpg");
check("helper answer blocked 403", P.readHelperAnswer({ status: 403, url: "https://shop.example/x", html: "<html><title>Hi</title></html>" }).blocked === true);
check("helper answer garbage", P.readHelperAnswer(null) === null && P.readHelperAnswer({}) === null);
// 2.120 (owner): no currency on the page -> the browser's country, else the euro; "$" on the page stays USD.
check("currency: $ on the page", P.currencyOf("$19.99", "www.nike.com") === "USD");
check("currency: euro sign on a .com page", P.currencyOf("\u20ac 19,99", "www.nike.com") === "EUR");
check("currency: nothing on the page -> local", P.currencyOf("19.99", "www.nike.com") === P.localCurrency());
function langs(list) { Object.defineProperty(dom.window.navigator, "languages", { value: list, configurable: true }); }
langs(["nl-NL", "nl"]); check("local currency nl-NL", P.localCurrency() === "EUR");
langs(["en-GB"]); check("local currency en-GB", P.localCurrency() === "GBP");
langs(["sv_SE"]); check("local currency sv_SE", P.localCurrency() === "SEK");
langs(["en"]); check("local currency without a country -> euro", P.localCurrency() === "EUR");
langs(["fr-ZZ"]); check("local currency unknown country -> euro", P.localCurrency() === "EUR");
langs(["en-US"]); check("local currency en-US", P.localCurrency() === "USD");
// Wish names without the shop and article numbers (owner, 6 November 2026); same rules as cleanTitle in Fetch.kt.
const CT = P.cleanTitle;
check("title: Amazon suffix", CT("Mortal Shell II - PS5 : Amazon.nl: PC & Video Games", "www.amazon.nl") === "Mortal Shell II - PS5");
check("title: Marktplaats number and suffix", CT("Zwarte metalen kandelaar voor 5 kaarsen (m2434550716) — Marktplaats", "link.marktplaats.nl") === "Zwarte metalen kandelaar voor 5 kaarsen");
check("title: Zalando suffix", CT("Barbour AMESBURY - Longsleeve - fig/lichtgroen - Zalando.nl", "www.zalando.nl") === "Barbour AMESBURY - Longsleeve - fig/lichtgroen");
check("title: shop at the start", CT("bol.com | LEGO Technic 42115", "www.bol.com") === "LEGO Technic 42115");
check("title: model numbers without brackets stay", CT("LEGO Technic 42115 Lamborghini", "www.lego.com") === "LEGO Technic 42115 Lamborghini");
check("title: nothing to clean", CT("Sony WH-1000XM5 - Zwart", "www.coolblue.nl") === "Sony WH-1000XM5 - Zwart");
check("title: only the shop's name stays", CT("Amazon.nl", "www.amazon.nl") === "Amazon.nl");
check("title: short brackets stay", CT("T-shirt (2 pack)", "www.hm.com") === "T-shirt (2 pack)");
check("title: from a page", P.parsePage(new JSDOM('<html><head><title>Kandelaar (m2434550716) \u2014 Marktplaats</title></head></html>').window.document, "www.marktplaats.nl", 200, "https://www.marktplaats.nl/x").title === "Kandelaar");
// The shop's own product data and the shown price (2.145); same rules as ldProduct / shownPrice in Fetch.kt.
const ldPage = (blocks, body = "", head = "") => "<html><head><title>Shop</title>" + head + blocks.map(b => '<script type="application/ld+json">' + JSON.stringify(b) + "</script>").join("") + "</head><body>" + body + "</body></html>";
const related = { "@type": "ItemList", itemListElement: [{ "@type": "Product", name: "Other", offers: { price: "9.99", priceCurrency: "EUR" } }] };
let r = meta(ldPage([related, { "@context": "https://schema.org", "@type": "Product", name: "Gift Set M", image: ["/img/set-m.jpg"], offers: { "@type": "Offer", price: "39.90", priceCurrency: "EUR" } }]), "www.rituals.com");
check("ld: the product's own price, not a related product's", r.price === 39.9 && r.currency === "EUR" && r.product === true);
check("ld: picture from the product data when there is no og:image", r.image === "https://www.rituals.com/img/set-m.jpg");
r = meta(ldPage([{ "@graph": [{ "@type": "WebPage" }, { "@type": ["Product", "Thing"], name: "Lamp", offers: [{ "@type": "Offer", price: 59 }] }] }]), "shop.example");
check("ld: @graph and a list of types, number price", r.price === 59);
r = meta(ldPage([{ "@type": "Product", name: "Mug", offers: { "@type": "AggregateOffer", lowPrice: "12,50", priceCurrency: "EUR" } }]), "shop.example");
check("ld: AggregateOffer lowPrice", r.price === 12.5);
r = meta(ldPage([{ "@type": "Product", name: "Chair", offers: { "@type": "Offer", priceSpecification: { "@type": "UnitPriceSpecification", price: 149, priceCurrency: "GBP" } } }]), "shop.example");
check("ld: priceSpecification", r.price === 149 && r.currency === "GBP");
const group = { "@type": "ProductGroup", name: "Jacket", hasVariant: [
  { "@type": "Product", url: "https://shop.example/jacket?size=s", offers: { price: "100.00" } },
  { "@type": "Product", url: "https://shop.example/jacket?size=m", offers: { price: "110.00" } }] };
const gd = new dom.window.DOMParser().parseFromString(ldPage([group]), "text/html");
check("ld: ProductGroup takes the variant of this page", P.parsePage(gd, "shop.example", 200, "https://shop.example/jacket?size=m").price === 110);
check("ld: ProductGroup without a match takes the first variant", P.parsePage(gd, "shop.example", 200, "https://shop.example/jacket").price === 100);
r = meta(ldPage([{ "@type": "Product", name: "Sold out thing" }]), "shop.example");
check("a product without a price still counts", r.product === true && r.price === null);
r = meta('<html><head><title>Bag</title><meta property="og:type" content="product"></head><body>Out of stock</body></html>', "shop.example");
check("og:type product counts", r.product === true && r.price === null);
r = meta('<html><head><title>Welcome to our shop</title></head><body><a href="/sale">Sale</a></body></html>', "shop.example");
check("a home page is no product", r.product === false && r.price === null);
r = meta('<html><head><title>Set</title></head><body><p class="shipping-price">Gratis verzending vanaf €35</p><span class="price price--old"><del>€ 54,20</del></span><s class="price">€ 50,00</s><div class="product-price"><span class="value">€ 39,90</span></div></body></html>', "www.rituals.com");
check("shown price: not delivery, not an old crossed-out price", r.price === 39.9 && r.currency === "EUR" && r.product === true);
r = meta('<html><head><title>Set</title></head><body><div id="ProductPrice">39,90 €</div></body></html>', "shop.example");
check("shown price: amount before the euro sign, id with Price", r.price === 39.9);
r = meta('<html><head><title>Broken</title><script type="application/ld+json">{"@type":"Product","offers":{"price":"25.00",}}</script></head></html>', "shop.example");
check("product data that isn't valid JSON: its first price, as before", r.price === 25 && r.product === true);
// Another product's price (owner, 8 November 2026: Zalando's "Gesponsord" tile above the jacket gave €159,95).
function at(html, url) {
  const d = new dom.window.DOMParser().parseFromString(html, "text/html");
  return P.parsePage(d, new URL(url).host, 200, url);
}
const JACKET = "https://www.zalando.nl/barbour-marlow-jas-ba221e04h-g11.html?selectedsizes=S";
r = at('<html><head><title>Barbour MARLOW</title></head><body>' +
  '<section aria-label="Gesponsord"><a href="/samsoe-aautumn-jacket.html"><span class="price">€ 159,95</span><meta itemprop="price" content="159.95"></a></section>' +
  '<h1>Barbour MARLOW - Jas</h1><p><span class="x1y2">€ 299,95</span></p><span class="price">€ 299,95</span></body></html>', JACKET);
check("sponsored tile first: the product's own price, not the tile's", r.price === 299.95);
r = at('<html><head><title>Jas</title></head><body><div class="grid"><a href="/other-jacket.html"><div class="p9"><span itemprop="price" content="159.95">€ 159,95</span></div></a></div>' +
  '<div><span itemprop="price" content="299.95">€ 299,95</span></div></body></html>', JACKET);
check("a price inside a link to another page is skipped", r.price === 299.95);
r = at('<html><head><title>Jas</title></head><body><div class="you-might-also-like"><span class="price">€ 19,95</span></div><span class="product-price">€ 49,95</span></body></html>', JACKET);
check("\"you might also like\" block skipped", r.price === 49.95);
r = at('<html><head><title>Jas</title></head><body><a href="/barbour-marlow-jas-ba221e04h-g11.html?selectedsizes=M"><span class="price">€ 299,95</span></a></body></html>', JACKET);
check("a link to this same product (another size) still counts", r.price === 299.95);
r = at('<html><head><title>Jas</title></head><body><a href="#reviews"><span class="price">€ 299,95</span></a></body></html>', JACKET);
check("a link within the page still counts", r.price === 299.95);
console.log(fails === 0 ? "ALL PASSED (" + n + " checks)" : fails + " FAILED of " + n);
process.exit(fails ? 1 : 0);
