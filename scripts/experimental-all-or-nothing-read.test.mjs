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
        return chunk.index === 0 ? plainA.slice() : plainB.slice();
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
      return plainA.slice();
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

test("rejects an oversized content type before fetching any chunk", async () => {
  const f = fixture();
  f.manifest.contentType = "x".repeat(257);
  let fetchCalls = 0;
  await assert.rejects(f.run({
    fetchChunk: async () => { fetchCalls++; return f.chunks[0]; }
  }), /Unable to read encrypted document/);
  assert.equal(fetchCalls, 0);
});

test("copies each verified chunk into private output before awaiting the next chunk", async () => {
  const f = fixture();
  const firstPlaintext = new Uint8Array([1, 2, 3]);
  const result = await f.run({
    decryptChunk: async (chunk) => {
      if (chunk.index === 0) return firstPlaintext;
      firstPlaintext.fill(99);
      return plainB;
    }
  });
  assert.deepEqual([...result], [1, 2, 3, 4, 5]);
});

test("does not expose mutable manifest nonce buffers to the decrypt adapter", async () => {
  const f = fixture();
  const result = await f.run({
    decryptChunk: async (chunk, context) => {
      assert.equal(Object.isFrozen(context), true);
      assert.equal("chunks" in context, false);
      assert.equal(context.vaultId, "vault_1");
      assert.equal(context.documentId, "doc_1");
      assert.equal(context.documentVersion, 1);
      assert.equal(context.keyId, "key_1");
      return chunk.index === 0 ? plainA.slice() : plainB.slice();
    }
  });
  assert.deepEqual([...result], [1, 2, 3, 4, 5]);
});

test("normalizes chunk-fetch failures and never returns partial plaintext", async () => {
  const f = fixture();
  let calls = 0;
  await assert.rejects(f.run({
    fetchChunk: async (index) => {
      calls++;
      if (index === 1) throw new Error("storage backend detail");
      return f.chunks[index];
    }
  }), (error) => {
    assert.equal(error.message, "Unable to read encrypted document.");
    return true;
  });
  assert.equal(calls, 2);
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
      return chunk.index === 0 ? plainA.slice() : plainB.slice();
    }
  });
  const result = await promise;
  assert.deepEqual([...result], [1, 2, 3, 4, 5]);
  assert.equal(captured[0].ciphertext[0], 10);
  assert.equal(captured[0].nonce[0], 1);
});

test("rejects sparse or augmented manifest chunk arrays before fetching", async () => {
  for (const mutate of [
    (chunks) => { delete chunks[1]; },
    (chunks) => { chunks.extra = true; }
  ]) {
    const f = fixture();
    mutate(f.manifest.chunks);
    let fetchCalls = 0;
    await assert.rejects(f.run({
      fetchChunk: async (index) => { fetchCalls++; return f.chunks[index]; }
    }), /Unable to read encrypted document/);
    assert.equal(fetchCalls, 0);
  }
});

test("rejects accessor manifest fields without invoking getters", async () => {
  const f = fixture();
  let getterCalls = 0;
  Object.defineProperty(f.manifest, "vaultId", {
    enumerable: true,
    configurable: true,
    get() { getterCalls++; return "vault_1"; }
  });
  let fetchCalls = 0;
  await assert.rejects(f.run({
    fetchChunk: async (index) => { fetchCalls++; return f.chunks[index]; }
  }), /Unable to read encrypted document/);
  assert.equal(getterCalls, 0);
  assert.equal(fetchCalls, 0);
});

test("rejects unexpected fetched-chunk fields before decrypting that chunk", async () => {
  const f = fixture();
  f.chunks[1].storageKey = "unexpected";
  const decrypted = [];
  await assert.rejects(f.run({
    decryptChunk: async (chunk) => {
      decrypted.push(chunk.index);
      return chunk.index === 0 ? plainA.slice() : plainB.slice();
    }
  }), /Unable to read encrypted document/);
  assert.deepEqual(decrypted, [0]);
});

test("manifest-authentication failure stops before fetching or decrypting", async () => {
  const f = fixture();
  let fetchCalls = 0;
  let decryptCalls = 0;
  await assert.rejects(f.run({
    authenticateManifest: async () => { throw new Error("private adapter detail"); },
    fetchChunk: async (index) => { fetchCalls++; return f.chunks[index]; },
    decryptChunk: async () => { decryptCalls++; return plainA.slice(); }
  }), (error) => error.message === "Unable to read encrypted document.");
  assert.equal(fetchCalls, 0);
  assert.equal(decryptCalls, 0);
});

test("clears the adapter-owned plaintext buffer after copying it to unpublished output", async () => {
  const f = fixture();
  const ownedPlaintext = new Uint8Array([1, 2, 3]);
  const result = await f.run({
    decryptChunk: async (chunk) => chunk.index === 0 ? ownedPlaintext : plainB.slice()
  });
  assert.deepEqual([...result], [1, 2, 3, 4, 5]);
  assert.deepEqual([...ownedPlaintext], [0, 0, 0]);
});

test("rejects and clears a plaintext chunk with the wrong declared length", async () => {
  const f = fixture();
  const invalidPlaintext = new Uint8Array([9]);
  await assert.rejects(f.run({
    decryptChunk: async (chunk) => chunk.index === 0 ? invalidPlaintext : plainB.slice()
  }), /Unable to read encrypted document/);
  assert.deepEqual([...invalidPlaintext], [0]);
});
