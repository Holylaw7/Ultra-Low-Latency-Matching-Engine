package com.ultralatency.matching.qualification.ga.durability;

/**
 * Deterministic corruption and durability fixtures required by the approved G3 matrix.
 *
 * <p>The fixture names are part of the qualification contract.  They deliberately describe
 * the physical boundary being exercised rather than an implementation-specific test hook.</p>
 */
public enum GaDurabilityFixture {

    /** Segment filename and header first-sequence mismatch. */
    SEGMENT_FIRST_SEQUENCE_MISMATCH,
    /** Segment identifier is invalid or inconsistent with the filename. */
    SEGMENT_ID_INVALID,
    /** Segment first sequence is zero/invalid. */
    SEGMENT_FIRST_SEQUENCE_ZERO,
    /** Invalid segment magic. */
    SEGMENT_MAGIC,
    /** Unsupported segment format version. */
    SEGMENT_VERSION,
    /** Invalid segment header length. */
    SEGMENT_HEADER_LENGTH,
    /** Unsupported segment header flags/reserved region. */
    SEGMENT_RESERVED_BYTES,
    /** Invalid record length below the supported bound. */
    RECORD_LENGTH_TOO_SMALL,
    /** Invalid record length above the supported bound. */
    RECORD_LENGTH_TOO_LARGE,
    /** Record length is exactly the lower malformed boundary. */
    RECORD_LENGTH_ZERO,
    /** Cancel record at zero length. */
    RECORD_LENGTH_ZERO_CANCEL,
    /** Record length is one byte shorter than a submit record. */
    RECORD_LENGTH_27,
    /** Cancel record at the 27-byte boundary. */
    RECORD_LENGTH_27_CANCEL,
    /** Record length is one byte longer than a submit record header. */
    RECORD_LENGTH_29,
    /** Cancel record at the 29-byte boundary. */
    RECORD_LENGTH_29_CANCEL,
    /** Record length is one byte shorter than a full submit record. */
    RECORD_LENGTH_51,
    /** Cancel record at the 51-byte boundary. */
    RECORD_LENGTH_51_CANCEL,
    /** Record length is one byte longer than a full submit record. */
    RECORD_LENGTH_53,
    /** Cancel record at the 53-byte boundary. */
    RECORD_LENGTH_53_CANCEL,
    /** Record length exceeds the codec maximum by one. */
    RECORD_LENGTH_MAX_PLUS_ONE,
    /** Cancel record above the codec maximum. */
    RECORD_LENGTH_MAX_PLUS_ONE_CANCEL,
    /** Unsupported record version or type. */
    RECORD_VERSION_OR_TYPE,
    /** Unsupported record version. */
    RECORD_VERSION,
    /** Unsupported record type. */
    RECORD_TYPE,
    /** Non-zero record flags. */
    RECORD_FLAGS,
    /** Invalid side code. */
    RECORD_INVALID_SIDE,
    /** Non-zero record flags or reserved bytes. */
    RECORD_RESERVED_BYTES,
    /** Each reserved record byte is checked by the matrix. */
    RECORD_RESERVED_BYTE_1,
    RECORD_RESERVED_BYTE_2,
    RECORD_RESERVED_BYTE_3,
    RECORD_RESERVED_BYTE_4,
    RECORD_RESERVED_BYTE_5,
    RECORD_RESERVED_BYTE_6,
    RECORD_RESERVED_BYTE_7,
    /** One command body bit is changed. */
    RECORD_BODY_CHECKSUM,
    /** Submit command body checksum mutation. */
    RECORD_BODY_CHECKSUM_SUBMIT,
    /** Cancel command body checksum mutation. */
    RECORD_BODY_CHECKSUM_CANCEL,
    /** Stored record checksum is changed. */
    RECORD_STORED_CHECKSUM,
    /** Submit command stored checksum mutation. */
    RECORD_STORED_CHECKSUM_SUBMIT,
    /** Cancel command stored checksum mutation. */
    RECORD_STORED_CHECKSUM_CANCEL,
    /** Duplicate previous logical sequence. */
    DUPLICATE_SEQUENCE,
    /** One logical sequence is skipped. */
    SEQUENCE_GAP,
    /** A sequence gap crosses a segment boundary. */
    CROSS_SEGMENT_GAP,
    /** Incomplete non-final record tail. */
    NON_FINAL_TORN_TAIL,
    /** Incomplete non-final record header. */
    NON_FINAL_TORN_HEADER,
    /** Incomplete non-final record body. */
    NON_FINAL_TORN_BODY,
    /** Incomplete non-final record checksum. */
    NON_FINAL_TORN_CHECKSUM,
    /** Incomplete final record tail, which is the only repairable tail. */
    FINAL_TORN_TAIL,
    /** Final tail cut after one byte. */
    FINAL_TORN_AFTER_1,
    /** Final tail cut after 27 bytes. */
    FINAL_TORN_AFTER_27,
    /** Final tail cut after 28 bytes. */
    FINAL_TORN_AFTER_28,
    /** Final tail cut after 51 bytes. */
    FINAL_TORN_AFTER_51,
    /** Snapshot metadata or checksum corruption. */
    SNAPSHOT_CORRUPTION,
    /** Snapshot magic corruption. */
    SNAPSHOT_MAGIC,
    /** Snapshot format version corruption. */
    SNAPSHOT_VERSION,
    /** Snapshot flags corruption. */
    SNAPSHOT_FLAGS,
    /** Snapshot reserved byte corruption. */
    SNAPSHOT_RESERVED,
    /** Snapshot active-order count corruption. */
    SNAPSHOT_COUNT,
    /** Snapshot order-record length corruption. */
    SNAPSHOT_LENGTH,
    /** Snapshot footer CRC corruption. */
    SNAPSHOT_CRC,
    /** Snapshot WAL-prefix digest corruption. */
    SNAPSHOT_WAL_PREFIX_DIGEST,
    /** Snapshot checkpoint digest corruption. */
    SNAPSHOT_CHECKPOINT_DIGEST,
    /** Snapshot duplicate order identity. */
    SNAPSHOT_DUPLICATE_ORDER,
    /** Snapshot order sort/canonical-order corruption. */
    SNAPSHOT_NON_CANONICAL_ORDER,
    /** Snapshot checkpoint is newer than the available WAL. */
    SNAPSHOT_NEWER_THAN_WAL,
    /** Orphan temporary Snapshot files are ignored, not published. */
    SNAPSHOT_ORPHAN_TEMP,
    /** Deterministic next-segment path collision. */
    ROTATION_PATH_COLLISION
}
