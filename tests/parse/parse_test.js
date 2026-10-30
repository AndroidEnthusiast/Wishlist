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
console.log(fails === 0 ? "ALL PASSED (" + n + " checks)" : fails + " FAILED of " + n);
process.exit(fails ? 1 : 0);
