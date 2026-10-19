// EXPERIMENTAL ONLY. Not wired to storage, preview, OCR, export, or UI.
// Manifest authentication and chunk decryption must be supplied by reviewed adapters.
// This module does not define a wire format, key hierarchy, nonce policy, or freshness guarantee.

const NONCE_BYTES = 12;
const GCM_TAG_BYTES = 16;
const MANIFEST_KEYS = [
  "version",
  "vaultId",
  "documentId",
  "documentVersion",
  "keyId",
  "contentType",
  "chunkFormat",
  "chunkCount",
  "totalPlaintextLength",
  "chunks"
];
const CHUNK_KEYS = ["index", "nonce", "ciphertextLength", "plaintextLength"];

function fail() {
  // Keep external failures non-sensitive and uniform.
  throw new Error("Unable to read encrypted document.");
}

function isRecord(value) {
  return value !== null && typeof value === "object" && !Array.isArray(value);
}

function exactDataRecord(value, keys) {
  if (!isRecord(value)) fail();
  const ownKeys = Reflect.ownKeys(value);
  if (ownKeys.length !== keys.length ||
      ownKeys.some((key) => typeof key !== "string" || !keys.includes(key))) fail();
  const snapshot = Object.create(null);
  for (const key of keys) {
    const descriptor = Object.getOwnPropertyDescriptor(value, key);
    if (!descriptor || !descriptor.enumerable || !("value" in descriptor)) fail();
    snapshot[key] = descriptor.value;
  }
  return snapshot;
}

function safeInteger(value, min, max) {
  return Number.isSafeInteger(value) && value >= min && value <= max;
}

function validateManifest(input, limits) {
  const m = exactDataRecord(input, MANIFEST_KEYS);
  if (m.version !== 1 || m.chunkFormat !== "chunked-v1" ||
      typeof m.vaultId !== "string" || m.vaultId.length < 1 || m.vaultId.length > 128 ||
      typeof m.documentId !== "string" || m.documentId.length < 1 || m.documentId.length > 128 ||
      typeof m.keyId !== "string" || m.keyId.length < 1 || m.keyId.length > 128 ||
      !safeInteger(m.documentVersion, 1, 0xffff_ffff) ||
      typeof m.contentType !== "string" ||
      !safeInteger(m.chunkCount, 1, limits.maxChunks) ||
      !safeInteger(m.totalPlaintextLength, 0, limits.maxDocumentBytes) ||
      !Array.isArray(m.chunks) || m.chunks.length !== m.chunkCount) fail();

  const chunks = [];
  let declaredTotal = 0;
  for (let i = 0; i < m.chunks.length; i++) {
    const c = exactDataRecord(m.chunks[i], CHUNK_KEYS);
    if (c.index !== i ||
        !(c.nonce instanceof Uint8Array) || c.nonce.byteLength !== NONCE_BYTES ||
        !safeInteger(c.ciphertextLength, GCM_TAG_BYTES, limits.maxChunkCiphertextBytes) ||
        !safeInteger(c.plaintextLength, 0, limits.maxChunkPlaintextBytes) ||
        c.ciphertextLength !== c.plaintextLength + GCM_TAG_BYTES) fail();
    declaredTotal += c.plaintextLength;
    if (!Number.isSafeInteger(declaredTotal) || declaredTotal > limits.maxDocumentBytes) fail();
    chunks.push(Object.freeze({
      index: c.index,
      nonce: c.nonce.slice(),
      ciphertextLength: c.ciphertextLength,
      plaintextLength: c.plaintextLength
    }));
  }
  if (declaredTotal !== m.totalPlaintextLength) fail();

  return Object.freeze({
    version: m.version,
    vaultId: m.vaultId,
    documentId: m.documentId,
    documentVersion: m.documentVersion,
    keyId: m.keyId,
    contentType: m.contentType,
    chunkFormat: m.chunkFormat,
    chunkCount: m.chunkCount,
    totalPlaintextLength: m.totalPlaintextLength,
    chunks: Object.freeze(chunks)
  });
}

/**
 * Experimental all-or-nothing coordinator.
 * authenticateManifest(envelope) MUST authenticate before returning the manifest.
 * fetchChunk(index) returns { index, nonce: Uint8Array, ciphertext: Uint8Array }.
 * decryptChunk(chunk, manifest) MUST perform AEAD verification using reviewed AAD.
 * No plaintext is returned until every chunk has verified and all lengths match.
 */
export async function readAllOrNothing({
  manifestEnvelope,
  authenticateManifest,
  fetchChunk,
  decryptChunk,
  limits
}) {
  if (typeof authenticateManifest !== "function" ||
      typeof fetchChunk !== "function" ||
      typeof decryptChunk !== "function" ||
      !isRecord(limits)) fail();

  const effectiveLimits = {
    maxChunks: limits.maxChunks,
    maxDocumentBytes: limits.maxDocumentBytes,
    maxChunkPlaintextBytes: limits.maxChunkPlaintextBytes,
    maxChunkCiphertextBytes: limits.maxChunkCiphertextBytes
  };
  if (!safeInteger(effectiveLimits.maxChunks, 1, 100_000) ||
      !safeInteger(effectiveLimits.maxDocumentBytes, 1, 1_073_741_824) ||
      !safeInteger(effectiveLimits.maxChunkPlaintextBytes, 1, effectiveLimits.maxDocumentBytes) ||
      !safeInteger(effectiveLimits.maxChunkCiphertextBytes, GCM_TAG_BYTES + 1, effectiveLimits.maxChunkPlaintextBytes + GCM_TAG_BYTES)) fail();

  try {
    const authenticated = await authenticateManifest(manifestEnvelope);
    const manifest = validateManifest(authenticated, effectiveLimits);
    const staged = [];
    let actualTotal = 0;

    for (let index = 0; index < manifest.chunkCount; index++) {
      const supplied = await fetchChunk(index);
      const chunk = exactDataRecord(supplied, ["index", "nonce", "ciphertext"]);
      const expected = manifest.chunks[index];
      if (chunk.index !== index ||
          !(chunk.nonce instanceof Uint8Array) ||
          !(chunk.ciphertext instanceof Uint8Array) ||
          chunk.nonce.byteLength !== NONCE_BYTES ||
          chunk.ciphertext.byteLength !== expected.ciphertextLength ||
          chunk.nonce.length !== expected.nonce.length ||
          chunk.nonce.some((byte, offset) => byte !== expected.nonce[offset])) fail();

      // Copy attacker-/caller-owned buffers before awaiting the decrypt adapter.
      const ciphertext = chunk.ciphertext.slice();
      const nonce = chunk.nonce.slice();
      const plaintext = await decryptChunk(
        Object.freeze({ index, nonce, ciphertext }),
        manifest
      );
      if (!(plaintext instanceof Uint8Array) ||
          plaintext.byteLength !== expected.plaintextLength) fail();
      const snapshot = plaintext.slice();
      actualTotal += snapshot.byteLength;
      if (!Number.isSafeInteger(actualTotal) || actualTotal > effectiveLimits.maxDocumentBytes) fail();
      staged.push(snapshot);
    }

    if (actualTotal !== manifest.totalPlaintextLength) fail();
    const result = new Uint8Array(actualTotal);
    let offset = 0;
    for (const chunk of staged) {
      result.set(chunk, offset);
      offset += chunk.byteLength;
    }
    return result;
  } catch {
    // Never return staged plaintext on any failure. JS cannot guarantee physical zeroization.
    fail();
  }
}
