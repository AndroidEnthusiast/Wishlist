// Doing things together in the browser (7 November 2026; supabase-together.sql), for the friends' page, the group page
// and the browser editor. The same rules as the app (Reactions.kt, Asks.kt); change both together.
//  * Reactions: one emoji per person per wish, shown as "😊 2" (yours highlighted). "+ react" opens a picker with a
//    search bar, "Recently used" and the groups (emoji.js, loaded only when it opens). Tap yours to take it away; a long
//    press (or right click) shows who. A browser visitor gives a first name once (remembered in this browser).
//  * "Is it still being bought?": tapping someone else's "Reserved" label asks the people who reserved it. Questions
//    about your own reservations (made in this browser) show at the top with the two answers; what happened to your
//    questions shows there once ("… is free again").
// Everything from other people goes on the page as text (textContent), never as HTML.
(function () {
  "use strict";
  var cfg = null;
  var reacts = {};   // list code -> [{i, e, n, m}]
  var asks = {};     // list code -> questions waiting for this browser
  var news = {};     // list code -> [{kind, data}] still on screen
  var missing = false;
  var emojiLoading = null;

  function el(tag, cls, text) {
    var e = document.createElement(tag);
    if (cls) e.className = cls;
    if (text !== undefined && text !== null) e.textContent = String(text);
    return e;
  }
  function T_(s) { return window.T ? window.T.apply(null, arguments) : s; }
  function title(t) { t = String(t || "").trim(); return t || T_("Unnamed wish"); }
  function body(code) {
    var a = cfg.auth ? cfg.auth() : null;
    return { p_code: code, p_id: a ? a.p_id : "", p_token: a ? a.p_token : "", p_device: cfg.device() };
  }
  // Supabase doesn't know the function yet (supabase-together.sql not run): 404.
  function isMissing(e, fn) { var m = String(e && e.message || ""); return m.indexOf(fn) >= 0 || m.indexOf("404") >= 0; }
  function redraw() { if (cfg && cfg.redraw) cfg.redraw(); }
  function said(r) {
    switch (r) {
      case "ok": return T_("Asked. Come back later: they have 2 days to answer.");
      case "asked": return T_("Someone already asked. It becomes free if they don’t answer.");
      case "recent": return T_("It’s still being bought. You can ask again in a week.");
      case "free": return T_("It’s free now. You can reserve it.");
      case "mine": return T_("You reserved this one yourself.");
      case "passed": return T_("The occasion has passed.");
      default: return T_("Couldn’t ask. Check your connection.");
    }
  }

  // ---------- a small dialog ----------
  function dialog(head, build) {
    var back = el("div", "wlt-back");
    var box = el("div", "wlt-box");
    box.setAttribute("role", "dialog");
    box.setAttribute("aria-modal", "true");
    if (head) box.appendChild(el("h2", "wlt-h", head));
    back.appendChild(box);
    function close() { back.remove(); document.removeEventListener("keydown", onKey); }
    function onKey(e) { if (e.key === "Escape") close(); }
    back.addEventListener("click", function (e) { if (e.target === back) close(); });
    document.addEventListener("keydown", onKey);
    build(box, close);
    document.body.appendChild(back);
    return close;
  }
  function button(text, cls, onClick) {
    var b = el("button", "act small" + (cls ? " " + cls : ""), text);
    b.type = "button";
    b.addEventListener("click", onClick);
    return b;
  }

  // A first name, asked once (owner: needed for reactions in the browser).
  function withName(then) {
    var n = cfg.name ? String(cfg.name() || "").trim() : "";
    if (n) { then(n); return; }
    dialog(T_("Your first name"), function (box, close) {
      box.appendChild(el("p", "wlt-p", T_("Shown with your reactions, so they know who it is.")));
      var f = el("form", "wlt-form");
      var i = el("input"); i.type = "text"; i.maxLength = 30; i.autocomplete = "given-name"; i.placeholder = T_("First name");
      f.appendChild(i);
      var row = el("div", "wlt-row");
      row.appendChild(button(T_("Cancel"), "outline", close));
      var ok = el("button", "act small", T_("Save")); ok.type = "submit";
      row.appendChild(ok);
      f.appendChild(row);
      f.addEventListener("submit", function (e) {
        e.preventDefault();
        var v = i.value.trim().slice(0, 30);
        if (!v) { i.focus(); return; }
        if (cfg.setName) cfg.setName(v);
        close();
        then(v);
      });
      box.appendChild(f);
      setTimeout(function () { i.focus(); }, 30);
    });
  }

  // ---------- reactions ----------
  function recent() {
    try { return JSON.parse(localStorage.getItem("wl_rx_recent") || "[]").filter(function (x) { return typeof x === "string"; }); } catch (e) { return []; }
  }
  function addRecent(e) {
    try { localStorage.setItem("wl_rx_recent", JSON.stringify([e].concat(recent().filter(function (x) { return x !== e; })).slice(0, 24))); } catch (x) { }
  }

  async function setReact(code, item, emoji) {
    var before = reacts[code] || [];
    var rest = before.filter(function (r) { return !(r.i === item && r.m); });
    var nm = cfg.name ? String(cfg.name() || "") : "";
    reacts[code] = emoji ? rest.concat([{ i: item, e: emoji, n: nm, m: true }]) : rest;
    if (emoji) addRecent(emoji);
    redraw();
    try {
      var b = body(code);
      b.p_item = item; b.p_name = nm; b.p_emoji = emoji;
      var ok = await cfg.rpc("wl_rx_set", b);
      if (ok !== true) throw new Error("refused");
    } catch (e) {
      reacts[code] = before;
      redraw();
      cfg.toast(isMissing(e, "wl_rx_set") ? T_("Reactions need an update in Supabase first.") : T_("Couldn’t react. Check your connection."));
    }
  }

  function loadEmoji() {
    if (window.WL_EMOJI) return Promise.resolve();
    if (emojiLoading) return emojiLoading;
    var src = "emoji.js";
    var me = document.querySelector('script[src*="together.js"]');
    var q = me ? (me.getAttribute("src").split("?")[1] || "") : "";
    emojiLoading = new Promise(function (ok, bad) {
      var s = document.createElement("script");
      s.src = src + (q ? "?" + q : "");
      s.onload = function () { ok(); };
      s.onerror = function () { emojiLoading = null; bad(new Error("emoji")); };
      document.head.appendChild(s);
    });
    return emojiLoading;
  }
  function emojiGroups() {
    var out = [], cur = null;
    String(window.WL_EMOJI || "").split("\n").forEach(function (l) {
      if (!l) return;
      if (l.charAt(0) === "=") { cur = { name: l.slice(1), list: [] }; out.push(cur); return; }
      var i = l.indexOf(" ");
      if (i > 0 && cur) cur.list.push({ c: l.slice(0, i), n: l.slice(i + 1) });
    });
    return out;
  }

  function picker(onPick) {
    dialog(T_("React"), function (box, close) {
      box.classList.add("wlt-picker");
      var search = el("input", "wlt-search"); search.type = "search"; search.placeholder = T_("Search emoji");
      search.setAttribute("aria-label", T_("Search emoji"));
      box.appendChild(search);
      var tabs = el("div", "wlt-tabs");
      box.appendChild(tabs);
      var grid = el("div", "wlt-emoji");
      box.appendChild(grid);
      var row = el("div", "wlt-row");
      row.appendChild(button(T_("Close"), "outline", close));
      box.appendChild(row);
      grid.appendChild(el("p", "wlt-p", T_("Loading…")));
      function cell(c, n) {
        var b = el("button", "wlt-e", c);
        b.type = "button"; b.title = n; b.setAttribute("aria-label", n);
        b.addEventListener("click", function () { close(); onPick(c); });
        return b;
      }
      loadEmoji().then(function () {
        var groups = emojiGroups();
        var heads = [];
        function all() {
          grid.textContent = "";
          var rc = recent();
          if (rc.length) {
            grid.appendChild(el("div", "wlt-gh", T_("Recently used")));
            rc.forEach(function (c) { grid.appendChild(cell(c, c)); });
          }
          heads = [];
          groups.forEach(function (g) {
            var h = el("div", "wlt-gh", T_(g.name));
            heads.push(h);
            grid.appendChild(h);
            g.list.forEach(function (e) { grid.appendChild(cell(e.c, e.n)); });
          });
        }
        groups.forEach(function (g, i) {
          var t = el("button", "wlt-tab");
          t.appendChild(groupIcon(i));
          t.type = "button"; t.title = T_(g.name); t.setAttribute("aria-label", T_(g.name));
          t.addEventListener("click", function () { search.value = ""; all(); if (heads[i]) grid.scrollTop = heads[i].offsetTop - grid.offsetTop; });
          tabs.appendChild(t);
        });
        all();
        search.addEventListener("input", function () {
          var q = search.value.trim().toLowerCase();
          if (!q) { tabs.hidden = false; all(); return; }
          tabs.hidden = true;
          grid.textContent = "";
          var words = q.split(/\s+/), n = 0;
          groups.forEach(function (g) {
            g.list.forEach(function (e) {
              if (n < 240 && words.every(function (w) { return e.n.indexOf(w) >= 0; })) { grid.appendChild(cell(e.c, e.n)); n++; }
            });
          });
          if (!n) grid.appendChild(el("p", "wlt-p", T_("No emoji found.")));
        });
      }).catch(function () {
        grid.textContent = "";
        grid.appendChild(el("p", "wlt-p", T_("Couldn’t load the emoji. Check your connection.")));
      });
      setTimeout(function () { search.focus(); }, 30);
    });
  }

  function who(list, e) {
    var names = list.filter(function (r) { return r.e === e; }).map(function (r) { return r.m ? T_("You") : (String(r.n || "").trim() || T_("Someone")); });
    dialog(e + " " + names.length, function (box, close) {
      box.appendChild(el("p", "wlt-p", names.join(", ")));
      var row = el("div", "wlt-row");
      row.appendChild(button(T_("Close"), "", close));
      box.appendChild(row);
    });
  }

  // Every reaction on a wish with who (owner, 7 November 2026: the "+N" circle shows them all).
  function everyone(list) {
    var by = {};
    list.forEach(function (r) { (by[r.e] = by[r.e] || []).push(r); });
    dialog(T_("Reactions"), function (box, close) {
      Object.keys(by).sort(function (a, b) { return by[b].length - by[a].length; }).forEach(function (e) {
        var p = el("p", "wlt-p");
        p.appendChild(el("span", "wlt-ce", e));
        p.appendChild(document.createTextNode(" " + by[e].length + " · " + by[e].map(function (r) {
          return r.m ? T_("You") : (String(r.n || "").trim() || T_("Someone"));
        }).join(", ")));
        box.appendChild(p);
      });
      var row = el("div", "wlt-row");
      row.appendChild(button(T_("Close"), "", close));
      box.appendChild(row);
    });
  }

  // The emoji groups' tabs: simple grey line icons (owner, 7 November 2026: emoji there looked tappable). Same
  // drawings as the app (GroupIcon in Reactions.kt), our own.
  var GROUP_ICONS = [
    "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18M8 14.5Q12 18.5 16 14.5M9 10h.01M15 10h.01",
    "M12 4.4a3.6 3.6 0 1 0 0 7.2a3.6 3.6 0 1 0 0-7.2M5 20Q5 13.5 12 13.5Q19 13.5 19 20",
    "M5 19Q4 6 19 5Q19 19 5 19M5 19L13 11",
    "M5 9L16 9L15 18Q14.8 19.5 13 19.5L8 19.5Q6.2 19.5 6 18L5 9M16 11Q20 11 19.5 13.5Q19 15.5 15.5 15.5M9 6.5L9 4.5M12 6.5L12 4.5",
    "M4 16L4 12.5L6.5 8L17.5 8L20 12.5L20 16L4 16M4 12.5L20 12.5M8 17h.01M16 17h.01",
    "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18M3 12L21 12M12 3Q7 12 12 21M12 3Q17 12 12 21",
    "M9 16Q9 14 7.5 12.5Q5 10 6 7Q7.5 3.5 12 3.5Q16.5 3.5 18 7Q19 10 16.5 12.5Q15 14 15 16L9 16M9.5 19L14.5 19M10.5 21.5L13.5 21.5",
    "M12 20Q3 14 3.5 9Q4 4.5 8 4.5Q10.5 4.5 12 7Q13.5 4.5 16 4.5Q20 4.5 20.5 9Q21 14 12 20",
    "M6 21L6 4M6 4.5Q9 3 12 4.5Q15 6 18 4.5L18 12.5Q15 14 12 12.5Q9 11 6 12.5"
  ];
  function groupIcon(i) {
    var NS = "http://www.w3.org/2000/svg";
    var s = document.createElementNS(NS, "svg");
    s.setAttribute("viewBox", "0 0 24 24"); s.setAttribute("width", "22"); s.setAttribute("height", "22");
    s.setAttribute("aria-hidden", "true");
    var p = document.createElementNS(NS, "path");
    p.setAttribute("d", GROUP_ICONS[Math.min(i, GROUP_ICONS.length - 1)]);
    p.setAttribute("fill", "none"); p.setAttribute("stroke", "currentColor"); p.setAttribute("stroke-width", "1.8");
    p.setAttribute("stroke-linecap", "round"); p.setAttribute("stroke-linejoin", "round");
    s.appendChild(p);
    return s;
  }

  // ---------- "Is it still being bought?" ----------
  function passed(occ) {
    if (!occ || occ.k !== "own" || !/^\d{4}-\d{2}-\d{2}$/.test(String(occ.d || ""))) return false;
    var t = new Date(), pad = function (n) { return (n < 10 ? "0" : "") + n; };
    return String(occ.d) < t.getFullYear() + "-" + pad(t.getMonth() + 1) + "-" + pad(t.getDate());
  }
  function left(until) {
    var h = Math.floor((Date.parse(until) - Date.now()) / 3600000);
    if (!isFinite(h)) return "";
    if (h >= 24) {
      var d = Math.floor(h / 24), r = h % 24;
      if (!r) return d === 1 ? T_("1 day left") : T_("{0} days left", d);
      return d === 1 ? T_("1 day {0} hours left", r) : T_("{0} days {1} hours left", d, r);
    }
    if (h >= 2) return T_("{0} hours left", h);
    if (h === 1) return T_("1 hour left");
    return T_("Less than an hour left");
  }

  async function answer(code, a, yes) {
    try {
      var ok = await cfg.rpc("wl_ask_answer", { p_device: cfg.device(), p_ask: a.ask, p_yes: !!yes });
      asks[code] = (asks[code] || []).filter(function (x) { return x.ask !== a.ask; });
      cfg.toast(ok !== true ? T_("This question was already answered.")
        : yes ? T_("Thanks! They’ll hear you’re still buying it.") : T_("Done. Someone else can reserve it now."));
    } catch (e) {
      cfg.toast(T_("Couldn’t answer. Check your connection."));
    }
    redraw();
    if (cfg.reload) cfg.reload();
  }

  window.WLT = {
    // c: {rpc(fn, body), device(), toast(msg), redraw(), reload(), name(), setName(n), auth() -> {p_id, p_token} | null}
    setup: function (c) { cfg = c; },

    // Loads the reactions on list [code] and, when [withAsks], this browser's questions and news there. True when
    // something changed (so the page only redraws then).
    load: async function (code, withAsks) {
      if (!cfg || !code) return false;
      var before = JSON.stringify([reacts[code], asks[code], news[code], missing]);
      try {
        reacts[code] = (await cfg.rpc("wl_rx_get", body(code))) || [];
        missing = false;
      } catch (e) {
        if (isMissing(e, "wl_rx_get")) missing = true;
      }
      if (withAsks) {
        try {
          asks[code] = (await cfg.rpc("wl_ask_mine", { p_device: cfg.device(), p_code: code })) || [];
          var n = (await cfg.rpc("wl_news_web", { p_device: cfg.device(), p_code: code })) || [];
          if (n.length) news[code] = (news[code] || []).concat(n);
        } catch (e) { /* older Supabase or offline: nothing to show */ }
      }
      return JSON.stringify([reacts[code], asks[code], news[code], missing]) !== before;
    },

    // Under a wish: the reactions and (when [canReact]) the "+ react" pill. Null when there is nothing to show.
    // [max]: at most this many reactions as chips, the rest in a "+N" circle that lists them all (owner, 7 November 2026).
    bar: function (code, w, canReact, max) {
      max = max || 3;
      if (!cfg || missing || !code) return null;
      var list = (reacts[code] || []).filter(function (r) { return r.i === w.id; });
      if (!list.length && !canReact) return null;
      var bar = el("div", "wlt-bar");
      var by = {};
      list.forEach(function (r) { (by[r.e] = by[r.e] || []).push(r); });
      var keys = Object.keys(by).sort(function (a, b) { return by[b].length - by[a].length; });
      keys.slice(0, max).forEach(function (e) {
        var mine = by[e].some(function (r) { return r.m; });
        var chip = el("button", "wlt-chip" + (mine ? " mine" : ""));
        chip.type = "button";
        chip.appendChild(el("span", "wlt-ce", e));
        chip.appendChild(el("span", "wlt-cn", String(by[e].length)));
        chip.setAttribute("aria-label", by[e].length + " " + e + (mine ? " · " + T_("You") : ""));
        var pressed = 0, longDone = false;
        chip.addEventListener("pointerdown", function () {
          longDone = false;
          pressed = setTimeout(function () { longDone = true; who(list, e); }, 550);
        });
        ["pointerup", "pointerleave", "pointercancel"].forEach(function (k) { chip.addEventListener(k, function () { clearTimeout(pressed); }); });
        chip.addEventListener("contextmenu", function (ev) { ev.preventDefault(); if (!longDone) who(list, e); longDone = true; });
        chip.addEventListener("click", function () {
          if (longDone) { longDone = false; return; }
          if (!canReact) { who(list, e); return; }
          if (mine) setReact(code, w.id, "");
          else withName(function () { setReact(code, w.id, e); });
        });
        bar.appendChild(chip);
      });
      if (keys.length > max) {
        var more = el("button", "wlt-chip wlt-more", "+" + (keys.length - max));
        more.type = "button";
        more.setAttribute("aria-label", T_("All reactions"));
        more.addEventListener("click", function () { everyone(list); });
        bar.appendChild(more);
      }
      if (canReact) {
        var add = el("button", "wlt-add", "+ " + T_("React"));
        add.type = "button";
        add.addEventListener("click", function () { withName(function () { picker(function (e) { setReact(code, w.id, e); }); }); });
        bar.appendChild(add);
      }
      return bar;
    },

    // True when tapping the "Reserved" label may ask: someone else's reservation ([r] false), or a 2× wish fully
    // reserved by others ([c] from WLRes.parse).
    canAsk: function (w, r, c) {
      if (w.got) return false;
      if (c && window.WLRes && WLRes.qty(w) > 1) return !!c.full && !c.my;
      return r === false;
    },

    // The "Reserved" label as a button that asks.
    label: function (text, code, w, occ) {
      var b = el("button", "badge status wlt-ask", text);
      b.type = "button";
      b.title = T_("Ask if it’s still being bought");
      b.addEventListener("click", function (e) { e.preventDefault(); e.stopPropagation(); window.WLT.ask(code, w, occ); });
      return b;
    },

    ask: function (code, w, occ) {
      var gone = passed(occ);
      dialog(T_("Someone reserved this"), function (box, close) {
        box.appendChild(el("p", "wlt-p", gone ? T_("The occasion has passed, so you can’t ask about it any more.")
          : T_("Want to buy “{0}” too? Ask if it’s still being bought. They have 2 days to answer; without an answer it becomes free.", title(w.title))));
        var row = el("div", "wlt-row");
        row.appendChild(button(gone ? T_("Close") : T_("Cancel"), "outline", close));
        if (!gone) {
          var go = button(T_("Ask if it’s still being bought"), "", async function () {
            go.disabled = true;
            var r;
            try {
              var a = cfg.auth ? cfg.auth() : null;
              r = await cfg.rpc("wl_ask", { p_code: code, p_item: w.id, p_device: cfg.device(), p_id: a ? a.p_id : "", p_token: a ? a.p_token : "" });
            } catch (e) {
              r = isMissing(e, "wl_ask") ? "missing" : "offline";
            }
            close();
            cfg.toast(r === "missing" ? T_("Asking needs an update in Supabase first.") : said(r));
            if (cfg.reload) cfg.reload();
          });
          row.appendChild(go);
        }
        box.appendChild(row);
      });
    },

    // The questions about this browser's reservations on list [code], and what happened to its own questions.
    panel: function (code) {
      if (!cfg || !code) return null;
      var a = asks[code] || [], n = news[code] || [];
      if (!a.length && !n.length) return null;
      var p = el("div", "wlt-panel");
      a.forEach(function (q) {
        var b = el("div", "wlt-q");
        b.appendChild(el("strong", null, T_("Still buying “{0}”?", title(q.title))));
        b.appendChild(el("p", "wlt-p", T_("Someone else would like to buy it.") + " " + left(q.until)));
        var row = el("div", "wlt-row start");
        row.appendChild(button(T_("Yes, I’m buying it"), "", function () { answer(code, q, true); }));
        row.appendChild(button(T_("Let someone else"), "outline", function () { answer(code, q, false); }));
        b.appendChild(row);
        p.appendChild(b);
      });
      n.forEach(function (x, i) {
        var d = x.data || {}, t = title(d.title);
        var line = x.kind === "ask_yes" ? T_("“{0}” is still being bought. Pick another gift, or ask again in a week.", t)
          : x.kind === "ask_free" ? T_("“{0}” is free again. Reserve it now?", t)
          : T_("Your reservation of “{0}” has ended: there was no answer within 2 days.", t);
        var b = el("div", "wlt-q");
        b.appendChild(el("p", "wlt-p", line));
        b.appendChild(button("OK", "outline", function () { news[code].splice(news[code].indexOf(x), 1); redraw(); }));
        p.appendChild(b);
      });
      return p;
    }
  };
})();
