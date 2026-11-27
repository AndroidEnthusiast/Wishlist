// The Iconic prize on the friends' page (owner, 27 November 2026; Iconic.kt in the app): when the list belongs to an
// Iconic member who shows it, its cover gets a holo edge, or a holo band sits at the top without a cover. Asks the
// server only "yes or no" for the list code in the address (wl_ic_list); an older server or an error shows nothing.
(function () {
  "use strict";
  var q = new URLSearchParams(location.search);
  var code = q.get("c") || q.get("code") || location.hash.replace(/^#/, "");
  if (!/^[A-Za-z0-9_-]{4,64}$/.test(code) || typeof SUPABASE_URL !== "string") return;
  var h = { "Content-Type": "application/json", "apikey": SUPABASE_KEY };
  if (SUPABASE_KEY.indexOf("eyJ") === 0) h["Authorization"] = "Bearer " + SUPABASE_KEY;
  fetch(SUPABASE_URL + "/rest/v1/rpc/wl_ic_list", { method: "POST", headers: h, body: JSON.stringify({ p_code: code }) })
    .then(function (r) { return r.ok ? r.json() : false; })
    .then(function (yes) {
      if (yes !== true) return;
      var cover = document.getElementById("cover");
      if (!cover) return;
      cover.classList.add("holo");
      var band = document.createElement("div");
      band.className = "holoband";
      band.setAttribute("aria-hidden", "true");
      cover.parentNode.insertBefore(band, cover);
      // The band only while there is no cover photo (the cover shows once the list has loaded).
      function tidy() { band.hidden = !cover.hidden; }
      tidy();
      new MutationObserver(tidy).observe(cover, { attributes: true, attributeFilter: ["hidden"] });
    })
    .catch(function () { /* offline or an older server: nothing changes */ });
})();
