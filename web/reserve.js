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
    free: function (w, c) {
      var q = qty(w);
      return c ? Math.max(0, q - (c.n - c.my)) : q;
    },
    // "2×" and "Exactly this" / "Similar is fine", as one short line, or "".
    tags: function (w) {
      var t = [];
      if (qty(w) > 1) t.push(qty(w) + "×");
      if (w.exact === "exact") t.push(T("Exactly this"));
      else if (w.exact === "similar") t.push(T("Similar is fine"));
      return t.join(" · ");
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
