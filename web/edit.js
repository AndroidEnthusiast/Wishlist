/*
 * The browser editor for a shared wishlist (edit.html?j=KEY). It does what the app does for a shared list, for people
 * who can't or don't want to install the app (iPhone, computer): see the wishes, add, change, delete (with Undo),
 * reserve gifts (everyone on the list sees what is reserved), categories, the list name and the note for friends, pictures.
 *
 * It follows the same rules as Shared.kt in the app, so people on the app and on the web can work on one list:
 *  - a change is shown at once and saved one wish at a time; if saving fails it is undone with a message;
 *  - saving a changed wish writes only the fields that were changed (wl_sh_put_item with p_fields), on top of what is
 *    online, so two people changing different things of the same wish both keep their change;
 *  - the list name, the note and each category change are separate small steps online;
 *  - the list is asked again every 20 seconds; an answer that started before a local change is ignored.
 * A new field on a wish must be added to normWish, wishJson, changedFields and applyFields here, and in the app.
 */
(function () {
  "use strict";

  var SUPABASE_URL = "https://aspnmvmsvwbezdltykgx.supabase.co";
  var SUPABASE_KEY = "sb_publishable_2RpbWlDJBB3yV2TO-h68xA_i-gXfd_t";
  var BUCKET = "wish-photos";
  var SIZES = ["XS", "S", "M", "L", "XL"];
  var CURRENCIES = ["USD", "EUR", "GBP", "CAD", "AUD", "NZD", "JPY", "CNY", "KRW", "INR", "MXN", "BRL", "CHF", "SEK",
    "NOK", "DKK", "PLN", "CZK", "HUF", "TRY", "ZAR", "SGD", "HKD", "AED"];
  var ABOUT_MAX = 300;
  var UNDO_MS = 4000;     // how long "Wish moved to the bin · Undo" shows
  var PHOTO_SIDE = 600;
  var REFRESH_MS = 20000;
  var INVITE_RE = /^[A-HJ-NP-Z2-9]{20}$/;
  // A shared wishlist is put together by several givers for someone else, so there is no "most wanted" here.
  var SORTS = [["new", "Newest first"], ["low", "Price: low to high"],
    ["high", "Price: high to low"], ["az", "Name A–Z"]];
  // Short names for the sort button when a sort is active (same as sortShortName in the app).
  var SORT_SHORT = { low: "Price: low", high: "Price: high", az: "A–Z" };
  var VIEWS = ["list", "grid"];
  var VIEW_LABELS = { list: "Large cards", grid: "Two per row" };

  var P = window.WLParse;
  var $ = function (id) { return document.getElementById(id); };

  // ---------- small helpers ----------

  function el(tag, cls, text) {
    var e = document.createElement(tag);
    if (cls) e.className = cls;
    if (text !== undefined && text !== null) e.textContent = text;
    return e;
  }

  function lsGet(k) { try { return localStorage.getItem(k); } catch (e) { return null; } }
  function lsSet(k, v) { try { localStorage.setItem(k, v); } catch (e) { /* private mode: fine */ } }

  function safeUrl(u) {
    try {
      var x = new URL(u);
      return (x.protocol === "http:" || x.protocol === "https:") ? x.href : "";
    } catch (e) { return ""; }
  }

  function hostOf(u) {
    try { return new URL(u).hostname.replace(/^www\./, ""); } catch (e) { return ""; }
  }

  function money(p, cur) {
    try { return new Intl.NumberFormat(undefined, { style: "currency", currency: cur || "USD" }).format(p); }
    catch (e) { return Number(p).toFixed(2); }
  }

  var ICONS = {
    edit: "M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04a1 1 0 0 0 0-1.41l-2.34-2.34a1 1 0 0 0-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z",
    del: "M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z",
    got: "M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z",
    more: "M12 8c1.1 0 2-.9 2-2s-.9-2-2-2-2 .9-2 2 .9 2 2 2zm0 2c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2zm0 6c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2z",
    add: "M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z",
    close: "M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z",
    search: "M15.5 14h-.79l-.28-.27A6.471 6.471 0 0 0 16 9.5 6.5 6.5 0 1 0 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z",
    share: "M18 16.08c-.76 0-1.44.3-1.96.77L8.91 12.7c.05-.23.09-.46.09-.7s-.04-.47-.09-.7l7.05-4.11c.54.5 1.25.81 2.04.81 1.66 0 3-1.34 3-3s-1.34-3-3-3-3 1.34-3 3c0 .24.04.47.09.7L8.04 9.81C7.5 9.31 6.79 9 6 9c-1.66 0-3 1.34-3 3s1.34 3 3 3c.79 0 1.5-.31 2.04-.81l7.12 4.16c-.05.21-.08.43-.08.65 0 1.61 1.31 2.92 2.92 2.92s2.92-1.31 2.92-2.92-1.31-2.92-2.92-2.92z",
    person: "M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z",
    star: "M12 17.27L18.18 21l-1.64-7.03L22 9.24l-7.19-.61L12 2 9.19 8.63 2 9.24l5.46 4.73L5.82 21z",
    starOff: "M22 9.24l-7.19-.62L12 2 9.19 8.63 2 9.24l5.46 4.73L5.82 21 12 17.27 18.18 21l-1.63-7.03L22 9.24zM12 15.4l-3.76 2.27 1-4.28-3.32-2.88 4.38-.38L12 6.1l1.71 4.04 4.38.38-3.32 2.88 1 4.28L12 15.4z"
  };

  function icon(name, size) {
    var NS = "http://www.w3.org/2000/svg";
    var s = document.createElementNS(NS, "svg");
    s.setAttribute("viewBox", "0 0 24 24");
    s.setAttribute("aria-hidden", "true");
    s.setAttribute("width", String(size || 22));
    s.setAttribute("height", String(size || 22));
    var p = document.createElementNS(NS, "path");
    p.setAttribute("d", ICONS[name]);
    p.setAttribute("fill", "currentColor");
    s.appendChild(p);
    return s;
  }

  function viewIcon(kind) {
    var NS = "http://www.w3.org/2000/svg";
    var s = document.createElementNS(NS, "svg");
    s.setAttribute("viewBox", "0 0 24 24");
    s.setAttribute("aria-hidden", "true");
    function box(x, y, w, h) {
      var q = document.createElementNS(NS, "rect");
      q.setAttribute("x", x); q.setAttribute("y", y);
      q.setAttribute("width", w); q.setAttribute("height", h);
      q.setAttribute("rx", 1.6);
      s.appendChild(q);
    }
    if (kind === "list") { box(3, 4, 18, 7); box(3, 13, 18, 7); }
    else { box(3, 3, 8, 8); box(13, 3, 8, 8); box(3, 13, 8, 8); box(13, 13, 8, 8); }
    return s;
  }

  function iconBtn(name, label, onClick, cls) {
    var b = el("button", "ibtn" + (cls ? " " + cls : ""));
    b.type = "button";
    b.setAttribute("aria-label", label);
    b.title = label;
    b.appendChild(icon(name, 22));
    b.addEventListener("click", function (ev) { ev.stopPropagation(); onClick(ev); });
    return b;
  }

  function textBtn(label, cls, onClick) {
    var b = el("button", "act" + (cls ? " " + cls : ""), label);
    b.type = "button";
    b.addEventListener("click", onClick);
    return b;
  }

  var toastTimer = null;
  function toast(msg) {
    var t = $("toast");
    t.textContent = msg;
    t.className = "show";
    clearTimeout(toastTimer);
    toastTimer = setTimeout(function () { t.className = ""; }, 4500);
  }

  function legacyCopy(text) {
    try {
      var t = document.createElement("textarea");
      t.value = text;
      t.setAttribute("readonly", "");
      t.style.position = "fixed";
      t.style.opacity = "0";
      document.body.appendChild(t);
      t.select();
      document.execCommand("copy");
      document.body.removeChild(t);
    } catch (e) { /* nothing more to try */ }
  }

  function copyText(text) {
    try {
      if (navigator.clipboard && navigator.clipboard.writeText) {
        navigator.clipboard.writeText(text).catch(function () { legacyCopy(text); });
        return;
      }
    } catch (e) { /* fall through */ }
    legacyCopy(text);
  }

  // ---------- talking to Supabase ----------

  function baseHeaders(extra) {
    var h = { "apikey": SUPABASE_KEY };
    if (SUPABASE_KEY.indexOf("eyJ") === 0) h["Authorization"] = "Bearer " + SUPABASE_KEY;
    for (var k in extra) h[k] = extra[k];
    return h;
  }

  async function rpc(fn, body, keepalive) {
    var r = await fetch(SUPABASE_URL + "/rest/v1/rpc/" + fn, {
      method: "POST", headers: baseHeaders({ "Content-Type": "application/json" }),
      body: JSON.stringify(body), keepalive: !!keepalive
    });
    var t = await r.text();
    if (!r.ok) throw new Error("Server said " + r.status + ": " + t.slice(0, 120));
    return t ? JSON.parse(t) : null;
  }

  function photoPrefix() { return SUPABASE_URL + "/storage/v1/object/public/" + BUCKET + "/" + code + "/"; }
  function photoNameOf(url) {
    url = String(url || "");
    if (!code || url.indexOf(photoPrefix()) !== 0) return null;
    var n = url.slice(photoPrefix().length);
    return n && n.indexOf("/") < 0 && n.indexOf("?") < 0 ? n : null;
  }

  async function uploadPhoto(name, blob) {
    var r = await fetch(SUPABASE_URL + "/storage/v1/object/" + BUCKET + "/" + code + "/" + name, {
      method: "POST",
      headers: baseHeaders({ "Content-Type": "image/jpeg", "Cache-Control": "max-age=31536000", "x-edit-key": key }),
      body: blob
    });
    var t = await r.text();
    // "Duplicate" means this exact picture is already there, which is fine.
    if (!r.ok && t.indexOf("Duplicate") < 0) throw new Error("Photo upload said " + r.status);
  }

  async function dropPhoto(url, keepalive) {
    var n = photoNameOf(url);
    if (!n) return;
    try {
      await fetch(SUPABASE_URL + "/storage/v1/object/" + BUCKET + "/" + code + "/" + n, {
        method: "DELETE", headers: baseHeaders({ "x-edit-key": key }), keepalive: !!keepalive
      });
    } catch (e) { /* a leftover picture is harmless */ }
  }

  // The helper function in Supabase loads a shop's page for us (supabase/functions/fetch-page).
  async function loadShopPage(u) {
    var r = await fetch(SUPABASE_URL + "/functions/v1/fetch-page", {
      method: "POST", headers: baseHeaders({ "Content-Type": "application/json" }),
      body: JSON.stringify({ key: key, url: u })
    });
    var j = null;
    try { j = await r.json(); } catch (e) { /* no body */ }
    if (!r.ok || !j || typeof j.html !== "string") {
      var err = new Error((j && j.error) || ("status " + r.status));
      err.code = (j && j.error) || String(r.status);
      throw err;
    }
    return j;
  }

  // ---------- the wishes ----------

  function num(v) { return (typeof v === "number" && isFinite(v)) ? v : null; }

  function normWish(o) {
    return {
      id: Number(o.id), url: String(o.url || ""), title: String(o.title || ""), desc: String(o.desc || ""),
      image: String(o.image || ""), price: num(o.price), old: num(o.old), cur: String(o.cur || "USD"),
      cat: String(o.cat || ""), got: !!o.got, star: !!o.star, note: String(o.note || ""),
      size: String(o.size || ""), by: String(o.by || ""),
      bin: num(o.bin) || 0, binBy: String(o.binBy || ""), chk: num(o.chk) || 0
    };
  }

  // What is sent online. Optional fields are left out when empty, so "cleared" means "missing".
  function wishJson(w) {
    var o = {
      id: w.id, url: w.url, title: w.title, desc: w.desc, image: w.image,
      price: w.price === null ? null : w.price, old: w.old === null ? null : w.old,
      cur: w.cur, cat: w.cat, got: !!w.got
    };
    if (w.star) o.star = true;
    if (w.note) o.note = w.note;
    if (w.size) o.size = w.size;
    if (w.by) o.by = w.by;
    if (w.bin) o.bin = w.bin;
    if (w.binBy) o.binBy = w.binBy;
    if (w.chk) o.chk = w.chk;
    return o;
  }

  var FIELDS = ["url", "title", "desc", "image", "price", "old", "cur", "cat", "got", "star", "note", "size"];

  // The names of the fields that differ between two versions of a wish. ("by" is only set when a wish is made; "bin"
  // and "binBy" only when a wish goes to the bin or comes back; "chk" only by the app's price check.)
  function changedFields(a, b) {
    return FIELDS.filter(function (f) { return a[f] !== b[f]; });
  }

  function applyFields(into, from, fields) {
    var w = {};
    for (var k in into) w[k] = into[k];
    fields.forEach(function (f) { w[f] = from[f]; });
    return w;
  }

  function copyWish(w, patch) {
    var o = {};
    for (var k in w) o[k] = w[k];
    for (var p in patch) o[p] = patch[p];
    return o;
  }

  // ---------- state ----------

  var key = null;           // the invite key (from the link)
  var code = "";            // the list's public code, from the server
  var content = null;       // { name, cats, about, items } as shown
  var state = "loading";    // loading | ok | gone | offline
  var pending = 0;          // saves in progress
  var stamp = 0;            // goes up with every local change, so an older refresh can't overwrite it
  var queue = Promise.resolve(); // saves run one after another (the lock)
  var trashed = null;       // the latest wish moved to the bin, while Undo is shown
  var blobs = {};           // pictures picked here but not uploaded yet: blob URL -> Blob
  var refreshing = false;
  var offlineSaid = false;

  var view = VIEWS.indexOf(lsGet("wl_view")) >= 0 ? lsGet("wl_view") : "list";
  var sortBy = (SORTS.map(function (s) { return s[0]; }).indexOf(lsGet("wl_sort_ed")) >= 0) ? lsGet("wl_sort_ed") : "new";
  var cat = "All", query = "";
  var me = lsGet("wl_name") || "";
  var instant = lsGet("wl_instant") === "1";
  // Reservations: wish id -> true (reserved here) or false (by someone else). Everyone on the list sees them.
  var res = {};
  var resBusy = false;
  var memDevice = null;

  function findWish(id) {
    var items = content ? content.items : [];
    for (var i = 0; i < items.length; i++) if (items[i].id === id) return items[i];
    return null;
  }

  function parseServer(r) {
    var items = [], about = "";
    (r.items || []).forEach(function (o) {
      if (o && o.kind === "about") about = String(o.about || "");
      // Wishes in the bin are not on the list (the app shows them in its Bin for 10 days).
      else if (o && Number(o.id) > 0 && !o.bin) items.push(normWish(o));
    });
    return {
      name: String(r.name || "Wishlist"),
      cats: (r.cats || []).map(String),
      about: about,
      items: items
    };
  }

  function cacheKey() { return "wl_ed_" + key.slice(0, 8); }
  function saveCache() {
    try { localStorage.setItem(cacheKey(), JSON.stringify({ code: code, content: content })); } catch (e) { /* fine */ }
  }
  function readCache() {
    try {
      var o = JSON.parse(localStorage.getItem(cacheKey()) || "null");
      if (o && o.content && Array.isArray(o.content.items)) return o;
    } catch (e) { /* ignore */ }
    return null;
  }

  // Asks the server for the list. A refresh never runs while a save is in progress, and an answer is thrown away if
  // something changed here in the meantime.
  async function refresh() {
    if (!key || pending > 0 || refreshing) return;
    refreshing = true;
    var s = stamp;
    var r;
    try {
      r = await rpc("wl_sh_get", { p_key: key });
    } catch (e) {
      refreshing = false;
      if (!content) { state = "offline"; render(); }
      else if (!offlineSaid) { offlineSaid = true; toast(T("No connection. Showing the last copy; changes can’t be saved until you’re back online.")); }
      return;
    }
    offlineSaid = false;
    refreshing = false;
    if (pending > 0 || stamp !== s) return;
    if (r === null) { state = "gone"; render(); return; }
    code = String(r.code || code);
    var nc = parseServer(r);
    if (!content || JSON.stringify(nc) !== JSON.stringify(content) || state !== "ok") {
      content = nc;
      state = "ok";
      render();
    }
    saveCache();
    loadRes();
  }

  // Shows a change at once and saves it. If saving fails the change is undone with a message.
  function write(before, after, job, failMessage) {
    content = after;
    stamp++;
    pending++;
    render();
    var run = queue.then(job);
    queue = run.catch(function () { /* the lock goes on whatever happened */ });
    run.then(function () { return true; }, function (e) {
      if (window.console) console.error(e);
      toast(failMessage || T("Couldn’t save that. Check your connection and try again."));
      return false;
    }).then(function (ok) {
      pending--;
      if (ok) {
        saveCache();
        // Other people's changes to other parts of the same wish were kept online: show them.
        if (pending === 0) refresh();
      } else if (content === after) {
        content = before;
        render();
        if (pending === 0) refresh();
      } else if (pending === 0) {
        refresh();
      }
    });
  }

  function newId() {
    var id = Date.now();
    while (findWish(id)) id++;
    return id;
  }

  // Adds a wish, or saves a changed one. base: the version the form was opened on (leave it out for quick changes
  // like the star). Only the fields that differ from it are written online, on top of what is online now.
  function putWish(w, base) {
    var before = content;
    var old = findWish(w.id);
    var mine = (!old && !w.by && me) ? copyWish(w, { by: me }) : w;
    var fields = old ? changedFields(base || old, mine) : null;
    if (fields && !fields.length) return;
    var was = base ? base.image : old ? old.image : undefined;
    var shown = (old && fields) ? applyFields(old, mine, fields) : mine;
    var items = old
      ? before.items.map(function (x) { return x.id === w.id ? shown : x; })
      : [mine].concat(before.items);
    var after = copyWish(before, { items: items });
    write(before, after, async function () {
      var out = mine;
      var usedFields = fields;
      var sendsImage = usedFields === null || usedFields.indexOf("image") >= 0;
      if (sendsImage && blobs[out.image]) {
        var blobUrl = out.image;
        var name = "wish_" + Date.now() + "_" + Math.random().toString(36).slice(2, 7) + ".jpg";
        try {
          await uploadPhoto(name, blobs[blobUrl]);
          out = copyWish(out, { image: photoPrefix() + name });
        } catch (e) {
          toast(T("Saved without the picture: it couldn’t be uploaded."));
          // keep whatever picture was there before
          out = copyWish(out, { image: usedFields === null ? "" : (base || old).image });
          if (usedFields !== null) usedFields = usedFields.filter(function (f) { return f !== "image"; });
        }
        delete blobs[blobUrl];
        try { URL.revokeObjectURL(blobUrl); } catch (e2) { /* ignore */ }
        // show the stored picture instead of the local one
        content = copyWish(content, {
          items: content.items.map(function (x) { return (x.id === out.id && x.image === blobUrl) ? copyWish(x, { image: out.image }) : x; })
        });
        render();
      }
      var body = { p_key: key, p_item: wishJson(out) };
      if (usedFields !== null) body.p_fields = usedFields;
      await rpc("wl_sh_put_item", body);
      // A picture that this save replaced or removed is deleted online too (never one someone else just set).
      if (usedFields !== null && usedFields.indexOf("image") >= 0 && was && was !== out.image) await dropPhoto(was);
    });
  }

  function quick(w, patch) { putWish(copyWish(w, patch)); }

  // ---- deleting: the wish goes to the bin at once (for everyone); Undo takes it out again ----
  // Only "bin" and "binBy" are written, so the picture and any reservation stay. Everyone on the list can restore it
  // from the bin in the app for 10 days.

  function binArgs(w, inBin) {
    return { p_key: key, p_item: wishJson(copyWish(w, inBin ? { bin: Date.now(), binBy: me } : { bin: 0, binBy: "" })), p_fields: ["bin", "binBy"] };
  }

  function trash(id) {
    var i = -1;
    content.items.forEach(function (x, n) { if (x.id === id) i = n; });
    if (i < 0) return null;
    var w = content.items[i];
    var before = content;
    write(before, copyWish(before, { items: before.items.filter(function (x) { return x.id !== id; }) }), async function () {
      await rpc("wl_sh_put_item", binArgs(w, true));
    }, T("Couldn’t delete that wish. Check your connection and try again."));
    return { wish: w, index: i };
  }

  // Undo: out of the bin and back where it was. The saves run in order, so this goes after the move to the bin.
  function untrash(t) {
    var before = content;
    var items = before.items.filter(function (x) { return x.id !== t.wish.id; });
    items.splice(Math.min(Math.max(t.index, 0), items.length), 0, t.wish);
    write(before, copyWish(before, { items: items }), async function () {
      await rpc("wl_sh_put_item", binArgs(t.wish, false));
    });
  }

  var undoTimer = null;

  function endUndo() {
    clearTimeout(undoTimer);
    undoTimer = null;
    trashed = null;
    $("snack").hidden = true;
  }

  function showUndo(t) {
    var s = $("snack");
    s.textContent = "";
    s.appendChild(el("span", "stext", T("Wish moved to the bin")));
    var u = el("button", "sbtn", T("Undo"));
    u.type = "button";
    u.addEventListener("click", function () {
      var x = trashed;
      endUndo();
      if (x) untrash(x);
    });
    s.appendChild(u);
    s.hidden = false;
    clearTimeout(undoTimer);
    undoTimer = setTimeout(endUndo, UNDO_MS);
  }

  // Moves the wish to the bin, then offers Undo for a few seconds.
  function deleteWish(w) {
    var t = trash(w.id);
    if (!t) return;
    trashed = t;
    render();
    showUndo(t);
  }

  function askDelete(w) {
    if (instant) { deleteWish(w); return; }
    dialog({
      title: T("Delete this wish?"),
      body: function (b) { b.appendChild(el("p", null, T("“{0}” goes to the bin. Everyone on the list can restore it from the bin in the app for 10 days.", w.title))); },
      actions: [
        { label: T("Cancel"), cls: "outline" },
        { label: T("Delete"), cls: "danger", onClick: function () { deleteWish(w); } }
      ]
    });
  }

  // ---- list name, note, categories ----

  function cleanCat(s) { return String(s || "").replace(/\s+/g, " ").trim(); }

  function catTaken(name, except) {
    return content.cats.some(function (c) { return c !== except && c.toLowerCase() === name.toLowerCase(); });
  }

  // Adds a category and returns its name (an existing one with the same name in other capitals is used instead).
  // Returns null for an empty name or "All".
  function addCat(raw) {
    var n = cleanCat(raw);
    if (!n || n.toLowerCase() === "all") return null;
    var found = null;
    content.cats.forEach(function (c) { if (c.toLowerCase() === n.toLowerCase()) found = c; });
    if (found) return found;
    var before = content;
    write(before, copyWish(before, { cats: before.cats.concat([n]) }),
      function () { return rpc("wl_sh_cat_add", { p_key: key, p_name: n }); });
    return n;
  }

  function renameCat(old, raw) {
    var n = cleanCat(raw);
    if (!n || n.toLowerCase() === "all" || content.cats.indexOf(old) < 0 || catTaken(n, old)) return false;
    if (n === old) return true;
    var before = content;
    var after = copyWish(before, {
      cats: before.cats.map(function (c) { return c === old ? n : c; }),
      items: before.items.map(function (w) { return w.cat === old ? copyWish(w, { cat: n }) : w; })
    });
    // Online it renames the category and moves every wish in it, also ones someone added a second ago.
    write(before, after, function () { return rpc("wl_sh_cat_rename", { p_key: key, p_old: old, p_new: n }); });
    if (cat === old) cat = n;
    return true;
  }

  function removeCat(name) {
    var before = content;
    var after = copyWish(before, {
      cats: before.cats.filter(function (c) { return c !== name; }),
      items: before.items.map(function (w) { return w.cat === name ? copyWish(w, { cat: "" }) : w; })
    });
    write(before, after, function () { return rpc("wl_sh_cat_remove", { p_key: key, p_name: name }); });
    if (cat === name) cat = "All";
  }

  function setName(raw) {
    var n = String(raw || "").trim().slice(0, 60) || "Shared wishlist";
    var before = content;
    write(before, copyWish(before, { name: n }), function () { return rpc("wl_sh_set_name", { p_key: key, p_name: n }); });
  }

  function setAbout(raw) {
    var a = String(raw || "").trim().slice(0, ABOUT_MAX);
    var before = content;
    write(before, copyWish(before, { about: a }), function () { return rpc("wl_sh_set_about", { p_key: key, p_about: a }); });
  }

  // ---------- sorting and searching (same as Sorting.kt) ----------

  function sortWishes(items, mode) {
    var rows = items.map(function (w, i) { return { w: w, i: i }; });
    rows.sort(function (a, b) {
      var d = 0;
      if (mode === "star") {
        d = (a.w.star && !a.w.got ? 0 : 1) - (b.w.star && !b.w.got ? 0 : 1);
      } else if (mode === "low" || mode === "high") {
        var pa = a.w.price, pb = b.w.price;
        if (pa === null || pb === null) d = (pa === null ? 1 : 0) - (pb === null ? 1 : 0);
        else d = mode === "low" ? pa - pb : pb - pa;
      } else if (mode === "az") {
        d = a.w.title.toLowerCase() < b.w.title.toLowerCase() ? -1 : a.w.title.toLowerCase() > b.w.title.toLowerCase() ? 1 : 0;
      } else {
        d = b.w.id - a.w.id;
      }
      return d || a.i - b.i;
    });
    return rows.map(function (r) { return r.w; });
  }

  function matchesQuery(w, q) {
    var words = q.trim().toLowerCase().split(/\s+/).filter(Boolean);
    if (!words.length) return true;
    var text = [w.title, w.desc, w.note, w.cat, hostOf(w.url)].join(" ").toLowerCase();
    return words.every(function (x) { return text.indexOf(x) >= 0; });
  }

  function visibleItems() {
    var items = content.items.filter(function (w) {
      return (cat === "All" || w.cat === cat) && matchesQuery(w, query);
    });
    return sortWishes(items, sortBy);
  }

  // ---------- reservations: the givers on this list reserve what they will buy ----------

  // The same device id as the friends' page (index.html), so a reservation made there is "yours" here too.
  function deviceId() {
    try {
      var d = localStorage.getItem("wl_device");
      if (!d) {
        d = (window.crypto && crypto.randomUUID) ? crypto.randomUUID()
          : "d" + Date.now().toString(36) + Math.random().toString(36).slice(2);
        localStorage.setItem("wl_device", d);
      }
      return d;
    } catch (e) {
      if (!memDevice) memDevice = "m" + Date.now().toString(36) + Math.random().toString(36).slice(2);
      return memDevice;
    }
  }

  async function loadRes() {
    if (!code || resBusy) return;
    try {
      var rs = await rpc("wl_get_reservations", { p_code: code, p_device: deviceId() });
      if (resBusy) return;
      var m = {};
      (rs || []).forEach(function (x) { m[x.id] = !!x.mine; });
      if (JSON.stringify(m) !== JSON.stringify(res)) { res = m; render(); }
    } catch (e) { /* keep what we have */ }
  }

  async function reserve(w, on) {
    if (resBusy) return;
    var before = res;
    var after = {};
    Object.keys(res).forEach(function (k) { after[k] = res[k]; });
    if (on) after[w.id] = true; else delete after[w.id];
    res = after; resBusy = true; render();
    var msg = null;
    try {
      if (on) {
        var ok = await rpc("wl_reserve_item", { p_code: code, p_item: w.id, p_device: deviceId() });
        if (ok !== true) msg = T("Someone else just reserved that one.");
      } else {
        await rpc("wl_unreserve_item", { p_code: code, p_item: w.id, p_device: deviceId() });
      }
    } catch (e) {
      res = before;
      msg = T("Couldn’t save that. Check your connection and try again.");
    }
    resBusy = false;
    if (msg) toast(msg);
    render();
    loadRes();
  }

  function resLabel(w) { return res[w.id] === true ? T("Reserved by you") : res[w.id] === false ? T("Reserved") : ""; }

  // ---------- drop-down menus (category, sort, the ⋮ on small cards) ----------

  var menuOpen = null;

  function closeMenu() {
    if (!menuOpen) return;
    var m = menuOpen;
    menuOpen = null;
    if (m.box.parentNode) m.box.parentNode.removeChild(m.box);
    m.anchor.setAttribute("aria-expanded", "false");
  }

  // items: [{ label, bold, onClick }]. The menu opens under the anchor; right: lined up with its right edge.
  function openMenu(anchor, items, right) {
    var again = menuOpen && menuOpen.anchor === anchor;
    closeMenu();
    if (again) return;
    var box = el("div", "menu");
    box.setAttribute("role", "menu");
    items.forEach(function (it) {
      var b = el("button", "mitem" + (it.bold ? " b" : ""));
      b.type = "button";
      b.setAttribute("role", "menuitem");
      if (it.icon) b.appendChild(icon(it.icon, 20));
      b.appendChild(document.createTextNode(it.label));
      b.addEventListener("click", function (ev) { ev.stopPropagation(); closeMenu(); it.onClick(); });
      box.appendChild(b);
    });
    document.body.appendChild(box);
    var r = anchor.getBoundingClientRect();
    box.style.position = "fixed";
    box.style.top = Math.round(r.bottom + 2) + "px";
    if (right) { box.style.left = "auto"; box.style.right = Math.round(window.innerWidth - r.right) + "px"; }
    else box.style.left = Math.round(Math.max(8, r.left)) + "px";
    var room = window.innerHeight - r.bottom - 12;
    if (room < 200 && r.top > room) { box.style.top = "auto"; box.style.bottom = Math.round(window.innerHeight - r.top + 2) + "px"; }
    anchor.setAttribute("aria-expanded", "true");
    menuOpen = { box: box, anchor: anchor, top: r.top };
    var first = box.querySelector(".mitem");
    if (first) { try { first.focus({ preventScroll: true }); } catch (e) { /* ignore */ } }
  }

  document.addEventListener("click", function (ev) {
    if (menuOpen && !menuOpen.box.contains(ev.target) && !menuOpen.anchor.contains(ev.target)) closeMenu();
  });
  window.addEventListener("resize", closeMenu);
  // Close when the page really scrolls (the menu would float away from its button), not for scrolling inside the menu.
  window.addEventListener("scroll", function (ev) {
    if (!menuOpen || menuOpen.box.contains(ev.target)) return;
    if (Math.abs(menuOpen.anchor.getBoundingClientRect().top - menuOpen.top) > 4) closeMenu();
  }, true);

  function openCatMenu() {
    var items = ["All"].concat(content.cats).map(function (c) {
      var n = c === "All" ? content.items.length : content.items.filter(function (w) { return w.cat === c; }).length;
      return { label: (c === "All" ? T("All") : c) + " (" + n + ")", bold: c === cat, onClick: function () { cat = c; render(); } };
    });
    items.push({ label: T("New category…"), icon: "add", onClick: function () { openNewCategory(function (n) { cat = n; render(); }); } });
    items.push({ label: T("Edit categories"), icon: "edit", onClick: openCategories });
    openMenu($("catBtn"), items);
  }

  function openSortMenu() {
    openMenu($("sortBtn"), SORTS.map(function (s2) {
      return { label: T(s2[1]), bold: s2[0] === sortBy, onClick: function () { sortBy = s2[0]; lsSet("wl_sort_ed", sortBy); render(); } };
    }));
  }

  function renderPicker() {
    if (cat !== "All" && content.cats.indexOf(cat) < 0) cat = "All";
    $("catName").textContent = cat === "All" ? T("All") : cat;
    var sb = $("sortBtn");
    sb.classList.toggle("on", sortBy !== "new");
    $("sortName").textContent = SORT_SHORT[sortBy] ? T(SORT_SHORT[sortBy]) : "";
    sb.setAttribute("aria-label", T("Sort: {0}", T((SORTS.filter(function (s2) { return s2[0] === sortBy; })[0] || SORTS[0])[1])));
  }

  // ---------- side menu ----------

  function setDrawer(open) {
    document.body.classList.toggle("menu-open", open);
    var b = $("menuBtn");
    b.setAttribute("aria-expanded", open ? "true" : "false");
    b.setAttribute("aria-label", open ? T("Close menu") : T("Open menu"));
    if (open) closeMenu();
  }
  function drawerIsOpen() { return document.body.classList.contains("menu-open"); }
  // A button in the side menu: close the menu, then do the thing.
  function fromDrawer(fn) { return function () { setDrawer(false); fn(); }; }

  function renderViews() {
    var box = $("views");
    box.textContent = "";
    VIEWS.forEach(function (k) {
      var b = el("button", "vbtn" + (k === view ? " on" : ""));
      b.type = "button";
      b.title = T(VIEW_LABELS[k]);
      b.setAttribute("aria-label", T(VIEW_LABELS[k]));
      b.setAttribute("aria-pressed", k === view ? "true" : "false");
      b.appendChild(viewIcon(k));
      b.addEventListener("click", function () { view = k; lsSet("wl_view", k); render(); });
      box.appendChild(b);
    });
  }

  function isDrop(w) { return w.price !== null && w.old !== null && w.old > w.price; }
  // A price check found a higher price: shown with "↑ +12%" in red, the opposite of a drop.
  function isRise(w) { return w.price !== null && w.old !== null && w.old > 0 && w.price > w.old + 0.005; }
  function changeClass(w) { return isDrop(w) ? " drop" : isRise(w) ? " rise" : ""; }

  function pictureOf(w, cls) {
    var link = safeUrl(w.url);
    var img = safeUrl(w.image) || (w.image.indexOf("blob:") === 0 ? w.image : "");
    var pic = el(link ? "a" : "div", "pic" + (img ? "" : " empty"));
    if (link) { pic.href = link; pic.target = "_blank"; pic.rel = "noopener noreferrer"; }
    if (img) {
      var im = el("img");
      im.src = img; im.alt = w.title; im.loading = "lazy"; im.referrerPolicy = "no-referrer";
      im.addEventListener("error", function () { im.remove(); pic.classList.add("empty"); pic.appendChild(el("span", null, "🎁")); });
      pic.appendChild(im);
    } else {
      pic.appendChild(el("span", null, "🎁"));
    }
    return pic;
  }

  function card(w, compact) {
    var mine = res[w.id] === true, taken = res[w.id] === false;
    var c = el("div", "card" + (compact ? " compact" : "") + (taken ? " dim" : ""));
    c.setAttribute("data-id", String(w.id));
    var pic = pictureOf(w);
    var drop = isDrop(w), rise = isRise(w);
    if (drop) pic.appendChild(el("span", "badge drop", "↓ " + (compact ? Math.round((1 - w.price / w.old) * 100) + "%" : T("{0}% off", Math.round((1 - w.price / w.old) * 100)))));
    else if (rise) pic.appendChild(el("span", "badge drop rise", "↑ +" + Math.round((w.price / w.old - 1) * 100) + "%"));
    var bx = el("div", "badges");
    if (mine || taken) bx.appendChild(el("span", "badge status", resLabel(w)));
    if (bx.childNodes.length) pic.appendChild(bx);
    c.appendChild(pic);

    var body = el("div", "body");
    if (compact) {
      // Like the app's small card: title and price, everything else in the ⋮ menu.
      var crow = el("div", "crow");
      var ct = el("div", "ctext");
      ct.appendChild(el("h2", "title", w.title));
      var cp = el("div", "price" + changeClass(w), w.price !== null ? money(w.price, w.cur) : T("No price"));
      ct.appendChild(cp);
      crow.appendChild(ct);
      var more = iconBtn("more", T("More"), function () {
        openMenu(more, [
          { label: T("Edit"), onClick: function () { openWishForm(w); } },
          mine ? { label: T("Undo reservation"), onClick: function () { reserve(w, false); } }
            : taken ? null : { label: T("Reserve"), onClick: function () { reserve(w, true); } },
          { label: T("Delete"), onClick: function () { askDelete(w); } }
        ].filter(Boolean), true);
      });
      more.setAttribute("aria-haspopup", "menu");
      more.setAttribute("aria-expanded", "false");
      crow.appendChild(more);
      body.appendChild(crow);
      c.appendChild(body);
      return c;
    }
    var meta = [hostOf(w.url), w.cat].filter(Boolean).join(" · ");
    if (!compact && meta) body.appendChild(el("div", "meta", meta));
    body.appendChild(el("h2", "title", w.title));
    if (w.desc && !compact) body.appendChild(el("p", "desc", w.desc));
    if (w.size) body.appendChild(el("span", "sizetag", T("Size {0}", w.size.slice(0, 8))));
    if (w.note) body.appendChild(el("p", "wnote", T("Note: {0}", w.note)));
    if (w.by && !compact) body.appendChild(el("div", "meta", T("Added by {0}", w.by)));

    var row = el("div", "row");
    var pr = el("div", "price" + changeClass(w), w.price !== null ? money(w.price, w.cur) : T("No price"));
    if (drop || rise) pr.appendChild(el("span", "old", money(w.old, w.cur)));
    row.appendChild(pr);
    var act = el("div", "actions");
    act.appendChild(iconBtn("edit", T("Edit"), function () { openWishForm(w); }));
    if (mine) act.appendChild(textBtn(T("Undo"), "outline small", function () { reserve(w, false); }));
    else if (!taken) act.appendChild(textBtn(T("Reserve"), "small", function () { reserve(w, true); }));
    act.appendChild(iconBtn("del", T("Delete"), function () { askDelete(w); }));
    row.appendChild(act);
    body.appendChild(row);
    c.appendChild(body);
    return c;
  }

  function render() {
    var v = $("view");
    var head = $("head"), tools = $("toolbar");
    if (state === "gone") {
      head.hidden = true; tools.hidden = true; $("fab").hidden = true; $("menuBtn").hidden = true; setDrawer(false);
      v.textContent = "";
      var g = el("div", "inv");
      g.appendChild(el("h2", null, T("This invitation doesn’t work any more")));
      g.appendChild(el("p", null, T("The invite link may have been replaced by a new one, or the list was deleted. Ask the person who invited you for the new link.")));
      v.appendChild(g);
      return;
    }
    if (!content) {
      head.hidden = true; tools.hidden = true; $("fab").hidden = true; $("menuBtn").hidden = true; setDrawer(false);
      v.textContent = "";
      if (state === "offline") {
        var off = el("div", "center");
        off.appendChild(el("p", null, T("Couldn’t load the list. Check your connection.")));
        off.appendChild(textBtn(T("Try again"), "", function () { state = "loading"; render(); refresh(); }));
        v.appendChild(off);
      } else {
        v.appendChild(el("div", "center", T("Loading…")));
      }
      return;
    }
    head.hidden = false; tools.hidden = false; $("fab").hidden = false; $("menuBtn").hidden = false;
    $("name").textContent = content.name;
    $("dname").textContent = content.name;
    document.title = T("{0} · editing", content.name);
    var who = $("who");
    who.textContent = "";
    who.appendChild(document.createTextNode(me ? T("Editing as {0}", me) + " · " : T("Editing together") + " · "));
    var ch = el("button", "link", me ? T("change name") : T("add your name"));
    ch.type = "button";
    ch.addEventListener("click", fromDrawer(openSettings));
    who.appendChild(ch);
    var ab = $("about");
    ab.textContent = content.about || "";
    ab.hidden = !content.about;
    renderPicker();
    renderViews();
    var shown = visibleItems();
    v.textContent = "";
    if (!shown.length) {
      var empty = el("div", "center");
      if (!content.items.length) {
        empty.appendChild(el("p", null, T("No wishes yet.")));
        empty.appendChild(textBtn(T("Add the first wish"), "", function () { openWishForm(null); }));
      } else {
        empty.appendChild(el("p", null, T("No wishes match.")));
        if (query) empty.appendChild(textBtn(T("Clear search"), "outline", function () { query = ""; $("search").value = ""; $("searchbar").hidden = true; render(); }));
      }
      v.appendChild(empty);
    } else {
      var grid = el("div", "grid" + (view === "grid" ? " two" : ""));
      shown.forEach(function (w) { grid.appendChild(card(w, view === "grid")); });
      v.appendChild(grid);
    }
  }

  // ---------- dialogs ----------

  var dialogs = [];

  function updateScrollLock() {
    document.body.style.overflow = dialogs.length ? "hidden" : "";
  }

  // spec: { title, body(container, api), actions: [{label, cls, onClick(api) -> false keeps it open, id}], cancelable }
  function dialog(spec) {
    var back = el("div", "backdrop dlg-back");
    var d = el("div", "dlg");
    d.setAttribute("role", "dialog");
    d.setAttribute("aria-modal", "true");
    var h = el("h2", null, spec.title);
    h.id = "dlgtitle" + (dialogs.length + 1);
    d.setAttribute("aria-labelledby", h.id);
    d.appendChild(h);
    var body = el("div", "dbody");
    d.appendChild(body);
    var foot = el("div", "dfoot");
    d.appendChild(foot);
    back.appendChild(d);
    var api = {
      root: d, body: body, foot: foot, buttons: {},
      close: function () {
        var i = dialogs.indexOf(entry);
        if (i >= 0) dialogs.splice(i, 1);
        if (back.parentNode) back.parentNode.removeChild(back);
        updateScrollLock();
        if (spec.onClose) spec.onClose();
      }
    };
    var entry = { api: api, spec: spec };
    if (spec.body) spec.body(body, api);
    (spec.actions || []).forEach(function (a) {
      var b = el("button", "act" + (a.cls ? " " + a.cls : ""), a.label);
      b.type = "button";
      if (a.id) api.buttons[a.id] = b;
      b.addEventListener("click", function () {
        var keep = a.onClick ? a.onClick(api) : undefined;
        if (keep !== false) api.close();
      });
      foot.appendChild(b);
    });
    dialogs.push(entry);
    document.body.appendChild(back);
    updateScrollLock();
    var first = body.querySelector("input, textarea, select");
    if (first && spec.focus !== false) { try { first.focus(); } catch (e) { /* ignore */ } }
    return api;
  }

  document.addEventListener("keydown", function (e) {
    if (e.key !== "Escape") return;
    if (menuOpen) { closeMenu(); return; }
    if (!dialogs.length && drawerIsOpen()) { setDrawer(false); return; }
    if (dialogs.length) {
      var top = dialogs[dialogs.length - 1];
      if (top.spec.cancelable !== false) top.api.close();
    }
  });

  var fieldCount = 0;

  // A label and its field. The label is tied to the field, so screen readers (and tests) find it by name.
  function field(label, input, hint) {
    var f = el("div", "fld");
    var id = "fld" + (++fieldCount);
    input.id = id;
    var l = el("label", "flab", label);
    l.setAttribute("for", id);
    f.appendChild(l);
    f.appendChild(input);
    if (hint) f.appendChild(el("span", "hint", hint));
    return f;
  }

  function textInput(value, ph, max) {
    var i = el("input");
    i.type = "text";
    i.value = value || "";
    if (ph) i.placeholder = ph;
    if (max) i.maxLength = max;
    return i;
  }

  function currencyLabel(c) {
    var sym = "";
    try { sym = new Intl.NumberFormat("en", { style: "currency", currency: c, currencyDisplay: "narrowSymbol" }).format(0).replace(/[0-9.,\s]/g, ""); } catch (e) { /* ignore */ }
    return sym && sym !== c ? c + " (" + sym + ")" : c;
  }

  // ---- the name and a category ----

  function openName(first) {
    var inp;
    dialog({
      title: first ? T("Welcome! What’s your name?") : T("Your name"),
      cancelable: !first,
      body: function (b) {
        if (first) b.appendChild(el("p", null, T("You’re invited to edit “{0}” together. Your name is shown next to the wishes you add.", content.name)));
        inp = textInput(me, T("Your name"), 30);
        b.appendChild(field(T("Your name"), inp));
      },
      actions: [
        first ? { label: T("Skip"), cls: "outline", onClick: function () { lsSet("wl_asked", "1"); } } : { label: T("Cancel"), cls: "outline" },
        { label: T("Save"), onClick: function () { me = inp.value.trim().slice(0, 30); lsSet("wl_name", me); lsSet("wl_asked", "1"); render(); } }
      ]
    });
  }

  function openNewCategory(onAdded) {
    var inp, err;
    dialog({
      title: T("New category"),
      body: function (b) {
        inp = textInput("", T("For example Books"), 40);
        b.appendChild(field(T("Name"), inp));
        err = el("p", "err");
        err.hidden = true;
        b.appendChild(err);
      },
      actions: [
        { label: T("Cancel"), cls: "outline" },
        {
          label: T("Add"),
          onClick: function () {
            var n = addCat(inp.value);
            if (n === null) { err.textContent = T("Pick another name."); err.hidden = false; return false; }
            if (onAdded) onAdded(n);
          }
        }
      ]
    });
  }

  function openRenameCategory(name, done) {
    var inp, err;
    dialog({
      title: T("Rename category"),
      body: function (b) {
        inp = textInput(name, "", 40);
        b.appendChild(field(T("Name"), inp));
        err = el("p", "err");
        err.hidden = true;
        b.appendChild(err);
      },
      actions: [
        { label: T("Cancel"), cls: "outline" },
        {
          label: T("Rename"),
          onClick: function () {
            if (!renameCat(name, inp.value)) { err.textContent = T("That name is empty or already used."); err.hidden = false; return false; }
            if (done) done();
          }
        }
      ]
    });
  }

  function openCategories() {
    var list;
    var api = dialog({
      title: T("Categories"),
      body: function (b) {
        list = el("div", "catlist");
        b.appendChild(list);
      },
      actions: [
        { label: T("New category"), cls: "outline", onClick: function () { openNewCategory(function () { setTimeout(fill, 0); }); return false; } },
        { label: T("Done") }
      ]
    });
    function fill() {
      list.textContent = "";
      if (!content.cats.length) list.appendChild(el("p", "hint", T("No categories yet.")));
      content.cats.forEach(function (c) {
        var r = el("div", "catrow");
        r.appendChild(el("span", "cname", c));
        r.appendChild(iconBtn("edit", T("Rename {0}", c), function () { openRenameCategory(c, function () { setTimeout(fill, 0); }); }));
        r.appendChild(iconBtn("del", T("Delete {0}", c), function () {
          dialog({
            title: T("Delete category?"),
            body: function (b2) { b2.appendChild(el("p", null, T("“{0}” is removed. The wishes in it stay, without a category.", c))); },
            actions: [{ label: T("Cancel"), cls: "outline" }, { label: T("Delete"), cls: "danger", onClick: function () { removeCat(c); setTimeout(fill, 0); } }]
          });
        }));
        list.appendChild(r);
      });
    }
    fill();
    api.fill = fill;
  }

  // ---- settings, share ----

  function openSettings() {
    var inp, sw;
    dialog({
      title: T("Settings"),
      body: function (b) {
        inp = textInput(me, T("Your name"), 30);
        b.appendChild(field(T("Your name"), inp, T("Shown next to the wishes you add.")));
        var row = el("label", "swrow");
        sw = el("input");
        sw.type = "checkbox";
        sw.checked = instant;
        row.appendChild(sw);
        var txt = el("span", "swtext");
        txt.appendChild(el("strong", null, T("Instant delete")));
        txt.appendChild(el("span", "hint", T("Delete without asking first. You can still undo for 10 seconds.")));
        row.appendChild(txt);
        b.appendChild(row);
      },
      actions: [
        { label: T("Cancel"), cls: "outline" },
        {
          label: T("Save"),
          onClick: function () {
            me = inp.value.trim().slice(0, 30);
            lsSet("wl_name", me);
            lsSet("wl_asked", "1");
            instant = sw.checked;
            lsSet("wl_instant", instant ? "1" : "0");
            render();
          }
        }
      ]
    });
  }

  // ---------- sharing with a picture that carries the list's name ----------
  // A link preview can't show the name (WhatsApp and the like read a fixed page), so the picture is drawn here and
  // shared with a short message and the link. Same picture and words as ShareCard.kt in the app: change both together.

  var CARD_ICONS = {
    invite: "M16 11c1.66 0 2.99-1.34 2.99-3S17.66 5 16 5c-1.66 0-3 1.34-3 3s1.34 3 3 3zm-8 0c1.66 0 2.99-1.34 2.99-3S9.66 5 8 5C6.34 5 5 6.34 5 8s1.34 3 3 3zm0 2c-2.33 0-7 1.17-7 3.5V19h14v-2.5c0-2.33-4.67-3.5-7-3.5zm8 0c-.29 0-.62.02-.97.05 1.16.84 1.97 1.97 1.97 3.45V19h6v-2.5c0-2.33-4.67-3.5-7-3.5z",
    friends: "M20 6h-2.18c.11-.31.18-.65.18-1 0-1.66-1.34-3-3-3-1.05 0-1.96.54-2.5 1.35l-.5.67-.5-.68C10.96 2.54 10.05 2 9 2 7.34 2 6 3.34 6 5c0 .35.07.69.18 1H4c-1.11 0-1.99.89-1.99 2L2 19c0 1.11.89 2 2 2h16c1.11 0 2-.89 2-2V8c0-1.11-.89-2-2-2zm-5-2c.55 0 1 .45 1 1s-.45 1-1 1-1-.45-1-1 .45-1 1-1zM9 4c.55 0 1 .45 1 1s-.45 1-1 1-1-.45-1-1 .45-1 1-1zm11 15H4v-2h16v2zm0-5H4V8h5.08L7 10.83 8.62 12 11 8.76l1-1.36 1 1.36L15.38 12 17 10.83 14.92 8H20v6z"
  };

  // "Join Jakim's B'day wishlist". A name that already ends in "wishlist" doesn't get it twice.
  function cardHeadline(kind, name) {
    var n = String(name || "").trim().replace(/\s*(wish\s?list|verlanglijst(je)?)$/i, "").trim();
    if (kind === "invite") return n ? T("Join {0} wishlist", n) : T("Join our wishlist");
    return n ? T("Check out {0} wishlist", n) : T("Check out this wishlist");
  }

  function wrapLines(g, text, width) {
    var words = text.split(/\s+/), lines = [], line = "";
    words.forEach(function (w) {
      var t = line ? line + " " + w : w;
      if (line && g.measureText(t).width > width) { lines.push(line); line = w; } else line = t;
    });
    if (line) lines.push(line);
    return lines;
  }

  // The picture (1200 x 630, like the link previews) as a PNG file.
  async function cardFile(kind, name) {
    try { if (document.fonts) await document.fonts.load('400 92px "Google Sans"'); } catch (e) { /* the fallback font is fine */ }
    var cv = document.createElement("canvas");
    cv.width = 1200; cv.height = 630;
    var g = cv.getContext("2d");
    g.fillStyle = "#4355B9"; g.fillRect(0, 0, 1200, 630);
    g.save(); g.translate(120, 165); g.scale(12.5, 12.5); g.fillStyle = "#ffffff"; g.fill(new Path2D(CARD_ICONS[kind])); g.restore();
    var font = '"Google Sans", system-ui, sans-serif', W = 600, size = 92, lines;
    for (;;) { // long names get a smaller font, up to three lines
      g.font = "400 " + size + "px " + font;
      lines = wrapLines(g, cardHeadline(kind, name), W);
      if (lines.length <= 3 || size <= 48) break;
      size -= 4;
    }
    var sub = kind === "invite" ? T("Add gift ideas together and see what’s already reserved.") : T("Reserve a gift so nobody gives the same gift twice.");
    g.font = "400 31px " + font;
    var subLines = wrapLines(g, sub, W);
    var lh = size * 1.1, sh = 31 * 1.4, total = lines.length * lh + 22 + subLines.length * sh;
    var y = Math.max(24, (630 - total) / 2);
    g.textBaseline = "top";
    g.fillStyle = "#ffffff"; g.font = "400 " + size + "px " + font;
    lines.forEach(function (l, i) { g.fillText(l, 516, y + i * lh); });
    g.fillStyle = "#e4e6ff"; g.font = "400 31px " + font;
    subLines.forEach(function (l, i) { g.fillText(l, 516, y + lines.length * lh + 22 + i * sh); });
    var blob = await new Promise(function (r) { cv.toBlob(r, "image/png"); });
    return new File([blob], "wishlist.png", { type: "image/png" });
  }

  function pageBase() { return location.origin + location.pathname.replace(/[^\/]*$/, ""); }

  function openShare() {
    // Through join/ and list/: those pages only exist for their own link previews, then open the normal pages.
    var inviteLink = pageBase() + "join/?j=" + key;
    var friendLink = pageBase() + "list/?c=" + code;
    // The pictures are drawn as soon as the window opens: sharing must start straight from the tap.
    var cards = {};
    ["invite", "friends"].forEach(function (k) {
      cardFile(k, content.name).then(function (f) { cards[k] = f; }, function () { /* share the link alone */ });
    });
    dialog({
      title: T("Share this list"),
      body: function (b) {
        function block(title, hint, link, kind) {
          var s = el("div", "shareblock");
          s.appendChild(el("strong", null, title));
          s.appendChild(el("span", "hint", hint));
          var i = el("input");
          i.type = "text"; i.readOnly = true; i.value = link;
          i.addEventListener("focus", function () { i.select(); });
          s.appendChild(i);
          var r = el("div", "btnrow");
          var cp = textBtn(T("Copy link"), "outline small", function () { copyText(link); cp.textContent = T("Copied ✓"); });
          r.appendChild(cp);
          if (navigator.share) {
            r.appendChild(textBtn(T("Share…"), "small", function () {
              var words = cardHeadline(kind, content.name) + " 🎁";
              var f = cards[kind];
              try {
                if (f && navigator.canShare && navigator.canShare({ files: [f] })) {
                  navigator.share({ files: [f], text: words + "\n" + link }).catch(function () { /* cancelled */ });
                } else {
                  navigator.share({ title: words, text: words, url: link }).catch(function () { /* cancelled */ });
                }
              } catch (e) { /* ignore */ }
            }));
          }
          s.appendChild(r);
          b.appendChild(s);
        }
        block(T("Invite link"), T("Whoever opens it can add and change wishes, like you. Only send it to people you trust."), inviteLink, "invite");
        block(T("Friends’ link"), T("For givers who don’t need to edit: they can look at the list and reserve gifts, nothing else. Everyone on this list sees what’s reserved."), friendLink, "friends");
      },
      actions: [{ label: T("Done") }]
    });
  }

  // ---- the add / edit form ----

  function openWishForm(edit, initialUrl) {
    var f = {
      url: edit ? edit.url : "",
      title: edit ? edit.title : "",
      desc: edit ? edit.desc : "",
      image: edit ? edit.image : "",
      price: edit && edit.price !== null ? String(edit.price) : "",
      cur: edit ? edit.cur : (lsGet("wl_cur") || mostUsedCurrency()),
      cat: edit ? edit.cat : "",
      note: edit ? edit.note : "",
      size: edit ? edit.size : ""
    };
    var picked = ""; // a picture chosen in this form and not saved yet (a blob URL)
    var busy = false;
    var els = {};
    var api;

    function dropPicked() {
      if (picked) { delete blobs[picked]; try { URL.revokeObjectURL(picked); } catch (e) { /* ignore */ } picked = ""; }
    }

    function setErr(msg) { els.err.textContent = msg || ""; els.err.hidden = !msg; }

    function showPicture() {
      els.pic.textContent = "";
      var s = safeUrl(f.image) || (f.image.indexOf("blob:") === 0 ? f.image : "");
      if (s) {
        var im = el("img");
        im.src = s; im.alt = T("Picture of the wish"); im.referrerPolicy = "no-referrer";
        im.addEventListener("error", function () { im.remove(); els.pic.appendChild(el("span", "hint", T("This picture can’t be shown."))); });
        els.pic.appendChild(im);
      } else {
        els.pic.appendChild(el("span", "hint", T("No picture")));
      }
      els.rm.hidden = !f.image;
    }

    async function go(raw) {
      var u = P.cleanUrl(raw);
      if (u === null) { setErr(T("Enter a full product link.")); return; }
      f.url = u; els.url.value = u;
      busy = true; setErr(""); els.fetchBtn.disabled = true; els.fetchBtn.textContent = T("Reading…");
      var m = null, failed = false;
      try {
        m = P.readHelperAnswer(await loadShopPage(u));
      } catch (e) {
        failed = true;
      }
      busy = false; els.fetchBtn.disabled = false; els.fetchBtn.textContent = T("Fetch details");
      if (failed || m === null) {
        setErr(T("Couldn’t read that page. Fill in the details yourself."));
      } else if (m.blocked) {
        setErr(T("The store blocked the automatic read. Enter the title and price yourself."));
      } else {
        f.title = m.title; els.title.value = m.title;
        f.desc = m.desc; els.desc.value = m.desc;
        setCurrency(m.currency);
        // A picture you chose yourself is kept. Otherwise the picture comes from the link.
        if (!picked) {
          if (m.image || !edit) f.image = m.image;
          showPicture();
        }
        if (m.price !== null || !edit) {
          f.price = m.price !== null ? m.price.toFixed(2) : "";
          els.price.value = f.price;
        }
        if (m.price === null) setErr(T("No price found on that page. You can type it in."));
      }
    }

    function setCurrency(c) {
      if (CURRENCIES.indexOf(c) < 0) {
        var o = el("option", null, currencyLabel(c));
        o.value = c;
        els.cur.appendChild(o);
      }
      els.cur.value = c;
      f.cur = c;
    }

    function drawCats() {
      els.cats.textContent = "";
      var nb = el("button", "chip", "＋ New");
      nb.type = "button";
      nb.addEventListener("click", function () { openNewCategory(function (n) { f.cat = n; setTimeout(drawCats, 0); }); });
      els.cats.appendChild(nb);
      // The category may not be online yet for a moment: show the new one right away from the content.
      content.cats.forEach(function (c) {
        var b = el("button", "chip" + (c === f.cat ? " on" : ""), c);
        b.type = "button";
        b.addEventListener("click", function () { f.cat = (f.cat === c ? "" : c); drawCats(); });
        els.cats.appendChild(b);
      });
      if (f.cat && content.cats.indexOf(f.cat) < 0) f.cat = "";
    }

    function save() {
      var title = f.title.trim();
      if (!title) { setErr(T("Give the wish a title.")); els.title.focus(); return false; }
      var priceText = f.price.trim().replace(",", ".");
      var p = priceText === "" ? null : Number(priceText);
      if (p !== null && !isFinite(p)) p = null;
      var u = P.cleanUrl(f.url) || f.url.trim();
      lsSet("wl_cur", f.cur);
      var mine;
      if (!edit) {
        mine = {
          id: newId(), url: u, title: title, desc: f.desc, image: f.image, price: p, old: null, cur: f.cur,
          cat: f.cat, got: false, star: false, note: f.note.trim(), size: f.size, by: me
        };
      } else {
        mine = copyWish(edit, {
          url: u, title: title, desc: f.desc, image: f.image, price: p,
          old: p === edit.price ? edit.old : null, cur: f.cur, cat: f.cat, note: f.note.trim(), size: f.size
        });
      }
      if (picked && f.image === picked) picked = ""; // now owned by the save (uploaded there)
      else dropPicked();
      putWish(mine, edit || undefined);
      return true;
    }

    dialog({
      title: edit ? T("Edit wish") : T("Add a wish"),
      cancelable: true,
      focus: false,
      onClose: function () { dropPicked(); },
      body: function (b, a) {
        api = a;
        els.url = textInput(f.url, "https://…", 2000);
        els.url.type = "url";
        els.url.inputMode = "url";
        els.url.autocapitalize = "off";
        els.url.addEventListener("input", function () {
          var prev = f.url;
          f.url = els.url.value;
          var pasted = !edit && f.url.length - prev.length > 3 && /https?:\/\/\S+\.\S+/.test(f.url);
          if (pasted && !busy) go(f.url);
        });
        // Pasting a link over another one of the same length doesn't change the length: the paste itself counts too.
        els.url.addEventListener("paste", function () {
          setTimeout(function () {
            f.url = els.url.value;
            if (!edit && !busy && /https?:\/\/\S+\.\S+/.test(f.url)) go(f.url);
          }, 0);
        });
        b.appendChild(field(T("Product link (optional)"), els.url));
        els.fetchBtn = textBtn(T("Fetch details"), "outline small", function () { if (!busy) go(f.url); });
        b.appendChild(els.fetchBtn);
        els.err = el("p", "err");
        els.err.hidden = true;
        els.err.setAttribute("role", "status");
        b.appendChild(els.err);

        // picture
        var pf = el("div", "fld");
        pf.appendChild(el("span", "flab", T("Picture")));
        els.pic = el("div", "picbox");
        pf.appendChild(els.pic);
        var pr = el("div", "btnrow");
        var file = el("input");
        file.type = "file"; file.accept = "image/*"; file.hidden = true;
        file.addEventListener("change", async function () {
          var fl = file.files && file.files[0];
          file.value = "";
          if (!fl) return;
          try {
            var blob = await shrinkPicture(fl);
            dropPicked();
            picked = URL.createObjectURL(blob);
            blobs[picked] = blob;
            f.image = picked;
            setErr("");
            showPicture();
          } catch (e) {
            setErr(T("Couldn’t use that picture."));
          }
        });
        pr.appendChild(file);
        els.choose = textBtn(T("Choose picture"), "outline small", function () { file.click(); });
        pr.appendChild(els.choose);
        els.rm = textBtn(T("Remove"), "outline small", function () { dropPicked(); f.image = ""; showPicture(); });
        pr.appendChild(els.rm);
        pf.appendChild(pr);
        b.appendChild(pf);
        showPicture();

        els.title = textInput(f.title, "", 120);
        els.title.addEventListener("input", function () { f.title = els.title.value; });
        b.appendChild(field(T("Title"), els.title));
        els.desc = el("textarea");
        els.desc.rows = 2; els.desc.maxLength = 300; els.desc.value = f.desc;
        els.desc.addEventListener("input", function () { f.desc = els.desc.value; });
        b.appendChild(field(T("Description"), els.desc));

        var pc = el("div", "row2");
        els.price = el("input");
        els.price.type = "text"; els.price.inputMode = "decimal"; els.price.value = f.price; els.price.placeholder = "0.00";
        els.price.addEventListener("input", function () { f.price = els.price.value; });
        pc.appendChild(field(T("Price"), els.price));
        els.cur = el("select");
        CURRENCIES.forEach(function (c) { var o = el("option", null, currencyLabel(c)); o.value = c; els.cur.appendChild(o); });
        els.cur.addEventListener("change", function () { f.cur = els.cur.value; });
        pc.appendChild(field(T("Currency"), els.cur));
        b.appendChild(pc);
        setCurrency(f.cur);

        els.size = el("select");
        var none = el("option", null, T("No size")); none.value = "";
        els.size.appendChild(none);
        SIZES.forEach(function (s) { var o = el("option", null, s); o.value = s; els.size.appendChild(o); });
        els.size.value = f.size;
        els.size.addEventListener("change", function () { f.size = els.size.value; });
        b.appendChild(field(T("Size"), els.size));

        els.note = el("textarea");
        els.note.rows = 2; els.note.maxLength = 300; els.note.value = f.note;
        els.note.addEventListener("input", function () { f.note = els.note.value; });
        b.appendChild(field(T("Note for givers (colour, details, …)"), els.note));

        var cf = el("div", "fld");
        cf.appendChild(el("span", "flab", T("Category")));
        els.cats = el("div", "chips wrap");
        cf.appendChild(els.cats);
        var ec = el("button", "link", T("Edit categories"));
        ec.type = "button";
        ec.addEventListener("click", function () { openCategories(); });
        cf.appendChild(ec);
        b.appendChild(cf);
        drawCats();
        showPicture();
      },
      actions: [
        { label: T("Cancel"), cls: "outline" },
        { label: T("Save"), id: "save", onClick: function () { return save(); } }
      ]
    });
    if (!edit && initialUrl) { f.url = initialUrl; els.url.value = initialUrl; go(initialUrl); }
  }

  function mostUsedCurrency() {
    var count = {}, best = "USD", n = 0;
    (content ? content.items : []).forEach(function (w) {
      count[w.cur] = (count[w.cur] || 0) + 1;
      if (count[w.cur] > n) { n = count[w.cur]; best = w.cur; }
    });
    return best;
  }

  // A picture chosen on this device, shrunk to at most 600 pixels (about 50 KB) and made a JPEG, as the app does.
  function shrinkPicture(file) {
    return new Promise(function (resolve, reject) {
      var url = URL.createObjectURL(file);
      var img = new Image();
      img.onload = function () {
        try {
          var w = img.naturalWidth, h = img.naturalHeight;
          if (!w || !h) throw new Error("empty");
          var k = Math.min(1, PHOTO_SIDE / Math.max(w, h));
          var cw = Math.max(1, Math.round(w * k)), ch = Math.max(1, Math.round(h * k));
          var cv = document.createElement("canvas");
          cv.width = cw; cv.height = ch;
          var cx = cv.getContext("2d");
          // JPEG has no transparency: put see-through pictures on white
          cx.fillStyle = "#fff";
          cx.fillRect(0, 0, cw, ch);
          cx.drawImage(img, 0, 0, cw, ch);
          URL.revokeObjectURL(url);
          cv.toBlob(function (blob) { blob ? resolve(blob) : reject(new Error("toBlob")); }, "image/jpeg", 0.82);
        } catch (e) {
          URL.revokeObjectURL(url);
          reject(e);
        }
      };
      img.onerror = function () { URL.revokeObjectURL(url); reject(new Error("load")); };
      img.src = url;
    });
  }

  // ---------- start ----------

  function readKey() {
    var raw = new URLSearchParams(location.search).get("j");
    if (raw === null) return null;
    var k = String(raw).toUpperCase().replace(/[^A-Z0-9]/g, "");
    return INVITE_RE.test(k) ? k : "";
  }

  function wire() {
    $("drawer").appendChild(langSwitch()); // English · Nederlands
    $("drawer").appendChild(themeSwitch()); // System · Light · Dark
    $("search").addEventListener("input", function () { query = this.value; if (content) render(); });
    function closeSearch() { $("searchbar").hidden = true; if (query) { query = ""; $("search").value = ""; render(); } }
    $("searchBtn").addEventListener("click", function () {
      if (!$("searchbar").hidden) { closeSearch(); return; }
      $("searchbar").hidden = false;
      $("search").focus();
    });
    $("searchClose").addEventListener("click", closeSearch);
    // The ⚙ in the top bar: the settings of this list. (Refreshing: the list refreshes by itself, or pull down to reload.)
    $("listBtn").addEventListener("click", function () {
      if (!content) return;
      openMenu($("listBtn"), [
        { label: T("Rename list"), icon: "edit", onClick: openRename },
        { label: T("Note for friends"), onClick: openAbout },
        { label: T("Categories"), onClick: openCategories },
        { label: T("Share list"), icon: "share", onClick: openShare }
      ], true);
    });
    $("catBtn").addEventListener("click", function () { if (content) openCatMenu(); });
    $("sortBtn").addEventListener("click", function () { if (content) openSortMenu(); });
    $("menuBtn").addEventListener("click", function () { setDrawer(!drawerIsOpen()); });
    $("scrim").addEventListener("click", function () { setDrawer(false); });

    $("fab").addEventListener("click", function () { openWishForm(null); });
    function openRename() {
      var inp;
      dialog({
        title: T("Name of the wishlist"),
        body: function (b) { inp = textInput(content.name, "", 60); b.appendChild(field(T("Name"), inp)); },
        actions: [{ label: T("Cancel"), cls: "outline" }, { label: T("Save"), onClick: function () { setName(inp.value); } }]
      });
    }
    function openAbout() {
      var ta, cnt;
      dialog({
        title: T("Note for friends"),
        body: function (b) {
          ta = el("textarea");
          ta.rows = 5; ta.maxLength = ABOUT_MAX; ta.value = content.about;
          b.appendChild(field(T("Shown at the top of the friends’ page"), ta));
          cnt = el("p", "hint", ta.value.length + "/" + ABOUT_MAX);
          ta.addEventListener("input", function () { cnt.textContent = ta.value.length + "/" + ABOUT_MAX; });
          b.appendChild(cnt);
        },
        actions: [{ label: T("Cancel"), cls: "outline" }, { label: T("Save"), onClick: function () { setAbout(ta.value); } }]
      });
    }
    $("shareBtn").addEventListener("click", openShare);
    $("meBtn").addEventListener("click", fromDrawer(openSettings));
    document.addEventListener("visibilitychange", function () { if (!document.hidden) refresh(); });
    window.addEventListener("online", refresh);
    setInterval(function () { if (!document.hidden) refresh(); }, REFRESH_MS);
  }

  function showIosTip() {
    var ua = navigator.userAgent || "";
    var ios = /iPhone|iPad|iPod/.test(ua);
    if (!ios || navigator.standalone || lsGet("wl_tip") === "1") return;
    var t = $("tip");
    t.textContent = "";
    t.appendChild(el("span", null, T("Tip: tap the Share button in Safari, then “Add to Home Screen”, to open this list like an app.")));
    var x = el("button", "link", "OK");
    x.type = "button";
    x.addEventListener("click", function () { t.hidden = true; lsSet("wl_tip", "1"); });
    t.appendChild(x);
    t.hidden = false;
  }

  function start() {
    key = readKey();
    if (!key) {
      state = "gone";
      render();
      return;
    }
    wire();
    var c = readCache();
    if (c) {
      code = c.code || "";
      content = c.content;
      content.items = (content.items || []).map(normWish);
      state = "ok";
    }
    render();
    refresh().then(function () {
      if (content && state === "ok") {
        if (!lsGet("wl_asked") && !me) openName(true);
        showIosTip();
      }
    });
  }

  // A few things the tests need to look at.
  window.__wl = {
    content: function () { return content; },
    pending: function () { return pending; },
    refresh: refresh,
    state: function () { return state; },
    cardHeadline: cardHeadline,
    cardFile: cardFile
  };

  start();
})();
