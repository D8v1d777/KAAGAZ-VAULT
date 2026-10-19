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

test("changes to each authenticated semantic field produce different AAD", () => {
  const baseline = hex(encodeChunkAAD(metadata()));
  const changes = [
    { vaultId: "v2" },
    { documentId: "d2" },
    { documentVersion: 2 },
    { keyId: "k2" },
    { contentType: "application/pdf" },
    { chunkFormat: "chunked-v2" },
    { chunkIndex: 1 }
  ];
  for (const change of changes) {
    if (change.chunkFormat) {
      assert.throws(() => encodeChunkAAD({ ...metadata(), ...change }), /chunkFormat/);
    } else {
      assert.notEqual(hex(encodeChunkAAD({ ...metadata(), ...change })), baseline);
    }
  }
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
