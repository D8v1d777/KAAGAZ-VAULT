import test from "node:test";
import assert from "node:assert/strict";
import { readAllOrNothing } from "../src/crypto/experimental-all-or-nothing-read.mjs";

const limits = {
  maxChunks: 8,
  maxDocumentBytes: 1024,
  maxChunkPlaintextBytes: 512,
  maxChunkCiphertextBytes: 528
};
const nonceA = new Uint8Array(12).fill(1);
const nonceB = new Uint8Array(12).fill(2);
const plainA = new Uint8Array([1, 2, 3]);
const plainB = new Uint8Array([4, 5]);
const cipherA = new Uint8Array(19).fill(10);
const cipherB = new Uint8Array(18).fill(11);

function fixture() {
  const manifest = {
    version: 1,
    vaultId: "vault_1",
    documentId: "doc_1",
    documentVersion: 1,
    keyId: "key_1",
    contentType: "application/pdf",
    chunkFormat: "chunked-v1",
    chunkCount: 2,
    totalPlaintextLength: 5,
    chunks: [
      { index: 0, nonce: nonceA.slice(), ciphertextLength: 19, plaintextLength: 3 },
      { index: 1, nonce: nonceB.slice(), ciphertextLength: 18, plaintextLength: 2 }
    ]
  };
  const chunks = [
    { index: 0, nonce: nonceA.slice(), ciphertext: cipherA.slice() },
    { index: 1, nonce: nonceB.slice(), ciphertext: cipherB.slice() }
  ];
  const seen = [];
  return {
    manifest,
    chunks,
    seen,
    run: (overrides = {}) => readAllOrNothing({
      manifestEnvelope: { opaque: true },
      authenticateManifest: async () => manifest,
      fetchChunk: async (index) => chunks[index],
      decryptChunk: async (chunk) => {
        seen.push(chunk.index);
        return chunk.index === 0 ? plainA : plainB;
      },
      limits,
      ...overrides
    })
  };
}

test("publishes concatenated plaintext only after all chunks verify", async () => {
  const f = fixture();
  const result = await f.run();
  assert.deepEqual([...result], [1, 2, 3, 4, 5]);
  assert.deepEqual(f.seen, [0, 1]);
});

test("rejects missing, duplicate, and out-of-range chunk indexes", async () => {
  for (const index of [undefined, 0, 2]) {
    const f = fixture();
    f.chunks[1] = { ...f.chunks[1], index };
    await assert.rejects(f.run(), /Unable to read encrypted document/);
  }
});

test("rejects chunks returned in the wrong order", async () => {
  const f = fixture();
  f.chunks.reverse();
  await assert.rejects(f.run(), /Unable to read encrypted document/);
});

test("rejects a chunk nonce or ciphertext length that disagrees with authenticated manifest", async () => {
  const f = fixture();
  f.chunks[1].nonce[0] ^= 1;
  await assert.rejects(f.run(), /Unable to read encrypted document/);

  const g = fixture();
  g.chunks[1].ciphertext = new Uint8Array(17);
  await assert.rejects(g.run(), /Unable to read encrypted document/);
});

test("late authentication failure rejects without returning partial plaintext", async () => {
  const f = fixture();
  let published = false;
  const promise = f.run({
    decryptChunk: async (chunk) => {
      if (chunk.index === 1) throw new Error("tag failure");
      return plainA;
    }
  }).then(() => { published = true; });
  await assert.rejects(promise, /Unable to read encrypted document/);
  assert.equal(published, false);
});

test("rejects malformed manifest schema, count, and total length", async () => {
  for (const mutate of [
    (m) => { m.unexpected = true; },
    (m) => { m.chunkCount = 1; },
    (m) => { m.totalPlaintextLength = 4; },
    (m) => { m.chunks[0].index = 1; },
    (m) => { m.chunks[0].nonce = new Uint8Array(11); }
  ]) {
    const f = fixture();
    mutate(f.manifest);
    await assert.rejects(f.run(), /Unable to read encrypted document/);
  }
});

test("rejects oversized limits and never calls decrypt before manifest validation", async () => {
  const f = fixture();
  let decryptCalls = 0;
  f.manifest.chunkCount = 100;
  await assert.rejects(f.run({
    decryptChunk: async () => { decryptCalls++; return plainA; }
  }), /Unable to read encrypted document/);
  assert.equal(decryptCalls, 0);
});

test("snapshots ciphertext and nonce before the asynchronous decrypt adapter", async () => {
  const f = fixture();
  const captured = [];
  const promise = f.run({
    decryptChunk: async (chunk) => {
      captured.push(chunk);
      f.chunks[0].ciphertext.fill(99);
      f.chunks[0].nonce.fill(99);
      return chunk.index === 0 ? plainA : plainB;
    }
  });
  const result = await promise;
  assert.deepEqual([...result], [1, 2, 3, 4, 5]);
  assert.equal(captured[0].ciphertext[0], 10);
  assert.equal(captured[0].nonce[0], 1);
});
