// Quantity on the web pages (owner, 6 November 2026): a wish can be wanted more than once ("qty", 1 to 99) and every
// giver reserves how many they'll buy. The same rules as Quantity.kt in the app: a 2× wish that still has a free one
// and isn't yours counts as free (so it can be reserved); the label says "1 of 2 reserved", "You reserved 1" or
// "Reserved". Used by friends.js and edit.js. Builds elements with textContent only.
(function () {
  "use strict";
  function qty(w) {
    var q = Math.floor(Number(w && w.qty));
    return q > 1 && q < 100 ? q : 1;
  }
  window.WLRes = {
    qty: qty,
    // The rows of wl_get_reservations: res[id] = true (yours) / false (taken), cnt[id] = {my, n, full}.
    parse: function (rows) {
      var res = {}, cnt = {};
      (rows || []).forEach(function (x) {
        var mine = !!x.mine;
        var c = {
          my: typeof x.my === "number" ? x.my : (mine ? 1 : 0),
          n: typeof x.n === "number" ? x.n : 1,
          full: typeof x.full === "boolean" ? x.full : true
        };
        cnt[x.id] = c;
        if (mine) res[x.id] = true; else if (c.full) res[x.id] = false;
      });
      return { res: res, cnt: cnt };
    },
    label: function (w, r, c, mineLabel) {
      var q = qty(w);
      if (q > 1 && c) {
        if (c.my > 0) return T("You reserved {0}", c.my);
        return c.full ? T("Reserved") : T("{0} of {1} reserved", c.n, q);
      }
      return r === true ? mineLabel : r === false ? T("Reserved") : "";
    },
    // What the reservation button shows (owner, 23 November 2026; same as reserveState in Quantity.kt): "reserve",
    // "mine" ("Reserved by you" / "You reserved 2" with a lock), "taken" ("Reserved" with a lock, the card darkened)
    // or "" (owned: no button).
    state: function (w, r, c) {
      if (w.got) return "";
      if (qty(w) > 1 && c) return c.my > 0 ? "mine" : c.full ? "taken" : "reserve";
      return r === true ? "mine" : r === false ? "taken" : "reserve";
    },
    // The line about reservations: only "1 of 3 reserved" while some are still free (the rest is on the button).
    line: function (w, c) {
      var q = qty(w);
      return q > 1 && c && !c.my && !c.full && c.n > 0 ? T("{0} of {1} reserved", c.n, q) : "";
    },
    // The one reservation button (owner, 23 November 2026). o: busy, set(true/false), ask() for someone else's
    // reservation ("Is it still being bought?", together.js) or null. "Reserved by you" asks "Unreserve?" in the card
    // first. Built with textContent only.
    button: function (w, r, c, o) {
      var st = this.state(w, r, c);
      var box = document.createElement("span");
      box.className = "resbox";
      if (!st) return box;
      function lock() {
        var ns = "http://www.w3.org/2000/svg";
        var svg = document.createElementNS(ns, "svg");
        svg.setAttribute("viewBox", "0 0 24 24"); svg.setAttribute("aria-hidden", "true"); svg.setAttribute("class", "lock");
        var p = document.createElementNS(ns, "path");
        // Google's Material "lock" icon (Apache 2.0).
        p.setAttribute("d", "M18 8h-1V6c0-2.76-2.24-5-5-5S7 3.24 7 6v2H6c-1.1 0-2 .9-2 2v10c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V10c0-1.1-.9-2-2-2zm-6 9c-1.1 0-2-.9-2-2s.9-2 2-2 2 .9 2 2-.9 2-2 2zm3.1-9H8.9V6c0-1.71 1.39-3.1 3.1-3.1 1.71 0 3.1 1.39 3.1 3.1v2z");
        p.setAttribute("fill", "currentColor");
        svg.appendChild(p);
        return svg;
      }
      function btn(cls, text, withLock, fn) {
        var b = document.createElement("button");
        b.type = "button"; b.className = "act small " + cls;
        if (withLock) b.appendChild(lock());
        b.appendChild(document.createTextNode(text));
        if (fn) b.addEventListener("click", fn); else b.setAttribute("aria-disabled", "true");
        return b;
      }
      if (st === "reserve") {
        var rb = btn("", T("Reserve"), false, function () { o.set(true); });
        rb.disabled = !!o.busy;
        box.appendChild(rb);
      } else if (st === "mine") {
        var text = qty(w) > 1 && c && c.my > 0 ? T("You reserved {0}", c.my) : T("Reserved by you");
        var mb = btn("outline", text, true, function () {
          // "Unreserve?" inside the card, so a stray tap never frees it.
          box.textContent = "";
          box.classList.add("asking");
          box.appendChild(document.createTextNode(T("Unreserve?")));
          box.appendChild(btn("", T("Unreserve"), false, function () { o.set(false); }));
          box.appendChild(btn("outline", T("Keep it"), false, function () {
            box.classList.remove("asking"); box.textContent = ""; box.appendChild(mb);
          }));
        });
        mb.disabled = !!o.busy;
        box.appendChild(mb);
      } else {
        var tb = btn("outline taken", T("Reserved"), true, o.ask || null);
        if (o.ask) tb.title = T("Is it still being bought?");
        box.appendChild(tb);
      }
      return box;
    },
    free: function (w, c) {
      var q = qty(w);
      return c ? Math.max(0, q - (c.n - c.my)) : q;
    },
    // "2×", or "" (same as wishTags in the app). "Exactly this" / "Similar is fine" is no longer shown (owner,
    // 16 November 2026); the saved value stays in the data.
    tags: function (w) {
      return qty(w) > 1 ? qty(w) + "×" : "";
    },
    // "How many will you buy?" with 1..max and Cancel, inside the card.
    picker: function (max, onPick, onCancel) {
      var box = document.createElement("div");
      box.className = "howmany";
      var p = document.createElement("p");
      p.textContent = T("How many will you buy?");
      box.appendChild(p);
      var row = document.createElement("div");
      row.className = "hmrow";
      for (var k = 1; k <= Math.max(1, max); k++) {
        (function (n) {
          var b = document.createElement("button");
          b.type = "button"; b.className = "act small"; b.textContent = String(n);
          b.addEventListener("click", function () { onPick(n); });
          row.appendChild(b);
        })(k);
      }
      var c = document.createElement("button");
      c.type = "button"; c.className = "act outline small"; c.textContent = T("Cancel");
      c.addEventListener("click", onCancel);
      row.appendChild(c);
      box.appendChild(row);
      return box;
    }
  };
})();
