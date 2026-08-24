package com.ultralatency.matching.qualification;

import com.ultralatency.matching.domain.Sequence;
import com.ultralatency.matching.engine.EngineCommand;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * Immutable deterministic command vector and its canonical digest.
 *
 * @param version workload contract version
 * @param profile workload profile
 * @param seed workload seed
 * @param commands immutable ordered command vector
 * @param digestHex SHA-256 digest of the canonical command vector
 */
public record QualificationWorkload(
        String version,
        QualificationProfile profile,
        long seed,
        List<EngineCommand> commands,
        String digestHex) {

    /** Creates a validated immutable workload. */
    public QualificationWorkload {
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(profile, "profile");
        final boolean standardVersion = QualificationWorkloadV1.VERSION.equals(version);
        final boolean memoryVersion = QualificationWorkloadV1.MEMORY_STEADY_STATE_VERSION.equals(version);
        if (!standardVersion && !memoryVersion) {
            throw new IllegalArgumentException("unsupported workload version: " + version);
        }
        final boolean memoryProfile = profile == QualificationProfile.MEMORY_STEADY_STATE_V1;
        if (memoryProfile != memoryVersion) {
            throw new IllegalArgumentException("workload version does not match profile");
        }
        if (seed < 0) {
            throw new IllegalArgumentException("seed must be non-negative");
        }
        commands = List.copyOf(Objects.requireNonNull(commands, "commands"));
        if (commands.isEmpty()) {
            throw new IllegalArgumentException("commands must not be empty");
        }
        for (int index = 0; index < commands.size(); index++) {
            final EngineCommand command = Objects.requireNonNull(
                    commands.get(index), "commands contains null");
            final long expected = index + 1L;
            final Sequence sequence = command.sequence();
            if (sequence.value() != expected) {
                throw new IllegalArgumentException(
                        "command sequence must be contiguous from one");
            }
        }
        Objects.requireNonNull(digestHex, "digestHex");
        if (digestHex.length() != 64 || !isLowerHex(digestHex)) {
            throw new IllegalArgumentException("digestHex must be a lowercase SHA-256 value");
        }
        final String expectedDigest = QualificationCanonicalizer.digest(commands);
        if (!expectedDigest.equals(digestHex)) {
            throw new IllegalArgumentException("digestHex does not match commands");
        }
    }

    /** Returns the number of commands in the immutable vector. */
    public int commandCount() {
        return commands.size();
    }

    /** Returns the digest as immutable bytes for manifest/hash composition. */
    public byte[] digestBytes() {
        return HexFormat.of().parseHex(digestHex);
    }

    private static boolean isLowerHex(final String value) {
        for (int index = 0; index < value.length(); index++) {
            final char character = value.charAt(index);
            if (!((character >= '0' && character <= '9')
                    || (character >= 'a' && character <= 'f'))) {
                return false;
            }
        }
        return true;
    }
}
