// The i18n.js of October 2026 before prefsQuery existed: t6 checks the invitation still works with this old copy.
/*
 * The language of the web pages: "en" (UK English) or "nl" (Dutch). It follows the browser, unless the visitor
 * picked one with the switch at the bottom of the page (remembered in this browser only, "wl_lang").
 * In the scripts every text goes through T("English text", values…) with {0}, {1} for the values. Fixed texts in the
 * HTML (and their aria-label, title and placeholder) are translated when the page loads, by their English wording.
 * A new text: write it in English and add its Dutch line to NL below. The wishes themselves are never translated.
 * The app has the same texts in NlStrings.kt.
 */
(function () {
  "use strict";
  var NL = window.WL_NL || {};
  function pick() {
    try { var v = localStorage.getItem("wl_lang"); if (v === "nl" || v === "en") return v; } catch (e) { /* private mode */ }
    return /^nl\b/i.test(navigator.language || "") ? "nl" : "en";
  }
  var L = pick();
  window.WL_LANG = L;
  window.T = function (en) {
    var s = L === "nl" && Object.prototype.hasOwnProperty.call(NL, en) ? NL[en] : en;
    var a = arguments;
    return s.replace(/\{(\d)\}/g, function (m, i) { var v = a[+i + 1]; return v === undefined ? m : String(v); });
  };
  window.setLang = function (l) {
    try { localStorage.setItem("wl_lang", l); } catch (e) { /* ignore */ }
    location.reload();
  };
  // Fixed texts in the page: text, aria-label, title and placeholder, looked up by their English wording.
  window.translatePage = function (root) {
    if (L !== "nl") return;
    var all = (root || document.body).querySelectorAll("*");
    for (var i = 0; i < all.length; i++) {
      var e = all[i];
      if (e.tagName === "SCRIPT" || e.tagName === "STYLE") continue;
      for (var c = e.firstChild; c; c = c.nextSibling) {
        if (c.nodeType !== 3) continue;
        var t = c.nodeValue.trim();
        if (t && Object.prototype.hasOwnProperty.call(NL, t)) c.nodeValue = c.nodeValue.replace(t, NL[t]);
      }
      ["aria-label", "title", "placeholder"].forEach(function (k) {
        var v = e.getAttribute(k);
        if (v && Object.prototype.hasOwnProperty.call(NL, v)) e.setAttribute(k, NL[v]);
      });
    }
  };
  // Light or dark for this browser: "system" (the device decides), "light" or "dark".
  function themeNow() {
    try { var v = localStorage.getItem("wl_theme"); if (v === "light" || v === "dark") return v; } catch (e) { /* ignore */ }
    return "system";
  }
  window.setTheme = function (t) {
    try { if (t === "system") localStorage.removeItem("wl_theme"); else localStorage.setItem("wl_theme", t); } catch (e) { /* ignore */ }
    if (t === "system") document.documentElement.removeAttribute("data-theme");
    else document.documentElement.setAttribute("data-theme", t);
  };
  // The small "System · Light · Dark" choice, under the language switch.
  window.themeSwitch = function () {
    var p = document.createElement("p");
    p.className = "langs themes";
    p.setAttribute("role", "group");
    p.setAttribute("aria-label", window.T("Theme"));
    var btns = [];
    function mark() {
      var now = themeNow();
      btns.forEach(function (b) {
        var on = b.getAttribute("data-t") === now;
        b.className = "link" + (on ? " on" : "");
        if (on) b.setAttribute("aria-pressed", "true"); else b.setAttribute("aria-pressed", "false");
      });
    }
    [["system", "System"], ["light", "Light"], ["dark", "Dark"]].forEach(function (x, i) {
      if (i) p.appendChild(document.createTextNode(" · "));
      var b = document.createElement("button");
      b.type = "button";
      b.setAttribute("data-t", x[0]);
      b.textContent = window.T(x[1]);
      b.addEventListener("click", function () { window.setTheme(x[0]); mark(); });
      btns.push(b);
      p.appendChild(b);
    });
    mark();
    return p;
  };
  // The small "English · Nederlands" switch.
  window.langSwitch = function () {
    var p = document.createElement("p");
    p.className = "langs";
    [["en", "English"], ["nl", "Nederlands"]].forEach(function (x, i) {
      if (i) p.appendChild(document.createTextNode(" · "));
      var b = document.createElement("button");
      b.type = "button";
      b.className = "link" + (L === x[0] ? " on" : "");
      b.textContent = x[1];
      b.setAttribute("lang", x[0]);
      if (L === x[0]) b.setAttribute("aria-current", "true");
      b.addEventListener("click", function () { if (L !== x[0]) window.setLang(x[0]); });
      p.appendChild(b);
    });
    return p;
  };
  document.documentElement.lang = L;
  if (document.body) window.translatePage(document.body);
})();
