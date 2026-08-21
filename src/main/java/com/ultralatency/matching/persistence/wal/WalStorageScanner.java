package com.ultralatency.matching.persistence.wal;

import com.ultralatency.matching.engine.EngineCommand;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Package-private strict scanner shared by the reader and explicit writer reopen. */
final class WalStorageScanner {

    private static final Pattern SEGMENT_NAME = Pattern.compile("wal-(\\d{20})\\.log");

    private WalStorageScanner() {
    }

    static WalScanResult scan(
            final Path directory,
            final WalConfiguration configuration,
            final WalCommandCodec codec) throws IOException {
        if (!Files.exists(directory)) {
            return new WalScanResult(List.of(), List.of(), null, -1, false);
        }
        if (!Files.isDirectory(directory)) {
            throw new WalStorageException(directory, -1, "WAL path is not a directory");
        }
        final List<NamedSegment> namedSegments = discover(directory);
        if (namedSegments.isEmpty()) {
            return new WalScanResult(List.of(), List.of(), null, -1, false);
        }
        final List<EngineCommand> commands = new ArrayList<>();
        final List<WalSegmentInfo> segments = new ArrayList<>();
        long expectedSequence = 1;
        for (int index = 0; index < namedSegments.size(); index++) {
            final NamedSegment named = namedSegments.get(index);
            final boolean lastSegment = index == namedSegments.size() - 1;
            final byte[] bytes = readSegment(named.path(), configuration.segmentSizeBytes());
            final WalSegmentHeader header = decodeHeader(named.path(), bytes, codec);
            if (header.segmentId() != index + 1L) {
                throw corruption(
                        named.path(),
                        16,
                        "WAL segment id is not contiguous: " + header.segmentId(),
                        false);
            }
            if (header.firstCommandSequence().value() != named.firstSequence()) {
                throw corruption(
                        named.path(),
                        24,
                        "WAL filename and header first sequence differ",
                        false);
            }
            final ScanSegment segment = scanRecords(
                    named.path(),
                    bytes,
                    header,
                    expectedSequence,
                    lastSegment,
                    codec);
            if (segment.empty() && !lastSegment) {
                throw corruption(named.path(), WalCommandCodec.SEGMENT_HEADER_LENGTH,
                        "Empty non-final WAL segment", false);
            }
            if (segment.tornTail()) {
                return new WalScanResult(
                        List.copyOf(commands),
                        List.copyOf(segments),
                        named.path(),
                        segment.validEndOffset(),
                        false);
            }
            if (segment.empty()) {
                return new WalScanResult(
                        List.copyOf(commands),
                        List.copyOf(segments),
                        named.path(),
                        WalCommandCodec.SEGMENT_HEADER_LENGTH,
                        true);
            }
            commands.addAll(segment.commands());
            expectedSequence = segment.nextSequence();
            segments.add(new WalSegmentInfo(
                    named.path(),
                    header.segmentId(),
                    header.firstCommandSequence().value(),
                    expectedSequence - 1,
                    segment.validEndOffset(),
                    false));
        }
        return new WalScanResult(List.copyOf(commands), List.copyOf(segments), null, -1, false);
    }

    private static List<NamedSegment> discover(final Path directory) throws IOException {
        final List<NamedSegment> segments = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "wal-*.log")) {
            for (final Path path : stream) {
                final Matcher matcher = SEGMENT_NAME.matcher(path.getFileName().toString());
                if (!matcher.matches()) {
                    throw corruption(path, -1, "Invalid WAL segment filename", false);
                }
                try {
                    segments.add(new NamedSegment(path, Long.parseLong(matcher.group(1))));
                } catch (final NumberFormatException exception) {
                    throw new WalStorageException(path, -1, "Invalid WAL segment filename", exception);
                }
            }
        }
        segments.sort(Comparator.comparingLong(NamedSegment::firstSequence));
        return List.copyOf(segments);
    }

    private static byte[] readSegment(final Path path, final int maximumSize) throws IOException {
        final long size = Files.size(path);
        if (size > maximumSize) {
            throw corruption(path, 0, "WAL segment exceeds configured size", false);
        }
        if (size > Integer.MAX_VALUE) {
            throw corruption(path, 0, "WAL segment is too large to scan", false);
        }
        final byte[] bytes = new byte[(int) size];
        long offset = 0;
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
            final ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) {
                final int read = channel.read(buffer, offset);
                if (read <= 0) {
                    throw new WalStorageException(path, offset, "WAL segment read made no progress");
                }
                offset += read;
            }
        } catch (final WalStorageException exception) {
            throw exception;
        } catch (final IOException exception) {
            throw new WalStorageException(path, offset, "Unable to read WAL segment", exception);
        }
        return bytes;
    }

    private static WalSegmentHeader decodeHeader(
            final Path path,
            final byte[] bytes,
            final WalCommandCodec codec) throws WalCorruptionException {
        if (bytes.length < WalCommandCodec.SEGMENT_HEADER_LENGTH) {
            throw corruption(path, bytes.length, "Incomplete WAL segment header", false);
        }
        try {
            final byte[] headerBytes = new byte[WalCommandCodec.SEGMENT_HEADER_LENGTH];
            System.arraycopy(bytes, 0, headerBytes, 0, headerBytes.length);
            return codec.decodeSegmentHeader(headerBytes);
        } catch (final WalFormatException exception) {
            throw new WalCorruptionException(path, 0, "Invalid WAL segment header", exception, false);
        }
    }

    private static ScanSegment scanRecords(
            final Path path,
            final byte[] bytes,
            final WalSegmentHeader header,
            final long expectedSequence,
            final boolean lastSegment,
            final WalCommandCodec codec) throws IOException {
        final List<EngineCommand> commands = new ArrayList<>();
        int offset = WalCommandCodec.SEGMENT_HEADER_LENGTH;
        long nextSequence = expectedSequence;
        long validEndOffset = offset;
        while (offset < bytes.length) {
            final int remaining = bytes.length - offset;
            if (remaining < Integer.BYTES) {
                if (lastSegment) {
                    return new ScanSegment(commands, nextSequence, validEndOffset, true, true);
                }
                throw corruption(path, offset, "Incomplete record length before segment end", false);
            }
            final int declaredLength = ByteBuffer.wrap(bytes, offset, Integer.BYTES)
                    .order(ByteOrder.BIG_ENDIAN)
                    .getInt();
            if (declaredLength < WalCommandCodec.MIN_RECORD_LENGTH
                    || declaredLength > WalCommandCodec.MAX_RECORD_LENGTH) {
                throw corruption(path, offset, "WAL record length outside supported bounds", false);
            }
            if (declaredLength > remaining) {
                if (lastSegment) {
                    return new ScanSegment(commands, nextSequence, validEndOffset, true, true);
                }
                throw corruption(path, offset, "Incomplete record before segment end", false);
            }
            final byte[] record = new byte[declaredLength];
            System.arraycopy(bytes, offset, record, 0, declaredLength);
            final EngineCommand command;
            try {
                command = codec.decodeRecord(record);
            } catch (final WalFormatException exception) {
                throw new WalCorruptionException(
                        path,
                        offset,
                        "Invalid WAL record",
                        exception,
                        false);
            }
            if (command.sequence().value() != nextSequence) {
                throw corruption(
                        path,
                        offset,
                        "WAL command sequence gap: expected " + nextSequence
                                + ", actual " + command.sequence().value(),
                        false);
            }
            if (commands.isEmpty() && command.sequence().value()
                    != header.firstCommandSequence().value()) {
                throw corruption(path, offset, "Segment first sequence does not match first record", false);
            }
            commands.add(command);
            nextSequence = command.sequence().next().value();
            offset += declaredLength;
            validEndOffset = offset;
        }
        return new ScanSegment(commands, nextSequence, validEndOffset, false, commands.isEmpty());
    }

    private static WalCorruptionException corruption(
            final Path path,
            final long offset,
            final String message,
            final boolean incompleteTail) {
        return new WalCorruptionException(path, offset, message, incompleteTail);
    }

    private record NamedSegment(Path path, long firstSequence) {
    }

    private record ScanSegment(
            List<EngineCommand> commands,
            long nextSequence,
            long validEndOffset,
            boolean tornTail,
            boolean empty) {
    }
}
