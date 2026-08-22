package com.ultralatency.matching.recovery.online;

/** Explicit offline recovery policy selected before a recovery run begins. */
public enum RecoveryMode {

    /** Rebuild a genesis engine by replaying every command in the WAL. */
    PURE_WAL,

    /** Restore the selected Snapshot and replay only the retained WAL suffix. */
    SNAPSHOT_THEN_WAL
}
