package com.ultralatency.matching.integration.durable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ultralatency.matching.domain.OrderId;
import com.ultralatency.matching.domain.Price;
import com.ultralatency.matching.domain.Quantity;
import com.ultralatency.matching.domain.Sequence;
import com.ultralatency.matching.domain.Side;
import com.ultralatency.matching.engine.EngineCommand;
import com.ultralatency.matching.engine.SubmitLimitCommand;
import com.ultralatency.matching.network.protocol.ClientRequestId;
import com.ultralatency.matching.pipeline.PipelinePublishOutcome;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class DurableCommandCoordinatorTest {

    @Test
    void appendsBeforePublishingAndAdvancesSequenceOnlyAfterDurability() {
        final List<String> events = new ArrayList<>();
        final DurableCommandCoordinator coordinator = coordinator(
                command -> events.add("append:" + command.sequence().value()),
                command -> {
                    events.add("publish:" + command.sequence().value());
                    return PipelinePublishOutcome.ACCEPTED;
                },
                failure -> { });
        coordinator.start();

        final LiveAcceptedOutcome outcome = coordinator.accept(
                new ClientRequestId(10),
                sequence -> command(sequence.toSequence(), 10));

        assertEquals(DurableOutcomeStage.LIVE_ACCEPTED, outcome.stage());
        assertEquals(new DurableCommandSequence(2), coordinator.nextCommandSequence());
        assertEquals(List.of("append:1", "publish:1"), events);
        assertEquals(DurableLifecycleState.RUNNING, coordinator.state());
        assertFalse(coordinator.terminalFailure().isPresent());
    }

    @Test
    void appendFailureNeverPublishesAndRetainsFirstCause() {
        final IOException appendFailure = new IOException("force failed");
        final List<String> events = new ArrayList<>();
        final AtomicReference<DurableTerminalFailure> observed = new AtomicReference<>();
        final AtomicInteger observations = new AtomicInteger();
        final DurableCommandCoordinator coordinator = coordinator(
                command -> {
                    events.add("append");
                    throw appendFailure;
                },
                command -> {
                    events.add("publish");
                    return PipelinePublishOutcome.ACCEPTED;
                },
                failure -> {
                    observed.set(failure);
                    observations.incrementAndGet();
                });
        coordinator.start();

        final DurableTerminalException first = assertThrows(
                DurableTerminalException.class,
                () -> coordinator.accept(new ClientRequestId(1), sequence -> command(sequence, 1)));
        final DurableTerminalException second = assertThrows(
                DurableTerminalException.class,
                () -> coordinator.accept(new ClientRequestId(2), sequence -> command(sequence, 2)));

        assertEquals(DurableFailureStage.APPEND, first.failure().stage());
        assertSame(appendFailure, first.failure().cause());
        assertSame(first.failure(), second.failure());
        assertSame(first.failure(), observed.get());
        assertEquals(1, observations.get());
        assertEquals(List.of("append"), events);
        assertEquals(new DurableCommandSequence(1), coordinator.nextCommandSequence());
        assertEquals(DurableLifecycleState.FAILED, coordinator.state());
    }

    @Test
    void durableThenFullConsumesSequenceAndFailsStop() {
        final List<String> events = new ArrayList<>();
        final DurableCommandCoordinator coordinator = coordinator(
                command -> events.add("append:" + command.sequence().value()),
                command -> {
                    events.add("publish:" + command.sequence().value());
                    return PipelinePublishOutcome.FULL;
                },
                failure -> { });
        coordinator.start();

        final DurableTerminalException failure = assertThrows(
                DurableTerminalException.class,
                () -> coordinator.accept(new ClientRequestId(1), sequence -> command(sequence, 1)));

        assertEquals(DurableFailureStage.DURABLE_THEN_FULL, failure.failure().stage());
        assertEquals(new DurableCommandSequence(2), coordinator.nextCommandSequence());
        assertEquals(List.of("append:1", "publish:1"), events);
        assertEquals(DurableLifecycleState.FAILED, coordinator.state());
        assertThrows(
                DurableTerminalException.class,
                () -> coordinator.accept(new ClientRequestId(2), sequence -> command(sequence, 2)));
    }

    @Test
    void publishFailureAfterDurabilityIsTerminalAndNotRetried() {
        final RuntimeException publishFailure = new RuntimeException("pipeline failed");
        final AtomicInteger publishCalls = new AtomicInteger();
        final DurableCommandCoordinator coordinator = coordinator(
                command -> { },
                command -> {
                    publishCalls.incrementAndGet();
                    throw publishFailure;
                },
                failure -> { });
        coordinator.start();

        final DurableTerminalException failure = assertThrows(
                DurableTerminalException.class,
                () -> coordinator.accept(new ClientRequestId(1), sequence -> command(sequence, 1)));

        assertEquals(DurableFailureStage.PIPELINE, failure.failure().stage());
        assertSame(publishFailure, failure.failure().cause());
        assertEquals(1, publishCalls.get());
        assertThrows(
                DurableTerminalException.class,
                () -> coordinator.accept(new ClientRequestId(2), sequence -> command(sequence, 2)));
        assertEquals(1, publishCalls.get());
    }

    @Test
    void factoryMustPreserveCoordinatorSequenceBeforeAppend() {
        final AtomicInteger appendCalls = new AtomicInteger();
        final DurableCommandCoordinator coordinator = coordinator(
                command -> appendCalls.incrementAndGet(),
                command -> PipelinePublishOutcome.ACCEPTED,
                failure -> { });
        coordinator.start();

        assertThrows(
                IllegalArgumentException.class,
                () -> coordinator.accept(
                        new ClientRequestId(1),
                        sequence -> command(sequence.next(), 1)));

        assertEquals(0, appendCalls.get());
        assertEquals(new DurableCommandSequence(1), coordinator.nextCommandSequence());
        assertEquals(DurableLifecycleState.RUNNING, coordinator.state());
    }

    @Test
    void lifecycleIsSynchronousAndRejectsAdmissionAfterShutdown() {
        final DurableCommandCoordinator coordinator = coordinator(
                command -> { },
                command -> PipelinePublishOutcome.ACCEPTED,
                failure -> { });

        assertEquals(DurableLifecycleState.NEW, coordinator.state());
        assertThrows(
                IllegalStateException.class,
                () -> coordinator.accept(new ClientRequestId(1), sequence -> command(sequence, 1)));

        coordinator.start();
        assertEquals(DurableLifecycleState.RUNNING, coordinator.state());
        assertThrows(IllegalStateException.class, coordinator::start);
        assertEquals(DurableLifecycleState.STOPPED, coordinator.shutdown());
        assertEquals(DurableLifecycleState.STOPPED, coordinator.shutdown());
        assertThrows(
                IllegalStateException.class,
                () -> coordinator.accept(new ClientRequestId(1), sequence -> command(sequence, 1)));
        assertTrue(coordinator.terminalFailure().isEmpty());
    }

    private static DurableCommandCoordinator coordinator(
            final DurableAppendPort appendPort,
            final DurablePublishPort publishPort,
            final DurableFailurePort failurePort) {
        return new DurableCommandCoordinator(appendPort, publishPort, failurePort);
    }

    private static EngineCommand command(
            final DurableCommandSequence sequence,
            final long orderId) {
        return command(sequence.toSequence(), orderId);
    }

    private static EngineCommand command(final Sequence sequence, final long orderId) {
        return new SubmitLimitCommand(
                sequence,
                new OrderId(orderId),
                Side.BUY,
                new Price(100),
                new Quantity(1));
    }
}
