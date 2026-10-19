const MAGIC = [0x4b, 0x56, 0x41, 0x44]; // ASCII "KVAD" — domain separation.
const PROTOCOL_VERSION = 1;
const MAX_U32 = 0xffff_ffff;
// Use an absolute-end assertion: JavaScript's $ can match before a final line terminator.
const ID_PATTERN = /^[A-Za-z0-9_-]{1,128}(?![\\s\\S])/;
const ALLOWED_CONTENT_TYPES = new Set([
  "application/octet-stream",
  "application/pdf",
  "image/jpeg",
  "image/png",
  "image/webp",
  "text/plain",
  "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
  "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
  "application/vnd.openxmlformats-officedocument.presentationml.presentation"
]);

const FIELDS = [
  "vaultId",
  "documentId",
  "documentVersion",
  "keyId",
  "contentType",
  "chunkFormat",
  "chunkIndex"
];

function assertRecord(record) {
  if (record === null || typeof record !== "object" || Array.isArray(record)) {
    throw new TypeError("AAD metadata must be a plain record.");
  }
  const prototype = Object.getPrototypeOf(record);
  if (prototype !== Object.prototype && prototype !== null) {
    throw new TypeError("AAD metadata must be a plain record.");
  }

  // Object.keys() omits symbol and non-enumerable properties. Reflect.ownKeys()
  // makes the exact-schema check cover every own property, not only enumerable strings.
  const keys = Reflect.ownKeys(record);
  if (keys.length !== FIELDS.length || keys.some((key) => typeof key !== "string" || !FIELDS.includes(key))) {
    throw new TypeError("AAD metadata must contain exactly the documented fields.");
  }

  // Reject getters/setters so validation and serialization cannot invoke caller code
  // or observe values that change between reads.
  for (const key of FIELDS) {
    const descriptor = Object.getOwnPropertyDescriptor(record, key);
    if (!descriptor || !descriptor.enumerable || !("value" in descriptor)) {
      throw new TypeError("AAD metadata fields must be enumerable data properties.");
    }
  }
}

function encodeId(value, name) {
  if (typeof value !== "string" || !ID_PATTERN.test(value)) {
    throw new TypeError(`${name} must be 1–128 ASCII letters, digits, underscores or hyphens.`);
  }
  return new TextEncoder().encode(value);
}

function encodeU16LengthPrefixed(bytes, name) {
  if (bytes.byteLength < 1 || bytes.byteLength > 0xffff) {
    throw new RangeError(`${name} byte length is outside the supported range.`);
  }
  return [bytes.byteLength >>> 8, bytes.byteLength & 0xff, ...bytes];
}

function encodeU32(value, name, allowZero) {
  if (!Number.isSafeInteger(value) || value < (allowZero ? 0 : 1) || value > MAX_U32) {
    throw new RangeError(`${name} must be an integer in the supported unsigned 32-bit range.`);
  }
  return [
    (value >>> 24) & 0xff,
    (value >>> 16) & 0xff,
    (value >>> 8) & 0xff,
    value & 0xff
  ];
}

/**
 * Encode the Phase 3 chunk AAD schema as one unambiguous byte sequence.
 * This module deliberately has no decoder: ciphertext consumers must not
 * infer semantic metadata by parsing attacker-controlled AAD.
 */
export function encodeChunkAAD(metadata) {
  assertRecord(metadata);

  const vaultId = encodeId(metadata.vaultId, "vaultId");
  const documentId = encodeId(metadata.documentId, "documentId");
  const keyId = encodeId(metadata.keyId, "keyId");

  if (!Number.isSafeInteger(metadata.documentVersion) ||
      metadata.documentVersion < 1 || metadata.documentVersion > MAX_U32) {
    throw new RangeError("documentVersion must be an integer from 1 to 2^32-1.");
  }
  if (!Number.isSafeInteger(metadata.chunkIndex) ||
      metadata.chunkIndex < 0 || metadata.chunkIndex > MAX_U32) {
    throw new RangeError("chunkIndex must be an integer from 0 to 2^32-1.");
  }
  if (typeof metadata.contentType !== "string" ||
      !ALLOWED_CONTENT_TYPES.has(metadata.contentType)) {
    throw new TypeError("contentType is not in the supported canonical allowlist.");
  }
  if (metadata.chunkFormat !== "chunked-v1") {
    throw new TypeError("Unsupported chunkFormat.");
  }

  const contentType = new TextEncoder().encode(metadata.contentType);
  const bytes = [
    ...MAGIC,
    PROTOCOL_VERSION,
    ...encodeU16LengthPrefixed(vaultId, "vaultId"),
    ...encodeU16LengthPrefixed(documentId, "documentId"),
    ...encodeU32(metadata.documentVersion, "documentVersion", false),
    ...encodeU16LengthPrefixed(keyId, "keyId"),
    ...encodeU16LengthPrefixed(contentType, "contentType"),
    1, // chunkFormat enum: chunked-v1
    ...encodeU32(metadata.chunkIndex, "chunkIndex", true)
  ];
  return new Uint8Array(bytes);
}
