package com.ultralatency.matching.qualification;

import com.ultralatency.matching.domain.OrderId;
import com.ultralatency.matching.domain.Price;
import com.ultralatency.matching.domain.Quantity;
import com.ultralatency.matching.domain.Sequence;
import com.ultralatency.matching.domain.Side;
import com.ultralatency.matching.engine.CancelOrderCommand;
import com.ultralatency.matching.engine.EngineCommand;
import com.ultralatency.matching.engine.SubmitLimitCommand;
import java.util.ArrayList;
import java.util.List;

/**
 * Version-one deterministic qualification workload generator.
 */
public final class QualificationWorkloadV1 {

    /** Stable workload version written to every qualification manifest. */
    public static final String VERSION = "qualification-workload-v1";

    /** Explicit identity for the bounded-state memory qualification workload. */
    public static final String MEMORY_STEADY_STATE_VERSION =
            "qualification-memory-steady-state-v1";

    /** Maximum active orders after any complete steady-state cycle. */
    public static final int MEMORY_STEADY_STATE_MAX_ACTIVE_ORDERS = 1;

    private QualificationWorkloadV1() {
    }

    /**
     * Generates an immutable workload without starting a runtime or creating output files.
     *
     * @param configuration validated workload configuration
     * @return deterministic command vector
     */
    public static QualificationWorkload generate(
            final QualificationConfiguration configuration) {
        if (configuration == null) {
            throw new NullPointerException("configuration");
        }
        final List<EngineCommand> commands = new ArrayList<>(configuration.commandCount());
        for (int index = 0; index < configuration.commandCount(); index++) {
            commands.add(commandAt(configuration, index));
        }
        final List<EngineCommand> immutableCommands = List.copyOf(commands);
        return new QualificationWorkload(
                version(configuration.profile()),
                configuration.profile(),
                configuration.seed(),
                immutableCommands,
                QualificationCanonicalizer.digest(immutableCommands));
    }

    /** Returns one deterministic command without materializing the complete workload. */
    static EngineCommand commandAt(
            final QualificationConfiguration configuration,
            final int index) {
        if (configuration == null) {
            throw new NullPointerException("configuration");
        }
        if (index < 0 || index >= configuration.commandCount()) {
            throw new IndexOutOfBoundsException("workload index is outside configuration");
        }
        final long sequence = index + 1L;
        final int cycleLength = cycleLength(configuration.profile());
        final int cycle = index / cycleLength;
        final int position = index % cycleLength;
        return command(configuration.profile(), configuration.seed(),
                sequence, cycle, position);
    }

    /** Compares a persisted command vector with the deterministic source without a second vector. */
    static boolean matches(
            final List<EngineCommand> commands,
            final QualificationConfiguration configuration) {
        if (commands == null || configuration == null
                || commands.size() != configuration.commandCount()) {
            return false;
        }
        for (int index = 0; index < commands.size(); index++) {
            if (!commands.get(index).equals(commandAt(configuration, index))) {
                return false;
            }
        }
        return true;
    }

    /** Returns the version identity associated with one profile. */
    static String version(final QualificationProfile profile) {
        return profile == QualificationProfile.MEMORY_STEADY_STATE_V1
                ? MEMORY_STEADY_STATE_VERSION : VERSION;
    }

    private static int cycleLength(final QualificationProfile profile) {
        return switch (profile) {
            case LIFECYCLE_MIX -> 6;
            case CROSSING_MULTI_MATCH -> 6;
            case RESTING_DEPTH -> 8;
            case MEMORY_STEADY_STATE_V1 -> 4;
        };
    }

    private static EngineCommand command(
            final QualificationProfile profile,
            final long seed,
            final long sequence,
            final int cycle,
            final int position) {
        final long baseOrderId = Math.addExact(
                Math.multiplyExact((long) cycle, 1_000L),
                1L + Math.floorMod(seed, 97L));
        final long priceOffset = Math.floorMod(seed, 7L);
        return switch (profile) {
            case LIFECYCLE_MIX -> lifecycleCommand(
                    sequence, baseOrderId, priceOffset, position);
            case CROSSING_MULTI_MATCH -> crossingCommand(
                    sequence, baseOrderId, priceOffset, position);
            case RESTING_DEPTH -> restingCommand(
                    sequence, baseOrderId, priceOffset, position);
            case MEMORY_STEADY_STATE_V1 -> memorySteadyStateCommand(
                    sequence, baseOrderId, priceOffset, position);
        };
    }

    private static EngineCommand memorySteadyStateCommand(
            final long sequence,
            final long baseOrderId,
            final long priceOffset,
            final int position) {
        return switch (position) {
            case 0 -> submit(sequence, baseOrderId, Side.SELL, 100L + priceOffset, 1L);
            case 1 -> submit(sequence, baseOrderId + 1L, Side.BUY, 100L + priceOffset, 1L);
            case 2 -> submit(sequence, baseOrderId + 2L, Side.SELL, 101L + priceOffset, 1L);
            case 3 -> cancel(sequence, baseOrderId + 2L);
            default -> throw new IllegalArgumentException("unsupported memory steady-state position");
        };
    }

    private static EngineCommand lifecycleCommand(
            final long sequence,
            final long baseOrderId,
            final long priceOffset,
            final int position) {
        return switch (position) {
            case 0 -> submit(sequence, baseOrderId, Side.SELL, 100L + priceOffset, 2L);
            case 1 -> submit(sequence, baseOrderId + 1L, Side.SELL, 101L + priceOffset, 1L);
            case 2, 3 -> cancel(sequence, baseOrderId);
            case 4 -> submit(sequence, baseOrderId + 2L, Side.BUY, 99L + priceOffset, 1L);
            case 5 -> cancel(sequence, baseOrderId + 2L);
            default -> throw new IllegalArgumentException("unsupported lifecycle position");
        };
    }

    private static EngineCommand crossingCommand(
            final long sequence,
            final long baseOrderId,
            final long priceOffset,
            final int position) {
        return switch (position) {
            case 0 -> submit(sequence, baseOrderId, Side.SELL, 100L + priceOffset, 1L);
            case 1 -> submit(sequence, baseOrderId + 1L, Side.SELL, 101L + priceOffset, 2L);
            case 2 -> submit(sequence, baseOrderId + 2L, Side.BUY, 101L + priceOffset, 2L);
            case 3 -> submit(sequence, baseOrderId + 3L, Side.BUY, 101L + priceOffset, 1L);
            case 4 -> submit(sequence, baseOrderId + 4L, Side.SELL, 99L + priceOffset, 1L);
            case 5 -> submit(sequence, baseOrderId + 5L, Side.BUY, 99L + priceOffset, 1L);
            default -> throw new IllegalArgumentException("unsupported crossing position");
        };
    }

    private static EngineCommand restingCommand(
            final long sequence,
            final long baseOrderId,
            final long priceOffset,
            final int position) {
        return switch (position) {
            case 0 -> submit(sequence, baseOrderId, Side.SELL, 100L + priceOffset, 1L);
            case 1 -> submit(sequence, baseOrderId + 1L, Side.SELL, 101L + priceOffset, 1L);
            case 2 -> submit(sequence, baseOrderId + 2L, Side.BUY, 99L + priceOffset, 1L);
            case 3 -> submit(sequence, baseOrderId + 3L, Side.BUY, 98L + priceOffset, 1L);
            case 4 -> cancel(sequence, baseOrderId);
            case 5 -> cancel(sequence, baseOrderId + 1L);
            case 6 -> cancel(sequence, baseOrderId + 2L);
            case 7 -> cancel(sequence, baseOrderId + 3L);
            default -> throw new IllegalArgumentException("unsupported resting position");
        };
    }

    private static SubmitLimitCommand submit(
            final long sequence,
            final long orderId,
            final Side side,
            final long price,
            final long quantity) {
        return new SubmitLimitCommand(
                Sequence.of(sequence),
                OrderId.of(orderId),
                side,
                Price.of(price),
                Quantity.of(quantity));
    }

    private static CancelOrderCommand cancel(final long sequence, final long orderId) {
        return new CancelOrderCommand(Sequence.of(sequence), OrderId.of(orderId));
    }

}
