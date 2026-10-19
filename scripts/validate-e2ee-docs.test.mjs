import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";

const read = (path) => readFileSync(new URL(path, import.meta.url), "utf8");
const e2ee = read("../docs/E2EE-ARCHITECTURE.md");
const research = read("../docs/RESEARCH-EVIDENCE.md");
const plan = read("../docs/PHASE-PLAN.md");
const architecture = read("../docs/ARCHITECTURE.md");
const report = read("../docs/phase-2-report.html");

test("E2EE documentation states the security boundary and does not claim implementation", () => {
  assert.match(e2ee, /not a complete E2EE implementation/i);
  assert.match(e2ee, /service must not possess a decryption key/i);
  assert.match(e2ee, /Do not call the application end-to-end encrypted until/i);
  assert.match(e2ee, /malicious application update/i);
  assert.match(e2ee, /metadata/i);
});

test("key lifecycle includes passphrase separation, recovery, and nonce-safety requirements", () => {
  assert.match(e2ee, /Argon2id/);
  assert.match(e2ee, /unique random 96-bit nonce/i);
  assert.match(e2ee, /Never retry encryption with the same key\/nonce pair/i);
  assert.match(e2ee, /recovery secret/i);
  assert.match(e2ee, /account login credential and vault passphrase have different purposes/i);
});

test("research separates published evidence from design hypotheses and provides measurable validation", () => {
  for (const phrase of ["W3C", "WCAG 2.2", "NIST", "Security Fatigue", "hypotheses", "90%", "Evidence limitations"]) {
    assert.ok(research.includes(phrase), "Missing research criterion: " + phrase);
  }
  assert.match(research, /not universal psychological laws/i);
});

test("delivery plan keeps implementation and independent review as explicit incomplete gates", () => {
  assert.match(plan, /\| 3\. Functional storage and crypto \|[^\n]*\| \*\*0% — not started\*\*/);
  assert.match(plan, /independent review/i);
  assert.match(plan, /Do not claim E2EE or production security until implemented/i);
  assert.match(architecture, /E2EE architecture decision/i);
});

test("visual report exposes the E2EE decision and human-centered release blockers", () => {
  for (const phrase of ["End-to-end encryption decision", "Human-centered security", "Release blockers", "Independent security review"]) {
    assert.ok(report.includes(phrase), "Missing report content: " + phrase);
  }
});
