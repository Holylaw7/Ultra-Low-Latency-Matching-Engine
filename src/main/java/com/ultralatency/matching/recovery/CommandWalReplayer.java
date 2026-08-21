package com.ultralatency.matching.recovery;

import com.ultralatency.matching.domain.Sequence;
import com.ultralatency.matching.engine.EngineCommand;
import com.ultralatency.matching.engine.EngineResult;
import com.ultralatency.matching.engine.MatchingEngine;
import com.ultralatency.matching.persistence.wal.CommandWalReader;
import com.ultralatency.matching.persistence.wal.WalConfiguration;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Offline strict command replay into a new genesis matching engine. */
public final class CommandWalReplayer {

    private final WalConfiguration configuration;

    /**
     * Creates a replayer for one configured WAL directory.
     *
     * @param configuration WAL configuration
     */
    public CommandWalReplayer(final WalConfiguration configuration) {
        this.configuration = Objects.requireNonNull(configuration, "configuration");
    }

    /**
     * Replays the closed WAL into a new genesis engine.
     *
     * @return immutable ordered transcript and canonical digest
     * @throws IOException when strict WAL reading or engine replay fails
     */
    public ReplayTranscript replay() throws IOException {
        final List<EngineCommand> commands = new CommandWalReader(configuration).read();
        if (commands.isEmpty()) {
            throw new ReplayException(null, "Replay WAL must contain command sequence 1");
        }
        if (commands.get(0).sequence().value() != 1) {
            throw new ReplayException(
                    commands.get(0).sequence(),
                    "Replay WAL must start at command sequence 1");
        }
        return execute(commands, List.of()).transcript();
    }

    /**
     * Replays the closed WAL and applies a public command suffix to the same genesis execution.
     *
     * @param probeCommands fixed public API probe suffix
     * @return replay transcript and ordered probe results
     * @throws IOException when strict WAL reading or engine replay fails
     */
    public ReplayProbeResult replayWithProbe(final List<EngineCommand> probeCommands)
            throws IOException {
        final List<EngineCommand> commands = new CommandWalReader(configuration).read();
        if (commands.isEmpty()) {
            throw new ReplayException(null, "Replay WAL must contain command sequence 1");
        }
        if (commands.get(0).sequence().value() != 1) {
            throw new ReplayException(
                    commands.get(0).sequence(),
                    "Replay WAL must start at command sequence 1");
        }
        return execute(commands, List.copyOf(Objects.requireNonNull(probeCommands, "probeCommands")));
    }

    private static ReplayProbeResult execute(
            final List<EngineCommand> commands,
            final List<EngineCommand> probeCommands) throws ReplayException {
        final MatchingEngine engine = new MatchingEngine();
        final List<EngineResult> results = new ArrayList<>(commands.size());
        for (final EngineCommand command : commands) {
            results.add(process(engine, command));
        }
        final ReplayTranscript transcript = new ReplayTranscript(
                List.copyOf(results),
                ReplayTranscriptDigest.sha256Hex(results));
        final List<EngineResult> probes = new ArrayList<>(probeCommands.size());
        for (final EngineCommand probe : probeCommands) {
            probes.add(process(engine, probe));
        }
        return new ReplayProbeResult(transcript, probes);
    }

    private static EngineResult process(
            final MatchingEngine engine,
            final EngineCommand command) throws ReplayException {
        Objects.requireNonNull(command, "command");
        try {
            return engine.process(command);
        } catch (final RuntimeException exception) {
            final Sequence sequence = command.sequence();
            throw new ReplayException(
                    sequence,
                    "Matching engine rejected replay command at sequence " + sequence.value(),
                    exception);
        }
    }
}
