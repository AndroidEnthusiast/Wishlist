// The check-up every three months (owner, 19 November 2026; MAP "How a screen is built"): measures the app files and
// writes checkup.md for a GitHub issue. Only reads. Run: node tests/tools/checkup.js
const fs = require("fs");
const path = require("path");
const root = path.join(__dirname, "..", "..");
const dir = path.join(root, "app", "src", "main", "java", "com", "wishlist", "app");
const files = fs.readdirSync(dir).filter(f => f.endsWith(".kt"))
  .map(f => ({ f, n: fs.readFileSync(path.join(dir, f), "utf8").split("\n").length }))
  .sort((a, b) => b.n - a.n);
const arch = fs.readFileSync(path.join(root, "app", "src", "test", "java", "com", "wishlist", "app", "ArchitectureTest.kt"), "utf8");
const allowed = [...arch.matchAll(/^\s+"([A-Za-z]+\.kt)" to (\d+)/gm)];
const rebuilt = (arch.match(/val REBUILT = listOf\(([^)]*)\)/) || ["", ""])[1].split(",").filter(s => s.trim()).length;
const total = files.reduce((s, x) => s + x.n, 0);
const md = [
  "The check-up every three months: how clean the app is, so drift is fixed while it's small.",
  "",
  `- App files: ${files.length}, ${total} lines in all.`,
  `- Still over 400 lines (ArchitectureTest.ALLOWED, may only shrink): ${allowed.length} files.`,
  `- Screens rebuilt to "layout + state holder" (ArchitectureTest.REBUILT): ${rebuilt} files.`,
  "",
  "The 10 biggest files:",
  ...files.slice(0, 10).map(x => `- ${x.f}: ${x.n} lines`),
  "",
  "What to do: start a Claude Code session and say \"Do the quarterly check-up\". It reads this issue, proposes the next",
  "screen or file to rebuild, and asks before building anything."
].join("\n");
fs.writeFileSync(path.join(root, "checkup.md"), md + "\n");
console.log(md);
