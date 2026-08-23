package com.ultralatency.matching.qualification;

import com.ultralatency.matching.engine.CancelOrderCommand;
import com.ultralatency.matching.engine.EngineCommand;
import com.ultralatency.matching.engine.SubmitLimitCommand;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Package-private canonical command encoding for workload identity. */
final class QualificationCanonicalizer {

    static final String EMPTY_DIGEST =
            "0000000000000000000000000000000000000000000000000000000000000000";

    private QualificationCanonicalizer() {
    }

    static String digest(final List<EngineCommand> commands) {
        final MessageDigest digest = sha256();
        for (final EngineCommand command : commands) {
            digest.update(encode(command));
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    static String digest(final QualificationConfiguration configuration) {
        Objects.requireNonNull(configuration, "configuration");
        final MessageDigest digest = sha256();
        updateText(digest, configuration.profile().name());
        updateLong(digest, configuration.seed());
        updateLong(digest, configuration.commandCount());
        updateLong(digest, configuration.commandTimeout().toNanos());
        updateText(digest, configuration.outputDirectory().toString());
        return HexFormat.of().formatHex(digest.digest());
    }

    static String digest(final QualificationResult result) {
        Objects.requireNonNull(result, "result");
        final MessageDigest digest = sha256();
        digest.update((byte) (result.success() ? 1 : 0));
        updateLong(digest, result.acceptedCommands());
        updateLong(digest, result.responseCount());
        updateLong(digest, result.tradeCount());
        updateText(digest, result.checkpointDigestHex());
        updateText(digest, result.transcriptDigestHex());
        updateText(digest, result.publicProbeDigestHex());
        result.measurements().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    updateText(digest, entry.getKey());
                    updateText(digest, entry.getValue());
                });
        return HexFormat.of().formatHex(digest.digest());
    }

    private static void updateLong(final MessageDigest digest, final long value) {
        digest.update(ByteBuffer.allocate(Long.BYTES).putLong(value).array());
    }

    private static void updateText(final MessageDigest digest, final String value) {
        final byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }

    private static byte[] encode(final EngineCommand command) {
        if (command instanceof SubmitLimitCommand submit) {
            final ByteBuffer buffer = ByteBuffer.allocate(1 + Long.BYTES * 5);
            buffer.put((byte) 1);
            buffer.putLong(submit.sequence().value());
            buffer.putLong(submit.orderId().value());
            buffer.put((byte) submit.side().ordinal());
            buffer.putLong(submit.price().ticks());
            buffer.putLong(submit.quantity().units());
            return buffer.array();
        }
        if (command instanceof CancelOrderCommand cancel) {
            final ByteBuffer buffer = ByteBuffer.allocate(1 + Long.BYTES * 2);
            buffer.put((byte) 2);
            buffer.putLong(cancel.sequence().value());
            buffer.putLong(cancel.orderId().value());
            return buffer.array();
        }
        throw new IllegalArgumentException("unsupported command type: " + command.getClass());
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (final NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the JDK", exception);
        }
    }
}
