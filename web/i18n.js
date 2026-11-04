/*
 * Texts and light/dark on the web pages. Since 4 November 2026 (owner) only UK English: Dutch was removed to keep
 * every change small. In the scripts every text still goes through T("English text", values…) with {0}, {1} for the
 * values, so a translation later only needs a dictionary here. The wishes themselves are never translated.
 */
(function () {
  "use strict";
  // A link from the invitation can carry the light/dark choice made there: "t" (light / dark). It is remembered in
  // this browser, as if picked here. (An old link's "l=nl" is simply ignored.)
  (function fromLink() {
    var t = /[?&]t=(light|dark)\b/.exec(location.search);
    try { if (t) localStorage.setItem("wl_theme", t[1]); } catch (e) { /* private mode: the page still follows the link below */ }
    if (t) document.documentElement.setAttribute("data-theme", t[1]);
  })();
  window.WL_LANG = "en";
  window.T = function (en) {
    var a = arguments;
    return en.replace(/\{(\d)\}/g, function (m, i) { var v = a[+i + 1]; return v === undefined ? m : String(v); });
  };
  // Kept for a later translation of the fixed texts in the HTML; nothing to do in English.
  window.translatePage = function () { };
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
  // The small "System · Light · Dark" choice at the bottom of the page.
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
  // "&t=dark": the light/dark choice made in this browser, for a link to another page of the site. Empty when nothing was picked.
  window.prefsQuery = function () {
    try {
      var t = localStorage.getItem("wl_theme");
      if (t === "light" || t === "dark") return "&t=" + t;
    } catch (e) { /* nothing remembered */ }
    return "";
  };
  document.documentElement.lang = "en-GB";
})();
