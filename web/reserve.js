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
  // "Unreserve?" as a pop-up like the others (edit.css .wlt-box), with the padlock opening as it appears (owner,
  // 23 November 2026; the same as UnreserveDialog in the app). Built with textContent only.
  var NS = "http://www.w3.org/2000/svg";
  // The golden padlock (owner, 24 November 2026: "like the lock emoji", our own drawing; the same as Padlock.kt in the
  // app): a gold body with a shine and a rim, a silver shackle drawn behind it (its legs vanish inside), a keyhole.
  // "grey" for someone else's reservation. The gradients are defined once per page.
  function el(name, attrs) {
    var e = document.createElementNS(NS, name);
    for (var k in attrs) e.setAttribute(k, attrs[k]);
    return e;
  }
  function defs() {
    if (document.getElementById("wl-gold")) return;
    var svg = el("svg", { width: "0", height: "0", "aria-hidden": "true" });
    svg.style.position = "absolute";
    var d = el("defs", {});
    [["wl-gold", "#FFE07A", "#FFC21A", "#E69A00", true], ["wl-grey", "#E3E6EA", "#C3C8CE", "#9EA4AB", true],
      ["wl-silver", "#F4F6F8", "#B9C0C8", "#8D96A0", false]].forEach(function (g) {
      var lg = el("linearGradient", g[4] ? { id: g[0], x1: "0", y1: "0", x2: "0", y2: "1" } : { id: g[0], x1: "0", y1: "0", x2: "1", y2: "0" });
      [["0", g[1]], ["0.5", g[2]], ["1", g[3]]].forEach(function (st) { lg.appendChild(el("stop", { offset: st[0], "stop-color": st[1] })); });
      d.appendChild(lg);
    });
    svg.appendChild(d);
    document.body.appendChild(svg);
  }
  function padlock(cls, grey) {
    defs();
    var svg = el("svg", { viewBox: "0 0 24 24", "class": cls, "aria-hidden": "true" });
    var g = el("g", { "class": "shackle" });
    // A long left leg that stays in the body, a short right one that pops out (owner, 25 November 2026).
    var arc = "M8,14.5V8a4,4 0 0,1 8,0v4";
    g.appendChild(el("path", { d: arc, fill: "none", stroke: "url(#wl-silver)", "stroke-width": "2.8", "stroke-linecap": "round" }));
    g.appendChild(el("path", { d: arc, fill: "none", stroke: "#fff", "stroke-opacity": ".45", "stroke-width": ".7", "stroke-linecap": "round" }));
    svg.appendChild(g);
    svg.appendChild(el("rect", { x: "4.5", y: "10.5", width: "15", height: "10.5", rx: "2.6", fill: grey ? "url(#wl-grey)" : "url(#wl-gold)", stroke: grey ? "#80868D" : "#B87900", "stroke-width": ".8" }));
    svg.appendChild(el("rect", { x: "6.2", y: "11.7", width: "11.6", height: "2", rx: "1", fill: "#fff", "fill-opacity": ".38" }));
    var hole = grey ? "#4F555C" : "#6B4200";
    svg.appendChild(el("circle", { cx: "12", cy: "15.4", r: "1.5", fill: hole }));
    svg.appendChild(el("rect", { x: "11.35", y: "15.4", width: "1.3", height: "2.9", rx: ".5", fill: hole }));
    return svg;
  }
  // Only http(s) pictures (a wish comes from another person).
  function webOnly(u) {
    try { var x = new URL(String(u || "")); return x.protocol === "http:" || x.protocol === "https:" ? x.href : ""; } catch (e) { return ""; }
  }
  // Wishes reserved a moment ago on this page: their "Reserved by you" padlock swings shut once.
  var justLocked = {};
  function askUnreserve(w, yes) {
    var back = document.createElement("div"); back.className = "wlt-back";
    var box = document.createElement("div"); box.className = "wlt-box resdlg";
    box.setAttribute("role", "dialog"); box.setAttribute("aria-modal", "true");
    var svg = padlock("unlock", false);
    box.appendChild(svg);
    var h = document.createElement("h2"); h.className = "wlt-h"; h.textContent = T("Unreserve?"); box.appendChild(h);
    // The gift's photo and name, then one short line (owner, 25 November 2026: a picture instead of so much text).
    var gift = document.createElement("div"); gift.className = "resgift";
    var pic = document.createElement("span"); pic.className = "resgift-pic";
    var src = webOnly(w.image);
    if (src) { var im = document.createElement("img"); im.src = src; im.alt = ""; im.referrerPolicy = "no-referrer"; pic.appendChild(im); }
    else pic.textContent = "🎁";
    gift.appendChild(pic);
    var nm = document.createElement("span"); nm.className = "resgift-name";
    nm.textContent = String(w.title || "").trim() || T("This gift");
    gift.appendChild(nm);
    box.appendChild(gift);
    var p = document.createElement("p"); p.className = "wlt-p";
    p.textContent = T("Someone else can reserve it again.");
    box.appendChild(p);
    function close() { back.remove(); document.removeEventListener("keydown", onKey); }
    function onKey(e) { if (e.key === "Escape") close(); }
    var row = document.createElement("div"); row.className = "wlt-row";
    var y = document.createElement("button"); y.type = "button"; y.className = "act"; y.textContent = T("Yes, unreserve");
    y.addEventListener("click", function () { close(); yes(); });
    var n = document.createElement("button"); n.type = "button"; n.className = "act plain"; n.textContent = T("No, keep it");
    n.addEventListener("click", close);
    row.appendChild(y); row.appendChild(n); box.appendChild(row);
    back.appendChild(box);
    back.addEventListener("click", function (e) { if (e.target === back) close(); });
    document.addEventListener("keydown", onKey);
    document.body.appendChild(back);
    y.focus();
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
    // The one reservation button (owner, 23 November 2026), the same as ReserveButton in the app. o: busy,
    // set(true/false), ask() for someone else's reservation ("Is it still being bought?", together.js) or null.
    // "Reserved by you": one tap opens the "Unreserve?" pop-up. The padlock is the golden one (grey for others).
    button: function (w, r, c, o) {
      var st = this.state(w, r, c);
      var box = document.createElement("span");
      box.className = "resbox";
      if (!st) return box;
      function btn(cls, text, lockCls, fn) {
        var b = document.createElement("button");
        b.type = "button"; b.className = "act small " + cls;
        if (lockCls) b.appendChild(padlock(lockCls, lockCls.indexOf("grey") >= 0));
        b.appendChild(document.createTextNode(text));
        if (fn) b.addEventListener("click", fn); else b.setAttribute("aria-disabled", "true");
        return b;
      }
      if (st === "reserve") {
        var rb = btn("", T("Reserve"), "", function () { justLocked[w.id] = { tap: Date.now(), start: 0 }; o.set(true); });
        rb.disabled = !!o.busy;
        box.appendChild(rb);
      } else if (st === "mine") {
        var text = qty(w) > 1 && c && c.my > 0 ? T("You reserved {0}", c.my) : T("Reserved by you");
        // Just reserved here: the padlock starts open and big, swings shut, then settles (once). A card drawn again
        // while it plays (the server's answer) carries on where it was instead of starting over.
        var jl = justLocked[w.id], now = Date.now(), ago = -1;
        if (jl && now - jl.tap < 15000) { jl.start = jl.start || now; ago = now - jl.start; }
        if (ago < 0 || ago >= 900) delete justLocked[w.id];
        var playing = ago >= 0 && ago < 900;
        var mb = btn("outline", text, playing ? "lock closing" : "lock", function () { askUnreserve(w, function () { o.set(false); }); });
        if (playing && ago > 0) {
          var sv = mb.querySelector("svg.lock");
          sv.style.animationDelay = "-" + ago + "ms";
          sv.querySelector(".shackle").style.animationDelay = "-" + ago + "ms";
        }
        mb.disabled = !!o.busy;
        box.appendChild(mb);
      } else {
        var tb = btn("outline taken", T("Reserved"), "lock grey", o.ask || null);
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
