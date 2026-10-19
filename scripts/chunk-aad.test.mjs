import test from "node:test";
import assert from "node:assert/strict";
import { webcrypto } from "node:crypto";
import { decryptBytes, encryptBytes, generateDocumentKey } from "../src/crypto/aes-gcm.mjs";
import { encodeChunkAAD } from "../src/crypto/chunk-aad.mjs";

const metadata = () => ({
  vaultId: "v1",
  documentId: "d1",
  documentVersion: 1,
  keyId: "k1",
  contentType: "text/plain",
  chunkFormat: "chunked-v1",
  chunkIndex: 0
});
const hex = (bytes) => Buffer.from(bytes).toString("hex");

test("matches byte-exact AAD golden bytes (manual fixture; independent reproduction pending)", () => {
  const expected =
    "4b56414401" + // KVAD domain separator + protocol version
    "00027631" +   // vaultId: length 2, "v1"
    "00026431" +   // documentId: length 2, "d1"
    "00000001" +   // documentVersion: uint32 big-endian
    "00026b31" +   // keyId: length 2, "k1"
    "000a746578742f706c61696e" + // contentType: length 10, "text/plain"
    "01" +         // chunked-v1 enum
    "00000000";    // chunkIndex: uint32 big-endian
  assert.equal(hex(encodeChunkAAD(metadata())), expected);
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
    { vaultId: "" },
    { vaultId: "has space" },
    { vaultId: "v1\n" },
    { vaultId: "v1\r" },
    { documentId: "d1\u2028" },
    { documentId: "x".repeat(129) },
    { keyId: "slash/name" },
    { keyId: "k1\u2029" },
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
