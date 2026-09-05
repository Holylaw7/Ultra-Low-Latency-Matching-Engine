package com.ultralatency.matching.qualification;

import com.ultralatency.matching.domain.Side;
import com.ultralatency.matching.engine.CancelOrderCommand;
import com.ultralatency.matching.engine.EngineCommand;
import com.ultralatency.matching.engine.SubmitLimitCommand;
import com.ultralatency.matching.network.protocol.ProtocolConstants;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

/**
 * Qualification-only Protocol v2 client for a paced public-path workload.
 *
 * <p>The caller owns the monotonic offer clock. This client owns one bounded wire-pending request
 * window and a separate response reader, so parsing a complete response never blocks the caller's
 * next scheduled offer. A response is removed from the wire-pending window only after the complete
 * response has passed framing, protocol, correlation and ordering validation. The parsed result is
 * then transferred exactly once to a separately bounded completion queue.</p>
 */
public final class ProtocolV2PacedQualificationClient implements AutoCloseable {

    private final Socket socket;
    private final InputStream input;
    private final OutputStream output;
    private final int maxInFlight;
    private final Object monitor = new Object();
    private final LinkedHashMap<Long, Pending> wirePending = new LinkedHashMap<>();
    private final Deque<CompletedExchange> completed;
    private final Thread readerThread;
    private volatile IOException readerFailure;
    private boolean closed;
    private boolean requestIdExhausted;
    private long nextRequestId = 1L;
    private int maximumObservedInFlight;
    private int maximumObservedCompleted;
    private long readerWakeCount;
    private final List<Long> readerWakeNanos = new ArrayList<>();
    private long capacityReleaseCount;

    /** Connects a bounded paced client to a Protocol v2 server. */
    public ProtocolV2PacedQualificationClient(
            final InetSocketAddress address,
            final Duration timeout,
            final int maxInFlight) throws IOException {
        Objects.requireNonNull(address, "address");
        Objects.requireNonNull(timeout, "timeout");
        if (maxInFlight < 1 || maxInFlight > ProtocolConstants.MAX_PIPELINED_IN_FLIGHT) {
            throw new IllegalArgumentException("maxInFlight is outside the protocol hard bound");
        }
        this.maxInFlight = maxInFlight;
        completed = new ArrayDeque<>(maxInFlight);
        final int timeoutMillis = timeoutMillis(timeout);
        socket = new Socket();
        socket.connect(address, timeoutMillis);
        socket.setSoTimeout(timeoutMillis);
        input = socket.getInputStream();
        output = socket.getOutputStream();
        readerThread = new Thread(this::readResponses, "qualification-protocol-v2-reader");
        readerThread.setDaemon(true);
        readerThread.start();
    }

    /** @return the configured maximum number of requests in flight */
    public int maxInFlight() {
        return maxInFlight;
    }

    /** @return requests still awaiting a fully validated response on the wire */
    public int inFlight() {
        synchronized (monitor) {
            return wirePending.size();
        }
    }

    /** @return the largest observed bounded pending size */
    public int maximumObservedInFlight() {
        synchronized (monitor) {
            return maximumObservedInFlight;
        }
    }

    /**
     * Returns the largest number of requests retained in the wire-pending map.
     *
     * <p>This is intentionally an alias with an evidence-oriented name.  A complete response is
     * removed from this map before it is transferred to the separately bounded completion queue,
     * so this value is the raw maximum pending-wire depth rather than a scheduler-side estimate.
     * Keeping the accessor on the qualification client also prevents the evidence publisher from
     * deriving a wire bound from a copied counter.</p>
     *
     * @return the largest observed pending-wire depth
     */
    public int maximumObservedPendingWire() {
        synchronized (monitor) {
            return maximumObservedInFlight;
        }
    }

    /** @return complete responses waiting for caller consumption */
    public int completedCount() {
        synchronized (monitor) {
            return completed.size();
        }
    }

    /** @return the largest observed number of retained complete responses */
    public int maximumObservedCompleted() {
        synchronized (monitor) {
            return maximumObservedCompleted;
        }
    }

    /** @return the number of qualification-side reader wake signals issued for new work */
    public long readerWakeCount() {
        synchronized (monitor) {
            return readerWakeCount;
        }
    }

    /** @return monotonic timestamps for every qualification-side reader wake signal */
    public List<Long> readerWakeNanos() {
        synchronized (monitor) {
            return List.copyOf(readerWakeNanos);
        }
    }

    /** @return the number of fully validated responses that released wire capacity */
    public long capacityReleaseCount() {
        synchronized (monitor) {
            return capacityReleaseCount;
        }
    }

    /**
     * Offers one request if the bounded window has capacity.
     *
     * @return {@code true} after the complete request frame is handed to the socket, or
     *         {@code false} when the bounded window is full
     */
    public boolean tryOffer(final EngineCommand command, final long requestId) throws IOException {
        Objects.requireNonNull(command, "command");
        if (requestId <= 0) {
            throw new IllegalArgumentException("requestId must be positive");
        }
        final Pending request;
        synchronized (monitor) {
            checkOpenLocked();
            if (wirePending.size() >= maxInFlight) {
                return false;
            }
            if (requestIdExhausted || requestId != nextRequestId) {
                throw new IllegalArgumentException(
                        "requestId must be the exact next session identifier");
            }
            if (wirePending.containsKey(requestId)) {
                throw new IllegalArgumentException("requestId is already in flight");
            }
            request = new Pending(command, requestId, System.nanoTime());
            wirePending.put(requestId, request);
            maximumObservedInFlight = Math.max(maximumObservedInFlight, wirePending.size());
            // The reader may be waiting with an empty wire-pending set. Installation and wake-up
            // are one synchronized state transition; otherwise the first offer can be stranded
            // until an unrelated completion or drain occurs.
            readerWakeCount++;
            readerWakeNanos.add(System.nanoTime());
            monitor.notifyAll();
        }
        try {
            output.write(encode(command, requestId));
            output.flush();
            synchronized (monitor) {
                if (nextRequestId == Long.MAX_VALUE) {
                    requestIdExhausted = true;
                } else {
                    nextRequestId++;
                }
            }
            return true;
        } catch (final IOException failure) {
            synchronized (monitor) {
                wirePending.remove(requestId, request);
                failLocked(failure);
            }
            throw failure;
        }
    }

    /**
     * Removes all complete responses currently available in protocol order.
     */
    public List<CompletedExchange> drainCompleted() throws IOException {
        synchronized (monitor) {
            checkFailureLocked();
            final List<CompletedExchange> completed = new ArrayList<>();
            while (!this.completed.isEmpty()) {
                completed.add(this.completed.removeFirst());
            }
            monitor.notifyAll();
            return List.copyOf(completed);
        }
    }

    /**
     * Waits for at least one response, or until the pending window becomes empty, then drains it.
     */
    public List<CompletedExchange> awaitCompleted(final Duration timeout) throws IOException {
        Objects.requireNonNull(timeout, "timeout");
        final long timeoutNanos = timeoutNanos(timeout);
        final long deadline = deadline(timeoutNanos);
        synchronized (monitor) {
            while (true) {
                checkFailureLocked();
                final List<CompletedExchange> available = drainCompletedLocked();
                if (!available.isEmpty() || wirePending.isEmpty()) {
                    return List.copyOf(available);
                }
                final long remaining = deadline - System.nanoTime();
                if (remaining <= 0L) {
                    throw new IOException("timed out waiting for Protocol v2 responses");
                }
                try {
                    final long millis = remaining / 1_000_000L;
                    final int nanos = (int) (remaining % 1_000_000L);
                    monitor.wait(Math.max(0L, millis), nanos);
                } catch (final InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IOException("interrupted while waiting for Protocol v2 responses",
                            interrupted);
                }
            }
        }
    }

    /** Closes the socket and stops the qualification response reader. */
    @Override
    public void close() throws IOException {
        synchronized (monitor) {
            if (closed) {
                return;
            }
            closed = true;
            monitor.notifyAll();
        }
        socket.close();
        try {
            readerThread.join(1_000L);
        } catch (final InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted while closing Protocol v2 client", interrupted);
        }
    }

    private void readResponses() {
        try {
            while (true) {
                final Pending request = awaitReadablePending();
                if (request == null) {
                    return;
                }
                final QualificationExchange exchange = readExchange(request.command, request.requestId);
                synchronized (monitor) {
                    if (closed) {
                        return;
                    }
                    if (wirePending.remove(request.requestId, request) == false) {
                        failLocked(new IOException("response correlation state changed"));
                        return;
                    }
                    if (completed.size() >= maxInFlight) {
                        // This is defensive: awaitReadablePending() refuses to read while the
                        // completion queue is full. Keeping the check makes the bound explicit
                        // even if that scheduling invariant is changed later.
                        failLocked(new IOException("completed response bound was exceeded"));
                        return;
                    }
                    final long completedNanos = System.nanoTime();
                    final long capacityReleaseNanos = System.nanoTime();
                    capacityReleaseCount++;
                    completed.addLast(request.completedExchange(
                            exchange, completedNanos, capacityReleaseNanos));
                    maximumObservedCompleted = Math.max(maximumObservedCompleted, completed.size());
                    monitor.notifyAll();
                }
            }
        } catch (final IOException failure) {
            synchronized (monitor) {
                if (!closed) {
                    failLocked(failure);
                }
            }
        }
    }

    private Pending awaitReadablePending() throws IOException {
        synchronized (monitor) {
            while (true) {
                checkFailureLocked();
                if (closed) {
                    return null;
                }
                if (completed.size() >= maxInFlight) {
                    try {
                        monitor.wait();
                    } catch (final InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new IOException("interrupted while reading Protocol v2 responses",
                                interrupted);
                    }
                    continue;
                }
                if (!wirePending.isEmpty()) {
                    return wirePending.values().iterator().next();
                }
                try {
                    monitor.wait();
                } catch (final InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IOException("interrupted while reading Protocol v2 responses",
                            interrupted);
                }
            }
        }
    }

    private List<CompletedExchange> drainCompletedLocked() {
        final List<CompletedExchange> completed = new ArrayList<>();
        while (!this.completed.isEmpty()) {
            completed.add(this.completed.removeFirst());
        }
        monitor.notifyAll();
        return completed;
    }

    private QualificationExchange readExchange(
            final EngineCommand command,
            final long requestId) throws IOException {
        final List<byte[]> frames = new ArrayList<>();
        final byte[] commandFrame = readFrame();
        frames.add(commandFrame);
        validateFrameType(commandFrame, ProtocolConstants.COMMAND_RESULT_TYPE);
        validateZeroes(commandFrame, 33, 3, "command result reserved bytes");
        final long responseRequestId = longAt(commandFrame, 16);
        final long responseSequence = longAt(commandFrame, 24);
        if (responseRequestId != requestId || responseSequence != command.sequence().value()) {
            throw new IOException("response identity does not match request");
        }
        final int outcomeCode = unsigned(commandFrame[32]);
        final int matchCount = intAt(commandFrame, 36);
        if (outcomeCode < 1 || outcomeCode > 3 || matchCount < 0
                || matchCount > ProtocolConstants.MAX_FRAME_LENGTH) {
            throw new IOException("invalid command result fields");
        }
        final List<QualificationMatch> matches = new ArrayList<>(matchCount);
        for (int index = 0; index < matchCount; index++) {
            final byte[] matchFrame = readFrame();
            frames.add(matchFrame);
            validateFrameType(matchFrame, ProtocolConstants.MATCH_RESULT_TYPE);
            if (longAt(matchFrame, 16) != requestId
                    || longAt(matchFrame, 24) != command.sequence().value()
                    || intAt(matchFrame, 32) != index
                    || intAt(matchFrame, 36) != matchCount) {
                throw new IOException("match response ordering does not match request");
            }
            matches.add(new QualificationMatch(
                    longAt(matchFrame, 40),
                    longAt(matchFrame, 48),
                    longAt(matchFrame, 56),
                    longAt(matchFrame, 64),
                    longAt(matchFrame, 72),
                    longAt(matchFrame, 88)));
        }
        return new QualificationExchange(
                requestId,
                command.sequence().value(),
                outcomeCode,
                matches,
                frames.size(),
                digest(frames));
    }

    private byte[] readFrame() throws IOException {
        final byte[] header = readFully(ProtocolConstants.HEADER_LENGTH);
        final int length = intAt(header, 8);
        if (length < ProtocolConstants.HEADER_LENGTH
                || length > ProtocolConstants.MAX_FRAME_LENGTH) {
            throw new IOException("invalid response frame length");
        }
        final byte[] frame = new byte[length];
        System.arraycopy(header, 0, frame, 0, header.length);
        final byte[] payload = readFully(length - header.length);
        System.arraycopy(payload, 0, frame, header.length, payload.length);
        if (intAt(frame, 0) != ProtocolConstants.MAGIC
                || unsigned(frame[4]) != ProtocolConstants.PIPELINED_VERSION
                || shortAt(frame, 6) != 0
                || intAt(frame, 8) != length
                || intAt(frame, 12) != 0) {
            throw new IOException("invalid response frame header");
        }
        return frame;
    }

    private byte[] readFully(final int length) throws IOException {
        final byte[] bytes = new byte[length];
        int offset = 0;
        while (offset < length) {
            final int read = input.read(bytes, offset, length - offset);
            if (read < 0) {
                throw new EOFException("connection closed before complete response frame");
            }
            offset += read;
        }
        return bytes;
    }

    private void checkOpenLocked() throws IOException {
        checkFailureLocked();
        if (closed) {
            throw new IOException("Protocol v2 client is closed");
        }
    }

    private void checkFailureLocked() throws IOException {
        if (readerFailure != null) {
            throw readerFailure;
        }
    }

    private void failLocked(final IOException failure) {
        if (readerFailure == null) {
            readerFailure = failure;
        }
        monitor.notifyAll();
    }

    private static void validateFrameType(final byte[] frame, final int expectedType)
            throws IOException {
        if (unsigned(frame[5]) != expectedType) {
            throw new IOException("unexpected response frame type: " + unsigned(frame[5]));
        }
        final int expectedLength = switch (expectedType) {
            case ProtocolConstants.COMMAND_RESULT_TYPE -> ProtocolConstants.COMMAND_RESULT_FRAME_LENGTH;
            case ProtocolConstants.MATCH_RESULT_TYPE -> ProtocolConstants.MATCH_RESULT_FRAME_LENGTH;
            default -> throw new IllegalArgumentException("unsupported expected response type");
        };
        if (frame.length != expectedLength) {
            throw new IOException("unexpected response frame length");
        }
    }

    private static void validateZeroes(
            final byte[] frame,
            final int offset,
            final int length,
            final String description) throws IOException {
        for (int index = 0; index < length; index++) {
            if (frame[offset + index] != 0) {
                throw new IOException("non-zero " + description);
            }
        }
    }

    private static byte[] encode(final EngineCommand command, final long requestId) {
        if (command instanceof SubmitLimitCommand submit) {
            final ByteBuffer buffer = ByteBuffer.allocate(ProtocolConstants.SUBMIT_LIMIT_FRAME_LENGTH)
                    .order(ByteOrder.BIG_ENDIAN);
            header(buffer, ProtocolConstants.SUBMIT_LIMIT_TYPE,
                    ProtocolConstants.SUBMIT_LIMIT_FRAME_LENGTH);
            buffer.putLong(requestId);
            buffer.putLong(submit.orderId().value());
            buffer.put((byte) (submit.side() == Side.BUY ? 1 : 2));
            buffer.put(new byte[7]);
            buffer.putLong(submit.price().ticks());
            buffer.putLong(submit.quantity().units());
            return buffer.array();
        }
        if (command instanceof CancelOrderCommand cancel) {
            final ByteBuffer buffer = ByteBuffer.allocate(ProtocolConstants.CANCEL_ORDER_FRAME_LENGTH)
                    .order(ByteOrder.BIG_ENDIAN);
            header(buffer, ProtocolConstants.CANCEL_ORDER_TYPE,
                    ProtocolConstants.CANCEL_ORDER_FRAME_LENGTH);
            buffer.putLong(requestId);
            buffer.putLong(cancel.orderId().value());
            return buffer.array();
        }
        throw new IllegalArgumentException("unsupported command type: " + command.getClass());
    }

    private static void header(
            final ByteBuffer buffer,
            final int messageType,
            final int frameLength) {
        buffer.putInt(ProtocolConstants.MAGIC);
        buffer.put((byte) ProtocolConstants.PIPELINED_VERSION);
        buffer.put((byte) messageType);
        buffer.putShort((short) 0);
        buffer.putInt(frameLength);
        buffer.putInt(0);
    }

    private static String digest(final List<byte[]> frames) {
        final MessageDigest digest = sha256();
        for (final byte[] frame : frames) {
            digest.update(ByteBuffer.allocate(Integer.BYTES)
                    .order(ByteOrder.BIG_ENDIAN)
                    .putInt(frame.length)
                    .array());
            digest.update(frame);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static int timeoutMillis(final Duration timeout) {
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        final long millis = timeout.toMillis();
        if (millis <= 0L || millis > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("timeout is outside socket bounds");
        }
        return (int) millis;
    }

    private static long timeoutNanos(final Duration timeout) {
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        try {
            return timeout.toNanos();
        } catch (final ArithmeticException exception) {
            throw new IllegalArgumentException("timeout is too large", exception);
        }
    }

    private static long deadline(final long timeoutNanos) {
        try {
            return Math.addExact(System.nanoTime(), timeoutNanos);
        } catch (final ArithmeticException exception) {
            return Long.MAX_VALUE;
        }
    }

    private static long longAt(final byte[] bytes, final int offset) {
        return ByteBuffer.wrap(bytes, offset, Long.BYTES)
                .order(ByteOrder.BIG_ENDIAN)
                .getLong();
    }

    private static int intAt(final byte[] bytes, final int offset) {
        return ByteBuffer.wrap(bytes, offset, Integer.BYTES)
                .order(ByteOrder.BIG_ENDIAN)
                .getInt();
    }

    private static int shortAt(final byte[] bytes, final int offset) {
        return ByteBuffer.wrap(bytes, offset, Short.BYTES)
                .order(ByteOrder.BIG_ENDIAN)
                .getShort();
    }

    private static int unsigned(final byte value) {
        return value & 0xFF;
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (final NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the JDK", exception);
        }
    }

    /** One response consumed from the bounded public-path window. */
    public record CompletedExchange(
            long requestId,
            EngineCommand command,
            QualificationExchange exchange,
            long offeredNanos,
            long completedNanos,
            long capacityReleaseNanos) {

        public CompletedExchange {
            if (requestId <= 0L || command == null || exchange == null || offeredNanos < 0L
                    || completedNanos < offeredNanos || capacityReleaseNanos < completedNanos) {
                throw new IllegalArgumentException("completed exchange chronology is invalid");
            }
        }

        /** Returns the observed request/response duration in nanoseconds. */
        public long latencyNanos() {
            return Math.max(1L, completedNanos - offeredNanos);
        }

        /** Returns the response-complete to wire-capacity-release delay. */
        public long capacityReleaseDelayNanos() {
            return capacityReleaseNanos - completedNanos;
        }
    }

    private static final class Pending {

        private final EngineCommand command;
        private final long requestId;
        private final long offeredNanos;
        private Pending(
                final EngineCommand command,
                final long requestId,
                final long offeredNanos) {
            this.command = command;
            this.requestId = requestId;
            this.offeredNanos = offeredNanos;
        }

        private CompletedExchange completedExchange(
            final QualificationExchange exchange,
                final long completedNanos,
                final long capacityReleaseNanos) {
            return new CompletedExchange(requestId, command, exchange, offeredNanos,
                    completedNanos, capacityReleaseNanos);
        }
    }
}
