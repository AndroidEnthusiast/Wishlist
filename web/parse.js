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
    return "USD";
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

  /**
   * Reads a loaded page (a Document). host: the shop's host name (after redirects). status: the HTTP status the shop
   * answered. base: the page's address, for turning a relative picture address into a full one.
   * Returns {title, desc, image, price, currency, blocked} or null when there is no title at all.
   */
  function parsePage(d, host, status, base) {
    var isAmazon = String(host).indexOf("amazon.") >= 0;
    var pageTitle = String(d.title || "").replace(/\s+/g, " ").trim();
    var blocked = status === 403 || status === 429 || status === 503 ||
      !!d.querySelector("form[action*=validateCaptcha]") || looksBlocked(pageTitle);
    if (blocked) return { title: "", desc: "", image: "", price: null, currency: "USD", blocked: true };

    function m() {
      for (var i = 0; i < arguments.length; i++) {
        var k = arguments[i];
        var e = d.querySelector('meta[property="' + k + '"], meta[name="' + k + '"], meta[itemprop="' + k + '"]');
        var v = e ? String(e.getAttribute("content") || "").trim() : "";
        if (v) return v;
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

    var price = toNum(m("product:price:amount", "og:price:amount", "price"));
    var cur = m("product:price:currency", "og:price:currency", "priceCurrency");
    if (price === null && isAmazon) {
      for (var i = 0; i < AMAZON_PRICE.length; i++) {
        var pe = d.querySelector(AMAZON_PRICE[i]);
        var t = pe ? pe.textContent : "";
        var n = toNum(t);
        if (n !== null && n > 0) { price = n; cur = currencyOf(t, host); break; }
      }
    }
    if (price === null) {
      var ld = Array.prototype.map.call(d.querySelectorAll('script[type="application/ld+json"]'),
        function (s) { return s.textContent; }).join(" ");
      var pm = /"price"\s*:\s*"?([0-9][0-9.,]*)/.exec(ld);
      price = pm ? toNum(pm[1]) : null;
      if (!cur) {
        var cm = /"priceCurrency"\s*:\s*"([A-Z]{3})"/.exec(ld);
        cur = cm ? cm[1] : "";
      }
    }
    if (price === null) {
      var ip = d.querySelector("[itemprop=price]");
      if (ip) price = toNum(ip.getAttribute("content") || ip.textContent);
    }
    if (price === null && d.body) {
      // Last resort for sites that only print the price as text.
      var copy = d.body.cloneNode(true);
      Array.prototype.forEach.call(copy.querySelectorAll("script, style, noscript, template"), function (x) { x.remove(); });
      var text = String(copy.textContent || "").replace(/\s+/g, " ");
      var hit = PRICE_WORD.exec(text);
      if (hit) {
        price = toNum(hit[3]);
        if (price !== null) cur = currencyOf(hit[2], host);
      } else if (String(host).indexOf("funda.") >= 0) {
        var e = EURO_THOUSANDS.exec(text);
        if (e) {
          price = toNum(e[1]);
          if (price !== null) cur = "EUR";
        }
      }
    }
    if (!title.trim()) return null;
    return {
      title: title.slice(0, 120),
      desc: m("og:description", "twitter:description", "description").slice(0, 200),
      image: image, price: price, currency: cur || currencyOf("", host), blocked: false
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
    cleanUrl: cleanUrl, toNum: toNum, currencyOf: currencyOf, looksBlocked: looksBlocked,
    parsePage: parsePage, readHelperAnswer: readHelperAnswer, hostOfUrl: hostOfUrl
  };
  root.WLParse = api;
  if (typeof module !== "undefined" && module.exports) module.exports = api;
})(typeof window !== "undefined" ? window : this);
