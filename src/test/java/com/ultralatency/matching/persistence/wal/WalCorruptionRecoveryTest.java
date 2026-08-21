package com.ultralatency.matching.persistence.wal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ultralatency.matching.domain.OrderId;
import com.ultralatency.matching.domain.Price;
import com.ultralatency.matching.domain.Quantity;
import com.ultralatency.matching.domain.Sequence;
import com.ultralatency.matching.domain.Side;
import com.ultralatency.matching.engine.EngineCommand;
import com.ultralatency.matching.engine.EngineResult;
import com.ultralatency.matching.engine.MatchingEngine;
import com.ultralatency.matching.engine.SubmitLimitCommand;
import com.ultralatency.matching.recovery.CommandWalReplayer;
import com.ultralatency.matching.recovery.ReplayTranscript;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WalCorruptionRecoveryTest {

    @TempDir
    Path directory;

    @Test
    void classifiesEveryIncompleteFinalRecordOffsetAndRepairsOnlyThatTail() throws IOException {
        final long recordStart = WalCommandCodec.SEGMENT_HEADER_LENGTH + 52L;
        final long completeSize = recordStart + 52L;
        for (long offset = recordStart + 1; offset < completeSize; offset++) {
            final Path caseDirectory = directory.resolve("truncate-" + offset);
            Files.createDirectories(caseDirectory);
            final WalConfiguration configuration = configuration(caseDirectory);
            write(configuration, List.of(command(1, 1), command(2, 2)));
            final Path segment = segment(caseDirectory);
            try (FileChannel channel = FileChannel.open(segment, StandardOpenOption.WRITE)) {
                channel.truncate(offset);
            }
            final long damagedSize = Files.size(segment);

            final WalCorruptionException failure = assertThrows(
                    WalCorruptionException.class,
                    () -> new CommandWalReader(configuration).read());
            assertTrue(failure.incompleteTail());
            assertEquals(damagedSize, Files.size(segment));

            try (CommandWalWriter reopened = CommandWalWriter.reopen(configuration)) {
                assertEquals(2, reopened.nextCommandSequence());
            }
            assertEquals(List.of(command(1, 1)), new CommandWalReader(configuration).read());
        }
    }

    @Test
    void exactBoundaryWithoutTailIsAValidPrefixAndReopenIsIdempotent() throws IOException {
        final WalConfiguration configuration = configuration(directory);
        write(configuration, List.of(command(1, 1), command(2, 2)));
        final Path segment = segment(directory);
        try (FileChannel channel = FileChannel.open(segment, StandardOpenOption.WRITE)) {
            channel.truncate(WalCommandCodec.SEGMENT_HEADER_LENGTH + 52L);
        }

        assertEquals(List.of(command(1, 1)), new CommandWalReader(configuration).read());
        try (CommandWalWriter first = CommandWalWriter.reopen(configuration)) {
            assertEquals(2, first.nextCommandSequence());
        }
        try (CommandWalWriter second = CommandWalWriter.reopen(configuration)) {
            assertEquals(2, second.nextCommandSequence());
        }
        assertEquals(List.of(command(1, 1)), new CommandWalReader(configuration).read());
    }

    @Test
    void corruptionDiagnosticsIncludeSegmentAndOffsetAndNeverRepair() throws IOException {
        final WalConfiguration configuration = configuration(directory);
        write(configuration, List.of(command(1, 1)));
        final Path segment = segment(directory);
        final byte[] bytes = Files.readAllBytes(segment);
        bytes[0] ^= 1;
        Files.write(segment, bytes);
        final long corruptedSize = Files.size(segment);

        final WalCorruptionException failure = assertThrows(
                WalCorruptionException.class,
                () -> new CommandWalReader(configuration).read());
        assertEquals(segment, failure.path());
        assertEquals(0, failure.offset());
        assertFalse(failure.incompleteTail());
        assertEquals(corruptedSize, Files.size(segment));
    }

    @Test
    void completeFinalRecordCorruptionIsNotAnEligibleTail() throws IOException {
        final WalConfiguration configuration = configuration(directory);
        write(configuration, List.of(command(1, 1), command(2, 2)));
        final Path segment = segment(directory);
        final byte[] bytes = Files.readAllBytes(segment);
        bytes[WalCommandCodec.SEGMENT_HEADER_LENGTH + 52 + 24] = 9;
        Files.write(segment, bytes);

        final WalCorruptionException failure = assertThrows(
                WalCorruptionException.class,
                () -> CommandWalWriter.reopen(configuration));
        assertFalse(failure.incompleteTail());
        assertEquals(WalCommandCodec.SEGMENT_HEADER_LENGTH + 52L, failure.offset());
    }

    @Test
    void missingAndMisnamedSegmentsFailClosed() throws IOException {
        final WalConfiguration configuration = configuration(directory.resolve("gap"));
        Files.createDirectories(configuration.directory());
        write(configuration, commands(100));
        final List<Path> segments = segmentPaths(configuration.directory());
        Files.delete(segments.get(0));
        assertThrows(WalCorruptionException.class, () -> new CommandWalReader(configuration).read());

        final Path misnamedDirectory = directory.resolve("misnamed");
        Files.createDirectories(misnamedDirectory);
        Files.write(misnamedDirectory.resolve("wal-invalid.log"), new byte[0]);
        final WalConfiguration misnamed = configuration(misnamedDirectory);
        assertThrows(WalCorruptionException.class, () -> new CommandWalReader(misnamed).read());
    }

    @Test
    void repairedWalReplaysExactlyTheValidDirectPrefix() throws IOException {
        final WalConfiguration configuration = configuration(directory);
        final List<EngineCommand> commands = List.of(command(1, 1), command(2, 2));
        write(configuration, commands);
        final Path segment = segment(directory);
        try (FileChannel channel = FileChannel.open(segment, StandardOpenOption.WRITE)) {
            channel.truncate(WalCommandCodec.SEGMENT_HEADER_LENGTH + 52L + 2L);
        }

        try (CommandWalWriter reopened = CommandWalWriter.reopen(configuration)) {
            assertEquals(2, reopened.nextCommandSequence());
        }
        final List<EngineResult> directPrefix = execute(List.of(commands.get(0)));
        final ReplayTranscript replay = new CommandWalReplayer(configuration).replay();
        assertEquals(directPrefix, replay.results());
        assertNotNull(replay.sha256DigestHex());
    }

    private static void write(
            final WalConfiguration configuration,
            final List<EngineCommand> commands) throws IOException {
        try (CommandWalWriter writer = new CommandWalWriter(configuration)) {
            for (final EngineCommand command : commands) {
                writer.append(command);
            }
        }
    }

    private WalConfiguration configuration(final Path path) {
        return new WalConfiguration(
                path,
                WalCommandCodec.MIN_SEGMENT_SIZE_BYTES,
                WalDurabilityMode.BUFFERED);
    }

    private static Path segment(final Path path) throws IOException {
        return segmentPaths(path).get(0);
    }

    private static List<Path> segmentPaths(final Path path) throws IOException {
        try (var stream = Files.list(path)) {
            return stream.filter(candidate -> candidate.getFileName().toString().endsWith(".log"))
                    .sorted()
                    .toList();
        }
    }

    private static List<EngineCommand> commands(final int count) {
        final List<EngineCommand> commands = new ArrayList<>();
        for (int sequence = 1; sequence <= count; sequence++) {
            commands.add(command(sequence, sequence));
        }
        return List.copyOf(commands);
    }

    private static SubmitLimitCommand command(final long sequence, final long orderId) {
        return new SubmitLimitCommand(
                new Sequence(sequence),
                new OrderId(orderId),
                Side.SELL,
                new Price(100),
                new Quantity(1));
    }

    private static List<EngineResult> execute(final List<EngineCommand> commands) {
        final MatchingEngine engine = new MatchingEngine();
        final List<EngineResult> results = new ArrayList<>();
        for (final EngineCommand command : commands) {
            results.add(engine.process(command));
        }
        return List.copyOf(results);
    }
}
