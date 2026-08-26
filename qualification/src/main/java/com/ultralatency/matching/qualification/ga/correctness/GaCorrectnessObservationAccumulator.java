package com.ultralatency.matching.qualification.ga.correctness;

import com.ultralatency.matching.engine.EngineCommand;
import com.ultralatency.matching.qualification.QualificationExchange;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/** Bounded live-protocol accumulator used by the G1/G2 runner. */
final class GaCorrectnessObservationAccumulator {

    private final MessageDigest commandDigest = newDigest();
    private final MessageDigest transcriptDigest = newDigest();
    private final Deque<ProbeObservation> probe = new ArrayDeque<>();
    private long acceptedCommands;
    private long tradeCount;
    private boolean finished;

    void accept(final EngineCommand command, final QualificationExchange exchange) {
        if (finished) {
            throw new IllegalStateException("correctness accumulator is already finished");
        }
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(exchange, "exchange");
        GaCorrectnessCanonicalizer.updateCommandDigest(commandDigest, command);
        GaCorrectnessCanonicalizer.updateExchangeDigest(transcriptDigest, exchange);
        acceptedCommands = Math.addExact(acceptedCommands, 1L);
        tradeCount = Math.addExact(tradeCount, exchange.matches().size());
        if (probe.size() == 2) {
            probe.removeFirst();
        }
        probe.addLast(new ProbeObservation(command, exchange));
    }

    Summary finish() {
        if (finished) {
            throw new IllegalStateException("correctness accumulator is already finished");
        }
        finished = true;
        final List<EngineCommand> commands = new ArrayList<>(probe.size());
        final List<QualificationExchange> exchanges = new ArrayList<>(probe.size());
        for (final ProbeObservation observation : probe) {
            commands.add(observation.command());
            exchanges.add(observation.exchange());
        }
        return new Summary(
                HexFormat.of().formatHex(commandDigest.digest()),
                HexFormat.of().formatHex(transcriptDigest.digest()),
                GaCorrectnessCanonicalizer.probe(commands, exchanges),
                acceptedCommands,
                tradeCount);
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (final java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the JDK", exception);
        }
    }

    record Summary(
            String commandDigestHex,
            String transcriptDigestHex,
            String publicProbeDigestHex,
            long acceptedCommands,
            long tradeCount) {
    }

    private record ProbeObservation(EngineCommand command, QualificationExchange exchange) {
    }
}
