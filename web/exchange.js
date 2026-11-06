/*
 * A gift exchange (Secret Santa) in the browser (version 2.129), for people without the app. Decided with the owner (see
 * docs/DECISIONS.md, "Side menu, Gifts I reserved and Gift exchange"):
 *  - exchange.html?x=LINK is the exchange link. Someone new gives their name and names their wishlist, then joins.
 *    Someone this browser already knows (also from a wishlist group, wl_gme) joins with the wishlist they have.
 *  - Before the draw: who is in, and "Waiting for … to draw names". The organiser draws in the app.
 *  - After the draw: the wrapped gift (tap: your name, with confetti), their wishlist (reserve like a friend), and the
 *    two anonymous chats: "You and Ben" (you are hidden) and "Your Secret Santa" (they are hidden).
 *  - Your own wishlist: add a wish by hand (name, link, price, note), edit, delete. No auto-fill (the app has it).
 *  - "My link" (exchange.html#me=ID.TOKEN.DEVICE) brings you back on any device. Keep it private.
 * The browser keeps: wl_gme (your profile and your list, shared with the group page) and wl_device (reservations).
 * Everything other people wrote (names, wishes, messages) is shown with textContent, links only as http(s).
 */
(function () {
  "use strict";
  var SUPABASE_URL = "https://aspnmvmsvwbezdltykgx.supabase.co";
  var SUPABASE_KEY = "sb_publishable_2RpbWlDJBB3yV2TO-h68xA_i-gXfd_t";
  var ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
  var KEY_RE = /^[A-HJ-NP-Z2-9]{20}$/;
  var REFRESH_MS = 30000;
  var CHAT_MS = 4000;
  var EMOJI = { xmas: "🎄", sint: "🎁", bday: "🎈", office: "☕", plain: "✨" };

  var $ = function (id) { return document.getElementById(id); };

  var me = null;          // { id, token, list, listToken, name, listName }
  var link = "";          // the exchange link key in the address
  var all = [];           // your exchanges (wl_gx_web_me), { id, link, name }
  var x = null;           // the exchange on screen (wl_gx_get)
  var screen = "x";       // "x" | "list" (your giftee's list) | "mine" | "pick"
  var other = null;       // your giftee's list, with res = reservations
  var mine = null;        // your own list { name, items }
  var memDevice = null;
  var timer = null;

  // ---------- small helpers ----------

  function el(tag, cls, text) {
    var e = document.createElement(tag);
    if (cls) e.className = cls;
    if (text !== undefined && text !== null) e.textContent = text;
    return e;
  }
  function lsGet(k) { try { return localStorage.getItem(k); } catch (e) { return null; } }
  function lsSet(k, v) { try { localStorage.setItem(k, v); } catch (e) { /* private mode: fine */ } }
  function lsDel(k) { try { localStorage.removeItem(k); } catch (e) { /* ignore */ } }
  function safeUrl(u) {
    try { var y = new URL(u); return (y.protocol === "http:" || y.protocol === "https:") ? y.href : ""; } catch (e) { return ""; }
  }
  function hostOf(u) { try { return new URL(u).hostname.replace(/^www\./, ""); } catch (e) { return ""; } }
  function money(p, cur) {
    try { return new Intl.NumberFormat("en-GB", { style: "currency", currency: cur || "EUR" }).format(p); }
    catch (e) { return Number(p).toFixed(2); }
  }
  function newKey(n) {
    var out = "", r = new Uint32Array(n || 20);
    crypto.getRandomValues(r);
    for (var i = 0; i < r.length; i++) out += ALPHABET[r[i] % ALPHABET.length];
    return out;
  }
  function deviceId() {
    try {
      var d = localStorage.getItem("wl_device");
      if (!d) {
        d = (window.crypto && crypto.randomUUID) ? crypto.randomUUID() : "d" + Date.now().toString(36) + Math.random().toString(36).slice(2);
        localStorage.setItem("wl_device", d);
      }
      return d;
    } catch (e) {
      if (!memDevice) memDevice = "m" + Date.now().toString(36) + Math.random().toString(36).slice(2);
      return memDevice;
    }
  }
  var toastTimer = null;
  function toast(msg) {
    var t = $("toast");
    t.textContent = msg;
    t.className = "show";
    clearTimeout(toastTimer);
    toastTimer = setTimeout(function () { t.className = ""; }, 4500);
  }
  function copyText(text) {
    try {
      if (navigator.clipboard && navigator.clipboard.writeText) { navigator.clipboard.writeText(text).catch(function () { legacyCopy(text); }); return; }
    } catch (e) { /* fall through */ }
    legacyCopy(text);
  }
  function legacyCopy(text) {
    try {
      var t = document.createElement("textarea");
      t.value = text; t.setAttribute("readonly", ""); t.style.position = "fixed"; t.style.opacity = "0";
      document.body.appendChild(t); t.select(); document.execCommand("copy"); document.body.removeChild(t);
    } catch (e) { /* nothing more to try */ }
  }
  function btn(label, cls, onClick) {
    var b = el("button", "act" + (cls ? " " + cls : ""), label);
    b.type = "button";
    b.addEventListener("click", onClick);
    return b;
  }
  function input(value, placeholder, max) {
    var i = el("input");
    i.type = "text"; i.value = value || ""; if (placeholder) i.placeholder = placeholder; if (max) i.maxLength = max;
    return i;
  }
  var fieldCount = 0;
  function field(label, inp) {
    var f = el("div", "fld");
    var id = "xf" + (++fieldCount);
    inp.id = id;
    var l = el("label", "flab", label);
    l.setAttribute("for", id);
    f.appendChild(l);
    f.appendChild(inp);
    return f;
  }
  function isAndroid() { return /Android/i.test(navigator.userAgent || ""); }
  function dayText(d) {
    if (!d) return "";
    var p = String(d).slice(0, 10).split("-");
    var dt = new Date(+p[0], +p[1] - 1, +p[2]);
    return dt.toLocaleDateString("en-GB", { day: "numeric", month: "long", year: "numeric" });
  }
  function daysTo(d) {
    if (!d) return null;
    var p = String(d).slice(0, 10).split("-");
    var t = new Date(); t.setHours(0, 0, 0, 0);
    return Math.round((new Date(+p[0], +p[1] - 1, +p[2]) - t) / 86400000);
  }

  // ---------- talking to Supabase ----------

  function headers(extra) {
    var h = { "apikey": SUPABASE_KEY };
    if (SUPABASE_KEY.indexOf("eyJ") === 0) h["Authorization"] = "Bearer " + SUPABASE_KEY;
    for (var k in extra) h[k] = extra[k];
    return h;
  }
  async function rpc(fn, body) {
    var r = await fetch(SUPABASE_URL + "/rest/v1/rpc/" + fn, {
      method: "POST", headers: headers({ "Content-Type": "application/json" }), body: JSON.stringify(body)
    });
    var t = await r.text();
    if (!r.ok) {
      var err = new Error("Server said " + r.status + ": " + t.slice(0, 120));
      err.notAllowed = t.indexOf("not allowed") >= 0;
      err.missing = r.status === 404 || t.indexOf("PGRST202") >= 0;
      throw err;
    }
    return t ? JSON.parse(t) : null;
  }
  function auth(extra) {
    var o = { p_id: me.id, p_token: me.token };
    for (var k in extra) o[k] = extra[k];
    return o;
  }
  function failText(e) {
    return e && e.missing ? T("Gift exchange needs an update in Supabase first.") : T("Couldn’t reach the server. Check your connection.");
  }

  // ---------- who you are ----------

  function saveMe() { lsSet("wl_gme", JSON.stringify(me)); }
  function loadMe() {
    try {
      var o = JSON.parse(lsGet("wl_gme") || "null");
      if (o && KEY_RE.test(o.id) && KEY_RE.test(o.token)) return o;
    } catch (e) { /* nothing kept */ }
    return null;
  }
  function myLink() {
    var base = location.href.split("#")[0].split("?")[0];
    return base + "#me=" + me.id + "." + me.token + "." + deviceId();
  }
  function readMeLink() {
    var m = /[#&]me=([A-HJ-NP-Z2-9]{20})\.([A-HJ-NP-Z2-9]{20})(?:\.([A-Za-z0-9-]{1,64}))?/.exec(location.hash || "");
    if (!m) return null;
    return { id: m[1], token: m[2], device: m[3] || "" };
  }
  function readLink() {
    var raw = new URLSearchParams(location.search).get("x");
    if (raw === null) return "";
    var k = String(raw).toUpperCase().replace(/[^A-Z0-9]/g, "");
    return KEY_RE.test(k) ? k : "bad";
  }

  // ---------- drawing ----------

  function setTitle(t) { $("name").textContent = t; document.title = t + " · Wishlist"; }
  function view() { var v = $("view"); v.textContent = ""; return v; }
  function center(text) { var v = view(); v.appendChild(el("div", "center", text)); return v; }
  function backTo(fn) {
    var b = $("backBtn");
    b.hidden = !fn;
    b.onclick = fn || null;
    $("head").classList.toggle("back", !!fn);
  }
  function avatar(name, size) {
    var a = el("span", "avatar");
    a.style.width = a.style.height = size + "px";
    a.textContent = (name || "?").trim().slice(0, 1).toUpperCase() || "?";
    a.style.fontSize = Math.round(size * 0.42) + "px";
    return a;
  }
  // The theme's cover, drawn with colours and its emoji (or the organiser's photo is in the app only).
  function cover(theme, title) {
    var c = el("div", "xcover t-" + (EMOJI[theme] ? theme : "xmas"));
    var e = EMOJI[theme] || EMOJI.xmas;
    for (var i = 0; i < 5; i++) c.appendChild(el("span", "xe xe" + i, e));
    if (title) c.appendChild(el("h2", "xctitle", title));
    return c;
  }
  function modal(title) {
    var back = el("div", "backdrop dlg-back");
    var d = el("div", "dlg");
    d.setAttribute("role", "dialog");
    d.setAttribute("aria-modal", "true");
    d.appendChild(el("h2", null, title));
    var body = el("div", "dbody"); d.appendChild(body);
    var foot = el("div", "dfoot"); d.appendChild(foot);
    back.appendChild(d);
    document.body.appendChild(back);
    document.body.style.overflow = "hidden";
    var api = { body: body, foot: foot, close: function () { back.remove(); document.body.style.overflow = ""; if (api.onClose) api.onClose(); } };
    back.addEventListener("click", function (e) { if (e.target === back) api.close(); });
    return api;
  }
  // Confetti for the reveal moment: small coloured pieces that fall and are gone after about two seconds.
  function confetti() {
    var reduced = window.matchMedia && matchMedia("(prefers-reduced-motion: reduce)").matches;
    if (reduced) return;
    var box = el("div", "confetti");
    box.setAttribute("aria-hidden", "true");
    var colours = ["#E53935", "#FFB300", "#43A047", "#1E88E5", "#8E24AA"];
    for (var i = 0; i < 70; i++) {
      var p = el("i");
      p.style.left = (Math.random() * 100) + "%";
      p.style.background = colours[i % colours.length];
      p.style.animationDelay = (Math.random() * 0.6) + "s";
      p.style.animationDuration = (1.4 + Math.random() * 1.2) + "s";
      box.appendChild(p);
    }
    document.body.appendChild(box);
    setTimeout(function () { box.remove(); }, 3200);
  }

  // ---------- joining ----------

  function showJoin(info) {
    setTitle(info.name);
    backTo(null);
    var v = view();
    var box = el("div", "gjoin");
    box.appendChild(cover(info.theme));
    box.appendChild(el("h2", "gtitle", T("Join the gift exchange “{0}”", info.name) + " " + (EMOJI[info.theme] || "")));
    var bits = [];
    if (info.organiser) bits.push(T("Organised by {0}", info.organiser));
    bits.push(info.count === 1 ? T("1 person") : T("{0} people", info.count));
    if (info.budget) bits.push(T("Budget: {0}", money(info.budget, info.currency)));
    if (info.day) bits.push(dayText(info.day));
    box.appendChild(el("p", "sub", bits.join(" · ")));
    box.appendChild(el("p", "hint", T("Everyone draws one name and secretly buys that person a gift from their wishlist. Nobody draws themselves.")));
    if (info.drawn) box.appendChild(el("p", "hint", T("Names are drawn already. You’ll be added to the draw.")));
    var err = el("p", "err"); err.hidden = true; err.setAttribute("role", "status");

    if (me && me.list) {
      var j = btn(T("Join with “{0}”", me.listName || T("my wishlist")), "", async function () {
        j.disabled = true;
        try {
          var g = await rpc("wl_gx_join", auth({ p_link: link, p_gx: "", p_list: me.list, p_list_token: me.listToken }));
          if (!g) throw new Error("gone");
          await start();
        } catch (e) { j.disabled = false; err.textContent = T("Couldn’t join. Check your connection and try again."); err.hidden = false; }
      });
      box.appendChild(j);
      box.appendChild(err);
      box.appendChild(el("p", "hint", T("Only the person who draws you can open your wishlist.")));
    } else {
      var nm = input("", T("For example: Sam"), 30);
      nm.autocomplete = "given-name";
      var ln = input("", T("For example: My wishes"), 60);
      box.appendChild(field(T("Your name*"), nm));
      box.appendChild(field(T("Name your wishlist*"), ln));
      box.appendChild(el("p", "hint", T("Only the person who draws you can open it. You can add wishes after joining.")));
      box.appendChild(err);
      var go = btn(T("Join"), "", async function () {
        var n = nm.value.trim(), l = ln.value.trim();
        if (!n) { err.textContent = T("What is your name?"); err.hidden = false; nm.focus(); return; }
        if (!l) { err.textContent = T("What is your wishlist called?"); err.hidden = false; ln.focus(); return; }
        go.disabled = true; err.hidden = true;
        try {
          await joinNew(n, l);
          await start();
        } catch (e) {
          go.disabled = false;
          err.textContent = T("Couldn’t join. Check your connection and try again.");
          err.hidden = false;
        }
      });
      box.appendChild(go);
      if (isAndroid()) {
        var hasApp = el("p", "hint");
        hasApp.appendChild(document.createTextNode(T("Have the Wishlist app?") + " "));
        var a = el("a", null, T("Open it in the app"));
        a.href = "wishlist://exchange?x=" + link;
        hasApp.appendChild(a);
        hasApp.appendChild(document.createTextNode("."));
        box.appendChild(hasApp);
      }
    }
    v.appendChild(box);
  }

  // A browser profile (the same kind the group page makes), your wishlist, and joining with it.
  async function joinNew(name, listName) {
    var id = newKey(), token = newKey(), code = newKey(8), listToken = newKey();
    await rpc("wl_gr_web_profile", { p_id: id, p_token: token, p_name: name });
    await rpc("wl_create_list", { p_code: code, p_token: listToken, p_name: listName });
    me = { id: id, token: token, list: code, listToken: listToken, name: name, listName: listName };
    saveMe();
    try { await rpc("wl_set_owner", { p_code: code, p_token: listToken, p_name: name }); } catch (e) { /* "Owned by" only */ }
    await rpc("wl_gr_web_list", auth({ p_list: code, p_list_token: listToken }));
    var g = await rpc("wl_gx_join", auth({ p_link: link, p_gx: "", p_list: code, p_list_token: listToken }));
    if (!g) throw new Error("gone");
  }

  // ---------- the exchange ----------

  function nameOf(id) {
    var m = x.members.filter(function (y) { return y.id === id; })[0];
    return (m && m.name) || T("Someone");
  }

  function showExchange() {
    screen = "x";
    other = null;
    setTitle(x.name);
    backTo(all.length > 1 ? function () { showPick(); } : null);
    var v = view();
    v.appendChild(cover(x.theme));
    var org = x.members.filter(function (m) { return m.id === x.organiser; })[0];
    var bits = [T("Organised by {0}", x.organiser === me.id ? T("you") : ((org && org.name) || x.organiser_name || T("someone")))];
    bits.push(x.members.length === 1 ? T("1 person") : T("{0} people", x.members.length));
    v.appendChild(el("p", "sub", bits.join(" · ")));
    var n = daysTo(x.day), e = EMOJI[x.theme] || "";
    if (n !== null && n >= 0) v.appendChild(el("p", "xcount", n === 0 ? T("Today is the exchange! {0}", e) : n === 1 ? T("Tomorrow is the exchange {0}", e) : T("{0} days to the exchange {1}", n, e)));
    var info = [];
    if (x.budget) info.push(T("Budget: {0}", money(x.budget, x.currency)));
    if (x.day) info.push(dayText(x.day));
    if (info.length) v.appendChild(el("p", "xinfo", info.join(" · ")));
    if (x.message) v.appendChild(el("blockquote", "xmsg", "“" + x.message + "”"));

    if (!x.drawn) {
      v.appendChild(el("h3", "xh", T("People")));
      v.appendChild(people(false));
      v.appendChild(el("div", "gbox xnote", T("Waiting for {0} to draw names. Come back here to open yours.", (org && org.name) || x.organiser_name || T("the organiser"))));
    } else if (x.me.waiting) {
      v.appendChild(el("div", "gbox xnote", T("You joined after the draw. {0} will add you soon.", (org && org.name) || T("The organiser"))));
    } else if (!x.me.seen) {
      var w = el("button", "xwrap");
      w.type = "button";
      w.appendChild(el("span", "xgift", "🎁"));
      w.appendChild(el("strong", null, T("Your name is ready")));
      w.appendChild(el("span", "hint", T("Tap the gift to open it. Make sure nobody is looking!")));
      w.addEventListener("click", async function () {
        w.disabled = true;
        try {
          var y = await rpc("wl_gx_open", auth({ p_gx: x.id }));
          if (y) x = y;
          confetti();
          showExchange();
        } catch (e2) { w.disabled = false; toast(failText(e2)); }
      });
      v.appendChild(w);
    } else if (x.me.giftee) {
      var g = x.me.giftee;
      var card = el("div", "gbox xgiftee");
      card.appendChild(el("span", "hint", T("You’re buying for")));
      card.appendChild(avatar(g.name, 64));
      card.appendChild(el("h3", "xgname", g.name || T("Someone")));
      if (x.budget) card.appendChild(el("span", null, T("Budget: {0}", money(x.budget, x.currency))));
      var row = el("div", "btnrow");
      row.appendChild(btn(T("See their wishlist"), "", openGiftee));
      var ask = btn(T("Ask {0} anonymously", g.name || T("them")), "outline", function () { chat("giftee"); });
      if (x.me.unread_giftee > 0) ask.appendChild(el("span", "xdot"));
      row.appendChild(ask);
      card.appendChild(row);
      v.appendChild(card);
    }
    if (x.drawn && x.me.has_santa) {
      var s = el("button", "xsanta");
      s.type = "button";
      s.addEventListener("click", function () { chat("santa"); });
      s.appendChild(el("span", "xse", "🤫"));
      var st = el("span", "xst");
      st.appendChild(el("strong", null, T("Your Secret Santa")));
      st.appendChild(el("span", "hint", x.me.unread_santa > 0 ? T("Wrote to you. Tap to read and answer.") : T("Can ask you questions. You can write to them too.")));
      s.appendChild(st);
      if (x.me.unread_santa > 0) s.appendChild(el("span", "xdot"));
      v.appendChild(s);
    }
    if (x.drawn) {
      v.appendChild(el("h3", "xh", T("People")));
      v.appendChild(people(true));
    }
    if (x.revealed) {
      v.appendChild(el("h3", "xh", T("Who had whom") + " " + e));
      var ul = el("ul", "xpairs");
      x.members.forEach(function (m) { if (m.gave_to) ul.appendChild(el("li", null, (m.name || T("Someone")) + "  →  " + nameOf(m.gave_to))); });
      v.appendChild(ul);
    }

    // Your own wishlist, My link, leaving.
    var mineBox = el("div", "gbox");
    mineBox.appendChild(el("h3", null, T("Your wishlist")));
    mineBox.appendChild(el("p", "hint", x.me.list ? T("“{0}” · {1} wishes. Only your Secret Santa can open it.", x.me.list.name, x.me.list.count) : T("You don’t join with a wishlist yet. Your Secret Santa needs one.")));
    if (me.list) mineBox.appendChild(btn(T("Edit my wishlist"), "outline", openMine));
    v.appendChild(mineBox);
    var linkBox = el("div", "gbox");
    linkBox.appendChild(el("h3", null, T("My link")));
    linkBox.appendChild(el("p", "hint", T("Open this exchange on another device with your link. Keep it private: it is the key to your wishlist.")));
    linkBox.appendChild(btn(T("Copy my link"), "outline", function () { copyText(myLink()); toast(T("Link copied")); }));
    v.appendChild(linkBox);
    if (x.organiser === me.id) v.appendChild(el("p", "hint xorg", T("You organise this exchange. Drawing names and rules are in the Wishlist app.")));
    else v.appendChild(btn(T("Leave exchange"), "outline xleave", leave));
  }

  function people(drawn) {
    var ul = el("ul", "xpeople");
    x.members.forEach(function (m) {
      var li = el("li");
      li.appendChild(avatar(m.name, 32));
      li.appendChild(el("span", "xpn", m.id === me.id ? T("You") : (m.name || T("Someone"))));
      var tags = [];
      if (m.id === x.organiser) tags.push(T("Organiser"));
      if (drawn && m.waiting) tags.push(T("Waiting for the draw"));
      if (tags.length) li.appendChild(el("span", "xpt", tags.join(" · ")));
      ul.appendChild(li);
    });
    return ul;
  }

  function leave() {
    var d = modal(T("Leave “{0}”?", x.name));
    d.body.appendChild(el("p", null, x.drawn ? T("Whoever drew you gets someone else. Your wishlist stays yours.") : T("Your wishlist stays yours.")));
    d.foot.appendChild(btn(T("Stay"), "outline", d.close));
    var go = btn(T("Leave"), "danger", async function () {
      go.disabled = true;
      try {
        await rpc("wl_gx_leave", auth({ p_gx: x.id, p_next: "" }));
        d.close();
        toast(T("You left “{0}”", x.name));
        x = null; link = "";
        try { history.replaceState(null, "", location.pathname); } catch (e) { /* keep the address */ }
        start();
      } catch (e) { go.disabled = false; toast(failText(e)); }
    });
    d.foot.appendChild(go);
  }

  // Choose one of your exchanges (no link in the address, or Back).
  function showPick() {
    screen = "pick";
    setTitle(T("Gift exchange"));
    backTo(null);
    var v = view();
    all.forEach(function (y) {
      var b = el("button", "grow", y.name);
      b.type = "button";
      b.addEventListener("click", function () { openExchange(y.id); });
      v.appendChild(b);
    });
  }

  async function openExchange(id) {
    center(T("Loading…"));
    try {
      x = await rpc("wl_gx_get", auth({ p_gx: id }));
      if (!x) { center(T("You aren’t in this gift exchange any more.")); return; }
      showExchange();
    } catch (e) { center(failText(e)); }
  }

  // ---------- your giftee's wishlist ----------

  function visible(items) {
    return (items || []).filter(function (w) { return w && w.id !== 0 && !w.bin && !w.priv; });
  }

  async function openGiftee() {
    screen = "list";
    backTo(function () { showExchange(); });
    center(T("Loading…"));
    await loadGiftee();
  }
  async function loadGiftee() {
    try {
      var l = await rpc("wl_gx_list", auth({ p_gx: x.id }));
      if (!l) { center(T("{0} has no wishlist right now.", x.me.giftee ? x.me.giftee.name : T("They"))); return; }
      var rs = await rpc("wl_get_reservations", { p_code: l.code, p_device: deviceId() });
      var res = {};
      (rs || []).forEach(function (r) { if (r.mine) res[r.id] = true; else if (r.full !== false) res[r.id] = false; });
      other = l; other.res = res;
      if (screen === "list") drawGiftee();
    } catch (e) { if (!other) center(failText(e)); }
  }
  function drawGiftee() {
    var v = view();
    setTitle(other.name || T("Wishlist"));
    v.appendChild(el("p", "owner", T("Owned by {0}", x.me.giftee.name)));
    var ws = visible(other.items);
    if (!ws.length) { v.appendChild(el("div", "center", T("Nothing here yet. Ask them anonymously what they’d like."))); return; }
    ws.sort(function (a, b) { return (a.got ? 1 : 0) - (b.got ? 1 : 0) || (b.star ? 1 : 0) - (a.star ? 1 : 0); });
    var grid = el("div", "grid");
    ws.forEach(function (w) { grid.appendChild(card(w, false)); });
    v.appendChild(grid);
    v.appendChild(el("p", "note", T("Reserve what you’ll buy, so nobody gives it twice. They can’t see it.")));
  }
  async function toggle(w, reserve) {
    try {
      if (reserve) {
        var ok = await rpc("wl_reserve_item", { p_code: other.code, p_item: w.id, p_device: deviceId() });
        if (!ok) toast(T("Someone else just reserved that one."));
      } else {
        await rpc("wl_unreserve_item", { p_code: other.code, p_item: w.id, p_device: deviceId() });
      }
    } catch (e) { toast(T("No connection. Try again.")); }
    await loadGiftee();
  }

  function overBudget(w) {
    if (!x.budget || typeof w.price !== "number") return false;
    if (w.cur && x.currency && w.cur !== x.currency) return false;
    return w.price > x.budget + 0.005;
  }

  // One wish as a card. [own]: your own list (edit, delete; never reservations).
  function card(w, own) {
    var r = own ? undefined : other.res[w.id];
    var got = !!w.got;
    var c = el("div", "card" + ((got || r === false) ? " dim" : ""));
    var lnk = safeUrl(w.url);
    var img = safeUrl(w.image);
    var pic = el(lnk ? "a" : "div", "pic" + (img ? "" : " empty"));
    if (lnk) { pic.href = lnk; pic.target = "_blank"; pic.rel = "noopener noreferrer"; }
    if (img) {
      var im = el("img"); im.src = img; im.alt = w.title || ""; im.loading = "lazy"; im.referrerPolicy = "no-referrer";
      pic.appendChild(im);
    } else pic.appendChild(el("span", null, "🎁"));
    var status = got ? T("Owned") : r === true ? T("Reserved by you") : r === false ? T("Reserved") : (!own && overBudget(w) ? T("Over budget") : "");
    if (status || (w.star && !got)) {
      var bx = el("div", "badges");
      if (w.star && !got) { var s = el("span", "badge star"); s.appendChild(el("span", "ico", "★")); s.appendChild(el("span", "lbl", T("Top wish"))); bx.appendChild(s); }
      if (status) bx.appendChild(el("span", "badge status", status));
      pic.appendChild(bx);
    }
    c.appendChild(pic);
    var body = el("div", "body");
    var meta = hostOf(w.url);
    if (meta) body.appendChild(el("div", "meta", meta));
    body.appendChild(el("h2", "title", w.title || ""));
    if (w.desc) body.appendChild(el("p", "desc", w.desc));
    if (w.size) body.appendChild(el("span", "sizetag", T("Size {0}", String(w.size).slice(0, 8))));
    if (w.note) body.appendChild(el("p", "wnote", T("Note: {0}", w.note)));
    var row = el("div", "row");
    row.appendChild(el("div", "price", typeof w.price === "number" ? money(w.price, w.cur) : T("No price")));
    if (own) {
      var acts = el("div", "actions");
      acts.appendChild(btn(T("Edit"), "outline small", function () { wishForm(w); }));
      acts.appendChild(btn(T("Delete"), "outline small", function () { removeWish(w); }));
      row.appendChild(acts);
    } else if (!got) {
      if (r === undefined) row.appendChild(btn(T("Reserve"), "small", function () { toggle(w, true); }));
      else if (r === true) row.appendChild(btn(T("Undo"), "outline small", function () { toggle(w, false); }));
    }
    body.appendChild(row);
    c.appendChild(body);
    return c;
  }

  // ---------- your own wishlist ----------

  async function openMine() {
    screen = "mine";
    backTo(function () { start(); });
    center(T("Loading…"));
    try {
      var l = await rpc("wl_get_list", { p_code: me.list });
      mine = { name: (l && l.name) || me.listName || T("My wishlist"), items: visible(l && l.items), keep: (l && l.items || []).filter(function (w) { return w && (w.id === 0 || w.bin || w.priv); }) };
      me.listName = mine.name; saveMe();
      drawMine();
    } catch (e) { center(failText(e)); }
  }
  function drawMine() {
    if (screen !== "mine") return;
    setTitle(mine.name);
    var v = view();
    v.appendChild(btn(T("Add wish"), "", function () { wishForm(null); }));
    if (!mine.items.length) { v.appendChild(el("div", "center", T("Your wishlist is empty. Add a wish so your Secret Santa knows what you’d like."))); return; }
    var grid = el("div", "grid");
    mine.items.slice().sort(function (a, b) { return b.id - a.id; }).forEach(function (w) { grid.appendChild(card(w, true)); });
    v.appendChild(grid);
  }
  // The whole list goes online (only you edit it); wishes in the bin and the list's own settings stay as they were.
  async function saveMine(before) {
    drawMine();
    try {
      await rpc("wl_sync_list", { p_code: me.list, p_token: me.listToken, p_name: mine.name, p_cats: [], p_items: mine.keep.concat(mine.items) });
      return true;
    } catch (e) {
      if (before) { mine.items = before; drawMine(); }
      toast(T("Couldn’t save. Check your connection."));
      return false;
    }
  }
  function removeWish(w) {
    var before = mine.items.slice();
    mine.items = mine.items.filter(function (y) { return y.id !== w.id; });
    saveMine(before).then(function (ok) { if (ok) toast(T("Wish deleted")); });
  }
  function wishForm(edit) {
    var d = modal(edit ? T("Edit wish") : T("Add wish"));
    var t = input(edit ? edit.title : "", T("For example: Blue teapot"), 200);
    var u = input(edit ? edit.url : "", "https://…", 2000); u.type = "url";
    var p = input(edit && typeof edit.price === "number" ? String(edit.price) : "", "0.00", 10); p.inputMode = "decimal";
    var n = input(edit ? edit.note || "" : "", T("Colour, size, anything else"), 300);
    d.body.appendChild(field(T("Name*"), t));
    d.body.appendChild(field(T("Link to the shop"), u));
    d.body.appendChild(field(T("Price"), p));
    d.body.appendChild(field(T("Note for your Secret Santa"), n));
    var err = el("p", "err"); err.hidden = true; d.body.appendChild(err);
    d.foot.appendChild(btn(T("Cancel"), "outline", d.close));
    d.foot.appendChild(btn(T("Save"), "", async function () {
      var title = t.value.trim();
      if (!title) { err.textContent = T("What is the wish called?"); err.hidden = false; t.focus(); return; }
      var url = u.value.trim();
      if (url && !safeUrl(url)) { err.textContent = T("That link doesn’t look right. It starts with https://"); err.hidden = false; u.focus(); return; }
      var price = parseFloat(p.value.replace(",", "."));
      var w = {
        id: edit ? edit.id : Date.now(), url: safeUrl(url), title: title, desc: edit ? edit.desc || "" : "", image: edit ? edit.image || "" : "",
        price: isFinite(price) && price >= 0 ? price : null, old: null, cur: (edit && edit.cur) || x && x.currency || "EUR", cat: "", got: !!(edit && edit.got)
      };
      if (edit && edit.star) w.star = true;
      if (n.value.trim()) w.note = n.value.trim();
      var before = mine.items.slice();
      mine.items = edit ? mine.items.map(function (y) { return y.id === w.id ? w : y; }) : [w].concat(mine.items);
      d.close();
      if (await saveMine(before)) toast(edit ? T("Wish saved") : T("Wish added"));
    }));
    setTimeout(function () { t.focus(); }, 0);
  }

  // ---------- the anonymous chats ----------

  function chat(side) {
    var g = x.me.giftee;
    var d = modal(side === "giftee" ? T("You and {0}", (g && g.name) || T("your giftee")) : T("Your Secret Santa"));
    d.body.classList.add("xchat");
    d.body.appendChild(el("p", "hint", side === "giftee" ? T("They don’t see your name. Ask about sizes, colours, anything.") : T("You don’t know who this is. They’re buying your gift.")));
    var list = el("div", "xmsgs");
    list.setAttribute("aria-live", "polite");
    d.body.appendChild(list);
    var err = el("p", "err"); err.hidden = true; d.body.appendChild(err);
    var box = el("textarea", "xin"); box.maxLength = 1000; box.rows = 2; box.placeholder = T("Message");
    box.setAttribute("aria-label", T("Message"));
    d.foot.appendChild(box);
    var last = 0;
    var shown = {};
    async function load() {
      try {
        var ms = await rpc("wl_gx_chat", auth({ p_gx: x.id, p_side: side, p_after: last }));
        (ms || []).forEach(function (m) {
          if (shown[m.id]) return;
          shown[m.id] = true;
          last = Math.max(last, m.id);
          list.appendChild(el("div", "xbub" + (m.mine ? " mine" : ""), m.body));
        });
        if (ms && ms.length) list.scrollTop = list.scrollHeight;
        var theirs = (ms || []).filter(function (m) { return !m.mine; });
        if (theirs.length) await rpc("wl_gx_chat_seen", auth({ p_gx: x.id, p_side: side, p_upto: theirs[theirs.length - 1].id }));
        err.hidden = true;
      } catch (e) { err.textContent = failText(e); err.hidden = false; }
    }
    var send = btn(T("Send"), "", async function () {
      var body = box.value.trim();
      if (!body) return;
      send.disabled = true;
      try {
        var id = await rpc("wl_gx_send", auth({ p_gx: x.id, p_side: side, p_body: body }));
        if (!id || id === "0" || id === 0) { err.textContent = T("This chat isn’t open any more."); err.hidden = false; }
        else { box.value = ""; await load(); }
      } catch (e) { err.textContent = T("Couldn’t send. Check your connection."); err.hidden = false; }
      send.disabled = false;
    });
    d.foot.appendChild(send);
    d.foot.appendChild(btn(T("Close"), "outline", d.close));
    var t = setInterval(load, CHAT_MS);
    d.onClose = function () { clearInterval(t); refresh(); };
    load();
  }

  // ---------- start ----------

  async function start() {
    clearInterval(timer);
    var meLink = readMeLink();
    if (meLink) {
      if (meLink.device) lsSet("wl_device", meLink.device);
      me = { id: meLink.id, token: meLink.token };
      try { history.replaceState(null, "", location.pathname + location.search); } catch (e) { /* keep the address */ }
    } else me = loadMe();

    if (me) {
      try {
        var w = await rpc("wl_gx_web_me", auth({}));
        me.name = w.name; me.list = w.list || me.list || ""; me.listToken = w.list_token || me.listToken || "";
        all = w.exchanges || [];
        saveMe();
      } catch (e) {
        if (e.notAllowed) {
          lsDel("wl_gme"); me = null; all = [];
          if (meLink) { center(T("This link doesn’t work any more. Was your wishlist moved to the app?")); return; }
        } else { center(failText(e)); return; }
      }
    }
    if (link === "bad") { center(T("This exchange link isn’t complete. Ask for the link again.")); return; }
    var target = null;
    if (link) {
      var info;
      try { info = await rpc("wl_gx_info", { p_link: link }); } catch (e) { center(failText(e)); return; }
      var known = info && all.filter(function (y) { return y.id === info.id; })[0];
      if (known) target = known.id;
      else if (!info) {
        if (!all.length) { setTitle(T("Gift exchange")); center(T("This exchange link doesn’t work any more. Ask for a new one.")); return; }
      } else { showJoin(info); return; }
    }
    if (!target) {
      if (x && all.some(function (y) { return y.id === x.id; })) target = x.id;
      else if (all.length === 1) target = all[0].id;
      else if (all.length > 1) { showPick(); return; }
      else {
        setTitle(T("Gift exchange"));
        center(me ? T("You aren’t in a gift exchange any more.") : T("Open the exchange link you were sent to join."));
        return;
      }
    }
    await openExchange(target);
    timer = setInterval(refresh, REFRESH_MS);
  }

  // Every 30 seconds while the page is open (and after a chat closes): the exchange again.
  async function refresh() {
    if (document.hidden || !me || !x || screen !== "x" || document.querySelector(".dlg-back")) return;
    try {
      var y = await rpc("wl_gx_get", auth({ p_gx: x.id }));
      if (!y) { x = null; start(); return; }
      x = y;
      showExchange();
    } catch (e) { /* offline for a moment: the page stays as it is */ }
  }

  $("langs").appendChild(themeSwitch());
  link = readLink();
  start();
  // For the tests.
  window.WLExchange = { start: start, state: function () { return { me: me, x: x, screen: screen, mine: mine, all: all }; } };
})();
