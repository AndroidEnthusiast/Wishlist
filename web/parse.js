/*
 * Reads a product page: title, picture, description and price. This is the same logic as Fetch.kt in the app
 * (parsePage, cleanUrl, toNum, currencyOf), written for the browser. A browser may not load a shop's page itself,
 * so the page's HTML comes from the helper function in Supabase (supabase/functions/fetch-page); this file only
 * reads it. If you change something here, change Fetch.kt the same way (and the other way around).
 */
(function (root) {
  "use strict";

  function hostOfUrl(u) {
    var m = /https?:\/\/([^\/?#]+)/.exec(String(u || ""));
    return m ? m[1] : "";
  }

  // Trims Amazon links to https://host/dp/ASIN so tracking junk never gets in the way.
  function amazonCanonical(u) {
    var host = hostOfUrl(u);
    if (!host || host.indexOf("amazon.") < 0) return u;
    var m = /\/(?:dp|gp\/product|gp\/aw\/d)\/([A-Z0-9]{10})/.exec(u);
    return m ? "https://" + host + "/dp/" + m[1] : u;
  }

  // Trims Etsy links to https://host/listing/ID, dropping the long tracking part.
  function etsyCanonical(u) {
    var host = hostOfUrl(u);
    if (!host || host.indexOf("etsy.com") < 0) return u;
    var m = /\/listing\/([0-9]+)/.exec(u);
    return m ? "https://" + host + "/listing/" + m[1] : u;
  }

  // Pulls the first link out of pasted text, or turns "store.com/item" into a full URL. null when there is none.
  function cleanUrl(s) {
    s = String(s === undefined || s === null ? "" : s);
    var m = /https?:\/\/\S+/.exec(s);
    var raw = m ? m[0] : null;
    if (!raw) {
      var t = s.trim();
      if (t.indexOf(".") >= 0 && !/\s/.test(t)) raw = "https://" + t;
    }
    if (!raw) return null;
    return etsyCanonical(amazonCanonical(raw));
  }

  // Turns "$1,299.00", "1.299,00 €", "12,50" or "725.000" into a number (null when there is none).
  function toNum(str) {
    var s = String(str === undefined || str === null ? "" : str).replace(/[^0-9.,]/g, "").replace(/^[.,]+|[.,]+$/g, "");
    if (!s) return null;
    var n;
    if (s.indexOf(",") >= 0 && s.indexOf(".") >= 0) {
      n = s.lastIndexOf(",") > s.lastIndexOf(".") ? s.replace(/\./g, "").replace(/,/g, ".") : s.replace(/,/g, "");
    } else if (s.indexOf(",") >= 0) {
      n = (s.length - s.lastIndexOf(",") === 3) ? s.replace(/,/g, ".") : s.replace(/,/g, "");
    } else if (/^[0-9]{1,3}(\.[0-9]{3})+$/.test(s)) {
      // "725.000" is seven hundred twenty-five thousand (dots as thousands separators), not 725.
      n = s.replace(/\./g, "");
    } else {
      n = s;
    }
    var v = Number(n);
    return isFinite(v) && /^[0-9]*\.?[0-9]*$/.test(n) && n !== "" && n !== "." ? v : null;
  }

  function endsWith(s, suffix) {
    return s.length >= suffix.length && s.slice(s.length - suffix.length) === suffix;
  }

  // True when the text itself says which currency it is: a symbol or a three-letter code. Same as Fetch.kt.
  function namesCurrency(text) {
    return /[€£$₹¥]|\b(EUR|GBP|USD|CAD|AUD|CHF|SEK|NOK|DKK|PLN|JPY|INR)\b/.test(String(text || ""));
  }

  function currencyOf(text, host) {
    text = String(text || ""); host = String(host || "");
    if (text.indexOf("£") >= 0 || endsWith(host, ".co.uk")) return "GBP";
    if (text.indexOf("€") >= 0 || endsWith(host, ".de") || endsWith(host, ".fr") || endsWith(host, ".it") ||
        endsWith(host, ".es") || endsWith(host, ".nl")) return "EUR";
    if (text.indexOf("₹") >= 0 || endsWith(host, ".in")) return "INR";
    if (text.indexOf("¥") >= 0 || endsWith(host, ".co.jp")) return "JPY";
    if (endsWith(host, ".ca")) return "CAD";
    if (endsWith(host, ".com.au")) return "AUD";
    if (endsWith(host, ".com.mx")) return "MXN";
    if (text.indexOf("$") >= 0) return "USD";
    // Nothing on the page says (owner, 2.120): the currency of the browser's country, else the euro. Before, this was $.
    return localCurrency();
  }

  // The currencies of the countries a browser may be set to; other countries get the euro. Same list as Fetch.kt.
  var COUNTRY_CURRENCY = { GB: "GBP", US: "USD", CA: "CAD", AU: "AUD", NZ: "NZD", JP: "JPY", CN: "CNY", KR: "KRW",
    IN: "INR", MX: "MXN", BR: "BRL", CH: "CHF", SE: "SEK", NO: "NOK", DK: "DKK", PL: "PLN" };
  ["AT", "BE", "CY", "DE", "EE", "ES", "FI", "FR", "GR", "HR", "IE", "IT", "LT", "LU", "LV", "MT", "NL", "PT", "SI", "SK"]
    .forEach(function (c) { COUNTRY_CURRENCY[c] = "EUR"; });

  // The currency of the browser's country (its first language with a country, like "nl-NL"), or the euro.
  function localCurrency() {
    var langs = [];
    try { langs = (typeof navigator !== "undefined" && (navigator.languages || [navigator.language])) || []; } catch (e) {}
    for (var i = 0; i < langs.length; i++) {
      var m = /^[a-z]{2,3}[-_]([A-Za-z]{2})\b/.exec(String(langs[i] || ""));
      if (m) return COUNTRY_CURRENCY[m[1].toUpperCase()] || "EUR";
    }
    return "EUR";
  }

  var AMAZON_PRICE = [
    "#corePriceDisplay_desktop_feature_div .priceToPay .a-offscreen",
    "#corePrice_feature_div .priceToPay .a-offscreen",
    "#corePriceDisplay_desktop_feature_div .a-price:not(.a-text-price) .a-offscreen",
    "#corePrice_feature_div .a-price:not(.a-text-price) .a-offscreen",
    "#apex_desktop .a-price:not(.a-text-price) .a-offscreen",
    "#priceblock_dealprice", "#priceblock_ourprice", "#price_inside_buy",
    "#tp_price_block_total_price_ww .a-offscreen",
    ".a-price:not(.a-text-price) .a-offscreen"
  ];

  // "Vraagprijs € 725.000", "Price: $19.99" and similar: a price word directly followed by a currency symbol.
  var PRICE_WORD = /\b(vraagprijs|huurprijs|prijs|price|preis|prix)\b\s*:?\s*([€$£])\s?([0-9][0-9.,]*)/i;
  // Funda shows the asking price as "€ 725.000 k.k."
  var EURO_THOUSANDS = /€\s?([0-9]{1,3}(?:\.[0-9]{3})+)/;

  // True for the titles that anti-bot pages use.
  function looksBlocked(title) {
    var t = String(title || "").toLowerCase();
    return ["robot check", "just a moment", "access denied", "attention required",
      "pardon our interruption", "captcha", "are you a human", "verify you are human"
    ].some(function (x) { return t.indexOf(x) >= 0; });
  }

  function absUrl(v, base) {
    try { return new URL(v, base).href; } catch (e) { return ""; }
  }

  // The shop's own name in its web address: "link.marktplaats.nl" -> "marktplaats", "www.bol.com" -> "bol".
  function shopWord(host) {
    var parts = String(host || "").toLowerCase().replace(/^www\./, "").split(".").filter(function (x) { return x; });
    return parts.length >= 2 ? parts[parts.length - 2] : (parts[0] || "");
  }
  // A wish name without the shop (owner, 6 November 2026): article numbers in brackets go, and so does a part that
  // names the shop at the end or the start. Same rules as cleanTitle in Fetch.kt.
  function cleanTitle(title, host) {
    var word = shopWord(host);
    var t = String(title || "").replace(/\s*[(\[][^)\]]*\d{5,}[^)\]]*[)\]]/g, "").replace(/\s+/g, " ").trim();
    if (word.length >= 3) {
      for (;;) {
        var re = /\s+[|\-–—:·]\s+/g, m, seps = [];
        while ((m = re.exec(t))) seps.push({ start: m.index, end: m.index + m[0].length });
        if (!seps.length) break;
        var last = seps[seps.length - 1], first = seps[0];
        if (t.slice(last.end).toLowerCase().indexOf(word) >= 0) t = t.slice(0, last.start).trim();
        else if (t.slice(0, first.start).toLowerCase().indexOf(word) >= 0) t = t.slice(first.end).trim();
        else break;
      }
    }
    return t || String(title || "").trim();
  }

  /**
   * Reads a loaded page (a Document). host: the shop's host name (after redirects). status: the HTTP status the shop
   * answered. base: the page's address, for turning a relative picture address into a full one.
   * Returns {title, desc, image, price, currency, blocked} or null when there is no title at all.
   */
  // ---- The shop's own product data (JSON-LD, schema.org), 2.145. Same rules as Fetch.kt (ldProduct, shownPrice). ----
  function ldIsProduct(o) {
    var t = o["@type"];
    var types = Array.isArray(t) ? t.map(String) : [String(t || "")];
    return types.some(function (x) { return endsWith(x, "Product") || endsWith(x, "ProductGroup"); });
  }
  function ldNodes(v) {
    if (Array.isArray(v)) return v.reduce(function (a, x) { return a.concat(ldNodes(x)); }, []);
    if (v && typeof v === "object") return [v].concat(v["@graph"] ? ldNodes(v["@graph"]).filter(function (x) { return x !== v; }) : []);
    return [];
  }
  function ldPrice(offers) {
    if (Array.isArray(offers)) {
      for (var i = 0; i < offers.length; i++) { var r = ldPrice(offers[i]); if (r) return r; }
      return null;
    }
    if (!offers || typeof offers !== "object") return null;
    var cur = String(offers.priceCurrency || "");
    var keys = ["price", "lowPrice"];
    for (var k = 0; k < keys.length; k++) {
      var v = offers[keys[k]];
      var n = v === undefined || v === null || v === "" ? null : toNum(String(v));
      if (n !== null && n > 0) return { price: n, currency: cur };
    }
    var ps = ldPrice(offers.priceSpecification);
    if (ps) return { price: ps.price, currency: ps.currency || cur };
    return ldPrice(offers.offers);
  }
  function ldImage(v) {
    if (typeof v === "string") return v;
    if (Array.isArray(v)) return v.length ? ldImage(v[0]) : "";
    if (v && typeof v === "object") return String(v.url || v.contentUrl || "");
    return "";
  }
  function ldProduct(d, url) {
    var scripts = d.querySelectorAll('script[type="application/ld+json"]');
    for (var i = 0; i < scripts.length; i++) {
      var v;
      try { v = JSON.parse(String(scripts[i].textContent || "").trim()); } catch (e) { continue; }
      var p = ldNodes(v).filter(function (o) { return o && typeof o === "object" && ldIsProduct(o); })[0];
      if (!p) continue;
      var price = ldPrice(p.offers);
      var variants = Array.isArray(p.hasVariant) ? p.hasVariant.filter(function (x) { return x && typeof x === "object"; }) : null;
      if (variants) {
        var path = String(url || "").replace(/^[a-z]+:\/\/[^/]*\//i, "");
        var here = variants.filter(function (x) {
          var u = String(x.url || "");
          return u && (url === u || endsWith(String(url || ""), u) || endsWith(u, path));
        })[0];
        var hp = here ? ldPrice(here.offers) : null;
        if (hp) price = hp;
        else if (!price) for (var j = 0; j < variants.length && !price; j++) price = ldPrice(variants[j].offers);
      }
      var image = ldImage(p.image) || (variants && variants[0] ? ldImage(variants[0].image) : "");
      return { name: String(p.name || "").trim(), image: image, price: price ? price.price : null, currency: price ? price.currency : "" };
    }
    return null;
  }

  var SHOWN_PRICE = /([€$£])\s?([0-9][0-9.,]*)|([0-9][0-9.,]*)\s?(?:€|EUR|£|GBP|USD)/;
  var OLD_PRICE = /old|was|strike|compare|before|previous|original|crossed|rrp|msrp|list-price|from-price/;
  var NOT_A_PRICE = /verzend|bezorg|shipping|delivery|versand|livraison|vanaf|from|per maand|\/mnd|p\/m/;
  // Another product on the page (owner, 8 November 2026: Zalando's "Gesponsord" tile above the product gave a wish
  // its €159,95): an element inside a sponsored, "you might also like" or related block, or inside a link to another
  // page. Its price is never this product's price. Same rules as foreign() in Fetch.kt.
  var FOREIGN = /sponsor|gesponsord|recommend|aanbevol|related|similar|you-may|you-might|also-like|ook-leuk|upsell|cross-sell|crosssell|reco-|reco_|carousel/;
  function pathOf(u, base) {
    try { var x = new URL(u, base || "https://x.invalid/"); return x.host + x.pathname.replace(/\/+$/, ""); } catch (e) { return ""; }
  }
  function foreign(e, base) {
    var here = base ? pathOf(base, base) : "";
    for (var a = e; a && a.getAttribute; a = a.parentElement) {
      var tag = String(a.tagName || "").toLowerCase();
      if (tag === "body" || tag === "html") break;
      var marks = [a.getAttribute("class"), a.id, a.getAttribute("aria-label"), a.getAttribute("data-testid")]
        .map(function (x) { return String(x || ""); }).join(" ").toLowerCase();
      if (FOREIGN.test(marks)) return true;
      if (tag === "a" && here) {
        var h = String(a.getAttribute("href") || "").trim();
        if (h && h.charAt(0) !== "#" && !/^javascript:/i.test(h) && pathOf(h, base) !== here) return true;
      }
    }
    return false;
  }

  function shownPrice(d, host, base) {
    var els = d.querySelectorAll('[class*="price" i], [id*="price" i]');
    for (var i = 0; i < els.length && i < 400; i++) {
      var e = els[i];
      var tag = String(e.tagName || "").toLowerCase();
      if (["del", "s", "strike", "script", "style", "meta"].indexOf(tag) >= 0) continue;
      var cls = (String(e.getAttribute("class") || "") + " " + String(e.id || "")).toLowerCase();
      if (OLD_PRICE.test(cls)) continue;
      if (foreign(e, base)) continue;
      var bad = false;
      for (var a = e.parentElement; a && !bad; a = a.parentElement) {
        var at = String(a.tagName || "").toLowerCase();
        if (at === "del" || at === "s" || at === "strike" || OLD_PRICE.test(String(a.getAttribute("class") || "").toLowerCase())) bad = true;
      }
      if (bad) continue;
      var own = Array.prototype.filter.call(e.childNodes, function (x) { return x.nodeType === 3; })
        .map(function (x) { return x.textContent; }).join(" ").replace(/\s+/g, " ").trim();
      var t = own || String(e.textContent || "").replace(/\s+/g, " ").trim();
      if (t.length > 40 || NOT_A_PRICE.test(t.toLowerCase())) continue;
      var mm = SHOWN_PRICE.exec(t);
      if (!mm) continue;
      var amount = toNum(mm[2] || mm[3]);
      if (amount === null || amount <= 0) continue;
      return { price: amount, currency: currencyOf(t, host) };
    }
    return null;
  }

  function parsePage(d, host, status, base) {
    var isAmazon = String(host).indexOf("amazon.") >= 0;
    var pageTitle = String(d.title || "").replace(/\s+/g, " ").trim();
    var blocked = status === 403 || status === 429 || status === 503 ||
      !!d.querySelector("form[action*=validateCaptcha]") || looksBlocked(pageTitle);
    if (blocked) return { title: "", desc: "", image: "", price: null, currency: "USD", blocked: true };

    function m() {
      for (var i = 0; i < arguments.length; i++) {
        var k = arguments[i];
        var es = d.querySelectorAll('meta[property="' + k + '"], meta[name="' + k + '"], meta[itemprop="' + k + '"]');
        for (var j = 0; j < es.length; j++) {
          // Not the price of another product's tile (sponsored, related).
          if (foreign(es[j], base)) continue;
          var v = String(es[j].getAttribute("content") || "").trim();
          if (v) return v;
        }
      }
      return "";
    }

    var title = m("og:title", "twitter:title") || pageTitle;
    if (isAmazon) {
      title = title.replace(/^Amazon\.[a-z.]+\s*:\s*/, "").replace(/\s+:\s+[^:]+$/, "");
    }
    var image = "";
    var im = d.querySelector('meta[property="og:image"], meta[name="twitter:image"]');
    if (im) image = absUrl(String(im.getAttribute("content") || ""), base);
    if (!image && isAmazon) {
      var li = d.querySelector("#landingImage, #imgBlkFront");
      if (li) image = li.getAttribute("data-old-hires") || absUrl(String(li.getAttribute("src") || ""), base);
    }

    var ld = ldProduct(d, base || "");
    // An error page (404 "not found", 410, 500…) is not the product (2.178): the prices on it belong to the shop's
    // suggestions, so none is read. The name and picture are still read, as before. Same as Fetch.kt.
    var gone = status >= 400;
    var price = gone ? null : toNum(m("product:price:amount", "og:price:amount", "price"));
    var cur = m("product:price:currency", "og:price:currency", "priceCurrency");
    // Did the page name the currency itself (its product data, or a symbol next to the price)? See Meta.currencySure.
    var sure = !!cur;
    if (price === null && isAmazon && !gone) {
      for (var i = 0; i < AMAZON_PRICE.length; i++) {
        var pe = d.querySelector(AMAZON_PRICE[i]);
        var t = pe ? pe.textContent : "";
        var n = toNum(t);
        if (n !== null && n > 0) { price = n; cur = currencyOf(t, host); sure = namesCurrency(t); break; }
      }
    }
    // The product's own data (2.145): its own price, not the first "price" anywhere in the page.
    if (price === null && ld && ld.price !== null && !gone) {
      price = ld.price;
      if (!cur) { cur = ld.currency; sure = !!cur; }
    }
    if (price === null && !gone) {
      var ips = d.querySelectorAll("[itemprop=price]");
      for (var q = 0; q < ips.length && price === null; q++) {
        if (!foreign(ips[q], base)) price = toNum(ips[q].getAttribute("content") || ips[q].textContent);
      }
    }
    if (price === null && !ld && !gone) {
      // Product data that isn't valid JSON: the first "price" in it, as before 2.145.
      var raw = Array.prototype.map.call(d.querySelectorAll('script[type="application/ld+json"]'),
        function (s) { return s.textContent; }).join(" ");
      var pm = /"price"\s*:\s*"?([0-9][0-9.,]*)/.exec(raw);
      price = pm ? toNum(pm[1]) : null;
      if (price !== null && !cur) {
        var cm = /"priceCurrency"\s*:\s*"([A-Z]{3})"/.exec(raw);
        cur = cm ? cm[1] : "";
        sure = !!cur;
      }
    }
    if (price === null && !gone) {
      // The price the page shows (2.145; the owner chose this last try knowing it can now and then pick a wrong one).
      var sp = shownPrice(d, host, base);
      if (sp) { price = sp.price; if (!cur) { cur = sp.currency; sure = true; } }
    }
    if (price === null && d.body && !gone) {
      // Last resort for sites that only print the price as text.
      var copy = d.body.cloneNode(true);
      Array.prototype.forEach.call(copy.querySelectorAll("script, style, noscript, template"), function (x) { x.remove(); });
      var text = String(copy.textContent || "").replace(/\s+/g, " ");
      var hit = PRICE_WORD.exec(text);
      if (hit) {
        price = toNum(hit[3]);
        if (price !== null) { cur = currencyOf(hit[2], host); sure = true; }
      } else if (String(host).indexOf("funda.") >= 0) {
        var e = EURO_THOUSANDS.exec(text);
        if (e) {
          price = toNum(e[1]);
          if (price !== null) { cur = "EUR"; sure = true; }
        }
      }
    }
    if (!title.trim() && ld) title = ld.name;
    if (!image && ld && ld.image) image = absUrl(ld.image, base);
    var product = !!ld || price !== null || m("og:type").toLowerCase().indexOf("product") >= 0 ||
      !!d.querySelector('[itemtype*="schema.org/Product"]');
    if (!title.trim()) return null;
    return {
      title: cleanTitle(title, host).slice(0, 120),
      desc: m("og:description", "twitter:description", "description").slice(0, 200),
      image: image, price: price, currency: cur || currencyOf("", host), blocked: false, product: product,
      currencySure: sure && price !== null
    };
  }

  // The answer of the helper function ({status, url, html}) -> the same kind of result as parsePage.
  function readHelperAnswer(ans) {
    if (!ans || typeof ans.html !== "string") return null;
    var doc = new root.DOMParser().parseFromString(ans.html, "text/html");
    var host = hostOfUrl(ans.url);
    return parsePage(doc, host, Number(ans.status) || 200, ans.url);
  }

  var api = {
    cleanUrl: cleanUrl, toNum: toNum, currencyOf: currencyOf, localCurrency: localCurrency, looksBlocked: looksBlocked,
    parsePage: parsePage, readHelperAnswer: readHelperAnswer, hostOfUrl: hostOfUrl, cleanTitle: cleanTitle
  };
  root.WLParse = api;
  if (typeof module !== "undefined" && module.exports) module.exports = api;
})(typeof window !== "undefined" ? window : this);
