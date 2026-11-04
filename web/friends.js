// The friends' page and the invite page (web/index.html). A file of its own so the page only runs scripts from this site.
"use strict";
var SUPABASE_URL = "https://aspnmvmsvwbezdltykgx.supabase.co";
var SUPABASE_KEY = "sb_publishable_2RpbWlDJBB3yV2TO-h68xA_i-gXfd_t";

var CODE_RE = /^[A-HJ-NP-Z2-9]{8}$/;

// The newest app. GitHub redirects this address straight to the file, so the download starts at once.
// The release (tag "latest") is remade after every build by the step "Publish APK to Releases"
// in .github/workflows/build.yml.
var APK_URL = "https://github.com/AndroidEnthusiast/Wishlist/releases/download/latest/Wishlist.apk";
var $ = function (id) { return document.getElementById(id); };

var code = null, list = null, res = {}, cat = "All", busy = false, state = "loading", memDevice = null;

// How the list is laid out for this visitor: "list" (large cards) or "grid" (two per row).
var VIEWS = ["list", "grid"];
var VIEW_LABELS = { list: T("Large cards"), grid: T("Two per row") };
var view = loadView();

// The friends' link of a shared wishlist goes through list/, which adds s=1. Such a list is put together by several
// givers for someone else: there is no "most wanted" (nobody can say that for the person), and the givers see what's reserved.
var SHARED_LIST = new URLSearchParams(location.search).get("s") === "1";
var DEFAULT_SORT = SHARED_LIST ? "asis" : "star";

// Sorting and filtering, only for this visitor. The sort is remembered, the filters are not.
var SORTS = SHARED_LIST ? ["asis", "low", "high"] : ["star", "asis", "low", "high"];
var sortBy = loadSort(), maxPrice = null, onlyAvail = false, toolsOpen = false;

function loadSort() {
  try {
    var v = localStorage.getItem("wl_sort");
    if (SORTS.indexOf(v) >= 0) return v;
  } catch (e) { }
  return DEFAULT_SORT;
}

function saveSort(v) {
  try { localStorage.setItem("wl_sort", v); } catch (e) { }
}

function loadView() {
  try {
    var v = localStorage.getItem("wl_view");
    if (VIEWS.indexOf(v) >= 0) return v;
  } catch (e) { }
  return "grid"; // two per row by default (owner, 2.100); the large cards are the "details" view
}

function saveView(v) {
  try { localStorage.setItem("wl_view", v); } catch (e) { }
}

// Gifts reserved in this browser on this list (id -> title). If one is taken off the list later (deleted, or made a
// private wish), the next visit says so and suggests another gift. The owner never learns it was reserved.
function myResKey() { return "wl_res_" + code; }
function readMyRes() {
  try { var o = JSON.parse(localStorage.getItem(myResKey()) || "{}"); return o && typeof o === "object" ? o : {}; } catch (e) { return {}; }
}
function writeMyRes(o) {
  try { localStorage.setItem(myResKey(), JSON.stringify(o)); } catch (e) { }
}
function noteGone(titles, owned) {
  var g = $("gone");
  g.textContent = "";
  titles.forEach(function (t) {
    g.appendChild(el("span", null, T("“{0}” is no longer on the list. Have a look for another gift.", t || T("Wish"))));
    g.appendChild(document.createTextNode(" "));
  });
  (owned || []).forEach(function (t) {
    g.appendChild(el("span", null, T("“{0}” is marked as owned: they already have it. Have a look for another gift.", t || T("Wish"))));
    g.appendChild(document.createTextNode(" "));
  });
  var ok = el("button", "link", "OK");
  ok.type = "button";
  ok.addEventListener("click", function () { g.hidden = true; });
  g.appendChild(ok);
  g.hidden = false;
}

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

function parseCode(s) {
  s = String(s || "").toUpperCase();
  var m = /WL-([A-Z0-9]{8})/.exec(s);
  var c = m ? m[1] : s.replace(/[^A-Z0-9]/g, "");
  if (c.indexOf("WL") === 0 && c.length === 10) c = c.slice(2);
  return CODE_RE.test(c) ? c : null;
}

// An invitation link (?j=KEY) is for editing a shared wishlist together. The key is 20 safe letters and digits.
var INVITE_RE = /^[A-HJ-NP-Z2-9]{20}$/;

// null: this is not an invitation link. "": it is one, but the key is not valid.
function readInvite() {
  var raw = new URLSearchParams(location.search).get("j");
  if (raw === null) return null;
  var k = String(raw).toUpperCase().replace(/[^A-Z0-9]/g, "");
  return INVITE_RE.test(k) ? k : "";
}

function readCode() {
  var q = new URLSearchParams(location.search);
  var raw = q.get("c") || q.get("code") || location.hash.replace(/^#/, "");
  return parseCode(raw);
}

async function rpc(fn, body) {
  var h = { "Content-Type": "application/json", "apikey": SUPABASE_KEY };
  if (SUPABASE_KEY.indexOf("eyJ") === 0) h["Authorization"] = "Bearer " + SUPABASE_KEY;
  var r = await fetch(SUPABASE_URL + "/rest/v1/rpc/" + fn, { method: "POST", headers: h, body: JSON.stringify(body) });
  var t = await r.text();
  if (!r.ok) throw new Error("Server said " + r.status);
  return t ? JSON.parse(t) : null;
}

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

function el(tag, cls, text) {
  var e = document.createElement(tag);
  if (cls) e.className = cls;
  if (text !== undefined && text !== null) e.textContent = text;
  return e;
}

var toastTimer = null;
function toast(msg) {
  var t = $("toast");
  t.textContent = msg;
  t.className = "show";
  clearTimeout(toastTimer);
  toastTimer = setTimeout(function () { t.className = ""; }, 3500);
}

function showCodeForm(msg) {
  var v = $("view");
  v.textContent = "";
  var box = el("div", "center");
  box.appendChild(el("p", null, msg));
  var f = el("form", "code");
  var i = el("input");
  i.placeholder = T("Enter list code");
  i.setAttribute("aria-label", T("List code"));
  i.autocapitalize = "characters";
  var b = el("button", "act", T("Open"));
  b.type = "submit";
  f.appendChild(i); f.appendChild(b);
  f.addEventListener("submit", function (ev) {
    ev.preventDefault();
    var c = parseCode(i.value);
    if (!c) { toast(T("That doesn’t look like a list code.")); return; }
    location.search = "?c=" + c;
  });
  box.appendChild(f);
  v.appendChild(box);
  $("bar").hidden = true;
  $("chips").hidden = true;
  $("tools").hidden = true;
  $("about").hidden = true;
  $("cover").hidden = true;
  $("note").hidden = true;
}

// The page someone sees when they open an invitation link.
// The link to this invitation, the same one the app shares.
function inviteUrl(key) {
  return location.origin + location.pathname + "?j=" + key;
}

// The editor in the browser (edit.html) sits next to this page. Works on any phone or computer.
// The language and light/dark chosen here go along in the link, so the editor looks the same even when it opens in
// another browser (or later from an iPhone home-screen icon), which can't see what this page remembered.
function editorUrl(key) {
  return location.origin + location.pathname.replace(/[^\/]*$/, "") + "edit.html?j=" + key + (typeof prefsQuery === "function" ? prefsQuery() : "");
}

// Only Android phones can install the app. Everyone else gets only "Edit in the browser" on the invitation.
function isAndroid() {
  return /Android/i.test(navigator.userAgent || "");
}

// Puts text on the clipboard. Only works from a tap. Failing is fine: the download goes on anyway.
function copyText(text) {
  try {
    if (navigator.clipboard && navigator.clipboard.writeText) {
      navigator.clipboard.writeText(text).catch(function () { legacyCopy(text); });
      return;
    }
  } catch (e) { /* fall through */ }
  legacyCopy(text);
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

async function showInvite(key) {
  var v = $("view");
  ["bar", "chips", "tools", "about", "cover", "note"].forEach(function (id) { $(id).hidden = true; });
  $("name").textContent = T("Wishlist invitation");
  $("sub").textContent = "";
  v.textContent = "";
  v.appendChild(el("div", "center", T("Loading…")));
  var info = null;
  if (key) {
    try {
      info = await rpc("wl_sh_info", { p_key: key });
    } catch (e) {
      v.textContent = "";
      var off = el("div", "center");
      off.appendChild(el("p", null, T("Couldn’t check the invitation. Check your connection.")));
      var again = el("button", "act", T("Try again"));
      again.type = "button";
      again.addEventListener("click", function () { showInvite(key); });
      off.appendChild(again);
      v.appendChild(off);
      return;
    }
  }
  v.textContent = "";
  var box = el("div", "inv");
  if (!info) {
    box.appendChild(el("h2", null, T("This invitation doesn’t work")));
    box.appendChild(el("p", null, T("The link may be incomplete, or the person who sent it made a new one. Ask them for a new invite link.")));
    v.appendChild(box);
    return;
  }
  box.classList.add("invite"); // centred on the screen
  document.body.classList.add("inviting"); // one screen, no scrolling (see wishlist.css)
  var nm = String(info.name || "a wishlist");
  document.title = T("Invitation: {0}", nm);
  var head = el("h2", null, T("You’re invited to add gifts for:"));
  box.appendChild(head);
  // Same size as the heading, bolder. Also set here, so a browser with an old copy of wishlist.css shows it right.
  var ln = el("p", "listname", nm + " 🎁");
  ln.style.cssText = "margin:0 0 12px;font-size:1.25rem;line-height:1.3;font-weight:800;color:var(--fg);overflow-wrap:anywhere";
  box.appendChild(ln);
  box.appendChild(el("p", null, T("Accept the invitation to add gift ideas, reserve gifts so nobody buys the same one twice, and leave notes for the other givers.")));
  var n = Number(info.count) || 0;
  box.appendChild(el("p", null, n === 1 ? T("1 wish so far") : T("{0} wishes so far", n)));

  var btns = el("div", "btns");
  if (!isAndroid()) {
    // iPhone, iPad, computer: there is no app for you, only the editor in the browser. The page itself stays, so
    // it's clear what you are invited to.
    var only = el("a", "act", T("Edit in the browser"));
    only.href = editorUrl(key);
    // Light/dark can change on this page without a reload: take the latest choice along.
    only.addEventListener("click", function () { only.href = editorUrl(key); });
    btns.appendChild(only);
    box.appendChild(btns);
    v.appendChild(box);
    confetti(box, head);
    return;
  }
  var open = el("a", "act", T("Open in the app"));
  open.href = "wishlist://join?j=" + key;
  btns.appendChild(open);
  var dl = el("a", "act outline", T("Download app"));
  dl.href = APK_URL;
  dl.setAttribute("download", "Wishlist.apk");
  dl.rel = "noopener";
  // The app can't be told which list you came for through an install, so the invitation goes on the clipboard.
  // The app looks for it the first time it starts. The download itself carries on whatever happens here.
  dl.addEventListener("click", function () {
    copyText(inviteUrl(key));
    done.textContent = T("Invitation copied. Install the app and open it: it will offer to join “{0}”.", nm);
    done.hidden = false;
  });
  btns.appendChild(dl);
  var web = el("a", "act outline", T("Edit in the browser"));
  web.href = editorUrl(key);
  web.addEventListener("click", function () { web.href = editorUrl(key); });
  btns.appendChild(web);
  box.appendChild(btns);
  var done = el("p", "copied", "");
  done.hidden = true;
  done.setAttribute("role", "status");
  box.appendChild(done);

  v.appendChild(box);
  confetti(box, head);
}

// A short burst of colourful confetti above the invitation's heading. Skipped when the phone asks for less motion.
function confetti(box, head) {
  if (!window.requestAnimationFrame) return;
  try { if (window.matchMedia && matchMedia("(prefers-reduced-motion: reduce)").matches) return; } catch (e) { }
  var cv = document.createElement("canvas");
  var g = cv.getContext && cv.getContext("2d");
  if (!g) return;
  var dpr = window.devicePixelRatio || 1;
  // The whole screen, so the burst can fly up to the top of the screen without being cut off.
  var W = window.innerWidth, H = window.innerHeight;
  cv.className = "confetti";
  cv.setAttribute("aria-hidden", "true");
  // Placed on top of the page, never in it, so nothing moves (also with an old copy of wishlist.css in the browser).
  // Over the title bar, but behind the invitation's words (they sit above it, see below).
  cv.style.cssText = "position:fixed;left:0;top:0;pointer-events:none;z-index:7";
  var bar = document.querySelector(".topbar");
  if (bar) bar.style.zIndex = "0"; // nothing scrolls under it on the invitation
  for (var c = box.firstChild; c; c = c.nextSibling) { if (c.style) { c.style.position = "relative"; c.style.zIndex = "8"; } }
  cv.width = W * dpr; cv.height = H * dpr;
  cv.style.width = W + "px"; cv.style.height = H + "px";
  box.insertBefore(cv, box.firstChild);
  g.scale(dpr, dpr);
  var COLORS = ["#4355b9", "#ffc83d", "#ff6b8a", "#2ec4b6", "#8fa2ff", "#ff9f43"];
  var hr = head.getBoundingClientRect();
  var ox = hr.left + hr.width / 2, oy = hr.top - 4, bits = [];
  // Fast enough for the highest bits to reach the top part of the screen, then everything falls and fades.
  var top = Math.max(120, oy), reach = Math.sqrt(2 * 0.22 * top * 0.95);
  for (var i = 0; i < 110; i++) {
    var a = -Math.PI / 2 + (Math.random() - 0.5) * Math.PI * 0.9, sp = reach * (0.35 + Math.random() * 0.65);
    bits.push({ x: ox + (Math.random() - 0.5) * 60, y: oy, vx: Math.cos(a) * sp, vy: Math.sin(a) * sp,
      w: 5 + Math.random() * 5, h: 8 + Math.random() * 6, r: Math.random() * 6, vr: (Math.random() - 0.5) * 0.4,
      c: COLORS[i % COLORS.length] });
  }
  var start = null, LIFE = 2000;
  function frame(t) {
    if (start === null) start = t;
    var k = (t - start) / LIFE;
    g.clearRect(0, 0, W, H);
    if (k >= 1) { cv.remove(); return; }
    g.globalAlpha = k < 0.7 ? 1 : 1 - (k - 0.7) / 0.3;
    bits.forEach(function (b) {
      b.vy += 0.22; b.vx *= 0.985; b.x += b.vx; b.y += b.vy; b.r += b.vr;
      g.save(); g.translate(b.x, b.y); g.rotate(b.r); g.fillStyle = b.c;
      g.fillRect(-b.w / 2, -b.h / 2, b.w, b.h * Math.abs(Math.cos(b.r * 1.7)) + 1);
      g.restore();
    });
    requestAnimationFrame(frame);
  }
  requestAnimationFrame(frame);
}

// The category drop-down ("All ▾"), like the app and the editor.
function renderChips() {
  var box = $("chips");
  var cats = (list && list.cats) || [];
  if (!cats.length) { box.hidden = true; return; }
  if (cat !== "All" && cats.indexOf(cat) < 0) cat = "All";
  box.hidden = false;
  $("catName").textContent = cat === "All" ? T("All") : cat;
}

var menuBox = null;
function closeMenu() {
  if (!menuBox) return;
  menuBox.remove(); menuBox = null;
  $("catBtn").setAttribute("aria-expanded", "false");
}
function openCatMenu() {
  if (menuBox) { closeMenu(); return; }
  var items = (list && list.items) || [];
  var box = el("div", "menu");
  box.setAttribute("role", "menu");
  ["All"].concat(list.cats || []).forEach(function (c) {
    var n = c === "All" ? items.length : items.filter(function (w) { return w.cat === c; }).length;
    var b = el("button", "mitem" + (c === cat ? " b" : ""), (c === "All" ? T("All") : c) + " (" + n + ")");
    b.type = "button";
    b.setAttribute("role", "menuitem");
    b.setAttribute("data-cat", c);
    b.addEventListener("click", function (ev) { ev.stopPropagation(); closeMenu(); cat = c; render(); });
    box.appendChild(b);
  });
  document.body.appendChild(box);
  var r = $("catBtn").getBoundingClientRect();
  box.style.position = "fixed";
  box.style.top = Math.round(r.bottom + 2) + "px";
  box.style.left = Math.round(Math.max(8, r.left)) + "px";
  menuBox = box;
  $("catBtn").setAttribute("aria-expanded", "true");
}

function isDrop(w) {
  return typeof w.price === "number" && typeof w.old === "number" && w.old > w.price;
}
// A price check found a higher price: shown with "+12%" in red, the opposite of a drop.
function isRise(w) {
  return typeof w.price === "number" && typeof w.old === "number" && w.old > 0 && w.price > w.old + 0.005;
}
function changeClass(w) { return isDrop(w) ? " drop" : isRise(w) ? " rise" : ""; }

// The gold T("Top wish") label. Small cards show just the star.
function starBadge() {
  var b = el("span", "badge star");
  b.setAttribute("role", "img");
  b.setAttribute("aria-label", T("Top wish"));
  b.title = T("Top wish");
  b.appendChild(el("span", "ico", "★"));
  b.appendChild(el("span", "lbl", T("Top wish")));
  return b;
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

function renderViews() {
  var box = $("views");
  box.textContent = "";
  VIEWS.forEach(function (k) {
    var b = el("button", "vbtn" + (k === view ? " on" : ""));
    b.type = "button";
    b.title = VIEW_LABELS[k];
    b.setAttribute("aria-label", VIEW_LABELS[k]);
    b.setAttribute("aria-pressed", k === view ? "true" : "false");
    b.appendChild(viewIcon(k));
    b.addEventListener("click", function () { view = k; saveView(k); render(); });
    box.appendChild(b);
  });
}

function card(w, compact) {
  var r = res[w.id];
  var got = !!w.got;
  var dim = got || r === false;
  var c = el("div", "card" + (compact ? " compact" : "") + (dim ? " dim" : ""));

  var link = safeUrl(w.url);
  var pic = el(link ? "a" : "div", "pic" + (w.image && safeUrl(w.image) ? "" : " empty"));
  if (link) { pic.href = link; pic.target = "_blank"; pic.rel = "noopener noreferrer"; }
  var img = safeUrl(w.image);
  if (img) {
    var im = el("img");
    im.src = img; im.alt = w.title || ""; im.loading = "lazy"; im.referrerPolicy = "no-referrer";
    // Blur stage (owner, 2.100): a soft blur of the same photo fills the area, the photo sits big in the middle.
    var back = el("img", "back");
    back.src = img; back.alt = ""; back.loading = "lazy"; back.referrerPolicy = "no-referrer"; back.setAttribute("aria-hidden", "true");
    im.addEventListener("error", function () { im.remove(); back.remove(); pic.classList.add("empty"); pic.appendChild(el("span", null, "🎁")); });
    pic.appendChild(back);
    pic.appendChild(im);
  } else {
    pic.appendChild(el("span", null, "🎁"));
  }
  var drop = isDrop(w), rise = isRise(w);
  // Plus and minus, not arrows (it isn't a stock trade).
  if (drop) pic.appendChild(el("span", "badge drop", "−" + Math.round((1 - w.price / w.old) * 100) + "%"));
  else if (rise) pic.appendChild(el("span", "badge drop rise", "+" + Math.round((w.price / w.old - 1) * 100) + "%"));
  var status = got ? T("Owned") : r === true ? T("Reserved by you") : r === false ? T("Reserved") : "";
  var bx = el("div", "badges");
  if (w.star && !got && !SHARED_LIST) bx.appendChild(starBadge());
  if (status) bx.appendChild(el("span", "badge status", status));
  if (bx.childNodes.length) pic.appendChild(bx);
  c.appendChild(pic);

  var body = el("div", "body");
  var meta = [hostOf(w.url), w.cat].filter(Boolean).join(" · ");
  if (!compact) body.appendChild(el("div", "meta", meta));
  body.appendChild(el("h2", "title", w.title || ""));
  if (w.desc && !compact) body.appendChild(el("p", "desc", w.desc));
  if (w.size) body.appendChild(el("span", "sizetag", T("Size {0}", String(w.size).slice(0, 8))));
  if (w.note) body.appendChild(el("p", "wnote", T("Note: {0}", w.note)));

  var row = el("div", "row");
  var pr = el("div", "price" + changeClass(w), typeof w.price === "number" ? money(w.price, w.cur) : T("No price"));
  if (drop || rise) pr.appendChild(el("span", "old", money(w.old, w.cur)));
  row.appendChild(pr);

  if (!got) {
    if (r === undefined) {
      var b = el("button", "act small", T("Reserve"));
      b.type = "button"; b.disabled = busy;
      b.addEventListener("click", function () { toggle(w, true); });
      row.appendChild(b);
    } else if (r === true) {
      var u = el("button", "act outline small", T("Undo"));
      u.type = "button"; u.disabled = busy;
      u.addEventListener("click", function () { toggle(w, false); });
      row.appendChild(u);
    }
  }
  body.appendChild(row);

  c.appendChild(body);
  return c;
}

function filtersOn() { return maxPrice !== null || onlyAvail; }

function clearFilters() {
  maxPrice = null; onlyAvail = false;
  $("maxp").value = ""; $("avail").checked = false;
  render();
}

// The wishes to show: category, budget and availability filters, then the order.
function visibleItems() {
  var items = (list.items || []).filter(function (w) { return cat === "All" || w.cat === cat; });
  if (maxPrice !== null) {
    items = items.filter(function (w) { return typeof w.price !== "number" || w.price <= maxPrice; });
  }
  if (onlyAvail) items = items.filter(function (w) { return !w.got && res[w.id] !== false; });
  var rows = items.map(function (w, i) { return { w: w, i: i }; });
  var price = function (w) { return typeof w.price === "number" ? w.price : null; };
  rows.sort(function (a, b) {
    // Owned wishes always go to the bottom (owner, 4 November 2026), so the ones still wanted come first.
    var d = (a.w.got ? 1 : 0) - (b.w.got ? 1 : 0);
    if (d) return d;
    if (sortBy === "star") {
      d = (a.w.star && !a.w.got ? 0 : 1) - (b.w.star && !b.w.got ? 0 : 1);
    } else if (sortBy === "low" || sortBy === "high") {
      var pa = price(a.w), pb = price(b.w);
      if (pa === null || pb === null) d = (pa === null ? 1 : 0) - (pb === null ? 1 : 0);
      else d = sortBy === "low" ? pa - pb : pb - pa;
    }
    return d || a.i - b.i;
  });
  return rows.map(function (r) { return r.w; });
}

function renderTools() {
  $("tools").hidden = !toolsOpen;
  var btn = $("filterBtn");
  btn.classList.toggle("on", toolsOpen);
  btn.setAttribute("aria-expanded", toolsOpen ? "true" : "false");
  $("filterDot").hidden = !(filtersOn() || sortBy !== DEFAULT_SORT);
  $("sort").value = sortBy;
}

function render() {
  var v = $("view");
  if (state === "gone") { showCodeForm(T("This list isn’t shared any more.")); return; }
  if (state === "offline" && !list) {
    v.textContent = "";
    var box = el("div", "center");
    box.appendChild(el("p", null, T("Couldn’t load the list. Check your connection.")));
    var b = el("button", "act", T("Try again"));
    b.type = "button";
    b.addEventListener("click", function () { state = "loading"; refresh(); });
    box.appendChild(b);
    v.appendChild(box);
    $("bar").hidden = true;
    $("chips").hidden = true;
    $("tools").hidden = true;
    return;
  }
  if (!list) { v.textContent = ""; v.appendChild(el("div", "center", T("Loading…"))); return; }

  $("name").textContent = list.name || "Wishlist";
  document.title = list.name || "Wishlist";
  // Whose list this is (2.102): the owner's profile name, sent by their app (supabase-friends.sql).
  var ow = $("owner");
  ow.textContent = list.owner ? T("Owned by {0}", String(list.owner).slice(0, 30)) : "";
  ow.hidden = !list.owner;
  $("sub").textContent = T("Reserve a gift so nobody gives the same gift twice.");
  var ab = $("about");
  ab.textContent = list.about || "";
  ab.hidden = !list.about;
  renderCover(list.cover, list.cedit);
  $("bar").hidden = false;
  renderChips();
  renderViews();
  renderTools();
  var shown = visibleItems();
  v.textContent = "";
  if (!shown.length) {
    var empty = el("div", "center");
    if (filtersOn()) {
      empty.appendChild(el("p", null, T("No wishes match your filters.")));
      var cf = el("button", "act outline", T("Clear filters"));
      cf.type = "button";
      cf.addEventListener("click", clearFilters);
      empty.appendChild(cf);
    } else {
      empty.textContent = T("Nothing here yet.");
    }
    v.appendChild(empty);
  } else {
    var g = el("div", "grid" + (view === "grid" ? " two" : ""));
    shown.forEach(function (w) { g.appendChild(card(w, view === "grid")); });
    v.appendChild(g);
  }
  $("note").hidden = false;
}

// The cover photo at the top (2.105): the picture as the owner cut it out in the app, with its text on top in the
// same place, size and colour (see HeroPicture in the app). Only pictures from our own storage are shown.
function coverOk(url) {
  return typeof url === "string" && url.indexOf(SUPABASE_URL.replace(/\/+$/, "") + "/storage/v1/object/public/") === 0;
}
function renderCover(url, cedit) {
  var box = $("cover");
  if (!coverOk(url)) { box.hidden = true; return; }
  var e = {};
  try { e = JSON.parse(cedit || "{}") || {}; } catch (x) { e = {}; }
  $("coverImg").src = url;
  var t = $("coverText");
  var text = String(e.text || "").slice(0, 40);
  t.textContent = text;
  t.hidden = !text;
  if (text) {
    var c = Number(e.color);
    if (!isFinite(c)) c = 0xFFFFFFFF;
    var rgb = c % 0x1000000;
    var r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
    t.style.color = "rgb(" + r + "," + g + "," + b + ")";
    var light = (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255 > 0.5;
    t.className = e.box ? "boxed" : "";
    t.style.background = e.box ? (light ? "rgba(0,0,0,.45)" : "rgba(255,255,255,.75)") : "";
    var num = function (v, d) { v = Number(v); return isFinite(v) ? v : d; };
    t.style.left = (Math.min(1, Math.max(0, num(e.tx, 0.5))) * 100) + "%";
    t.style.top = (Math.min(1, Math.max(0, num(e.ty, 0.75))) * 100) + "%";
    t.style.fontSize = (150 * Math.min(0.6, Math.max(0.05, num(e.tsize, 0.16)))) + "px";
  }
  box.hidden = false;
}

async function refresh() {
  if (!code || busy) return;
  try {
    var l = await rpc("wl_get_list", { p_code: code });
    if (l === null) { state = "gone"; list = null; render(); return; }
    var rs = await rpc("wl_get_reservations", { p_code: code, p_device: deviceId() });
    var m = {};
    (rs || []).forEach(function (x) { m[x.id] = x.mine; });
    // The note for friends travels as one extra item of kind "about". It is not a wish.
    var about = "", cover = "", cedit = "";
    // Wishes in the bin (deleted, kept 30 days so they can be restored) are not on the list.
    l.items = (l.items || []).filter(function (w) {
      if (w && w.kind === "about") {
        about = String(w.about || "");
        // The list's cover photo (2.105) and its text travel in the same item.
        cover = String(w.cover || ""); cedit = String(w.cedit || "");
        return false;
      }
      return !(w && w.bin);
    });
    l.about = about;
    l.cover = cover;
    l.cedit = cedit;
    // Remember what this browser reserved, and say so when one of those gifts is no longer on the list, or (owner,
    // 4 November 2026) was marked as owned, so nobody buys a duplicate.
    var mine = readMyRes(), seen = {}, gone = [], owned = [];
    l.items.forEach(function (w) { seen[w.id] = w; });
    Object.keys(m).forEach(function (id) { if (m[id] === true && seen[id] && !seen[id].got) mine[id] = String(seen[id].title || ""); });
    Object.keys(mine).forEach(function (id) {
      if (!seen[id]) { gone.push(mine[id]); delete mine[id]; }
      else if (seen[id].got) { owned.push(mine[id]); delete mine[id]; }
    });
    writeMyRes(mine);
    if (gone.length || owned.length) noteGone(gone, owned);
    list = l; res = m; state = "ok";
  } catch (e) {
    if (!list) state = "offline";
  }
  render();
}

async function toggle(w, reserve) {
  busy = true; render();
  var msg = null;
  try {
    if (reserve) {
      var ok = await rpc("wl_reserve_item", { p_code: code, p_item: w.id, p_device: deviceId() });
      if (ok !== true) msg = T("Someone else just reserved that one.");
    } else {
      await rpc("wl_unreserve_item", { p_code: code, p_item: w.id, p_device: deviceId() });
      var mine = readMyRes();
      delete mine[w.id];
      writeMyRes(mine);
    }
    busy = false;
    await refresh();
  } catch (e) {
    busy = false;
    msg = T("No connection. Try again.");
    render();
  }
  if (msg) toast(msg);
}

document.addEventListener("keydown", function (e) {
  if (e.key !== "Escape") return;
  if (menuBox) closeMenu();
});

$("filterBtn").addEventListener("click", function () { toolsOpen = !toolsOpen; render(); });
$("catBtn").addEventListener("click", function (ev) { ev.stopPropagation(); openCatMenu(); });
document.addEventListener("click", function (ev) { if (menuBox && !menuBox.contains(ev.target)) closeMenu(); });
$("sort").addEventListener("change", function () { sortBy = this.value; saveSort(sortBy); render(); });
$("maxp").addEventListener("input", function () {
  var n = parseFloat(this.value.replace(",", "."));
  maxPrice = isFinite(n) && n >= 0 ? n : null;
  render();
});
$("avail").addEventListener("change", function () { onlyAvail = this.checked; render(); });
$("clearF").addEventListener("click", clearFilters);

var invite = readInvite();
$("langs").appendChild(themeSwitch());
if (invite !== null) {
  showInvite(invite);
} else {
  if (SHARED_LIST) {
    var starOpt = $("sort").querySelector('option[value="star"]');
    if (starOpt) starOpt.remove();
    $("note").textContent = T("Tap Reserve on a gift you plan to buy. Everyone who puts this list together sees what’s reserved, so nobody gives the same gift twice.");
  }
  code = readCode();
  if (!code) {
    showCodeForm(T("Open the link your friend sent you, or enter their list code."));
  } else {
    refresh();
    setInterval(function () { if (!document.hidden) refresh(); }, 20000);
    document.addEventListener("visibilitychange", function () { if (!document.hidden) refresh(); });
  }
}
