package com.ultralatency.matching.qualification;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

/** Atomic, immutable publication helpers for qualification artifacts. */
public final class QualificationEvidencePublication {

    private QualificationEvidencePublication() {
    }

    /** Publishes UTF-8 text using force-before-atomic-move semantics. */
    public static void text(final Path target, final String value) throws IOException {
        Objects.requireNonNull(value, "value");
        bytes(target, value.getBytes(StandardCharsets.UTF_8));
    }

    /** Publishes raw nanosecond samples as one immutable CSV-like text artifact. */
    public static void samples(final Path target, final long[] values) throws IOException {
        Objects.requireNonNull(values, "values");
        final StringBuilder output = new StringBuilder(values.length * 12);
        output.append("sampleIndex,nanos\n");
        for (int index = 0; index < values.length; index++) {
            if (values[index] < 0) {
                throw new IllegalArgumentException("latency samples must be non-negative");
            }
            output.append(index).append(',').append(values[index]).append('\n');
        }
        text(target, output.toString());
    }

    /** Publishes bytes once; an existing target is never overwritten. */
    public static void bytes(final Path target, final byte[] value) throws IOException {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(value, "value");
        final Path absolute = target.toAbsolutePath().normalize();
        if (Files.exists(absolute, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("qualification evidence already exists: " + absolute);
        }
        final Path parent = Objects.requireNonNull(absolute.getParent(), "target parent");
        Files.createDirectories(parent);
        final Path temporary = Files.createTempFile(parent, absolute.getFileName() + ".", ".tmp");
        boolean moved = false;
        try {
            try (FileChannel channel = FileChannel.open(
                    temporary, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                final ByteBuffer buffer = ByteBuffer.wrap(value);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }
            try {
                Files.move(temporary, absolute, StandardCopyOption.ATOMIC_MOVE);
                moved = true;
            } catch (final AtomicMoveNotSupportedException exception) {
                throw new IOException("atomic qualification publication is required", exception);
            }
        } finally {
            if (!moved) {
                Files.deleteIfExists(temporary);
            }
        }
    }
}
