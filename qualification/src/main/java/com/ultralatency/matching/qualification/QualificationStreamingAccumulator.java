package com.ultralatency.matching.qualification;

import com.ultralatency.matching.engine.EngineCommand;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Incremental qualification evidence with a fixed-size public probe window.
 *
 * <p>No command or exchange history is retained beyond the configured probe suffix.</p>
 */
public final class QualificationStreamingAccumulator {

    private final int probeLimit;
    private final MessageDigest commandDigest = sha256();
    private final MessageDigest transcriptDigest = sha256();
    private final Deque<ProbeObservation> probe = new ArrayDeque<>();
    private long responseCount;
    private long tradeCount;
    private boolean finished;

    /** Creates a bounded accumulator. */
    public QualificationStreamingAccumulator(final int probeLimit) {
        if (probeLimit < 0) {
            throw new IllegalArgumentException("probeLimit must not be negative");
        }
        this.probeLimit = probeLimit;
    }

    /** Adds one ordered command/response observation. */
    public void accept(final EngineCommand command, final QualificationExchange exchange) {
        if (finished) {
            throw new IllegalStateException("streaming evidence is already finished");
        }
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(exchange, "exchange");
        QualificationCanonicalizer.updateCommand(commandDigest, command);
        transcriptDigest.update(exchange.transcriptDigestHex().getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        responseCount = Math.addExact(responseCount, exchange.responseFrameCount());
        tradeCount = Math.addExact(tradeCount, exchange.matches().size());
        if (probeLimit == 0) {
            return;
        }
        if (probe.size() == probeLimit) {
            probe.removeFirst();
        }
        probe.addLast(new ProbeObservation(command, exchange));
    }

    /** Finalizes the immutable evidence summary. */
    public QualificationStreamingSummary finish() {
        if (finished) {
            throw new IllegalStateException("streaming evidence was already finished");
        }
        finished = true;
        final MessageDigest publicProbeDigest = sha256();
        for (final ProbeObservation observation : probe) {
            QualificationCanonicalizer.updateCommand(publicProbeDigest, observation.command());
            QualificationCanonicalizer.updateExchange(publicProbeDigest, observation.exchange());
        }
        return new QualificationStreamingSummary(
                HexFormat.of().formatHex(commandDigest.digest()),
                HexFormat.of().formatHex(transcriptDigest.digest()),
                HexFormat.of().formatHex(publicProbeDigest.digest()),
                responseCount,
                tradeCount,
                probe.size());
    }

    /** Returns the current retained probe count for bounded-state assertions. */
    public int retainedProbeCount() {
        return probe.size();
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (final NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the JDK", exception);
        }
    }

    private record ProbeObservation(EngineCommand command, QualificationExchange exchange) {
    }
}
