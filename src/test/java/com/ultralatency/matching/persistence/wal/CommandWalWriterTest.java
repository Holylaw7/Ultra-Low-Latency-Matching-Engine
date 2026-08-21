package com.ultralatency.matching.persistence.wal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ultralatency.matching.domain.OrderId;
import com.ultralatency.matching.domain.Price;
import com.ultralatency.matching.domain.Quantity;
import com.ultralatency.matching.domain.Sequence;
import com.ultralatency.matching.domain.Side;
import com.ultralatency.matching.engine.EngineCommand;
import com.ultralatency.matching.engine.SubmitLimitCommand;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CommandWalWriterTest {

    @TempDir
    Path directory;

    @Test
    void writesAndReadsOrderedCommandsAcrossRotation() throws IOException {
        final WalConfiguration configuration = configuration(WalDurabilityMode.BUFFERED);
        final List<EngineCommand> commands = commands(100);

        try (CommandWalWriter writer = new CommandWalWriter(configuration)) {
            for (final EngineCommand command : commands) {
                writer.append(command);
            }
        }

        assertEquals(commands, new CommandWalReader(configuration).read());
        assertTrue(segmentPaths().size() > 1);
        for (final Path segment : segmentPaths()) {
            assertTrue(Files.size(segment) <= configuration.segmentSizeBytes());
        }
    }

    @Test
    void rejectsSequenceGapWithoutMutatingWal() throws IOException {
        final WalConfiguration configuration = configuration(WalDurabilityMode.SYNC_EACH_APPEND);
        try (CommandWalWriter writer = new CommandWalWriter(configuration)) {
            writer.append(command(1));
            assertThrows(WalSequenceException.class, () -> writer.append(command(3)));
            assertEquals(2, writer.nextCommandSequence());
        }

        assertEquals(List.of(command(1)), new CommandWalReader(configuration).read());
    }

    @Test
    void closeIsIdempotentAndRejectsLaterAppend() throws IOException {
        final CommandWalWriter writer = new CommandWalWriter(configuration(WalDurabilityMode.BUFFERED));
        writer.append(command(1));
        writer.close();
        writer.close();

        assertThrows(IllegalStateException.class, () -> writer.append(command(2)));
    }

    @Test
    void activeSegmentHasExclusiveWriterOwnership() throws IOException {
        final WalConfiguration configuration = configuration(WalDurabilityMode.BUFFERED);
        final CommandWalWriter first = new CommandWalWriter(configuration);
        first.append(command(1));
        try {
            assertThrows(WalStorageException.class, () -> new CommandWalWriter(configuration));
        } finally {
            first.close();
        }
    }

    @Test
    void explicitReopenTruncatesOnlyIncompleteFinalRecord() throws IOException {
        final WalConfiguration configuration = configuration(WalDurabilityMode.BUFFERED);
        final Path segment;
        try (CommandWalWriter writer = new CommandWalWriter(configuration)) {
            writer.append(command(1));
            writer.append(command(2));
            segment = writer.activeSegmentPath();
        }
        final long completeSize = Files.size(segment);
        try (var channel = java.nio.channels.FileChannel.open(
                segment,
                java.nio.file.StandardOpenOption.WRITE)) {
            channel.truncate(completeSize - 2);
        }

        final WalCorruptionException strictFailure = assertThrows(
                WalCorruptionException.class,
                () -> new CommandWalReader(configuration).read());
        assertTrue(strictFailure.incompleteTail());

        try (CommandWalWriter reopened = CommandWalWriter.reopen(configuration)) {
            assertEquals(2, reopened.nextCommandSequence());
            reopened.append(command(2));
        }
        assertEquals(List.of(command(1), command(2)), new CommandWalReader(configuration).read());
    }

    @Test
    void completeRecordCorruptionFailsClosedWithoutRepair() throws IOException {
        final WalConfiguration configuration = configuration(WalDurabilityMode.BUFFERED);
        final Path segment;
        try (CommandWalWriter writer = new CommandWalWriter(configuration)) {
            writer.append(command(1));
            segment = writer.activeSegmentPath();
        }
        final byte[] bytes = Files.readAllBytes(segment);
        bytes[WalCommandCodec.SEGMENT_HEADER_LENGTH + 10] ^= 1;
        Files.write(segment, bytes);

        final WalCorruptionException strictFailure = assertThrows(
                WalCorruptionException.class,
                () -> CommandWalWriter.reopen(configuration));
        assertFalse(strictFailure.incompleteTail());
        assertEquals(52 + WalCommandCodec.SEGMENT_HEADER_LENGTH, Files.size(segment));
    }

    private WalConfiguration configuration(final WalDurabilityMode mode) {
        return new WalConfiguration(
                directory,
                WalCommandCodec.MIN_SEGMENT_SIZE_BYTES,
                mode);
    }

    private List<Path> segmentPaths() throws IOException {
        final List<Path> paths = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "wal-*.log")) {
            stream.forEach(paths::add);
        }
        return paths;
    }

    private static List<EngineCommand> commands(final int count) {
        final List<EngineCommand> commands = new ArrayList<>();
        for (int index = 1; index <= count; index++) {
            commands.add(command(index));
        }
        return List.copyOf(commands);
    }

    private static EngineCommand command(final long sequence) {
        return new SubmitLimitCommand(
                new Sequence(sequence),
                new OrderId(sequence),
                Side.BUY,
                new Price(100),
                new Quantity(1));
    }
}
