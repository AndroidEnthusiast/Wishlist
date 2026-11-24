// Web files stay small (owner, 24 November 2026: "debt that can't grow"; docs/decisions/roadmap.md). The same rule as
// the app's ArchitectureTest: every .js, .css and .html file in web/ stays under LIMIT lines. Files that were bigger
// on 24 November 2026 are in ALLOWED with their size then: they may shrink, never grow, and leave the list once under
// the limit. Never add a file to ALLOWED: split it instead. Run: node size/test.js (part of run-all.sh).
const fs = require('fs');
const path = require('path');

const LIMIT = 400;
const ALLOWED = {
  'edit.js': 1819,
  'group.js': 963,
  'friends.js': 922,
  'exchange.js': 777,
  'together.js': 457,
  'parse.js': 443,
};

const web = path.join(__dirname, '..', '..', 'web');
const files = [];
(function walk(d) {
  for (const e of fs.readdirSync(d, { withFileTypes: true })) {
    const p = path.join(d, e.name);
    if (e.isDirectory()) walk(p);
    else if (/\.(js|css|html)$/.test(e.name)) files.push(p);
  }
})(web);

const lines = (p) => fs.readFileSync(p, 'utf8').split('\n').length - 1;
const problems = [];
for (const p of files) {
  const name = path.relative(web, p).split(path.sep).join('/');
  const n = lines(p);
  const was = ALLOWED[name];
  if (was === undefined && n > LIMIT) problems.push(`web/${name}: ${n} lines, split it (under ${LIMIT} each)`);
  if (was !== undefined && n > was) problems.push(`web/${name} grew from ${was} to ${n} lines: split it instead`);
  if (was !== undefined && n < was && n > LIMIT) problems.push(`web/${name} shrank to ${n}, well done: lower it in tests/size/test.js ALLOWED`);
  if (was !== undefined && n <= LIMIT) problems.push(`web/${name} is under ${LIMIT} lines now: take it off ALLOWED in tests/size/test.js`);
}
for (const name of Object.keys(ALLOWED)) {
  if (!fs.existsSync(path.join(web, name))) problems.push(`web/${name} is gone or renamed: take it off ALLOWED in tests/size/test.js`);
}
if (problems.length) { console.log(problems.join('\n')); process.exit(1); }
console.log(`OK: ${files.length} web files, none grew (${Object.keys(ALLOWED).length} big ones may only shrink)`);
