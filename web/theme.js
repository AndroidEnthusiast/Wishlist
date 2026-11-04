// Light or dark as chosen with the switch at the bottom (this browser only); otherwise the device decides.
// Loaded first in <head>, before the page is drawn, so it never flashes in the wrong colours.
// (A file of its own, not inline: the pages only run scripts from this site, see Content-Security-Policy.)
try { var wlt = localStorage.getItem("wl_theme"); if (wlt === "light" || wlt === "dark") document.documentElement.setAttribute("data-theme", wlt); } catch (e) { }
