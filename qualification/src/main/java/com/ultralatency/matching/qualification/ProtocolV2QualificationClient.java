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
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * JDK socket client for the explicit Protocol v2 bounded-pipelining boundary.
 *
 * <p>Each call admits at most the configured window before consuming the ordered response stream.
 * The client never retries a rejected request and does not merge request IDs with engine command
 * sequences.</p>
 */
public final class ProtocolV2QualificationClient implements AutoCloseable {

    private final Socket socket;
    private final InputStream input;
    private final OutputStream output;
    private final int maxInFlight;

    /** Connects to a running Protocol v2 server with the default bounded window. */
    public ProtocolV2QualificationClient(
            final InetSocketAddress address,
            final Duration timeout) throws IOException {
        this(address, timeout, ProtocolConstants.DEFAULT_PIPELINED_MAX_IN_FLIGHT);
    }

    /** Connects to a running Protocol v2 server with an explicit bounded window. */
    public ProtocolV2QualificationClient(
            final InetSocketAddress address,
            final Duration timeout,
            final int maxInFlight) throws IOException {
        Objects.requireNonNull(address, "address");
        Objects.requireNonNull(timeout, "timeout");
        if (maxInFlight < 1 || maxInFlight > ProtocolConstants.MAX_PIPELINED_IN_FLIGHT) {
            throw new IllegalArgumentException("maxInFlight is outside the protocol hard bound");
        }
        this.maxInFlight = maxInFlight;
        final int timeoutMillis = timeoutMillis(timeout);
        socket = new Socket();
        socket.connect(address, timeoutMillis);
        socket.setSoTimeout(timeoutMillis);
        input = socket.getInputStream();
        output = socket.getOutputStream();
    }

    /** @return the maximum number of requests admitted by one client window */
    public int maxInFlight() {
        return maxInFlight;
    }

    /**
     * Sends one bounded v2 window and reads its ordered response stream.
     *
     * @param commands commands whose sequences must match the server responses
     * @param firstRequestId first positive session request ID
     * @return exchanges in server command-sequence order
     * @throws IOException when framing, identity or response ordering is invalid
     */
    public List<QualificationExchange> exchangeWindow(
            final List<? extends EngineCommand> commands,
            final long firstRequestId) throws IOException {
        Objects.requireNonNull(commands, "commands");
        if (commands.isEmpty() || commands.size() > maxInFlight) {
            throw new IllegalArgumentException("command window is outside the configured bound");
        }
        if (firstRequestId <= 0
                || firstRequestId > Long.MAX_VALUE - commands.size() + 1L) {
            throw new IllegalArgumentException("request ID window is outside the session bound");
        }
        for (int index = 0; index < commands.size(); index++) {
            final EngineCommand command = Objects.requireNonNull(commands.get(index), "command");
            output.write(encode(command, firstRequestId + index));
        }
        output.flush();

        final List<QualificationExchange> exchanges = new ArrayList<>(commands.size());
        for (int index = 0; index < commands.size(); index++) {
            exchanges.add(readExchange(commands.get(index), firstRequestId + index));
        }
        return List.copyOf(exchanges);
    }

    /**
     * Sends a command list in bounded windows and consumes responses in order.
     *
     * @param commands commands to send
     * @param firstRequestId first positive session request ID
     * @return exchanges in command-list order
     * @throws IOException when any window has invalid response evidence
     */
    public List<QualificationExchange> exchangeAll(
            final List<? extends EngineCommand> commands,
            final long firstRequestId) throws IOException {
        Objects.requireNonNull(commands, "commands");
        if (commands.isEmpty()) {
            return List.of();
        }
        if (firstRequestId <= 0
                || firstRequestId > Long.MAX_VALUE - commands.size() + 1L) {
            throw new IllegalArgumentException("request ID list is outside the session bound");
        }
        for (final EngineCommand command : commands) {
            Objects.requireNonNull(command, "command");
        }
        final List<QualificationExchange> exchanges = new ArrayList<>(commands.size());
        int offset = 0;
        while (offset < commands.size()) {
            final int end = Math.min(commands.size(), offset + maxInFlight);
            exchanges.addAll(exchangeWindow(
                    commands.subList(offset, end),
                    firstRequestId + offset));
            offset = end;
        }
        return List.copyOf(exchanges);
    }

    /** Closes the client socket and both streams. */
    @Override
    public void close() throws IOException {
        socket.close();
    }

    private QualificationExchange readExchange(
            final EngineCommand command,
            final long requestId) throws IOException {
        final List<byte[]> frames = new ArrayList<>();
        final byte[] commandFrame = readFrame();
        frames.add(commandFrame);
        validateFrameType(commandFrame, ProtocolConstants.COMMAND_RESULT_TYPE);
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

    private static void validateFrameType(final byte[] frame, final int expectedType)
            throws IOException {
        if (unsigned(frame[5]) != expectedType) {
            throw new IOException("unexpected response frame type: " + unsigned(frame[5]));
        }
        final int expectedLength = switch (expectedType) {
            case ProtocolConstants.COMMAND_RESULT_TYPE -> ProtocolConstants.COMMAND_RESULT_FRAME_LENGTH;
            case ProtocolConstants.MATCH_RESULT_TYPE -> ProtocolConstants.MATCH_RESULT_FRAME_LENGTH;
            default -> throw new IllegalArgumentException("unsupported expected frame type");
        };
        if (frame.length != expectedLength) {
            throw new IOException("unexpected response frame length");
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
        if (millis <= 0 || millis > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("timeout is outside socket bounds");
        }
        return (int) millis;
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
}
