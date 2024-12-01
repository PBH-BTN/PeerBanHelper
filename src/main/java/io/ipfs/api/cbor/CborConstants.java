package io.ipfs.api.cbor;

/*
 * JACOB - CBOR implementation in Java.
 *
 * (C) Copyright - 2013 - J.W. Janssen <j.w.janssen@lxtreme.nl>
 *
 * Licensed under Apache License v2.0.
 */

/**
 * Constant values used by the CBOR format.
 */
public interface CborConstants {
    /** Major type 0: unsigned integers. */
    int TYPE_UNSIGNED_INTEGER = 0x00;
    /** Major type 1: negative integers. */
    int TYPE_NEGATIVE_INTEGER = 0x01;
    /** Major type 2: byte string. */
    int TYPE_BYTE_STRING = 0x02;
    /** Major type 3: text/UTF8 string. */
    int TYPE_TEXT_STRING = 0x03;
    /** Major type 4: array of items. */
    int TYPE_ARRAY = 0x04;
    /** Major type 5: map of pairs. */
    int TYPE_MAP = 0x05;
    /** Major type 6: semantic tags. */
    int TYPE_TAG = 0x06;
    /** Major type 7: floating point, simple data types. */
    int TYPE_FLOAT_SIMPLE = 0x07;

    /** Denotes a one-byte value (uint8). */
    int ONE_BYTE = 0x18;
    /** Denotes a two-byte value (uint16). */
    int TWO_BYTES = 0x19;
    /** Denotes a four-byte value (uint32). */
    int FOUR_BYTES = 0x1a;
    /** Denotes a eight-byte value (uint64). */
    int EIGHT_BYTES = 0x1b;

    /** The CBOR-encoded boolean <code>false</code> value (encoded as "simple value": {@link #MT_SIMPLE}). */
    int FALSE = 0x14;
    /** The CBOR-encoded boolean <code>true</code> value (encoded as "simple value": {@link #MT_SIMPLE}). */
    int TRUE = 0x15;
    /** The CBOR-encoded <code>null</code> value (encoded as "simple value": {@link #MT_SIMPLE}). */
    int NULL = 0x16;
    /** The CBOR-encoded "undefined" value (encoded as "simple value": {@link #MT_SIMPLE}). */
    int UNDEFINED = 0x17;
    /** Denotes a half-precision float (two-byte IEEE 754, see {@link #MT_FLOAT}). */
    int HALF_PRECISION_FLOAT = 0x19;
    /** Denotes a single-precision float (four-byte IEEE 754, see {@link #MT_FLOAT}). */
    int SINGLE_PRECISION_FLOAT = 0x1a;
    /** Denotes a double-precision float (eight-byte IEEE 754, see {@link #MT_FLOAT}). */
    int DOUBLE_PRECISION_FLOAT = 0x1b;
    /** The CBOR-encoded "break" stop code for unlimited arrays/maps. */
    int BREAK = 0x1f;

    /** Semantic tag value describing date/time values in the standard format (UTF8 string, RFC3339). */
    int TAG_STANDARD_DATE_TIME = 0;
    /** Semantic tag value describing date/time values as Epoch timestamp (numeric, RFC3339). */
    int TAG_EPOCH_DATE_TIME = 1;
    /** Semantic tag value describing a positive big integer value (byte string). */
    int TAG_POSITIVE_BIGINT = 2;
    /** Semantic tag value describing a negative big integer value (byte string). */
    int TAG_NEGATIVE_BIGINT = 3;
    /** Semantic tag value describing a decimal fraction value (two-element array, base 10). */
    int TAG_DECIMAL_FRACTION = 4;
    /** Semantic tag value describing a big decimal value (two-element array, base 2). */
    int TAG_BIGDECIMAL = 5;
    /** Semantic tag value describing an expected conversion to base64url encoding. */
    int TAG_EXPECTED_BASE64_URL_ENCODED = 21;
    /** Semantic tag value describing an expected conversion to base64 encoding. */
    int TAG_EXPECTED_BASE64_ENCODED = 22;
    /** Semantic tag value describing an expected conversion to base16 encoding. */
    int TAG_EXPECTED_BASE16_ENCODED = 23;
    /** Semantic tag value describing an encoded CBOR data item (byte string). */
    int TAG_CBOR_ENCODED = 24;
    /** Semantic tag value describing an URL (UTF8 string). */
    int TAG_URI = 32;
    /** Semantic tag value describing a base64url encoded string (UTF8 string). */
    int TAG_BASE64_URL_ENCODED = 33;
    /** Semantic tag value describing a base64 encoded string (UTF8 string). */
    int TAG_BASE64_ENCODED = 34;
    /** Semantic tag value describing a regular expression string (UTF8 string, PCRE). */
    int TAG_REGEXP = 35;
    /** Semantic tag value describing a MIME message (UTF8 string, RFC2045). */
    int TAG_MIME_MESSAGE = 36;
    /** Semantic tag value describing CBOR content. */
    int TAG_CBOR_MARKER = 55799;
}
