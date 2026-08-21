package com.ultralatency.matching.recovery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ultralatency.matching.domain.OrderId;
import com.ultralatency.matching.domain.Price;
import com.ultralatency.matching.domain.Quantity;
import com.ultralatency.matching.domain.Sequence;
import com.ultralatency.matching.domain.Side;
import com.ultralatency.matching.engine.EngineCommand;
import com.ultralatency.matching.engine.EngineResult;
import com.ultralatency.matching.engine.MatchingEngine;
import com.ultralatency.matching.engine.SubmitLimitCommand;
import com.ultralatency.matching.persistence.wal.CommandWalWriter;
import com.ultralatency.matching.persistence.wal.WalConfiguration;
import com.ultralatency.matching.persistence.wal.WalDurabilityMode;
import com.ultralatency.matching.persistence.wal.WalCommandCodec;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CommandWalReplayerTest {

    @TempDir
    Path directory;

    @Test
    void replaysGenesisStreamWithOrderedResultsAndStableDigest() throws IOException {
        final List<EngineCommand> commands = matchingCommands(1024);
        write(commands);

        final List<EngineResult> direct = executeDirect(commands);
        final CommandWalReplayer replayer = new CommandWalReplayer(configuration());
        final ReplayTranscript first = replayer.replay();
        final ReplayTranscript second = replayer.replay();

        assertEquals(direct, first.results());
        assertEquals(first, second);
        assertEquals(512, first.results().stream()
                .mapToInt(result -> result.matches().size())
                .sum());
        assertEquals(ReplayTranscriptDigest.sha256Hex(direct), first.sha256DigestHex());
    }

    @Test
    void replaysFixedPublicProbeSuffixAfterTheSamePrefix() throws IOException {
        final List<EngineCommand> prefix = matchingCommands(20);
        final List<EngineCommand> probes = List.of(
                new SubmitLimitCommand(
                        new Sequence(21),
                        new OrderId(10_000),
                        Side.BUY,
                        new Price(101),
                        new Quantity(2)),
                new com.ultralatency.matching.engine.CancelOrderCommand(
                        new Sequence(22),
                        new OrderId(10_000)));
        write(prefix);

        final MatchingEngine directEngine = new MatchingEngine();
        final List<EngineResult> directPrefix = execute(directEngine, prefix);
        final List<EngineResult> directProbes = execute(directEngine, probes);
        final ReplayProbeResult replay = new CommandWalReplayer(configuration())
                .replayWithProbe(probes);

        assertEquals(directPrefix, replay.transcript().results());
        assertEquals(directProbes, replay.probeResults());
    }

    @Test
    void digestPreservesOrderAndUsesCanonicalPublicFields() {
        final List<EngineCommand> commands = matchingCommands(4);
        final List<EngineResult> results = executeDirect(commands);
        final List<EngineResult> reversed = new ArrayList<>(results);
        java.util.Collections.reverse(reversed);

        assertNotEquals(
                ReplayTranscriptDigest.sha256Hex(results),
                ReplayTranscriptDigest.sha256Hex(reversed));
    }

    @Test
    void reportsEngineRejectionAtThePoisonCommandSequence() throws IOException {
        final List<EngineCommand> commands = List.of(
                command(1, 1, Side.SELL),
                command(2, 1, Side.BUY));
        write(commands);

        final ReplayException failure = assertThrows(
                ReplayException.class,
                () -> new CommandWalReplayer(configuration()).replay());
        assertEquals(new Sequence(2), failure.commandSequence());
    }

    @Test
    void rejectsEmptyClosedWal() {
        final ReplayException failure = assertThrows(
                ReplayException.class,
                () -> new CommandWalReplayer(configuration()).replay());
        assertNull(failure.commandSequence());
    }

    private void write(final List<EngineCommand> commands) throws IOException {
        try (CommandWalWriter writer = new CommandWalWriter(configuration())) {
            for (final EngineCommand command : commands) {
                writer.append(command);
            }
        }
    }

    private WalConfiguration configuration() {
        return new WalConfiguration(
                directory,
                WalCommandCodec.MIN_SEGMENT_SIZE_BYTES,
                WalDurabilityMode.BUFFERED);
    }

    private static List<EngineResult> executeDirect(final List<EngineCommand> commands) {
        return execute(new MatchingEngine(), commands);
    }

    private static List<EngineResult> execute(
            final MatchingEngine engine,
            final List<EngineCommand> commands) {
        final List<EngineResult> results = new ArrayList<>();
        for (final EngineCommand command : commands) {
            results.add(engine.process(command));
        }
        return List.copyOf(results);
    }

    private static List<EngineCommand> matchingCommands(final int count) {
        if (count % 2 != 0) {
            throw new IllegalArgumentException("count must be even");
        }
        final List<EngineCommand> commands = new ArrayList<>(count);
        for (int sequence = 1; sequence <= count; sequence += 2) {
            commands.add(command(sequence, (sequence + 1L) / 2, Side.SELL));
            commands.add(command(sequence + 1L, (sequence + 1L) / 2 + 10_000, Side.BUY));
        }
        return List.copyOf(commands);
    }

    private static SubmitLimitCommand command(
            final long sequence,
            final long orderId,
            final Side side) {
        return new SubmitLimitCommand(
                new Sequence(sequence),
                new OrderId(orderId),
                side,
                new Price(100),
                new Quantity(1));
    }
}
