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
  return bytes.slice();
}

function snapshotRecord(record, expectedKeys, name) {
  if (!record || typeof record !== "object" || Array.isArray(record)) {
    throw new TypeError(`${name} must be an object.`);
  }

  const ownKeys = Reflect.ownKeys(record);
  if (
    ownKeys.length !== expectedKeys.length ||
    ownKeys.some((key) => typeof key !== "string" || !expectedKeys.includes(key))
  ) {
    throw new TypeError(`${name} has an unexpected schema.`);
  }

  const snapshot = Object.create(null);
  for (const key of expectedKeys) {
    const descriptor = Object.getOwnPropertyDescriptor(record, key);
    if (!descriptor || !descriptor.enumerable || !("value" in descriptor)) {
      throw new TypeError(`${name} fields must be enumerable data properties.`);
    }
    snapshot[key] = descriptor.value;
  }
  return snapshot;
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
  const plaintextSnapshot = toBytes(plaintext, "plaintext").slice();
  validateKey(key, "encrypt");
  const aad = validateAssociatedData(associatedData);

  const nonce = provider.getRandomValues(new Uint8Array(NONCE_LENGTH_BYTES));
  const ciphertext = await provider.subtle.encrypt(
    { name: ALGORITHM, iv: nonce, additionalData: aad, tagLength: TAG_LENGTH_BITS },
    key,
    plaintextSnapshot
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
  const fields = snapshotRecord(
    envelope,
    ["version", "algorithm", "nonce", "ciphertext"],
    "envelope"
  );
  if (fields.version !== 1 || fields.algorithm !== "AES-256-GCM") {
    throw new Error("Unsupported encrypted envelope version or algorithm.");
  }
  const nonce = toBytes(fields.nonce, "nonce").slice();
  const ciphertext = toBytes(fields.ciphertext, "ciphertext").slice();
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
