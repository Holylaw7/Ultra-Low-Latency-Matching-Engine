package com.ultralatency.matching.qualification;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

/** Atomic immutable publication for campaign summaries. */
public final class QualificationCampaignSummaryStore {

    private QualificationCampaignSummaryStore() {
    }

    /** Reads one canonical immutable summary. */
    public static QualificationCampaignSummary read(final Path target) throws IOException {
        return QualificationCampaignSummary.read(target);
    }

    /** Publishes one summary exactly once with force, read-back and atomic move. */
    public static QualificationCampaignSummary publish(
            final Path target,
            final QualificationCampaignSummary summary) throws IOException {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(summary, "summary");
        final Path absoluteTarget = target.toAbsolutePath().normalize();
        final Path parent = Objects.requireNonNull(absoluteTarget.getParent(), "target parent");
        Files.createDirectories(parent);
        if (Files.exists(absoluteTarget, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("immutable campaign summary already exists");
        }
        final Path temporary = Files.createTempFile(parent, absoluteTarget.getFileName() + ".", ".tmp");
        boolean moved = false;
        try {
            try (var channel = java.nio.channels.FileChannel.open(
                    temporary, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                final var buffer = java.nio.ByteBuffer.wrap(summary.canonicalBytes());
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }
            final QualificationCampaignSummary readBack =
                    QualificationCampaignSummary.read(temporary);
            if (!readBack.sha256Hex().equals(summary.sha256Hex())) {
                throw new IOException("campaign summary read-back digest mismatch");
            }
            try {
                Files.move(temporary, absoluteTarget, StandardCopyOption.ATOMIC_MOVE);
                moved = true;
            } catch (final java.nio.file.AtomicMoveNotSupportedException exception) {
                throw new IOException("atomic campaign publication is required", exception);
            }
            return readBack;
        } finally {
            if (!moved) {
                Files.deleteIfExists(temporary);
            }
        }
    }
}
