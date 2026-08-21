package com.ultralatency.matching.persistence.wal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ultralatency.matching.domain.OrderId;
import com.ultralatency.matching.domain.Price;
import com.ultralatency.matching.domain.Quantity;
import com.ultralatency.matching.domain.Sequence;
import com.ultralatency.matching.domain.Side;
import com.ultralatency.matching.engine.SubmitLimitCommand;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CommandWalReaderTest {

    @TempDir
    Path directory;

    @Test
    void rejectsEarlierIncompleteRecordWithoutRepair() throws IOException {
        final WalConfiguration configuration = configuration();
        try (CommandWalWriter writer = new CommandWalWriter(configuration)) {
            for (int sequence = 1; sequence <= 100; sequence++) {
                writer.append(command(sequence));
            }
        }
        final Path first = segmentPaths().get(0);
        final long originalSize = Files.size(first);
        try (var channel = java.nio.channels.FileChannel.open(
                first,
                java.nio.file.StandardOpenOption.WRITE)) {
            channel.truncate(originalSize - 1);
        }
        final long damagedSize = Files.size(first);

        final WalCorruptionException failure = assertThrows(
                WalCorruptionException.class,
                () -> new CommandWalReader(configuration).read());
        assertFalse(failure.incompleteTail());
        assertTrue(damagedSize < originalSize);
        assertEquals(damagedSize, Files.size(first));
    }

    @Test
    void rejectsSegmentGapAndDoesNotSortAroundIt() throws IOException {
        final WalConfiguration configuration = configuration();
        try (CommandWalWriter writer = new CommandWalWriter(configuration)) {
            for (int sequence = 1; sequence <= 100; sequence++) {
                writer.append(command(sequence));
            }
        }
        final List<Path> segments = segmentPaths();
        Files.delete(segments.get(0));

        final WalCorruptionException failure = assertThrows(
                WalCorruptionException.class,
                () -> new CommandWalReader(configuration).read());
        assertFalse(failure.incompleteTail());
    }

    @Test
    void rejectsCorruptHeaderWithoutRepair() throws IOException {
        final WalConfiguration configuration = configuration();
        try (CommandWalWriter writer = new CommandWalWriter(configuration)) {
            writer.append(command(1));
        }
        final Path segment = segmentPaths().get(0);
        final byte[] bytes = Files.readAllBytes(segment);
        bytes[0] ^= 1;
        Files.write(segment, bytes);

        final WalCorruptionException failure = assertThrows(
                WalCorruptionException.class,
                () -> new CommandWalReader(configuration).read());
        assertFalse(failure.incompleteTail());
    }

    @Test
    void reportsEmptyTrailingSegmentForExplicitReopen() throws IOException {
        final WalConfiguration configuration = configuration();
        try (CommandWalWriter writer = new CommandWalWriter(configuration)) {
            writer.append(command(1));
        }
        final Path trailing = directory.resolve("wal-00000000000000000002.log");
        Files.write(
                trailing,
                new WalCommandCodec().encodeSegmentHeader(2, new Sequence(2)));

        final WalCorruptionException failure = assertThrows(
                WalCorruptionException.class,
                () -> new CommandWalReader(configuration).read());
        assertTrue(failure.incompleteTail());

        try (CommandWalWriter reopened = CommandWalWriter.reopen(configuration)) {
            assertTrue(reopened.nextCommandSequence() > 1);
            assertTrue(Files.notExists(trailing));
        }
    }

    private WalConfiguration configuration() {
        return new WalConfiguration(
                directory,
                WalCommandCodec.MIN_SEGMENT_SIZE_BYTES,
                WalDurabilityMode.BUFFERED);
    }

    private List<Path> segmentPaths() throws IOException {
        final List<Path> paths = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "wal-*.log")) {
            stream.forEach(paths::add);
        }
        paths.sort(Comparator.comparing(Path::toString));
        return paths;
    }

    private static SubmitLimitCommand command(final long sequence) {
        return new SubmitLimitCommand(
                new Sequence(sequence),
                new OrderId(sequence),
                Side.SELL,
                new Price(100),
                new Quantity(1));
    }
}
