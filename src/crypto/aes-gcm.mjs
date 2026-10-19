const ALGORITHM = "AES-GCM";
const KEY_LENGTH = 256;
const NONCE_LENGTH_BYTES = 12;
const TAG_LENGTH_BITS = 128;

function getCrypto(cryptoProvider) {
  const provider = cryptoProvider ?? globalThis.crypto;
  if (!provider?.subtle || typeof provider.getRandomValues !== "function") {
    throw new Error("Web Crypto API is required; refusing insecure fallback.");
  }
  return provider;
}

function toBytes(value, name) {
  if (!(value instanceof Uint8Array)) {
    throw new TypeError(`${name} must be a Uint8Array.`);
  }
  return value;
}

function validateAssociatedData(value) {
  const bytes = toBytes(value, "associatedData");
  if (bytes.byteLength === 0) {
    throw new TypeError("associatedData must not be empty.");
  }
  return bytes;
}

function validateKey(key, usage) {
  if (
    !key ||
    key.type !== "secret" ||
    key.algorithm?.name !== ALGORITHM ||
    key.algorithm?.length !== KEY_LENGTH ||
    !key.usages?.includes(usage)
  ) {
    throw new TypeError(`A valid AES-256-GCM key with ${usage} usage is required.`);
  }
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
  validateKey(key, "encrypt");
  const aad = validateAssociatedData(associatedData);

  const nonce = provider.getRandomValues(new Uint8Array(NONCE_LENGTH_BYTES));
  const ciphertext = await provider.subtle.encrypt(
    { name: ALGORITHM, iv: nonce, additionalData: aad, tagLength: TAG_LENGTH_BITS },
    key,
    plaintext
  );

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
  validateKey(key, "decrypt");
  const aad = validateAssociatedData(associatedData);

  const plaintext = await provider.subtle.decrypt(
    { name: ALGORITHM, iv: nonce, additionalData: aad, tagLength: TAG_LENGTH_BITS },
    key,
    ciphertext
  );
  return new Uint8Array(plaintext);
}
