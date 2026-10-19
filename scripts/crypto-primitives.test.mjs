import test from "node:test";
import assert from "node:assert/strict";
import { webcrypto } from "node:crypto";
import { decryptBytes, encryptBytes, generateDocumentKey } from "../src/crypto/aes-gcm.mjs";

const bytes = (value) => new TextEncoder().encode(value);
const text = (value) => new TextDecoder().decode(value);

test("generates a non-extractable AES-256-GCM document key", async () => {
  const key = await generateDocumentKey(webcrypto);
  assert.equal(key.algorithm.name, "AES-GCM");
  assert.equal(key.algorithm.length, 256);
  assert.equal(key.extractable, false);
  assert.deepEqual(key.usages.sort(), ["decrypt", "encrypt"]);
});

test("encrypts and decrypts bytes with authenticated associated data", async () => {
  const key = await generateDocumentKey(webcrypto);
  const plaintext = bytes("Kaagaz Vault crypto foundation");
  const aad = bytes("vault-id:demo;file-id:42;version:1");
  const envelope = await encryptBytes(plaintext, key, aad, webcrypto);
  assert.equal(envelope.version, 1);
  assert.equal(envelope.algorithm, "AES-256-GCM");
  assert.equal(envelope.nonce.byteLength, 12);
  assert.notDeepEqual(envelope.ciphertext, plaintext);
  assert.equal(text(await decryptBytes(envelope, key, aad, webcrypto)), text(plaintext));
});

test("fails closed when ciphertext or authenticated metadata is modified", async () => {
  const key = await generateDocumentKey(webcrypto);
  const envelope = await encryptBytes(bytes("private"), key, bytes("file:one"), webcrypto);
  const tampered = { ...envelope, ciphertext: envelope.ciphertext.slice() };
  tampered.ciphertext[0] ^= 1;
  await assert.rejects(decryptBytes(tampered, key, bytes("file:one"), webcrypto));
  await assert.rejects(decryptBytes(envelope, key, bytes("file:two"), webcrypto));
});

test("rejects wrong keys, unsupported formats, malformed nonces and truncated ciphertext", async () => {
  const key = await generateDocumentKey(webcrypto);
  const otherKey = await generateDocumentKey(webcrypto);
  const envelope = await encryptBytes(bytes("private"), key, undefined, webcrypto);
  await assert.rejects(decryptBytes(envelope, otherKey, undefined, webcrypto));
  await assert.rejects(decryptBytes({ ...envelope, version: 999 }, key));
  await assert.rejects(decryptBytes({ ...envelope, nonce: new Uint8Array(8) }, key));
  await assert.rejects(decryptBytes({ ...envelope, ciphertext: new Uint8Array(4) }, key));
});

test("validates input types and has no insecure crypto fallback", async () => {
  await assert.rejects(encryptBytes("not bytes", await generateDocumentKey(webcrypto), undefined, webcrypto), TypeError);
  await assert.rejects(generateDocumentKey({}), /Web Crypto API is required/);
});
