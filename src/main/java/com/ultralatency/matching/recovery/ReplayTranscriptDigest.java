package com.ultralatency.matching.recovery;

import com.ultralatency.matching.domain.Execution;
import com.ultralatency.matching.domain.Trade;
import com.ultralatency.matching.engine.CommandOutcome;
import com.ultralatency.matching.engine.EngineResult;
import com.ultralatency.matching.engine.MatchResult;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/** Canonical SHA-256 digest over ordered public replay result values. */
public final class ReplayTranscriptDigest {

    private static final byte FORMAT_VERSION = 1;
    private static final byte RESULT_TAG = 0x52;
    private static final byte MATCH_TAG = 0x4d;
    private static final byte EXECUTION_TAG = 0x45;
    private static final byte RESULT_END_TAG = 0x72;

    private ReplayTranscriptDigest() {
    }

    /**
     * Computes the canonical lowercase SHA-256 digest.
     *
     * @param results ordered public results
     * @return 64-character lowercase hexadecimal SHA-256 digest
     */
    public static String sha256Hex(final List<EngineResult> results) {
        Objects.requireNonNull(results, "results");
        final MessageDigest digest = newDigest();
        for (final EngineResult result : results) {
            updateResult(digest, result);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static void updateResult(final MessageDigest digest, final EngineResult result) {
        Objects.requireNonNull(result, "result");
        updateByte(digest, RESULT_TAG);
        updateByte(digest, FORMAT_VERSION);
        updateLong(digest, result.commandSequence().value());
        updateInt(digest, outcomeCode(result.outcome()));
        updateInt(digest, result.matches().size());
        for (final MatchResult match : result.matches()) {
            updateMatch(digest, match);
        }
        updateByte(digest, RESULT_END_TAG);
    }

    private static void updateMatch(final MessageDigest digest, final MatchResult match) {
        Objects.requireNonNull(match, "match");
        updateByte(digest, MATCH_TAG);
        updateLong(digest, match.eventSequence().value());
        updateTrade(digest, match.trade());
        updateExecution(digest, match.makerExecution());
        updateExecution(digest, match.takerExecution());
    }

    private static void updateTrade(final MessageDigest digest, final Trade trade) {
        updateLong(digest, trade.tradeId().value());
        updateLong(digest, trade.eventSequence().value());
        updateLong(digest, trade.price().ticks());
        updateLong(digest, trade.quantity().units());
        updateLong(digest, trade.makerOrderId().value());
        updateLong(digest, trade.takerOrderId().value());
    }

    private static void updateExecution(final MessageDigest digest, final Execution execution) {
        updateByte(digest, EXECUTION_TAG);
        updateLong(digest, execution.tradeId().value());
        updateLong(digest, execution.orderId().value());
        updateLong(digest, execution.price().ticks());
        updateLong(digest, execution.quantity().units());
        updateLong(digest, execution.remainingQuantityUnits());
    }

    private static int outcomeCode(final CommandOutcome outcome) {
        return switch (Objects.requireNonNull(outcome, "outcome")) {
            case ACCEPTED -> 1;
            case CANCELED -> 2;
            case NOT_FOUND -> 3;
        };
    }

    private static void updateByte(final MessageDigest digest, final byte value) {
        digest.update(value);
    }

    private static void updateInt(final MessageDigest digest, final int value) {
        final ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES).order(ByteOrder.BIG_ENDIAN);
        digest.update(buffer.putInt(value).array());
    }

    private static void updateLong(final MessageDigest digest, final long value) {
        final ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES).order(ByteOrder.BIG_ENDIAN);
        digest.update(buffer.putLong(value).array());
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (final NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK must provide SHA-256", exception);
        }
    }
}
