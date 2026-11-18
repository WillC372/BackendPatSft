// Lists every Spanish text of the interface (frontend JavaScript and the backend's messages) in the same form the
// app's translator asks for it: whitespace collapsed and numbers or variable values as {0}, {1}...
// The list is src/main/resources/i18n/ui-texts.txt, which the server translates in advance (UiTextWarmer).
// Usage (needs acorn and acorn-walk: npm install acorn acorn-walk in any folder and run from there):
//   node extract-ui-texts.mjs ../FrontendPatSft . src/main/resources/i18n/ui-texts.txt
import fs from "fs";
import path from "path";
import * as acorn from "acorn";
import * as walk from "acorn-walk";

const [front, back, out] = process.argv.slice(2);
const texts = new Set();
const HTML_EXPR = /^(icon|aquaFace|loading|emptyState|helpBox|inputField|relativeTime|avatarMarkup|logo|calculatorCard|reportView|chatCitations|withReferenceMarks|passwordChecklist|own)\(|\.map\(|\.join\(|Markup|Html|html|svg|SVG|chart|Chart|card|Card|badge|rows|list|items|options|body|content|markup/;
const ENT = { "&amp;": "&", "&lt;": "<", "&gt;": ">", "&quot;": '"', "&#39;": "'", "&nbsp;": " " };
const LETTER = /\p{L}/u;
const SPANISH_HINT = /[áéíóúñ¿¡ÁÉÍÓÚÑ]|\b(el|la|los|las|de|del|que|para|con|una|un|sin|por|tu|tus|se|es|en|y|o|al|no|más|tiene|puedes|está)\b/i;

// The translator's key: whitespace collapsed; variables (\u0000) and numbers become {0}, {1}... from left to right.
function key(raw) {
  let i = 0;
  return raw.replace(/\s+/g, " ").trim().replace(/\u0000|\d+(?:[.,:/]\d+)*/g, () => `{${i++}}`);
}

function looksLikeText(t) {
  if (t.length < 2 || t.length > 1500 || !LETTER.test(t.replace(/\{\d+\}/g, ""))) return false;
  if (/[;=`\\$]|=>|\(\)|\.(js|css|png|svg|json|mjs)\b|https?:|#\/|^--|^[\/.@]|\b\w+\(|\[\]|&&|\|\|/.test(t)) return false;
  if (/^[A-Z0-9_]+$/.test(t)) return false;                       // CONSTANT_KEY
  if (/["<>]|prefers-|@media/.test(t)) return false;                // leftovers of markup and CSS
  if (/^[a-z0-9_.:\-]+$/.test(t) && !/[áéíóúñ]/.test(t)) return false; // css-class, identifiers, mime types
  if (/^[a-z]+[A-Z]\w*$/.test(t)) return false;                     // camelCase
  if (/^\{\d+\}$/.test(t)) return false;
  const words = t.replace(/\{\d+\}/g, "").trim();
  if (!words) return false;
  // One word: only if it starts with a capital ("Medir") or carries a Spanish accent ("ácida").
  if (!/\s/.test(words)) return /^[A-ZÁÉÍÓÚÑ¿¡][a-záéíóúñü]+[.:!?]?$/.test(words) || /[áéíóúñ]/.test(words);
  return true;
}

function addFromMarkup(s) {
  // Attribute values the translator handles.
  for (const m of s.matchAll(/\b(?:placeholder|title|aria-label|alt|value)="([^"]*)"/g)) add(m[1]);
  // Text between tags (each tag is a boundary, as in the page).
  for (const part of s.replace(/<[^>]*>/g, "\n").split("\n")) add(part);
}

function add(raw) {
  let t = raw.replace(/&[a-z#0-9]+;/g, (e) => ENT[e] ?? " ");
  if (/\u0001/.test(t)) return;
  const k = key(t);
  if (looksLikeText(k)) texts.add(k);
}

// ---------- frontend ----------
const jsFiles = [];
(function list(dir) { for (const f of fs.readdirSync(dir)) { const p = path.join(dir, f); if (fs.statSync(p).isDirectory()) list(p); else if (p.endsWith(".js")) jsFiles.push(p); } })(path.join(front, "js"));
for (const file of jsFiles) {
  const src = fs.readFileSync(file, "utf8");
  const ast = acorn.parse(src, { ecmaVersion: "latest", sourceType: "module" });
  walk.full(ast, (node) => {
    if (node.type === "Literal" && typeof node.value === "string") {
      if (/<\w|<\/\w/.test(node.value)) addFromMarkup(node.value); else add(node.value);
    } else if (node.type === "TemplateLiteral") {
      let s = "";
      node.quasis.forEach((q, i) => {
        s += q.value.cooked ?? "";
        const e = node.expressions[i];
        if (!e) return;
        const code = src.slice(e.start, e.end);
        // Expressions that produce markup cut the text like a tag; the rest are values ({0}).
        s += HTML_EXPR.test(code) || e.type === "ConditionalExpression" || e.type === "LogicalExpression" ? "<x/>" : "\u0000";
      });
      if (/<\w|<\/\w|<x\/>/.test(s)) addFromMarkup(s); else add(s);
    }
  });
}
// index.html
addFromMarkup(fs.readFileSync(path.join(front, "index.html"), "utf8").replace(/<script[\s\S]*?<\/script>|<style[\s\S]*?<\/style>|<head>[\s\S]*?<\/head>/g, ""));
const frontCount = texts.size;

// ---------- backend messages (what reaches the screen: errors, labels, advice) ----------
const javaFiles = [];
(function list(dir) { for (const f of fs.readdirSync(dir)) { const p = path.join(dir, f); if (fs.statSync(p).isDirectory()) list(p); else if (p.endsWith(".java")) javaFiles.push(p); } })(path.join(back, "src/main/java"));
for (const file of javaFiles) {
  if (/\/(intelligence\/agent\/(ReportAgent|SubmitReportSchema)|intelligence\/ai\/|i18n\/)/.test(file)) continue; // prompts to the AI, not screen text
  for (const line of fs.readFileSync(file, "utf8").split("\n")) {
    if (/LOG\.|log\.(info|warn|error|debug)|@Pattern|@Query|Pattern\.compile|regexp/.test(line)) continue;
    for (const m of line.matchAll(/"((?:[^"\\]|\\.)*)"/g)) {
      let s = m[1].replace(/\\n/g, " ").replace(/\\"/g, '"');
      if (/%s/.test(s)) continue;
      s = s.replace(/%[-+0-9.,]*[dfe]/g, "\u0000");
      if (!SPANISH_HINT.test(s) || !/\s/.test(s.trim())) { if (!/^[A-ZÁÉÍÓÚ][a-záéíóúñ]+( [a-záéíóúñ]+){0,4}$/.test(s.trim())) continue; }
      add(s);
    }
  }
}
const list = [...texts].sort();
fs.writeFileSync(out, list.join("\n") + "\n");
console.log(`frontend: ${frontCount} · backend: ${texts.size - frontCount} · total: ${list.length}`);
