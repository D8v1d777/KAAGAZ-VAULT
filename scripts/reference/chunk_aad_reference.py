#!/usr/bin/env python3
"""Independent reference encoder for the KVAD v1 golden-vector fixture.

This deliberately uses Python's struct/bytes primitives rather than the
JavaScript encoder implementation. It is a review aid, not a production
cryptography implementation or a substitute for human protocol review.
"""
from __future__ import annotations

import re
import struct

GOLDEN_HEX = (
    "4b56414401"
    "00027631"
    "00026431"
    "00000001"
    "00026b31"
    "000a746578742f706c61696e"
    "01"
    "00000000"
)
ID_RE = re.compile(r"^[A-Za-z0-9_-]{1,128}$")
CONTENT_TYPES = {
    "application/octet-stream",
    "application/pdf",
    "image/jpeg",
    "image/png",
    "image/webp",
    "text/plain",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "application/vnd.openxmlformats-officedocument.presentationml.presentation",
}


def _id(value: str) -> bytes:
    if not isinstance(value, str) or not ID_RE.fullmatch(value):
        raise ValueError("identifier must be 1-128 ASCII letters, digits, '_' or '-'")
    return value.encode("ascii")


def _lp16(value: bytes) -> bytes:
    if not 1 <= len(value) <= 0xFFFF:
        raise ValueError("length-prefixed field out of bounds")
    return struct.pack(">H", len(value)) + value


def encode_chunk_aad(metadata: dict[str, object]) -> bytes:
    fields = {
        "vaultId", "documentId", "documentVersion", "keyId",
        "contentType", "chunkFormat", "chunkIndex",
    }
    if not isinstance(metadata, dict) or set(metadata) != fields:
        raise ValueError("metadata must contain exactly the documented fields")

    version = metadata["documentVersion"]
    index = metadata["chunkIndex"]
    content_type = metadata["contentType"]
    if type(version) is not int or not 1 <= version <= 0xFFFFFFFF:
        raise ValueError("documentVersion out of range")
    if type(index) is not int or not 0 <= index <= 0xFFFFFFFF:
        raise ValueError("chunkIndex out of range")
    if not isinstance(content_type, str) or content_type not in CONTENT_TYPES:
        raise ValueError("unsupported content type")
    if metadata["chunkFormat"] != "chunked-v1":
        raise ValueError("unsupported chunk format")

    return b"".join((
        b"KVAD",
        bytes((1,)),
        _lp16(_id(metadata["vaultId"])),
        _lp16(_id(metadata["documentId"])),
        struct.pack(">I", version),
        _lp16(_id(metadata["keyId"])),
        _lp16(content_type.encode("ascii")),
        bytes((1,)),
        struct.pack(">I", index),
    ))


def main() -> None:
    fixture = {
        "vaultId": "v1",
        "documentId": "d1",
        "documentVersion": 1,
        "keyId": "k1",
        "contentType": "text/plain",
        "chunkFormat": "chunked-v1",
        "chunkIndex": 0,
    }
    actual = encode_chunk_aad(fixture).hex()
    assert actual == GOLDEN_HEX, f"golden mismatch: {actual}"
    assert encode_chunk_aad({**fixture, "chunkIndex": 1}) != bytes.fromhex(GOLDEN_HEX)

    # Keep the independent reference grammar aligned with JavaScript's strict ID tests.
    invalid_ids = [
        ("vaultId", "v1\n"),
        ("vaultId", "v1\r"),
        ("documentId", "d1\u2028"),
        ("keyId", "k1\u2029"),
        ("vaultId", "has space"),
        ("keyId", "slash/name"),
        ("documentId", "x" * 129),
    ]
    for field, invalid in invalid_ids:
        try:
            encode_chunk_aad({**fixture, field: invalid})
        except (ValueError, UnicodeEncodeError):
            continue
        raise AssertionError(f"invalid identifier accepted: {field}={invalid!r}")

    print(f"KVAD v1 golden vector verified: {actual} ({len(bytes.fromhex(actual))} bytes)")
    print(f"invalid identifier cases rejected: {len(invalid_ids)}/{len(invalid_ids)}")


if __name__ == "__main__":
    main()
