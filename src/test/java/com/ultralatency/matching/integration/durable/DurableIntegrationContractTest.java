package com.ultralatency.matching.integration.durable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ultralatency.matching.domain.OrderId;
import com.ultralatency.matching.domain.Price;
import com.ultralatency.matching.domain.Quantity;
import com.ultralatency.matching.domain.Sequence;
import com.ultralatency.matching.domain.Side;
import com.ultralatency.matching.engine.CommandOutcome;
import com.ultralatency.matching.engine.EngineCommand;
import com.ultralatency.matching.engine.EngineResult;
import com.ultralatency.matching.engine.SubmitLimitCommand;
import com.ultralatency.matching.network.protocol.ClientRequestId;
import com.ultralatency.matching.persistence.wal.WalConfiguration;
import com.ultralatency.matching.persistence.wal.WalDurabilityMode;
import com.ultralatency.matching.pipeline.PipelineConfiguration;
import com.ultralatency.matching.pipeline.PipelinePublishOutcome;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class DurableIntegrationContractTest {

    @Test
    void liveConfigurationAllowsOnlySynchronousAppend() {
        final Path directory = Path.of("target", "durable-contract-test");
        final DurableConfiguration defaults = DurableConfiguration.defaults(directory);

        assertEquals(LiveDurabilityMode.SYNC_EACH_APPEND, defaults.durabilityMode());
        assertEquals(LiveDurabilityMode.SYNC_EACH_APPEND, defaults.liveDurabilityMode());
        assertEquals(WalDurabilityMode.SYNC_EACH_APPEND,
                defaults.walConfiguration().durabilityMode());
        assertEquals(PipelineConfiguration.defaults(), defaults.pipelineConfiguration());
        assertEquals(Duration.ofSeconds(2), defaults.shutdownTimeout());
        assertEquals(true, defaults.requiresEmptyWal());

        final WalConfiguration buffered = new WalConfiguration(
                directory,
                WalConfiguration.DEFAULT_SEGMENT_SIZE_BYTES,
                WalDurabilityMode.BUFFERED);
        assertThrows(IllegalArgumentException.class, () -> new DurableConfiguration(buffered));
        assertThrows(
                IllegalArgumentException.class,
                () -> new DurableConfiguration(
                        WalConfiguration.defaults(directory),
                        PipelineConfiguration.defaults(),
                        Duration.ZERO));
    }

    @Test
    void durableCommandSequenceIsPositiveAndAdvancesWithoutChangingIdentityDomains() {
        final DurableCommandSequence first = new DurableCommandSequence(9);

        assertEquals(9, first.value());
        assertEquals(new DurableCommandSequence(10), first.next());
        assertEquals(new Sequence(9), first.toSequence());
        assertEquals(new Sequence(9), first.sequence());
        assertThrows(
                IllegalArgumentException.class,
                () -> new DurableCommandSequence(0));
        assertThrows(
                ArithmeticException.class,
                () -> new DurableCommandSequence(Long.MAX_VALUE).next());
    }

    @Test
    void identityKeepsRequestAndCommandSequencesIndependent() {
        final DurableCommandIdentity identity = new DurableCommandIdentity(
                new ClientRequestId(41), new DurableCommandSequence(9));

        assertEquals(new ClientRequestId(41), identity.requestId());
        assertEquals(new DurableCommandSequence(9), identity.commandSequence());
        assertEquals(new Sequence(9), identity.domainCommandSequence());
        assertThrows(
                NullPointerException.class,
                () -> new DurableCommandIdentity(null, new DurableCommandSequence(1)));
        assertThrows(
                NullPointerException.class,
                () -> new DurableCommandIdentity(new ClientRequestId(1), null));
    }

    @Test
    void commandEnvelopeRejectsSequenceIdentityMismatch() {
        final EngineCommand command = command(7);
        final DurableCommand envelope = DurableCommand.of(new ClientRequestId(12), command);

        assertEquals(new ClientRequestId(12), envelope.identity().requestId());
        assertEquals(command, envelope.command());
        assertThrows(
                IllegalArgumentException.class,
                () -> new DurableCommand(
                        new DurableCommandIdentity(
                                new ClientRequestId(12), new DurableCommandSequence(8)),
                        command));
    }

    @Test
    void outcomesExposeStrictlyOrderedDistinctMilestones() {
        final DurableCommandIdentity identity = new DurableCommandIdentity(
                new ClientRequestId(5), new DurableCommandSequence(7));
        final DurableOutcome durable = new DurableOutcome(identity);
        final LiveAcceptedOutcome accepted = new LiveAcceptedOutcome(durable);
        final EngineResult result = new EngineResult(
                new Sequence(7), CommandOutcome.ACCEPTED, List.of());
        final ResponseCompletedOutcome completed = new ResponseCompletedOutcome(accepted, result);

        assertEquals(DurableOutcomeStage.DURABLE, durable.stage());
        assertEquals(DurableOutcomeStage.LIVE_ACCEPTED, accepted.stage());
        assertEquals(DurableOutcomeStage.RESPONSE_COMPLETED, completed.stage());
        assertEquals(identity, durable.identity());
        assertEquals(identity, accepted.identity());
        assertEquals(identity, completed.identity());
        assertEquals(new ClientRequestId(5), completed.requestId());
        assertEquals(new DurableCommandSequence(7), completed.commandSequence());
        assertEquals(new Sequence(7), completed.domainCommandSequence());
        assertSame(result, completed.result());
        assertThrows(
                IllegalArgumentException.class,
                () -> new ResponseCompletedOutcome(
                        accepted,
                        new EngineResult(new Sequence(8), CommandOutcome.ACCEPTED, List.of())));
    }

    @Test
    void terminalFailureRetainsStageAndOriginalCause() {
        final IOException cause = new IOException("force failed");
        final DurableTerminalFailure failure = new DurableTerminalFailure(
                DurableFailureStage.APPEND, cause);

        assertEquals(DurableFailureStage.APPEND, failure.stage());
        assertSame(cause, failure.cause());
        assertThrows(
                NullPointerException.class,
                () -> new DurableTerminalFailure(DurableFailureStage.APPEND, null));
    }

    @Test
    void portsPreserveExistingAppendAndPublishBoundaries() throws IOException {
        final AtomicReference<EngineCommand> appended = new AtomicReference<>();
        final AtomicReference<EngineCommand> published = new AtomicReference<>();
        final AtomicReference<DurableCommandIdentity> observedIdentity = new AtomicReference<>();
        final AtomicReference<EngineResult> observedResult = new AtomicReference<>();
        final DurableAppendPort appendPort = appended::set;
        final DurablePublishPort publishPort = command -> {
            published.set(command);
            return PipelinePublishOutcome.ACCEPTED;
        };
        final DurableResultPort resultPort = (identity, result) -> {
            observedIdentity.set(identity);
            observedResult.set(result);
        };
        final EngineCommand command = command(3);
        final DurableCommandIdentity identity = new DurableCommandIdentity(
                new ClientRequestId(2), new DurableCommandSequence(command.sequence()));
        final EngineResult result = new EngineResult(
                command.sequence(), CommandOutcome.ACCEPTED, List.of());

        appendPort.append(command);
        assertEquals(PipelinePublishOutcome.ACCEPTED, publishPort.tryPublish(command));
        resultPort.onResult(identity, result);

        assertSame(command, appended.get());
        assertSame(command, published.get());
        assertSame(identity, observedIdentity.get());
        assertSame(result, observedResult.get());
    }

    @Test
    void commandFactoryReceivesCoordinatorSequence() {
        final AtomicReference<DurableCommandSequence> received = new AtomicReference<>();
        final DurableCommandFactory factory = sequence -> {
            received.set(sequence);
            return command(sequence.value());
        };

        final EngineCommand built = factory.create(new Sequence(13));

        assertEquals(new DurableCommandSequence(13), received.get());
        assertEquals(new Sequence(13), built.sequence());
    }

    @Test
    void coordinatorPortExposesAdmissionAndTerminalObservation() {
        final DurableCommandCoordinatorPort coordinator =
                new DurableCommandCoordinatorPort() {
                    @Override
                    public LiveAcceptedOutcome accept(
                            final ClientRequestId requestId,
                            final DurableCommandFactory commandFactory) {
                        final EngineCommand command = commandFactory.create(new Sequence(1));
                        final DurableCommandIdentity identity = new DurableCommandIdentity(
                                requestId, new DurableCommandSequence(command.sequence()));
                        return new LiveAcceptedOutcome(new DurableOutcome(identity));
                    }

                    @Override
                    public DurableLifecycleState state() {
                        return DurableLifecycleState.RUNNING;
                    }

                    @Override
                    public Optional<DurableTerminalFailure> terminalFailure() {
                        return Optional.empty();
                    }
                };

        final LiveAcceptedOutcome accepted = coordinator.accept(
                new ClientRequestId(1), sequence -> command(sequence.value()));

        assertEquals(DurableOutcomeStage.LIVE_ACCEPTED, accepted.stage());
        assertEquals(DurableLifecycleState.RUNNING, coordinator.state());
        assertEquals(Optional.empty(), coordinator.terminalFailure());
    }

    private static EngineCommand command(final long sequence) {
        return new SubmitLimitCommand(
                new Sequence(sequence),
                new OrderId(sequence),
                Side.BUY,
                new Price(100),
                new Quantity(1));
    }
}
