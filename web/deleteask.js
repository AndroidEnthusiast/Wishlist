// "Delete wish?" in the browser editor (owner, 27 November 2026, chosen from a preview; the same as the app's
// DeleteUi.kt): the wish's photo centred on top, the title, its name, a tickbox "Turn on one-tap delete" that only
// counts with Delete, a red Delete and Cancel. Wish data comes from other people: built as elements with textContent,
// the picture only as an http(s) link (safeUrl). Used by edit.js: WLDeleteAsk(helpers, wish, onDelete(tickboxTicked)).
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
        var row = el("label", "deltick");
        tick = el("input");
        tick.type = "checkbox";
        row.appendChild(tick);
        row.appendChild(el("span", null, T("Turn on one-tap delete")));
        b.appendChild(row);
      },
      focus: false,
      actions: [
        { label: T("Delete"), cls: "danger", onClick: function () { onDelete(tick.checked); } },
        { label: T("Cancel"), cls: "plain" }
      ]
    });
  };
})();
