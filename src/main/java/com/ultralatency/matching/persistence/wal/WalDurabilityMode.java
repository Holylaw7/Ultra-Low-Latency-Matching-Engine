package com.ultralatency.matching.persistence.wal;

/**
 * Durability action performed by a command WAL append.
 */
public enum WalDurabilityMode {

    /**
     * Forces file content and metadata before an append is reported successful.
     */
    SYNC_EACH_APPEND,

    /**
     * Performs complete channel writes without claiming power-loss durability.
     */
    BUFFERED
}
