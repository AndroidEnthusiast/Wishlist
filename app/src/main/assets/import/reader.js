/*
 * The Import reader (owner, 18 November 2026; docs/decisions/import.md). It runs INSIDE a list page (GoWish, Giftful,
 * Amazon, bol.com, Etsy, Pinterest or any shop page) and hands back the wishes it finds, as plain data:
 *   WLImport.read(document, location.href) → {version, site, reader, name, date, wishes: [{title, url, image, price, love}]}
 * "price" is the price as written on the page ("€ 39,99"); the app turns it into a number with its own price reader.
 *
 * One file, three users: the app (built in as app/src/main/assets/import/reader.js and downloaded fresh from our web
 * pages, so a fix reaches every phone without a new app: raise VERSION with every change), the daily check on GitHub
 * (.github/workflows/import-check.yml, against the owner's test lists) and tests/import/test.js (saved pages).
 * It only reads the page: it never changes it, never sends anything anywhere and never touches the app.
 */
(function (root) {
  "use strict";
  var VERSION = 1;

  /** Sites with a reader of their own (step 2). Until a site has one, the general reader below reads it. */
  var SITES = [
    { site: "gowish", hosts: ["gowish.com", "gowish.nl", "gowish.dk", "gowish.de", "gowish.se", "gowish.no", "gowish.co.uk"] },
    { site: "giftful", hosts: ["giftful.com"] },
    { site: "amazon", hosts: ["amazon."] },
    { site: "bol", hosts: ["bol.com"] },
    { site: "etsy", hosts: ["etsy.com"] },
    { site: "pinterest", hosts: ["pinterest."] }
  ];
  /** site → function(doc, url) returning the same shape as anyPage, or null to let the general reader try. */
  var READERS = {};

  function siteOf(host) {
    host = String(host || "").toLowerCase().replace(/^www\./, "");
    for (var i = 0; i < SITES.length; i++) {
      for (var j = 0; j < SITES[i].hosts.length; j++) {
        var h = SITES[i].hosts[j];
        if (h.charAt(h.length - 1) === "." ? host.indexOf(h) === 0 || host.indexOf("." + h) >= 0 : host === h || host.slice(-h.length - 1) === "." + h) return SITES[i].site;
      }
    }
    return "";
  }

  function clean(s, max) {
    return String(s || "").replace(/\s+/g, " ").trim().slice(0, max || 200);
  }

  /** An absolute http(s) address, or "" (never javascript:, data: or anything else). */
  function abs(u, base) {
    if (!u) return "";
    try {
      var a = new URL(String(u).trim(), base);
      return a.protocol === "http:" || a.protocol === "https:" ? a.href : "";
    } catch (e) {
      return "";
    }
  }

  var PRICE_RE = /(?:€|£|\$|EUR|USD|GBP|CHF|SEK|NOK|DKK|PLN|kr\.?|zł)\s?\d[\d.,]*|\d[\d.,]*\s?(?:€|£|\$|EUR|USD|GBP|CHF|SEK|NOK|DKK|PLN|kr\.?|zł)/i;

  function priceIn(el) {
    var tagged = el.querySelectorAll("[class*='price' i], [data-price], [itemprop='price'], [class*='Price']");
    for (var i = 0; i < tagged.length; i++) {
      var c = tagged[i].getAttribute("content");
      var t = clean(tagged[i].textContent, 60);
      var m = t.match(PRICE_RE);
      if (m) return m[0];
      if (c && /\d/.test(c)) return clean(c, 30);
    }
    var all = clean(el.textContent, 2000).match(PRICE_RE);
    return all ? all[0] : "";
  }

  /** The best picture address of an <img> (lazy-loading shops keep it in data-src or srcset). */
  function imgSrc(img, base) {
    var set = img.getAttribute("srcset") || img.getAttribute("data-srcset") || "";
    var best = "", bestW = 0;
    set.split(",").forEach(function (part) {
      var bits = part.trim().split(/\s+/);
      var w = parseInt((bits[1] || "").replace(/[^0-9]/g, ""), 10) || 1;
      if (bits[0] && w >= bestW) { best = bits[0]; bestW = w; }
    });
    var cands = [best, img.getAttribute("data-src"), img.getAttribute("data-lazy-src"), img.getAttribute("data-original"), img.currentSrc, img.getAttribute("src")];
    for (var i = 0; i < cands.length; i++) {
      var u = abs(cands[i], base);
      if (u) return u;
    }
    return "";
  }

  function bigImg(el) {
    var imgs = el.querySelectorAll("img");
    for (var i = 0; i < imgs.length; i++) {
      var w = parseInt(imgs[i].getAttribute("width") || "0", 10);
      if (w && w < 40) continue; // an icon, not the product
      return imgs[i];
    }
    return null;
  }

  // ---- 1. the page's own product data (JSON-LD) ----
  function ldNodes(v, out) {
    if (!v || typeof v !== "object") return;
    if (Array.isArray(v)) { v.forEach(function (x) { ldNodes(x, out); }); return; }
    out.push(v);
    if (v["@graph"]) ldNodes(v["@graph"], out);
    if (v.itemListElement) ldNodes(v.itemListElement, out);
    if (v.item && typeof v.item === "object") ldNodes(v.item, out);
  }
  function isType(o, t) {
    var x = o["@type"];
    return (Array.isArray(x) ? x : [x]).some(function (y) { return String(y || "").slice(-t.length) === t; });
  }
  function ldPrice(o) {
    if (!o) return "";
    if (Array.isArray(o)) { for (var i = 0; i < o.length; i++) { var p = ldPrice(o[i]); if (p) return p; } return ""; }
    var n = o.price != null && o.price !== "" ? o.price : o.lowPrice;
    if (n != null && n !== "") return clean((o.priceCurrency || "") + " " + n, 40);
    return ldPrice(o.offers);
  }
  function ldImage(v) {
    if (typeof v === "string") return v;
    if (Array.isArray(v)) return v.length ? ldImage(v[0]) : "";
    if (v && typeof v === "object") return v.url || v.contentUrl || "";
    return "";
  }
  function fromLd(doc, base) {
    var nodes = [];
    var blocks = doc.querySelectorAll("script[type='application/ld+json']");
    for (var i = 0; i < blocks.length; i++) {
      try { ldNodes(JSON.parse(blocks[i].textContent), nodes); } catch (e) { /* a broken block is skipped */ }
    }
    var seen = {}, out = [];
    nodes.forEach(function (o) {
      if (!isType(o, "Product")) return;
      var w = { title: clean(o.name), url: abs(o.url || (o.offers && o.offers.url), base), image: abs(ldImage(o.image), base), price: ldPrice(o.offers), love: false };
      var k = w.url + "|" + w.title;
      if (!w.title || seen[k]) return;
      seen[k] = 1;
      out.push(w);
    });
    return out;
  }

  // ---- 2. product microdata ----
  function fromMicrodata(doc, base) {
    var els = doc.querySelectorAll("[itemtype*='schema.org/Product']");
    var out = [];
    for (var i = 0; i < els.length; i++) {
      var el = els[i];
      var name = el.querySelector("[itemprop='name']");
      var img = el.querySelector("[itemprop='image']");
      var link = el.querySelector("a[href]");
      var title = name ? clean(name.getAttribute("content") || name.textContent) : "";
      if (!title) continue;
      out.push({
        title: title,
        url: abs((el.querySelector("[itemprop='url']") || {}).href || (link && link.getAttribute("href")), base),
        image: img ? abs(img.getAttribute("content") || img.getAttribute("src") || img.getAttribute("href"), base) : "",
        price: priceIn(el),
        love: false
      });
    }
    return out;
  }

  // ---- 3. repeated cards: a grid or list of things that each have a picture and a link ----
  function sig(el) {
    var cls = (el.getAttribute("class") || "").split(/\s+/).filter(function (c) { return c && !/\d{2,}/.test(c); }).sort().slice(0, 3).join(".");
    return el.tagName + "." + cls;
  }
  function qualifies(el) {
    if (!el.querySelector || !bigImg(el)) return false;
    if (!(el.tagName === "A" && el.getAttribute("href")) && !el.querySelector("a[href]")) return false;
    var len = clean(el.textContent, 3000).length;
    return len > 1 && len < 700;
  }
  function cardLink(el) {
    if (el.tagName === "A") return el;
    var img = bigImg(el);
    var a = img && img.closest ? img.closest("a[href]") : null;
    if (a && el.contains(a)) return a;
    var links = el.querySelectorAll("a[href]");
    for (var i = 0; i < links.length; i++) {
      var h = links[i].getAttribute("href") || "";
      if (h && h.charAt(0) !== "#" && h.indexOf("javascript:") !== 0) return links[i];
    }
    return null;
  }
  function cardTitle(el, img, a) {
    var h = el.querySelector("h1, h2, h3, h4, h5, h6, [class*='title' i], [class*='name' i], [itemprop='name']");
    var t = h ? clean(h.textContent) : "";
    if (!t && img) t = clean(img.getAttribute("alt"));
    if (!t && a) t = clean(a.getAttribute("title") || a.getAttribute("aria-label") || a.textContent);
    return t;
  }
  function fromCards(doc, base) {
    var best = null, bestScore = 0;
    var parents = doc.querySelectorAll("body *");
    for (var i = 0; i < parents.length; i++) {
      var kids = parents[i].children;
      if (!kids || kids.length < 2) continue;
      var groups = {};
      for (var j = 0; j < kids.length; j++) {
        if (!qualifies(kids[j])) continue;
        var s = sig(kids[j]);
        (groups[s] = groups[s] || []).push(kids[j]);
      }
      for (var k in groups) {
        var g = groups[k];
        if (g.length < 2) continue;
        var withPrice = g.filter(function (c) { return !!priceIn(c); }).length;
        var score = g.length + withPrice * 0.5;
        if (score > bestScore) { best = g; bestScore = score; }
      }
    }
    if (!best) return [];
    var out = [], seen = {};
    best.forEach(function (el) {
      var img = bigImg(el);
      var a = cardLink(el);
      var w = { title: cardTitle(el, img, a), url: a ? abs(a.getAttribute("href"), base) : "", image: img ? imgSrc(img, base) : "", price: priceIn(el), love: false };
      var key = w.url + "|" + w.title;
      if (!w.title || seen[key]) return;
      seen[key] = 1;
      out.push(w);
    });
    // Links that all go to the same place are a menu or a banner row, not wishes.
    var urls = {};
    out.forEach(function (w) { urls[w.url] = 1; });
    return Object.keys(urls).length < 2 ? [] : out;
  }

  function listName(doc) {
    var og = doc.querySelector("meta[property='og:title']");
    var h1 = doc.querySelector("h1");
    return clean((og && og.getAttribute("content")) || (h1 && h1.textContent) || doc.title, 60);
  }

  /** The general reader: whichever way finds the most wishes. */
  function anyPage(doc, url) {
    var found = [fromLd(doc, url), fromMicrodata(doc, url), fromCards(doc, url)];
    var wishes = found.reduce(function (a, b) { return b.length > a.length ? b : a; }, []);
    return { name: listName(doc), date: "", wishes: wishes };
  }

  function read(doc, url) {
    var host = "";
    try { host = new URL(url).hostname; } catch (e) { /* no address: still try */ }
    var site = siteOf(host);
    var own = site && READERS[site] ? READERS[site](doc, url) : null;
    var res = own && own.wishes && own.wishes.length ? own : anyPage(doc, url);
    var wishes = (res.wishes || []).slice(0, 300).map(function (w) {
      return { title: clean(w.title), url: abs(w.url, url), image: abs(w.image, url), price: clean(w.price, 40), love: !!w.love };
    });
    return { version: VERSION, site: site, reader: own && own.wishes && own.wishes.length ? site : "any", name: clean(res.name, 60), date: clean(res.date, 10), wishes: wishes };
  }

  root.WLImport = { version: VERSION, read: read, siteOf: siteOf };
})(typeof window !== "undefined" ? window : this);
