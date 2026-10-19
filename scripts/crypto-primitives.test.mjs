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
  assert.deepEqual([...key.usages].sort(), ["decrypt", "encrypt"]);
});

test("encrypts and decrypts bytes with mandatory authenticated associated data", async () => {
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
  const aad = bytes("file:one");
  const envelope = await encryptBytes(bytes("private"), key, aad, webcrypto);
  await assert.rejects(decryptBytes(envelope, otherKey, aad, webcrypto));
  await assert.rejects(decryptBytes({ ...envelope, version: 999 }, key, aad, webcrypto));
  await assert.rejects(decryptBytes({ ...envelope, nonce: new Uint8Array(8) }, key, aad, webcrypto));
  await assert.rejects(decryptBytes({ ...envelope, ciphertext: new Uint8Array(4) }, key, aad, webcrypto));
});

test("rejects missing or empty associated data and validates key size/usages", async () => {
  const key = await generateDocumentKey(webcrypto);
  await assert.rejects(encryptBytes(bytes("private"), key, undefined, webcrypto), /associatedData/);
  await assert.rejects(encryptBytes(bytes("private"), key, new Uint8Array(), webcrypto), /must not be empty/);
  await assert.rejects(decryptBytes({ version: 1, algorithm: "AES-256-GCM", nonce: new Uint8Array(12), ciphertext: new Uint8Array(16) }, key, undefined, webcrypto), /associatedData/);
  const aes128 = await webcrypto.subtle.generateKey({ name: "AES-GCM", length: 128 }, false, ["encrypt", "decrypt"]);
  await assert.rejects(encryptBytes(bytes("x"), aes128, bytes("file:one"), webcrypto), /AES-256-GCM key/);
  const encryptOnly = await webcrypto.subtle.generateKey({ name: "AES-GCM", length: 256 }, false, ["encrypt"]);
  await assert.rejects(decryptBytes({ version: 1, algorithm: "AES-256-GCM", nonce: new Uint8Array(12), ciphertext: new Uint8Array(16) }, encryptOnly, bytes("file:one"), webcrypto), /decrypt usage/);
});

test("reads envelope values from property descriptors, not ordinary property access", async () => {
  const key = await generateDocumentKey(webcrypto);
  const aad = bytes("file:proxy");
  const original = await encryptBytes(bytes("proxy-safe"), key, aad, webcrypto);
  let propertyRead = false;
  const wrapped = new Proxy(original, {
    get() {
      propertyRead = true;
      return null;
    }
  });
  assert.equal(text(await decryptBytes(wrapped, key, aad, webcrypto)), "proxy-safe");
  assert.equal(propertyRead, false);
});

test("rejects envelope accessor properties without evaluating them", async () => {
  const key = await generateDocumentKey(webcrypto);
  const aad = bytes("file:schema");
  const original = await encryptBytes(bytes("schema-safe"), key, aad, webcrypto);
  let invoked = false;
  const envelope = { ...original };
  Object.defineProperty(envelope, "nonce", {
    enumerable: true,
    get() { invoked = true; return original.nonce; }
  });
  await assert.rejects(decryptBytes(envelope, key, aad, webcrypto), /data properties/);
  assert.equal(invoked, false);
});

test("rejects unknown envelope fields and symbol keys", async () => {
  const key = await generateDocumentKey(webcrypto);
  const aad = bytes("file:schema");
  const original = await encryptBytes(bytes("schema-safe"), key, aad, webcrypto);
  await assert.rejects(decryptBytes({ ...original, extra: true }, key, aad, webcrypto), /unexpected schema/);
  const withSymbol = { ...original, [Symbol("extra")]: true };
  await assert.rejects(decryptBytes(withSymbol, key, aad, webcrypto), /unexpected schema/);
});

test("documents the Web Crypto wrapping limitation for non-extractable document keys", async () => {
  const documentKey = await generateDocumentKey(webcrypto);
  const wrappingKey = await webcrypto.subtle.generateKey(
    { name: "AES-KW", length: 256 },
    false,
    ["wrapKey", "unwrapKey"]
  );
  await assert.rejects(
    webcrypto.subtle.wrapKey("raw", documentKey, wrappingKey, "AES-KW")
  );
});

test("validates input types and has no insecure crypto fallback", async () => {
  const key = await generateDocumentKey(webcrypto);
  await assert.rejects(encryptBytes("not bytes", key, bytes("file:one"), webcrypto), TypeError);
  await assert.rejects(generateDocumentKey({}), /Web Crypto API is required/);
});
