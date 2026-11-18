// Server script versions (owner, 18 November 2026; docs/MAP.md "Server script versions").
//   node tests/sql/stamps.js           checks that every supabase-*.sql stamps itself, once, with a version number
//   node tests/sql/stamps.js --server  also asks the live server (read-only, the public key) which versions it has,
//                                      and fails, listing the scripts to run, when it is behind (the daily check)
const fs = require("fs");
const path = require("path");
const root = path.join(__dirname, "..", "..");
// The order the owner runs them in (as tests/sql/run.sh); a script not in this list comes last.
const ORDER = ["setup", "photos", "shared", "friends", "chat", "push", "groups", "features", "exchange", "together", "collab",
  "sync", "limits", "cleanup", "vault", "privacy", "sizes-off"].map(n => `supabase-${n}.sql`);
const rank = f => { const i = ORDER.indexOf(f); return i < 0 ? 999 : i; };
const files = fs.readdirSync(root).filter(f => /^supabase-.*\.sql$/.test(f)).sort((a, b) => rank(a) - rank(b) || a.localeCompare(b));
const want = {};
let bad = [];
for (const f of files) {
  const s = fs.readFileSync(path.join(root, f), "utf8");
  const m = [...s.matchAll(/insert into public\.wl_versions \(script, version, run_at\) values \('([^']+)', (\d+), now\(\)\)/g)];
  if (m.length !== 1 || m[0][1] !== f) bad.push(f);
  else want[f] = Number(m[0][2]);
}
if (bad.length) { console.log("FAIL: these scripts don't stamp themselves exactly once: " + bad.join(", ")); process.exit(1); }
console.log(`all ${files.length} scripts stamp their version`);
if (process.argv[2] !== "--server") process.exit(0);

(async () => {
  const url = "https://aspnmvmsvwbezdltykgx.supabase.co/rest/v1/rpc/wl_script_versions";
  const key = "sb_publishable_2RpbWlDJBB3yV2TO-h68xA_i-gXfd_t"; // public, also in the app and web pages
  const r = await fetch(url, { method: "POST", headers: { apikey: key, "Content-Type": "application/json" }, body: "{}" });
  const text = await r.text();
  const have = r.status === 200 ? JSON.parse(text) : null;
  const behind = files.filter(f => !have || !(f in have) || have[f] < want[f]);
  if (!behind.length) { console.log("The server has every script's newest version."); return; }
  const lines = behind.map(f => `- [${f}](https://github.com/AndroidEnthusiast/Wishlist/blob/main/${f}) (server: ${have && f in have ? "version " + have[f] : "not run since stamps began"}, project: version ${want[f]})`);
  const msg = "These server scripts are newer in the project than on the server. Open each link, copy the script, and run it in Supabase (SQL Editor, New query, paste, Run), in this order:\n\n" + lines.join("\n") +
    (have ? "" : `\n\n(The server didn't answer the version question: status ${r.status}. Run supabase-setup.sql first.)`);
  fs.writeFileSync(path.join(root, "server-behind.md"), msg + "\n");
  console.log(msg);
  process.exit(1);
})().catch(e => { console.log("Couldn't ask the server: " + e.message); process.exit(1); });
