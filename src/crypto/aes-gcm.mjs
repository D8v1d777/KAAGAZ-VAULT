/**
 * Narrow Web Crypto AES-256-GCM primitive wrapper.
 *
 * This is a Phase 3 foundation, NOT a complete E2EE protocol. It deliberately
 * does not implement Argon2id, VEK wrapping, chunked files, recovery, storage,
 * or a production key lifecycle. Do not use with real documents until those
 * pieces and an independent security review are complete.
 */
const ALGORITHM = "AES-GCM";
const KEY_LENGTH = 256;
const NONCE_LENGTH_BYTES = 12;
const TAG_LENGTH_BITS = 128;

function getCrypto(cryptoProvider) {
  const provider = cryptoProvider ?? globalThis.crypto;
  if (!provider?.subtle || !provider?.getRandomValues) {
    throw new Error("Web Crypto API is required; no insecure fallback is available.");
  }
  return provider;
}

function toBytes(value, name) {
  if (!(value instanceof Uint8Array)) {
    throw new TypeError(name + " must be a Uint8Array.");
  }
  return value;
}

export async function generateDocumentKey(cryptoProvider) {
  const provider = getCrypto(cryptoProvider);
  return provider.subtle.generateKey(
    { name: ALGORITHM, length: KEY_LENGTH },
    false,
    ["encrypt", "decrypt"]
  );
}

export async function encryptBytes(plaintext, key, associatedData, cryptoProvider) {
  const provider = getCrypto(cryptoProvider);
  toBytes(plaintext, "plaintext");
  if (!key || key.type !== "secret" || key.algorithm?.name !== ALGORITHM) {
    throw new TypeError("A valid AES-GCM secret key is required.");
  }
  if (associatedData !== undefined) toBytes(associatedData, "associatedData");

  const nonce = provider.getRandomValues(new Uint8Array(NONCE_LENGTH_BYTES));
  const params = {
    name: ALGORITHM,
    iv: nonce,
    tagLength: TAG_LENGTH_BITS
  };
  if (associatedData !== undefined) params.additionalData = associatedData;

  const ciphertext = await provider.subtle.encrypt(params, key, plaintext);
  return Object.freeze({
    version: 1,
    algorithm: "AES-256-GCM",
    nonce: nonce.slice(),
    ciphertext: new Uint8Array(ciphertext)
  });
}

export async function decryptBytes(envelope, key, associatedData, cryptoProvider) {
  const provider = getCrypto(cryptoProvider);
  if (!envelope || envelope.version !== 1 || envelope.algorithm !== "AES-256-GCM") {
    throw new Error("Unsupported encrypted envelope version or algorithm.");
  }
  const nonce = toBytes(envelope.nonce, "nonce");
  const ciphertext = toBytes(envelope.ciphertext, "ciphertext");
  if (nonce.byteLength !== NONCE_LENGTH_BYTES) {
    throw new Error("Invalid AES-GCM nonce length.");
  }
  if (ciphertext.byteLength < TAG_LENGTH_BITS / 8) {
    throw new Error("Ciphertext is too short to contain an authentication tag.");
  }
  if (!key || key.type !== "secret" || key.algorithm?.name !== ALGORITHM) {
    throw new TypeError("A valid AES-GCM secret key is required.");
  }
  if (associatedData !== undefined) toBytes(associatedData, "associatedData");

  const params = {
    name: ALGORITHM,
    iv: nonce,
    tagLength: TAG_LENGTH_BITS
  };
  if (associatedData !== undefined) params.additionalData = associatedData;

  const plaintext = await provider.subtle.decrypt(params, key, ciphertext);
  return new Uint8Array(plaintext);
}
