// PreToolUse hook of the engineering team: blocks the git commands that would change `main` or rewrite history.
// The team works in branches (mejora/..., calidad/...) and pull requests; merging (which deploys) is the owner's.
import { execFileSync } from "node:child_process";
import { readFileSync } from "node:fs";
import path from "node:path";

let input = {};
try { input = JSON.parse(readFileSync(0, "utf8") || "{}"); } catch { process.exit(0); }
const command = String(input?.tool_input?.command || "");
if (!/\bgit\b/.test(command)) process.exit(0);

const block = (why) => {
  process.stderr.write(`Bloqueado por el equipo de ingeniería: ${why}. Trabaja en una rama mejora/H-XXX-... y abre un pull request; el merge a main lo hace el dueño.\n`);
  process.exit(2);
};

// Each part of the command (split by && ; ||) with the directory it runs in (following `cd`).
let dir = input.cwd || process.cwd();
// A `git switch`/`checkout` earlier in the same command changes the branch the later parts run on.
const switched = new Map();
const branchIn = (d) => {
  if (switched.has(d)) return switched.get(d);
  try { return execFileSync("git", ["-C", d, "rev-parse", "--abbrev-ref", "HEAD"], { encoding: "utf8" }).trim(); } catch { return ""; }
};
for (const raw of command.split(/&&|\|\||;|\n/)) {
  const part = raw.trim();
  const cd = part.match(/^cd\s+("([^"]+)"|'([^']+)'|(\S+))/);
  if (cd) { const target = cd[2] || cd[3] || cd[4]; dir = path.resolve(dir, target.replace(/^~(?=\/|$)/, process.env.HOME || "~")); continue; }
  const git = part.match(/\bgit\b(?:\s+-C\s+("[^"]+"|'[^']+'|\S+))?\s+(.*)$/);
  if (!git) continue;
  const where = git[1] ? path.resolve(dir, git[1].replace(/^["']|["']$/g, "")) : dir;
  const args = git[2];
  const onMain = () => ["main", "master"].includes(branchIn(where));
  const sw = args.match(/^(?:switch|checkout)\s+(?:-[cCbB]\s+)?([^\s-][^\s]*)/);
  if (sw && !/^checkout\s+--\s/.test(args)) { switched.set(where, sw[1].replace(/^origin\//, "")); continue; }
  if (/^push\b/.test(args)) {
    if (/(^|\s)(-f|--force|--force-with-lease|--mirror|--delete|-d)(\s|=|$)/.test(args)) block("push forzado o que borra ramas");
    if (/(^|[\s:+])(refs\/heads\/)?(main|master)(\s|$)/.test(args)) block("push a main");
    const refs = args.replace(/^push\s*/, "").split(/\s+/).filter((a) => a && !a.startsWith("-"));
    if (refs.length < 2 && onMain()) block("push desde main");
  }
  if (/^commit\b/.test(args) && onMain()) block("commit en main");
  if (/^merge\b/.test(args) && onMain()) block("merge en main");
  if (/^reset\b.*--hard/.test(args)) block("reset --hard");
  if (/^branch\b.*\s-(D|d|-delete)\b.*\b(main|master)\b/.test(args)) block("borrar main");
  if (/^rebase\b/.test(args) && onMain()) block("rebase de main");
}
process.exit(0);
