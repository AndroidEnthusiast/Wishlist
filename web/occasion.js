// A list's occasion (owner, 5 November 2026): "Birthday · in 3 weeks" under the list's name on the friends' page and
// in the browser editor (which only shows it: it is set in the app). The same rules as Occasion.kt in the app; change
// both together. Stored in the list's "about" item as occ = {k: kind, n: own name, d: date}: "MM-DD" for birthday and
// anniversary, "YYYY-MM-DD" for an own occasion, nothing for the ones whose date fills itself (Dutch dates).
// Yearly occasions move on to next year; an own occasion is gone the day after its date.
(function () {
  "use strict";
  var LABELS = {
    birthday: "Birthday", christmas: "Christmas", sinterklaas: "Sinterklaas", valentine: "Valentine’s Day",
    mother: "Mother’s Day", father: "Father’s Day", anniversary: "Anniversary"
  };

  function day(y, m, d) {
    // 29 February becomes the 28th in other years.
    var last = new Date(Date.UTC(y, m, 0)).getUTCDate();
    return Date.UTC(y, m - 1, Math.min(d, last));
  }
  function nthSunday(y, m, n) {
    var first = new Date(Date.UTC(y, m - 1, 1)).getUTCDay(); // 0 = Sunday
    return Date.UTC(y, m - 1, 1 + ((7 - first) % 7) + 7 * (n - 1));
  }
  function monthDay(s) {
    var r = /^(?:\d{4}-)?(\d{2})-(\d{2})$/.exec(String(s || ""));
    if (!r) return null;
    var m = +r[1], d = +r[2];
    return m >= 1 && m <= 12 && d >= 1 && d <= 31 ? [m, d] : null;
  }

  // Days from [today] (a local date) to the occasion's next day, or null (passed, or no date).
  function daysUntil(o, today) {
    if (!o || typeof o !== "object" || !o.k) return null;
    var t = Date.UTC(today.getFullYear(), today.getMonth(), today.getDate());
    var y = today.getFullYear();
    function yearly(f) { var v = f(y); return v < t ? f(y + 1) : v; }
    var next = null, md;
    switch (String(o.k)) {
      case "christmas": next = yearly(function (yy) { return day(yy, 12, 25); }); break;
      case "sinterklaas": next = yearly(function (yy) { return day(yy, 12, 5); }); break;
      case "valentine": next = yearly(function (yy) { return day(yy, 2, 14); }); break;
      case "mother": next = yearly(function (yy) { return nthSunday(yy, 5, 2); }); break;
      case "father": next = yearly(function (yy) { return nthSunday(yy, 6, 3); }); break;
      case "birthday": case "anniversary":
        md = monthDay(o.d);
        if (md) next = yearly(function (yy) { return day(yy, md[0], md[1]); });
        break;
      case "own":
        var r = /^(\d{4})-(\d{2})-(\d{2})$/.exec(String(o.d || ""));
        if (r) { var v = day(+r[1], +r[2], +r[3]); if (v >= t) next = v; }
        break;
    }
    return next === null ? null : Math.round((next - t) / 86400000);
  }

  function countdown(days) {
    if (days <= 0) return T("today");
    if (days === 1) return T("tomorrow");
    if (days < 14) return T("in {0} days", days);
    if (days < 63) return T("in {0} weeks", Math.floor((days + 3) / 7));
    return T("in {0} months", Math.round(days / 30.44));
  }

  function label(o) {
    var k = String(o.k);
    if (LABELS[k]) return T(LABELS[k]);
    return String(o.n || "").slice(0, 40) || T("Occasion");
  }

  // "Birthday · in 3 weeks", or "" when there is nothing to show. Plain text: put it in with textContent.
  window.occasionLine = function (o, today) {
    var d = daysUntil(o, today || new Date());
    return d === null ? "" : label(o) + " · " + countdown(d);
  };
})();
