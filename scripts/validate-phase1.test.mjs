import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import vm from "node:vm";

const html = readFileSync(new URL("../index.html", import.meta.url), "utf8");
const css = html.match(/<style>([\s\S]*?)<\/style>/)?.[1] ?? "";
const js = html.match(/<script>([\s\S]*?)<\/script>/)?.[1] ?? "";

function luminance(hex) {
  const rgb = hex.replace("#", "").match(/.{2}/g).map(v => parseInt(v, 16) / 255);
  const linear = rgb.map(v => v <= 0.04045 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4);
  return 0.2126 * linear[0] + 0.7152 * linear[1] + 0.0722 * linear[2];
}
function contrast(a, b) {
  const values = [luminance(a), luminance(b)].sort((x, y) => y - x);
  return (values[0] + 0.05) / (values[1] + 0.05);
}

test("document has language, title, landmarks and skip navigation", () => {
  assert.match(html, /<html lang="en">/);
  assert.match(html, /<title>Kaagaz Vault/);
  assert.match(html, /<a class="skip" href="#main">Skip to content<\/a>/);
  assert.match(html, /<aside class="side" aria-label="Main navigation">/);
  assert.match(html, /<main class="main" id="main">/);
});

test("keyboard focus, reduced motion and responsive breakpoints are present", () => {
  assert.match(css, /:focus-visible/);
  assert.match(css, /prefers-reduced-motion:reduce/);
  assert.match(css, /@media\(max-width:800px\)/);
  assert.match(css, /@media\(max-width:540px\)/);
  assert.match(html, /document\.addEventListener\("keydown"/);
});

test("mobile navigation keeps accessible names when visual labels are hidden", () => {
  for (const label of ["All documents", "Recently added", "Favourites", "Identity", "Finance", "Health", "Home and property", "Work and education"]) {
    assert.ok(html.includes(label), "Expected accessible label: " + label);
  }
  assert.match(html, /aria-label="Increase text size"/);
  assert.match(html, /aria-label="Help and guidance"/);
});

test("primary muted text colors meet 4.5:1 against their intended light surfaces", () => {
  assert.ok(contrast("#626f65", "#ffffff") >= 4.5, "muted text on white");
  assert.ok(contrast("#626f65", "#f6f7f4") >= 4.5, "muted text on app background");
  assert.ok(contrast("#53685a", "#edf3e9") >= 4.5, "hero body text on green-tinted surface");
  assert.ok(contrast("#4d6b54", "#edf3e9") >= 4.5, "hero eyebrow on green-tinted surface");
  assert.ok(contrast("#ffffff", "#28654f") >= 4.5, "primary button text");
});

test("interactive targets and document actions have minimum dimensions", () => {
  assert.match(css, /height:44px;cursor:pointer;font-size:14px/);
  assert.match(css, /min-width:44px;min-height:44px/);
  assert.match(css, /\.details\{[^}]*min-height:32px/);
  assert.match(html, /id="gridView" aria-pressed="true"/);
  assert.match(html, /id="listView" aria-pressed="false"/);
});

test("file selection is explicitly preview-only and rejects unsupported or oversized files", () => {
  assert.match(html, /Prototype preview: sample records are illustrative/);
  assert.match(js, /const allowed=new Set\(\["PDF","PNG","JPG","JPEG","WEBP","DOC","DOCX","TXT"\]\)/);
  assert.match(js, /const maxBytes=20\*1024\*1024/);
  assert.match(js, /The file contents have not been read or uploaded/);
  assert.match(html, /not uploaded, saved after refresh, or protected by a vault backend/);
});

test("user-controlled document strings are HTML-escaped before insertion", () => {
  assert.match(js, /const esc=s=>String\(s\)\.replace/);
  assert.match(js, /esc\(d\.name\)/);
  assert.match(js, /esc\(d\.category\)/);
});

test("inline JavaScript parses without syntax errors", () => {
  assert.ok(js.length > 1000, "inline app script should exist");
  assert.doesNotThrow(() => new vm.Script(js, { filename: "index.html:inline-script" }));
});

test("the prototype does not import third-party runtime scripts or styles", () => {
  assert.doesNotMatch(html, /<script[^>]+src=/i);
  assert.doesNotMatch(html, /<link[^>]+rel=["']stylesheet["'][^>]+href=["']https?:/i);
});
