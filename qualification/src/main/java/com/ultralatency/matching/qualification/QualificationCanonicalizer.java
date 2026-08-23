package com.ultralatency.matching.qualification;

import com.ultralatency.matching.engine.CancelOrderCommand;
import com.ultralatency.matching.engine.EngineCommand;
import com.ultralatency.matching.engine.SubmitLimitCommand;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/** Package-private canonical command encoding for workload identity. */
final class QualificationCanonicalizer {

    private QualificationCanonicalizer() {
    }

    static String digest(final List<EngineCommand> commands) {
        final MessageDigest digest = sha256();
        for (final EngineCommand command : commands) {
            digest.update(encode(command));
        }
        return HexFormat.of().formatHex(digest.digest());
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
