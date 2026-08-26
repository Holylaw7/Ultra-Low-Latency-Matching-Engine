package com.ultralatency.matching.qualification.ga.correctness;

import com.ultralatency.matching.engine.CancelOrderCommand;
import com.ultralatency.matching.engine.CommandOutcome;
import com.ultralatency.matching.engine.EngineCommand;
import com.ultralatency.matching.engine.EngineResult;
import com.ultralatency.matching.engine.MatchResult;
import com.ultralatency.matching.engine.SubmitLimitCommand;
import com.ultralatency.matching.qualification.QualificationExchange;
import com.ultralatency.matching.qualification.QualificationMatch;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/** Canonical digest shared by live Protocol observations and offline replay results. */
public final class GaCorrectnessCanonicalizer {

    private static final byte RESULT_TAG = 0x52;
    private static final byte FORMAT_VERSION = 1;
    private static final byte RESULT_END_TAG = 0x72;

    private GaCorrectnessCanonicalizer() {
    }

    /** Computes the canonical command digest used by the WAL codec identity. */
    public static String commands(final List<EngineCommand> commands) {
        Objects.requireNonNull(commands, "commands");
        final MessageDigest digest = sha256();
        commands.forEach(command -> updateCommandDigest(digest, command));
        return HexFormat.of().formatHex(digest.digest());
    }

    /** Updates a command digest without retaining the command. */
    public static void updateCommandDigest(
            final MessageDigest digest,
            final EngineCommand command) {
        Objects.requireNonNull(digest, "digest");
        updateCommand(digest, command);
    }

    /** Computes a canonical digest over public Protocol observations. */
    public static String exchanges(final List<QualificationExchange> exchanges) {
        Objects.requireNonNull(exchanges, "exchanges");
        final MessageDigest digest = sha256();
        exchanges.forEach(exchange -> updateExchangeDigest(digest, exchange));
        return HexFormat.of().formatHex(digest.digest());
    }

    /** Updates a public-observation digest without retaining the exchange. */
    public static void updateExchangeDigest(
            final MessageDigest digest,
            final QualificationExchange exchange) {
        Objects.requireNonNull(digest, "digest");
        updateExchange(digest, exchange);
    }

    /** Computes a canonical digest over offline replay results. */
    public static String results(final List<EngineResult> results) {
        return results(results, 0);
    }

    /** Computes a canonical digest over an offline replay suffix. */
    public static String results(final List<EngineResult> results, final int startIndex) {
        Objects.requireNonNull(results, "results");
        if (startIndex < 0 || startIndex > results.size()) {
            throw new IllegalArgumentException("startIndex is outside results");
        }
        final MessageDigest digest = sha256();
        for (int index = startIndex; index < results.size(); index++) {
            updateResult(digest, results.get(index));
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    /** Computes the fixed two-observation public probe digest. */
    public static String probe(
            final List<EngineCommand> commands,
            final List<QualificationExchange> exchanges) {
        Objects.requireNonNull(commands, "commands");
        Objects.requireNonNull(exchanges, "exchanges");
        if (commands.size() != exchanges.size()) {
            throw new IllegalArgumentException("probe command and exchange sizes differ");
        }
        final MessageDigest digest = sha256();
        final int start = Math.max(0, commands.size() - 2);
        for (int index = start; index < commands.size(); index++) {
            updateCommandDigest(digest, commands.get(index));
            updateExchangeDigest(digest, exchanges.get(index));
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    /** Computes the fixed two-observation public probe digest for replay results. */
    public static String probeResults(
            final List<EngineCommand> commands,
            final List<EngineResult> results,
            final int commandOffset) {
        Objects.requireNonNull(commands, "commands");
        Objects.requireNonNull(results, "results");
        if (commandOffset < 0 || commandOffset + results.size() > commands.size()) {
            throw new IllegalArgumentException("replay results do not align with commands");
        }
        final MessageDigest digest = sha256();
        final int start = Math.max(0, results.size() - 2);
        for (int index = start; index < results.size(); index++) {
            updateCommandDigest(digest, commands.get(commandOffset + index));
            updateResult(digest, results.get(index));
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    /** Converts one replay result to the public outcome code. */
    public static int outcomeCode(final CommandOutcome outcome) {
        return switch (Objects.requireNonNull(outcome, "outcome")) {
            case ACCEPTED -> 1;
            case CANCELED -> 2;
            case NOT_FOUND -> 3;
        };
    }

    private static void updateExchange(
            final MessageDigest digest,
            final QualificationExchange exchange) {
        updateResultHeader(digest, exchange.commandSequence(), exchange.outcomeCode(),
                exchange.matches().size());
        for (final QualificationMatch match : exchange.matches()) {
            updateMatch(digest, match.eventSequence(), match.tradeId(), match.price(),
                    match.quantity(), match.makerOrderId(), match.takerOrderId());
        }
        digest.update(RESULT_END_TAG);
    }

    private static void updateResult(final MessageDigest digest, final EngineResult result) {
        Objects.requireNonNull(result, "result");
        updateResultHeader(digest, result.commandSequence().value(),
                outcomeCode(result.outcome()), result.matches().size());
        for (final MatchResult match : result.matches()) {
            updateMatch(digest,
                    match.eventSequence().value(),
                    match.trade().tradeId().value(),
                    match.trade().price().ticks(),
                    match.trade().quantity().units(),
                    match.trade().makerOrderId().value(),
                    match.trade().takerOrderId().value());
        }
        digest.update(RESULT_END_TAG);
    }

    private static void updateResultHeader(
            final MessageDigest digest,
            final long commandSequence,
            final int outcome,
            final int matchCount) {
        digest.update(RESULT_TAG);
        digest.update(FORMAT_VERSION);
        updateLong(digest, commandSequence);
        updateInt(digest, outcome);
        updateInt(digest, matchCount);
    }

    private static void updateMatch(
            final MessageDigest digest,
            final long eventSequence,
            final long tradeId,
            final long price,
            final long quantity,
            final long makerOrderId,
            final long takerOrderId) {
        updateLong(digest, eventSequence);
        updateLong(digest, tradeId);
        updateLong(digest, price);
        updateLong(digest, quantity);
        updateLong(digest, makerOrderId);
        updateLong(digest, takerOrderId);
    }

    private static void updateCommand(
            final MessageDigest digest,
            final EngineCommand command) {
        Objects.requireNonNull(command, "command");
        if (command instanceof SubmitLimitCommand submit) {
            final ByteBuffer buffer = ByteBuffer.allocate(1 + Long.BYTES * 5)
                    .order(ByteOrder.BIG_ENDIAN);
            buffer.put((byte) 1)
                    .putLong(submit.sequence().value())
                    .putLong(submit.orderId().value())
                    .put((byte) submit.side().ordinal())
                    .putLong(submit.price().ticks())
                    .putLong(submit.quantity().units());
            digest.update(buffer.array());
        } else if (command instanceof CancelOrderCommand cancel) {
            final ByteBuffer buffer = ByteBuffer.allocate(1 + Long.BYTES * 2)
                    .order(ByteOrder.BIG_ENDIAN);
            buffer.put((byte) 2)
                    .putLong(cancel.sequence().value())
                    .putLong(cancel.orderId().value());
            digest.update(buffer.array());
        } else {
            throw new IllegalArgumentException("unsupported command type: " + command.getClass());
        }
    }

    private static void updateInt(final MessageDigest digest, final int value) {
        digest.update(ByteBuffer.allocate(Integer.BYTES)
                .order(ByteOrder.BIG_ENDIAN).putInt(value).array());
    }

    private static void updateLong(final MessageDigest digest, final long value) {
        digest.update(ByteBuffer.allocate(Long.BYTES)
                .order(ByteOrder.BIG_ENDIAN).putLong(value).array());
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (final NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the JDK", exception);
        }
    }
}
