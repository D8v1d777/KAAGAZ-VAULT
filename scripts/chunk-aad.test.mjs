import test from "node:test";
import { readFileSync } from "node:fs";
import assert from "node:assert/strict";
import { webcrypto } from "node:crypto";
import { decryptBytes, encryptBytes, generateDocumentKey } from "../src/crypto/aes-gcm.mjs";
import { encodeChunkAAD } from "../src/crypto/chunk-aad.mjs";

const vectors = JSON.parse(readFileSync(new URL("./reference/chunk_aad_vectors.json", import.meta.url), "utf8"));
const metadata = () => ({ ...vectors.valid.metadata });
const hex = (bytes) => Buffer.from(bytes).toString("hex");

test("matches byte-exact AAD golden bytes from the shared cross-runtime fixture", () => {
  assert.equal(hex(encodeChunkAAD(metadata())), vectors.valid.aadHex);
});

test("matches every shared cross-runtime golden vector", () => {
  for (const vector of vectors.additionalValidVectors ?? []) {
    assert.equal(
      hex(encodeChunkAAD(vector.metadata)),
      vector.aadHex,
      `golden vector mismatch: ${vector.name}`
    );
  }
});

test("supported authenticated metadata changes produce different AAD", () => {
  const baseline = hex(encodeChunkAAD(metadata()));
  const changes = [
    { vaultId: "v2" },
    { documentId: "d2" },
    { documentVersion: 2 },
    { keyId: "k2" },
    { contentType: "application/pdf" },
    { chunkIndex: 1 }
  ];
  for (const change of changes) {
    assert.notEqual(hex(encodeChunkAAD({ ...metadata(), ...change })), baseline);
  }
});

test("rejects unsupported chunk formats", () => {
  assert.throws(
    () => encodeChunkAAD({ ...metadata(), chunkFormat: "chunked-v2" }),
    /chunkFormat/
  );
});

test("rejects unknown/missing properties and non-record inputs", () => {
  assert.throws(() => encodeChunkAAD(null), /plain record/);
  assert.throws(() => encodeChunkAAD({ ...metadata(), ignored: true }), /exactly/);
  const { keyId, ...missing } = metadata();
  assert.equal(keyId, "k1");
  assert.throws(() => encodeChunkAAD(missing), /exactly/);
});

test("rejects symbol and non-enumerable extra properties", () => {
  const symbolExtra = metadata();
  symbolExtra[Symbol("unexpected")] = true;
  assert.throws(() => encodeChunkAAD(symbolExtra), /exactly/);

  const hiddenExtra = metadata();
  Object.defineProperty(hiddenExtra, "unexpected", { value: true });
  assert.throws(() => encodeChunkAAD(hiddenExtra), /exactly/);
});

test("rejects accessor fields without invoking them", () => {
  let getterCalled = false;
  const withGetter = metadata();
  Object.defineProperty(withGetter, "vaultId", {
    enumerable: true,
    configurable: true,
    get() {
      getterCalled = true;
      return "v1";
    }
  });
  assert.throws(() => encodeChunkAAD(withGetter), /data properties/);
  assert.equal(getterCalled, false);
});

test("rejects invalid identifiers, content types, formats and numeric boundaries", () => {
  for (const patch of [
    { contentType: "Text/Plain" },
    { contentType: "text/html" },
    { chunkFormat: "unknown" },
    { documentVersion: 0 },
    { documentVersion: 1.5 },
    { documentVersion: 0x1_0000_0000 },
    { chunkIndex: -1 },
    { chunkIndex: 1.5 },
    { chunkIndex: 0x1_0000_0000 }
  ]) {
    assert.throws(() => encodeChunkAAD({ ...metadata(), ...patch }));
  }
  for (const field of ["vaultId", "documentId", "keyId"]) {
    assert.doesNotThrow(() => encodeChunkAAD({ ...metadata(), [field]: "A".repeat(128) }));
  }
  for (const { field, value } of vectors.invalidIdentifiers) {
    assert.throws(() => encodeChunkAAD({ ...metadata(), [field]: value }));
  }
});

test("AES-GCM authentication fails if a bound metadata field changes", async () => {
  const key = await generateDocumentKey(webcrypto);
  const plain = new TextEncoder().encode("non-sensitive test fixture");
  const aad = encodeChunkAAD(metadata());
  const envelope = await encryptBytes(plain, key, aad, webcrypto);
  assert.deepEqual(
    new Uint8Array(await decryptBytes(envelope, key, aad, webcrypto)),
    plain
  );
  await assert.rejects(
    decryptBytes(envelope, key, encodeChunkAAD({ ...metadata(), documentVersion: 2 }), webcrypto)
  );
  await assert.rejects(
    decryptBytes(envelope, key, encodeChunkAAD({ ...metadata(), chunkIndex: 1 }), webcrypto)
  );
});


test("property corpus: deterministic encoding, field binding, and unique chunk indexes", () => {
  // Small seeded PRNG keeps this corpus reproducible without third-party packages.
  let state = 0x6d2b79f5;
  const next = () => {
    state ^= state << 13;
    state ^= state >>> 17;
    state ^= state << 5;
    return state >>> 0;
  };
  const id = (prefix) => {
    const alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_-";
    const length = 1 + (next() % 32);
    let result = prefix;
    while (result.length < length) result += alphabet[next() % alphabet.length];
    return result.slice(0, 128);
  };

  const seen = new Set();
  for (let i = 0; i < 256; i += 1) {
    const sample = {
      ...metadata(),
      vaultId: id("v"),
      documentId: id("d"),
      documentVersion: i + 1,
      keyId: id("k"),
      contentType: i % 2 === 0 ? "text/plain" : "application/pdf",
      chunkIndex: i
    };
    const encoded = encodeChunkAAD(sample);
    assert.ok(encoded instanceof Uint8Array, "encoder returns bytes");
    assert.deepEqual(encoded, encodeChunkAAD({ ...sample }), "equivalent records encode identically");
    assert.deepEqual([...encoded.slice(0, 5)], [0x4b, 0x56, 0x41, 0x44, 1], "magic and protocol version are stable");

    const encodedHex = hex(encoded);
    assert.ok(!seen.has(encodedHex), "distinct generated chunk metadata must not collide");
    seen.add(encodedHex);

    const changedContentType = sample.contentType === "text/plain" ? "application/pdf" : "text/plain";
    for (const patch of [
      { vaultId: id("x") },
      { documentId: id("y") },
      { documentVersion: sample.documentVersion + 1 },
      { keyId: id("z") },
      { contentType: changedContentType },
      { chunkIndex: sample.chunkIndex + 1 }
    ]) {
      assert.notDeepEqual(
        encodeChunkAAD({ ...sample, ...patch }),
        encoded,
        "changing any supported authenticated field changes AAD"
      );
    }
  }
});

test("accepts numeric encoding boundaries and every currently supported content type", () => {
  const contentTypes = [
    "application/octet-stream",
    "application/pdf",
    "image/jpeg",
    "image/png",
    "image/webp",
    "text/plain",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "application/vnd.openxmlformats-officedocument.presentationml.presentation"
  ];

  assert.doesNotThrow(() => encodeChunkAAD({
    ...metadata(),
    documentVersion: 1,
    chunkIndex: 0
  }));
  assert.doesNotThrow(() => encodeChunkAAD({
    ...metadata(),
    documentVersion: 0xffff_ffff,
    chunkIndex: 0xffff_ffff
  }));
  for (const contentType of contentTypes) {
    assert.doesNotThrow(() => encodeChunkAAD({ ...metadata(), contentType }), contentType);
  }
});

test("rejects adversarial numeric and identifier values", () => {
  for (const value of [NaN, Infinity, -Infinity, Number.MAX_SAFE_INTEGER, "1", null, 1n]) {
    assert.throws(() => encodeChunkAAD({ ...metadata(), documentVersion: value }));
    assert.throws(() => encodeChunkAAD({ ...metadata(), chunkIndex: value }));
  }
  for (const value of ["", " ", "v1\n", "v1\r", "v1\u2028", "é", "😀", "a".repeat(129)]) {
    assert.throws(() => encodeChunkAAD({ ...metadata(), vaultId: value }));
  }
});
