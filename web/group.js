/*
 * A wishlist group in the browser (version 2.108), for people without the app. Decided with the owner (see
 * docs/DECISIONS.md, "Wishlist groups"):
 *  - group.html?g=LINK is the group link. Someone new first gives their name and names their first wishlist, then
 *    joins. Someone who is already in (this browser remembers them) sees the group straight away.
 *  - The group: every member as a block (their newest wish, their picture or first letter, name, list). Tap a block
 *    to see their list and reserve gifts ("Reserved" / "Reserved by you"; the owner of a list never sees reservations,
 *    so your own list never loads them).
 *  - Your own wishlist: add a wish by pasting a shop link (it fills in itself through the helper fetch-page) or by
 *    hand (name, price, picture, description, note), edit, delete (with Undo), top wish. No categories, no price
 *    checks (app only).
 *  - "My link" (group.html#me=ID.TOKEN.DEVICE) brings you back on any device; opened in the app it moves your list,
 *    groups and reservations into the app. Keep it private: it is the key to your wishlist.
 * The browser keeps: wl_gme (your profile id and token, and your list's code and owner token) and wl_device (the
 * device id that reservations use, the same as the friends' page and the editor).
 */
(function () {
  "use strict";
  var SUPABASE_URL = "https://aspnmvmsvwbezdltykgx.supabase.co";
  var SUPABASE_KEY = "sb_publishable_2RpbWlDJBB3yV2TO-h68xA_i-gXfd_t";
  var BUCKET = "wish-photos";
  var APK_URL = "https://github.com/AndroidEnthusiast/Wishlist/releases/download/latest/Wishlist.apk";
  var ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
  var KEY_RE = /^[A-HJ-NP-Z2-9]{20}$/;
  var PHOTO_SIDE = 600;
  var REFRESH_MS = 30000;
  var CURRENCIES = ["EUR", "USD", "GBP"];

  var P = window.WLParse;
  var $ = function (id) { return document.getElementById(id); };

  var me = null;          // { id, token, list, listToken, name }
  var link = "";          // the group link key in the address
  var groups = [];        // your groups (wl_gr_state)
  var group = null;       // the group on screen
  var screen = "group";   // "group" | "member" | "mine"
  var member = null;      // the member whose list is open
  var other = null;       // that list (wl_gr_list), with res = their reservations
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
    try { var x = new URL(u); return (x.protocol === "http:" || x.protocol === "https:") ? x.href : ""; } catch (e) { return ""; }
  }
  function hostOf(u) { try { return new URL(u).hostname.replace(/^www\./, ""); } catch (e) { return ""; } }
  function money(p, cur) {
    try { return new Intl.NumberFormat(undefined, { style: "currency", currency: cur || "EUR" }).format(p); }
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
  // A message with one button (Undo), for about 5 seconds.
  var snackTimer = null;
  function snack(msg, label, onAct) {
    var s = $("snack");
    s.textContent = "";
    s.appendChild(el("span", "stext", msg));
    var b = el("button", "sbtn", label);
    b.type = "button";
    b.addEventListener("click", function () { s.hidden = true; clearTimeout(snackTimer); onAct(); });
    s.appendChild(b);
    s.hidden = false;
    clearTimeout(snackTimer);
    snackTimer = setTimeout(function () { s.hidden = true; }, 5000);
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
    var id = "gf" + (++fieldCount);
    inp.id = id;
    var l = el("label", "flab", label);
    l.setAttribute("for", id);
    f.appendChild(l);
    f.appendChild(inp);
    return f;
  }
  function isAndroid() { return /Android/i.test(navigator.userAgent || ""); }

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
      throw err;
    }
    return t ? JSON.parse(t) : null;
  }
  function auth(extra) {
    var o = { p_id: me.id, p_token: me.token };
    for (var k in extra) o[k] = extra[k];
    return o;
  }
  function photoUrl(name) { return SUPABASE_URL + "/storage/v1/object/public/" + BUCKET + "/" + me.list + "/" + name; }
  async function uploadPhoto(name, blob) {
    var r = await fetch(SUPABASE_URL + "/storage/v1/object/" + BUCKET + "/" + me.list + "/" + name, {
      method: "POST",
      headers: headers({ "Content-Type": "image/jpeg", "Cache-Control": "max-age=31536000", "x-owner-token": me.listToken }),
      body: blob
    });
    var t = await r.text();
    if (!r.ok && t.indexOf("Duplicate") < 0) throw new Error("Photo upload said " + r.status);
  }
  async function loadShopPage(u) {
    var r = await fetch(SUPABASE_URL + "/functions/v1/fetch-page", {
      method: "POST", headers: headers({ "Content-Type": "application/json" }),
      body: JSON.stringify({ key: (group && group.link) || link, url: u })
    });
    var j = null;
    try { j = await r.json(); } catch (e) { /* no body */ }
    if (!r.ok || !j || typeof j.html !== "string") throw new Error((j && j.error) || ("status " + r.status));
    return j;
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
  function appLink() { return "wishlist://me?me=" + me.id + "." + me.token + "." + deviceId(); }

  // "#me=ID.TOKEN.DEVICE" in the address: My link, opened on this device.
  function readMeLink() {
    var m = /[#&]me=([A-HJ-NP-Z2-9]{20})\.([A-HJ-NP-Z2-9]{20})(?:\.([A-Za-z0-9-]{1,64}))?/.exec(location.hash || "");
    if (!m) return null;
    return { id: m[1], token: m[2], device: m[3] || "" };
  }

  function readLink() {
    var raw = new URLSearchParams(location.search).get("g");
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

  // A round picture of a person: their photo, or their first letter.
  function avatar(m, size) {
    var a = el("span", "avatar");
    a.style.width = a.style.height = size + "px";
    if (m.face) {
      var im = el("img"); im.src = m.face; im.alt = ""; a.appendChild(im);
    } else {
      a.textContent = (m.name || "?").trim().slice(0, 1).toUpperCase() || "?";
      a.style.fontSize = Math.round(size * 0.42) + "px";
    }
    return a;
  }

  // ---------- joining ----------

  function showJoin(info) {
    setTitle(info.name);
    backTo(null);
    $("fab").hidden = true;
    var v = view();
    var box = el("div", "gjoin");
    if (info.photo) {
      var c = el("div", "gcover"); var im = el("img"); im.src = "data:image/jpeg;base64," + info.photo; im.alt = ""; c.appendChild(im); box.appendChild(c);
    }
    box.appendChild(el("h2", "gtitle", T("Join the wishlist group “{0}”", info.name)));
    box.appendChild(el("p", "sub", (info.organiser ? T("Organised by {0}", info.organiser) + " · " : "") +
      (info.count === 1 ? T("1 member") : T("{0} members", info.count))));
    box.appendChild(el("p", "hint", T("Everyone in a group brings one wishlist, and you can all see each other’s wishlists and reserve gifts.")));
    var err = el("p", "err"); err.hidden = true;
    err.setAttribute("role", "status");

    if (me && me.list) {
      // Already known in this browser: join with the wishlist you have.
      var j = btn(T("Join with “{0}”", me.listName || T("my wishlist")), "", async function () {
        j.disabled = true;
        try {
          var g = await rpc("wl_gr_join", auth({ p_link: link, p_group: "", p_list: me.list, p_list_token: me.listToken }));
          if (!g) throw new Error("gone");
          await start();
        } catch (e) { j.disabled = false; err.textContent = T("Couldn’t join. Check your connection and try again."); err.hidden = false; }
      });
      box.appendChild(j);
      box.appendChild(err);
    } else {
      // New: your name and your first wishlist.
      var nm = input("", T("For example: Sam"), 30);
      nm.autocomplete = "given-name";
      var ln = input("", T("For example: My birthday"), 60);
      box.appendChild(field(T("Your name*"), nm));
      box.appendChild(field(T("Name your wishlist*"), ln));
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
      var hasApp = el("p", "hint");
      hasApp.appendChild(document.createTextNode(T("Have the Wishlist app? Open this link on your phone with the app installed, or")));
      hasApp.appendChild(document.createTextNode(" "));
      var a = el("a", null, T("open it in the app"));
      a.href = "wishlist://group?g=" + link;
      hasApp.appendChild(a);
      hasApp.appendChild(document.createTextNode("."));
      if (isAndroid()) box.appendChild(hasApp);
    }
    v.appendChild(box);
  }

  // A browser profile, your first wishlist, and joining with it.
  async function joinNew(name, listName) {
    var id = newKey(), token = newKey(), code = newKey(8), listToken = newKey();
    await rpc("wl_gr_web_profile", { p_id: id, p_token: token, p_name: name });
    await rpc("wl_create_list", { p_code: code, p_token: listToken, p_name: listName });
    me = { id: id, token: token, list: code, listToken: listToken, name: name, listName: listName };
    saveMe();
    try { await rpc("wl_set_owner", { p_code: code, p_token: listToken, p_name: name }); } catch (e) { /* "Owned by" only */ }
    await rpc("wl_gr_web_list", auth({ p_list: code, p_list_token: listToken }));
    var g = await rpc("wl_gr_join", auth({ p_link: link, p_group: "", p_list: code, p_list_token: listToken }));
    if (!g) throw new Error("gone");
  }

  // ---------- the group ----------

  async function loadGroups() {
    var s = await rpc("wl_gr_state", auth({}));
    groups = (s && s.groups) || [];
    // Photos of the members (and the group), once per version.
    for (var i = 0; i < groups.length; i++) {
      var g = groups[i];
      g.members.forEach(function (m) { var k = "f" + m.id + "_" + m.pv; if (faceCache[k] !== undefined) m.face = faceCache[k]; });
    }
    return groups;
  }
  var faceCache = {}, photoCache = {};
  async function loadFaces(g) {
    var todo = g.members.filter(function (m) { return m.pv > 0 && faceCache["f" + m.id + "_" + m.pv] === undefined; });
    for (var i = 0; i < todo.length; i++) {
      var m = todo[i], k = "f" + m.id + "_" + m.pv;
      try {
        var b64 = await rpc("wl_gr_face", auth({ p_other: m.id }));
        faceCache[k] = b64 ? "data:image/jpeg;base64," + b64 : "";
      } catch (e) { faceCache[k] = ""; }
      m.face = faceCache[k];
    }
    if (g.pv > 0 && photoCache[g.id + "_" + g.pv] === undefined) {
      try {
        var p = await rpc("wl_gr_photo", auth({ p_group: g.id }));
        photoCache[g.id + "_" + g.pv] = p ? "data:image/jpeg;base64," + p : "";
      } catch (e) { photoCache[g.id + "_" + g.pv] = ""; }
    }
  }

  function showGroup() {
    screen = "group";
    var g = group;
    setTitle(g.name);
    backTo(groups.length > 1 ? showGroupList : null);
    $("fab").hidden = true;
    var v = view();
    var photo = photoCache[g.id + "_" + g.pv];
    if (photo) { var c = el("div", "gcover"); var im = el("img"); im.src = photo; im.alt = ""; c.appendChild(im); v.appendChild(c); }
    var org = g.members.filter(function (m) { return m.id === g.organiser; })[0];
    v.appendChild(el("p", "sub", (org ? T("Organised by {0}", org.id === me.id ? T("you") : org.name) + " · " : "") +
      (g.members.length === 1 ? T("1 member") : T("{0} members", g.members.length))));

    var grid = el("div", "gblocks");
    var sorted = g.members.slice().sort(function (a, b) { return (a.id === me.id ? 0 : 1) - (b.id === me.id ? 0 : 1); });
    sorted.forEach(function (m) { grid.appendChild(memberBlock(g, m)); });
    v.appendChild(grid);

    if (g.invite_all || g.organiser === me.id) {
      var sh = el("div", "gbox");
      sh.appendChild(el("h3", null, T("Invite people")));
      sh.appendChild(el("p", "hint", T("Anyone with the link can join, also without the app.")));
      var gl = location.href.split("#")[0].split("?")[0].replace(/group\.html$/, "") + "group/?g=" + g.link;
      sh.appendChild(btn(T("Copy group link"), "outline", function () { copyText(gl); toast(T("Link copied")); }));
      v.appendChild(sh);
    }
    v.appendChild(myLinkBox());
    var leave = btn(T("Leave group"), "outline", function () { askLeave(g); });
    var lw = el("div", "gleave"); lw.appendChild(leave); v.appendChild(lw);
  }

  function showGroupList() {
    group = null;
    setTitle(T("Wishlist groups"));
    backTo(null);
    $("fab").hidden = true;
    var v = view();
    groups.forEach(function (g) {
      var b = el("button", "grow", g.name);
      b.type = "button";
      b.addEventListener("click", function () { group = g; showGroup(); });
      v.appendChild(b);
    });
    v.appendChild(myLinkBox());
  }

  function memberBlock(g, m) {
    var isMe = m.id === me.id;
    var b = el("button", "gblock");
    b.type = "button";
    var pic = el("div", "pic" + (m.list && m.list.image ? "" : " empty"));
    var img = m.list && safeUrl(m.list.image);
    if (img) {
      var back = el("img", "back"); back.src = img; back.alt = ""; back.referrerPolicy = "no-referrer"; back.setAttribute("aria-hidden", "true");
      var im = el("img"); im.src = img; im.alt = ""; im.referrerPolicy = "no-referrer";
      pic.appendChild(back); pic.appendChild(im);
    } else {
      pic.appendChild(el("span", "gempty", !m.list ? T("No wishlist yet") : m.list.count ? "🎁" : T("Empty")));
    }
    if (m.list) pic.appendChild(el("span", "gcount", String(m.list.count)));
    var av = avatar(m, 40); av.classList.add("onpic"); pic.appendChild(av);
    b.appendChild(pic);
    b.appendChild(el("span", "gname", isMe ? T("You") : (m.name || T("Someone"))));
    var sub = [m.id === g.organiser ? T("Organiser") : "", m.list ? m.list.name : ""].filter(Boolean).join(" · ");
    if (sub) b.appendChild(el("span", "gsub", sub));
    b.addEventListener("click", function () {
      if (isMe) openMine();
      else if (!m.list) toast(T("{0} doesn’t bring a wishlist yet.", m.name));
      else openMember(m);
    });
    return b;
  }

  // "My link": back on another device, or into the app.
  function myLinkBox() {
    var box = el("div", "gbox");
    box.appendChild(el("h3", null, T("Your private link")));
    box.appendChild(el("p", "hint", T("Save this link to get back to your wishlist on another device. Keep it to yourself: whoever has it can change your wishlist.")));
    box.appendChild(btn(T("Copy my link"), "outline", function () { copyText(myLink()); toast(T("Link copied")); }));
    if (isAndroid()) {
      box.appendChild(el("p", "hint", T("Rather use the app? Install it, then open your private link in the app to move your wishlist there.")));
      var row = el("div", "btnrow");
      var get = el("a", "act", T("Get the app")); get.href = APK_URL; row.appendChild(get);
      var open = el("a", "act outline", T("Move to the app")); open.href = appLink(); row.appendChild(open);
      box.appendChild(row);
    }
    return box;
  }

  function askLeave(g) {
    var organiser = g.organiser === me.id;
    var others = g.members.filter(function (m) { return m.id !== me.id; });
    var d = modal(T("Leave “{0}”?", g.name));
    if (organiser && !others.length) {
      d.body.appendChild(el("p", null, T("You are the only member. The organiser can delete the group in the app.")));
      d.foot.appendChild(btn(T("Close"), "outline", d.close));
      return;
    }
    d.body.appendChild(el("p", null, T("Your wishlist stays yours. You can join again with the group link.")));
    var next = null;
    if (organiser) {
      d.body.appendChild(el("p", "flab", T("Choose the next organiser first:")));
      others.forEach(function (o) {
        var l = el("label", "swrow");
        var r = el("input"); r.type = "radio"; r.name = "nextorg";
        r.addEventListener("change", function () { next = o.id; go.disabled = false; });
        l.appendChild(r); l.appendChild(el("span", null, o.name || T("Someone")));
        d.body.appendChild(l);
      });
    }
    d.foot.appendChild(btn(T("Cancel"), "outline", d.close));
    var go = btn(T("Leave"), "danger", async function () {
      go.disabled = true;
      try {
        await rpc("wl_gr_leave", auth({ p_group: g.id, p_next: next || "" }));
        d.close();
        toast(T("You left “{0}”", g.name));
        await start();
      } catch (e) { go.disabled = false; toast(T("Couldn’t do that. Check your connection.")); }
    });
    go.disabled = organiser;
    d.foot.appendChild(go);
  }

  // A simple dialog: { body, foot, close }.
  function modal(title) {
    var back = el("div", "backdrop dlg-back");
    var d = el("div", "dlg");
    d.setAttribute("role", "dialog");
    d.setAttribute("aria-modal", "true");
    var h = el("h2", null, title);
    d.appendChild(h);
    var body = el("div", "dbody"); d.appendChild(body);
    var foot = el("div", "dfoot"); d.appendChild(foot);
    back.appendChild(d);
    document.body.appendChild(back);
    document.body.style.overflow = "hidden";
    var api = { body: body, foot: foot, close: function () { back.remove(); document.body.style.overflow = ""; } };
    back.addEventListener("click", function (e) { if (e.target === back) api.close(); });
    return api;
  }

  // ---------- someone else's list ----------

  async function openMember(m) {
    member = m;
    screen = "member";
    setTitle(m.list.name);
    backTo(function () { member = null; other = null; showGroup(); });
    center(T("Loading…"));
    await loadMember();
  }

  async function loadMember() {
    if (!member) return;
    try {
      var l = await rpc("wl_gr_list", auth({ p_group: group.id, p_code: member.list.code }));
      var rs = await rpc("wl_get_reservations", { p_code: member.list.code, p_device: deviceId() });
      if (!l) { center(T("This wishlist isn’t in the group any more.")); return; }
      var res = {};
      (rs || []).forEach(function (r) { res[r.id] = !!r.mine; });
      other = l; other.res = res;
      if (screen === "member") drawMember();
    } catch (e) {
      if (!other) center(T("Couldn’t load the list. Check your connection."));
    }
  }

  function visible(items) {
    return (items || []).filter(function (w) { return w && w.id !== 0 && !w.bin && !w.priv; });
  }

  function drawMember() {
    var v = view();
    setTitle(other.name || member.list.name);
    v.appendChild(el("p", "owner", T("Owned by {0}", member.name)));
    var ws = visible(other.items);
    if (!ws.length) { v.appendChild(el("div", "center", T("Nothing here yet."))); return; }
    // Owned wishes at the bottom (owner, 4 November 2026), then top wishes first.
    ws.sort(function (a, b) { return (a.got ? 1 : 0) - (b.got ? 1 : 0) || (b.star ? 1 : 0) - (a.star ? 1 : 0); });
    var grid = el("div", "grid");
    ws.forEach(function (w) { grid.appendChild(card(w, false)); });
    v.appendChild(grid);
    v.appendChild(el("p", "note", T("Tap Reserve on a gift you plan to buy so nobody gives the same gift twice. The list owner can’t see what’s reserved.")));
  }

  async function toggle(w, reserve) {
    try {
      if (reserve) {
        var ok = await rpc("wl_reserve_item", { p_code: member.list.code, p_item: w.id, p_device: deviceId() });
        if (!ok) toast(T("Someone else just reserved that one."));
      } else {
        await rpc("wl_unreserve_item", { p_code: member.list.code, p_item: w.id, p_device: deviceId() });
      }
    } catch (e) { toast(T("No connection. Try again.")); }
    await loadMember();
  }

  // One wish as a card. [own]: your own list (edit, delete, top wish; never reservations).
  function card(w, own) {
    var r = own ? undefined : other.res[w.id];
    var got = !!w.got;
    var c = el("div", "card" + ((got || r === false) ? " dim" : ""));
    var link = safeUrl(w.url);
    var pic = el(link ? "a" : "div", "pic" + (safeUrl(w.image) ? "" : " empty"));
    if (link) { pic.href = link; pic.target = "_blank"; pic.rel = "noopener noreferrer"; }
    var img = safeUrl(w.image);
    if (img) {
      var back = el("img", "back"); back.src = img; back.alt = ""; back.referrerPolicy = "no-referrer"; back.setAttribute("aria-hidden", "true");
      var im = el("img"); im.src = img; im.alt = w.title || ""; im.loading = "lazy"; im.referrerPolicy = "no-referrer";
      pic.appendChild(back); pic.appendChild(im);
    } else pic.appendChild(el("span", null, "🎁"));
    var bx = el("div", "badges");
    if (w.star && !got) {
      var s = el("span", "badge star"); s.appendChild(el("span", "ico", "★")); s.appendChild(el("span", "lbl", T("Top wish"))); bx.appendChild(s);
    }
    var status = got ? T("Owned") : r === true ? T("Reserved by you") : r === false ? T("Reserved") : "";
    if (status) bx.appendChild(el("span", "badge status", status));
    if (bx.childNodes.length) pic.appendChild(bx);
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
      var st = el("button", "ibtn" + (w.star ? " on" : ""), w.star ? "★" : "☆");
      st.type = "button"; st.title = w.star ? T("Not a top wish") : T("Top wish");
      st.setAttribute("aria-label", st.title);
      st.addEventListener("click", function () { setStar(w, !w.star); });
      acts.appendChild(st);
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
    backTo(function () { $("fab").hidden = true; showGroup(); });
    center(T("Loading…"));
    try {
      var l = await rpc("wl_get_list", { p_code: me.list });
      mine = { name: (l && l.name) || me.listName || T("My wishlist"), items: visible(l && l.items) };
      me.listName = mine.name; saveMe();
      drawMine();
    } catch (e) {
      center(T("Couldn’t load the list. Check your connection."));
    }
  }

  function drawMine() {
    if (screen !== "mine") return;
    setTitle(mine.name);
    var v = view();
    $("fab").hidden = false;
    var tools = el("div", "btnrow gtools");
    tools.appendChild(btn(T("Rename"), "outline small", rename));
    v.appendChild(tools);
    if (!mine.items.length) {
      v.appendChild(el("div", "center", T("Your wishlist is empty. Tap Add wish to add your first wish.")));
      return;
    }
    var ws = mine.items.slice().sort(function (a, b) { return (b.star ? 1 : 0) - (a.star ? 1 : 0) || b.id - a.id; });
    var grid = el("div", "grid");
    ws.forEach(function (w) { grid.appendChild(card(w, true)); });
    v.appendChild(grid);
  }

  // The whole list goes online (only you edit it). A failed save goes back to how it was, with a message.
  async function saveMine(before) {
    drawMine();
    try {
      await rpc("wl_sync_list", { p_code: me.list, p_token: me.listToken, p_name: mine.name, p_cats: [], p_items: mine.items });
      return true;
    } catch (e) {
      if (before) { mine.items = before.items; mine.name = before.name; drawMine(); }
      toast(T("Couldn’t save. Check your connection."));
      return false;
    }
  }
  function snapshot() { return { name: mine.name, items: mine.items.slice() }; }

  function setStar(w, on) {
    var before = snapshot();
    mine.items = mine.items.map(function (x) {
      if (x.id !== w.id) return x;
      var y = Object.assign({}, x);
      if (on) y.star = true; else delete y.star;
      return y;
    });
    saveMine(before);
  }

  function removeWish(w) {
    var before = snapshot();
    mine.items = mine.items.filter(function (x) { return x.id !== w.id; });
    saveMine(before).then(function (ok) {
      if (ok) snack(T("Wish deleted"), T("Undo"), function () {
        var b2 = snapshot();
        mine.items = [w].concat(mine.items);
        saveMine(b2);
      });
    });
  }

  function rename() {
    var d = modal(T("Rename"));
    var n = input(mine.name, "", 60);
    d.body.appendChild(field(T("Name*"), n));
    d.foot.appendChild(btn(T("Cancel"), "outline", d.close));
    d.foot.appendChild(btn(T("Save"), "", function () {
      var v = n.value.trim();
      if (!v) return;
      var before = snapshot();
      mine.name = v; me.listName = v; saveMe();
      d.close();
      saveMine(before);
    }));
    setTimeout(function () { n.focus(); }, 0);
  }

  // A picture chosen on this device, shrunk to at most 600 pixels and made a JPEG, as the app does.
  function shrinkPicture(file) {
    return new Promise(function (resolve, reject) {
      var url = URL.createObjectURL(file);
      var img = new Image();
      img.onload = function () {
        try {
          var w = img.naturalWidth, h = img.naturalHeight;
          var k = Math.min(1, PHOTO_SIDE / Math.max(w, h));
          var cv = document.createElement("canvas");
          cv.width = Math.max(1, Math.round(w * k)); cv.height = Math.max(1, Math.round(h * k));
          var cx = cv.getContext("2d");
          cx.fillStyle = "#fff"; cx.fillRect(0, 0, cv.width, cv.height);
          cx.drawImage(img, 0, 0, cv.width, cv.height);
          URL.revokeObjectURL(url);
          cv.toBlob(function (b) { b ? resolve(b) : reject(new Error("toBlob")); }, "image/jpeg", 0.82);
        } catch (e) { URL.revokeObjectURL(url); reject(e); }
      };
      img.onerror = function () { URL.revokeObjectURL(url); reject(new Error("load")); };
      img.src = url;
    });
  }

  // Add a wish (no [edit]) or change one: a shop link fills in the rest; or fill it in yourself.
  function wishForm(edit) {
    var f = {
      url: edit ? edit.url || "" : "", title: edit ? edit.title || "" : "", desc: edit ? edit.desc || "" : "",
      image: edit ? edit.image || "" : "", price: edit && typeof edit.price === "number" ? edit.price.toFixed(2) : "",
      cur: edit ? edit.cur || "EUR" : (lsGet("wl_cur") || "EUR"), note: edit ? edit.note || "" : ""
    };
    var blob = null, busy = false;
    var d = modal(edit ? T("Edit wish") : T("Add a wish"));
    var url = input(f.url, "https://…", 2000); url.type = "url"; url.inputMode = "url"; url.autocapitalize = "off";
    d.body.appendChild(field(T("Product link (optional)"), url));
    var fetchBtn = btn(T("Fetch details"), "outline small", function () { if (!busy) go(url.value); });
    d.body.appendChild(fetchBtn);
    var err = el("p", "err"); err.hidden = true; err.setAttribute("role", "status");
    d.body.appendChild(err);
    function setErr(m) { err.textContent = m || ""; err.hidden = !m; }

    var pf = el("div", "fld");
    pf.appendChild(el("span", "flab", T("Picture")));
    var picBox = el("div", "picbox");
    pf.appendChild(picBox);
    var prow = el("div", "btnrow");
    var file = el("input"); file.type = "file"; file.accept = "image/*"; file.hidden = true;
    file.addEventListener("change", async function () {
      var fl = file.files && file.files[0];
      if (!fl) return;
      try { blob = await shrinkPicture(fl); f.image = URL.createObjectURL(blob); showPic(); }
      catch (e) { setErr(T("This picture can’t be used. Try another one.")); }
    });
    prow.appendChild(file);
    prow.appendChild(btn(T("Choose a picture"), "outline small", function () { file.click(); }));
    var rm = btn(T("Remove picture"), "outline small", function () { f.image = ""; blob = null; showPic(); });
    prow.appendChild(rm);
    pf.appendChild(prow);
    d.body.appendChild(pf);
    function showPic() {
      picBox.textContent = "";
      var s = safeUrl(f.image) || (f.image.indexOf("blob:") === 0 ? f.image : "");
      if (s) { var im = el("img"); im.src = s; im.alt = T("Picture of the wish"); im.referrerPolicy = "no-referrer"; picBox.appendChild(im); }
      else picBox.appendChild(el("span", "hint", T("No picture")));
      rm.hidden = !f.image;
    }
    showPic();

    var title = input(f.title, "", 200);
    d.body.appendChild(field(T("Title*"), title));
    var pr = el("div", "row2");
    var price = input(f.price, "0.00", 20); price.inputMode = "decimal";
    pr.appendChild(field(T("Price"), price));
    var cur = el("select");
    var curs = CURRENCIES.indexOf(f.cur) < 0 ? CURRENCIES.concat([f.cur]) : CURRENCIES;
    curs.forEach(function (c) { var o = el("option", null, c); o.value = c; cur.appendChild(o); });
    cur.value = f.cur;
    pr.appendChild(field(T("Currency"), cur));
    d.body.appendChild(pr);
    var desc = el("textarea"); desc.value = f.desc; desc.rows = 3; desc.maxLength = 2000;
    d.body.appendChild(field(T("Description"), desc));
    var note = el("textarea"); note.value = f.note; note.rows = 2; note.maxLength = 300;
    note.placeholder = T("For example: size M, or any colour but pink");
    d.body.appendChild(field(T("Note for givers"), note));

    async function go(raw) {
      var u = P.cleanUrl(raw);
      if (u === null) { setErr(T("Enter a full product link.")); return; }
      url.value = u;
      busy = true; setErr(""); fetchBtn.disabled = true; fetchBtn.textContent = T("Reading…");
      var m = null, failed = false;
      try { m = P.readHelperAnswer(await loadShopPage(u)); } catch (e) { failed = true; }
      busy = false; fetchBtn.disabled = false; fetchBtn.textContent = T("Fetch details");
      if (failed || m === null) setErr(T("Couldn’t read that page. Fill in the details yourself."));
      else if (m.blocked) setErr(T("The store blocked the automatic read. Enter the title and price yourself."));
      else {
        title.value = m.title; desc.value = m.desc;
        if (m.currency) {
          if (!Array.prototype.some.call(cur.options, function (o) { return o.value === m.currency; })) {
            var o = el("option", null, m.currency); o.value = m.currency; cur.appendChild(o);
          }
          cur.value = m.currency;
        }
        if (!blob) { f.image = m.image || ""; showPic(); }
        if (m.price !== null) price.value = m.price.toFixed(2);
        else setErr(T("No price found on that page. You can type it in."));
      }
    }
    url.addEventListener("paste", function () {
      setTimeout(function () { if (!edit && !busy && /https?:\/\/\S+\.\S+/.test(url.value)) go(url.value); }, 0);
    });

    d.foot.appendChild(btn(T("Cancel"), "outline", d.close));
    var save = btn(T("Save"), "", async function () {
      var t = title.value.trim();
      if (!t) { setErr(T("Give the wish a title.")); title.focus(); return; }
      var pt = price.value.trim().replace(",", ".");
      var p = pt === "" ? null : Number(pt);
      if (p !== null && !isFinite(p)) p = null;
      save.disabled = true;
      var image = f.image;
      if (blob) {
        try {
          var name = "w" + Date.now().toString(36) + newKey(6).toLowerCase() + ".jpg";
          await uploadPhoto(name, blob);
          image = photoUrl(name);
        } catch (e) {
          image = edit && safeUrl(edit.image) ? edit.image : "";
          toast(T("The picture couldn’t be uploaded. The wish was saved without it."));
        }
      }
      lsSet("wl_cur", cur.value);
      var u = P.cleanUrl(url.value) || url.value.trim();
      var w = {
        id: edit ? edit.id : Date.now(), url: u, title: t, desc: desc.value.trim(), image: safeUrl(image) ? image : "",
        price: p, old: edit && p === edit.price ? edit.old : null, cur: cur.value, cat: "", got: !!(edit && edit.got)
      };
      if (edit && edit.star) w.star = true;
      if (note.value.trim()) w.note = note.value.trim();
      var before = snapshot();
      mine.items = edit ? mine.items.map(function (x) { return x.id === w.id ? w : x; }) : [w].concat(mine.items);
      d.close();
      if (await saveMine(before)) toast(edit ? T("Wish saved") : T("Wish added"));
    });
    d.foot.appendChild(save);
  }

  // ---------- start ----------

  async function start() {
    clearInterval(timer);
    var meLink = readMeLink();
    if (meLink) {
      // My link opened here: this browser becomes you (and your reservations show as yours).
      if (meLink.device) lsSet("wl_device", meLink.device);
      me = { id: meLink.id, token: meLink.token };
      try { history.replaceState(null, "", location.pathname + location.search); } catch (e) { /* keep the address */ }
    } else me = loadMe();

    if (me) {
      try {
        var w = await rpc("wl_gr_web_me", auth({}));
        me.name = w.name; me.list = w.list || me.list || ""; me.listToken = w.list_token || me.listToken || "";
        saveMe();
      } catch (e) {
        if (e.notAllowed) {
          // Moved to the app (or never existed): this browser starts again.
          lsDel("wl_gme"); me = null;
          if (meLink) { center(T("This link doesn’t work any more. Was your wishlist moved to the app?")); return; }
        } else { center(T("Couldn’t reach the server. Check your connection.")); return; }
      }
    }

    if (link === "bad") { center(T("This group link isn’t complete. Ask for the link again.")); return; }
    if (me) {
      try { await loadGroups(); } catch (e) { center(T("Couldn’t reach the server. Check your connection.")); return; }
    }
    if (link) {
      var info;
      try { info = await rpc("wl_gr_info", { p_link: link }); } catch (e) { center(T("Couldn’t reach the server. Check your connection.")); return; }
      var known = info && groups.filter(function (g) { return g.id === info.id; })[0];
      if (known) group = known;
      else if (!info) {
        if (!groups.length) { setTitle("Wishlist"); center(T("This group link doesn’t work any more. Ask for a new one.")); return; }
        group = groups[0];
      } else { showJoin(info); return; }
    } else if (groups.length) {
      group = group ? groups.filter(function (g) { return g.id === group.id; })[0] || groups[0] : groups[0];
    } else {
      setTitle("Wishlist");
      center(me ? T("You aren’t in a wishlist group any more.") : T("Open the group link you were sent to join."));
      return;
    }
    await loadFaces(group);
    showGroup();
    timer = setInterval(refresh, REFRESH_MS);
  }

  // Every 30 seconds while the page is open: the group (and the list on screen) again.
  async function refresh() {
    if (document.hidden || !me || !group) return;
    try {
      await loadGroups();
      var g = groups.filter(function (x) { return x.id === group.id; })[0];
      if (!g) { group = null; start(); return; }
      group = g;
      await loadFaces(g);
      if (screen === "group" && !document.querySelector(".dlg-back")) showGroup();
      else if (screen === "member") {
        member = g.members.filter(function (m) { return member && m.id === member.id; })[0] || null;
        if (member && member.list) await loadMember(); else showGroup();
      }
    } catch (e) { /* offline for a moment: the page stays as it is */ }
  }

  $("fab").addEventListener("click", function () { wishForm(null); });
  $("langs").appendChild(themeSwitch());
  link = readLink();
  start();
  // For the tests.
  window.WLGroup = { start: start, state: function () { return { me: me, groups: groups, group: group, screen: screen, mine: mine }; } };
})();
