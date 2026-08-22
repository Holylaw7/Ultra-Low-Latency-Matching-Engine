package com.ultralatency.matching.integration.durable;

import com.ultralatency.matching.persistence.wal.WalDurabilityMode;

/**
 * Durability modes that are valid for the Phase 7 live command path.
 *
 * <p>The live path deliberately has one mode.  The WAL component may still expose
 * {@link WalDurabilityMode#BUFFERED} for its isolated component measurements, but that mode
 * cannot be represented by this type and therefore cannot be selected for live acceptance.</p>
 */
public enum LiveDurabilityMode {

    /** Append the record and synchronously force it before reporting success. */
    SYNC_EACH_APPEND;

    /**
     * Returns the corresponding frozen WAL action for adapter construction.
     *
     * @return the only WAL action allowed by the live boundary
     */
    public WalDurabilityMode walMode() {
        return WalDurabilityMode.SYNC_EACH_APPEND;
    }
}
