// Every web page only runs scripts from this site (Content-Security-Policy, added 4 November 2026): no inline
// <script> with code in the real pages, and the policy is there. The redirect pages under join/, list/, group/ only
// pass the address on and have no policy. Real Chromium runs with the policy in the e2e and group suites, where a
// blocked script or connection shows up as a console error.
const fs = require("fs");
const path = require("path");
const WEB = path.join(__dirname, "..", "..", "web");
let fails = 0, total = 0;
function check(name, cond, extra) { total++; if (!cond) { fails++; console.log("FAIL " + name + (extra !== undefined ? "  -> " + JSON.stringify(extra) : "")); } }
for (const f of fs.readdirSync(WEB).filter(f => f.endsWith(".html"))) {
  const s = fs.readFileSync(path.join(WEB, f), "utf8");
  const csp = /<meta http-equiv="Content-Security-Policy" content="([^"]+)">/.exec(s);
  check(f + ": has a Content-Security-Policy", !!csp);
  if (!csp) continue;
  const head = s.slice(0, s.indexOf("</head>"));
  check(f + ": the policy comes before any script", head.indexOf(csp[0]) < head.search(/<script/));
  check(f + ": scripts only from this site", /script-src 'self';/.test(csp[1]) && !/unsafe-inline'[^;]*;?\s*$/.test(csp[1].split("script-src")[1].split(";")[0]), csp[1]);
  check(f + ": no plug-ins, no other base address", /object-src 'none'/.test(csp[1]) && /base-uri 'none'/.test(csp[1]));
  check(f + ": no inline script code", !/<script(?![^>]*\bsrc=)[^>]*>/.test(s), f);
  check(f + ": no inline event handlers", !/\son[a-z]+\s*=\s*"/i.test(s));
  check(f + ": no referrer sent", /<meta name="referrer" content="no-referrer">/.test(s));
  for (const m of s.matchAll(/<script src="([^"?]+)\?v=dev"><\/script>/g))
    check(f + ": " + m[1] + " exists", fs.existsSync(path.join(WEB, m[1])));
}
console.log(fails === 0 ? "ALL PASSED (" + total + " checks)" : fails + " FAILED of " + total);
process.exit(fails ? 1 : 0);
