// "Delete wish?" in the browser editor (owner, 27 November 2026, chosen from a preview; the same as the app's
// DeleteUi.kt): the wish's photo centred on top, the title, its name, a tickbox "Turn on one-tap delete" that only
// counts with Delete, a red Delete and Cancel. Wish data comes from other people: built as elements with textContent,
// the picture only as an http(s) link (safeUrl). Used by edit.js: WLDeleteAsk(helpers, wish, onDelete(tickboxTicked)).
// The group and gift exchange pages (no Settings there, so no tickbox; owner, 27 November 2026) use
// WLDeleteAsk.page(T, wish, onDelete) and WLDeleteAsk.undo(T, message, onUndo).
(function () {
  "use strict";
  window.WLDeleteAsk = function (h, w, onDelete) {
    var el = h.el, T = h.T, tick;
    h.dialog({
      title: T("Delete wish?"),
      cls: "delq",
      top: function (t) {
        var pic = el("div", "delpic");
        var img = h.safeUrl(w.image);
        if (img) {
          var im = el("img");
          im.src = img; im.alt = ""; im.referrerPolicy = "no-referrer";
          im.addEventListener("error", function () { im.remove(); pic.appendChild(el("span", null, "🎁")); });
          pic.appendChild(im);
        } else pic.appendChild(el("span", null, "🎁"));
        t.appendChild(pic);
      },
      body: function (b) {
        b.appendChild(el("p", "delname", w.title || T("Unnamed wish")));
        if (h.noTick) return;
        var row = el("label", "deltick");
        tick = el("input");
        tick.type = "checkbox";
        row.appendChild(tick);
        row.appendChild(el("span", null, T("Turn on one-tap delete")));
        b.appendChild(row);
      },
      focus: false,
      actions: [
        { label: T("Delete"), cls: "danger", onClick: function () { onDelete(!!(tick && tick.checked)); } },
        { label: T("Cancel"), cls: "plain" }
      ]
    });
  };

  function el(tag, cls, text) {
    var e = document.createElement(tag);
    if (cls) e.className = cls;
    if (text !== undefined && text !== null) e.textContent = text;
    return e;
  }
  function safeUrl(u) {
    try { var x = new URL(u); return (x.protocol === "http:" || x.protocol === "https:") ? x.href : ""; } catch (e) { return ""; }
  }

  // A pop-up of its own for pages without edit.js's dialog(): the same classes (edit.css), closed by Cancel, a tap
  // outside or Escape.
  function dialog(spec) {
    var back = el("div", "backdrop dlg-back"), d = el("div", "dlg" + (spec.cls ? " " + spec.cls : ""));
    d.setAttribute("role", "dialog"); d.setAttribute("aria-modal", "true");
    if (spec.top) { var top = el("div", "dtop"); spec.top(top); d.appendChild(top); }
    var h = el("h2", null, spec.title); h.id = "delq-title"; d.setAttribute("aria-labelledby", h.id); d.appendChild(h);
    var body = el("div", "dbody"); d.appendChild(body);
    var foot = el("div", "dfoot"); d.appendChild(foot);
    function close() { back.remove(); document.body.style.overflow = ""; document.removeEventListener("keydown", esc); }
    function esc(e) { if (e.key === "Escape") close(); }
    if (spec.body) spec.body(body);
    spec.actions.forEach(function (a) {
      var b = el("button", "act" + (a.cls ? " " + a.cls : ""), a.label);
      b.type = "button";
      b.addEventListener("click", function () { close(); if (a.onClick) a.onClick(); });
      foot.appendChild(b);
    });
    back.addEventListener("click", function (e) { if (e.target === back) close(); });
    document.addEventListener("keydown", esc);
    back.appendChild(d); document.body.appendChild(back); document.body.style.overflow = "hidden";
  }

  window.WLDeleteAsk.page = function (T, w, onDelete) {
    window.WLDeleteAsk({ dialog: dialog, el: el, safeUrl: safeUrl, T: T, noTick: true }, w, function () { onDelete(); });
  };

  // "Wish deleted" with Undo for about 5 seconds, in the page's #snack (the same look as the editor's).
  var timer = 0;
  window.WLDeleteAsk.undo = function (T, msg, onUndo) {
    var s = document.getElementById("snack");
    if (!s) return;
    s.textContent = "";
    s.appendChild(el("span", "stext", msg));
    var b = el("button", "sbtn", T("Undo"));
    b.type = "button";
    b.addEventListener("click", function () { s.hidden = true; clearTimeout(timer); onUndo(); });
    s.appendChild(b);
    s.hidden = false;
    clearTimeout(timer);
    timer = setTimeout(function () { s.hidden = true; }, 5000);
  };
})();
