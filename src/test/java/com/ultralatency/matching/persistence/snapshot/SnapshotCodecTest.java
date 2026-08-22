package com.ultralatency.matching.persistence.snapshot;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ultralatency.matching.domain.OrderId;
import com.ultralatency.matching.domain.Price;
import com.ultralatency.matching.domain.Quantity;
import com.ultralatency.matching.domain.Sequence;
import com.ultralatency.matching.domain.Side;
import com.ultralatency.matching.engine.MatchingEngineCheckpoint;
import com.ultralatency.matching.orderbook.OrderBookCheckpoint;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class SnapshotCodecTest {

    private final SnapshotCodec codec = new SnapshotCodec();

    @Test
    void encodesExactHeaderRecordLengthAndRoundTrips() {
        final Snapshot source = new Snapshot(checkpoint(), digest(7));

        final byte[] encoded = codec.encode(source);

        assertEquals(228, encoded.length);
        assertEquals("ULMESNP1", new String(encoded, 0, 8, java.nio.charset.StandardCharsets.US_ASCII));
        assertEquals(1, intAt(encoded, 8));
        assertEquals(128, intAt(encoded, 12));
        assertEquals(228L, longAt(encoded, 16));
        assertEquals(2, intAt(encoded, 48));
        assertEquals(48, intAt(encoded, 52));
        assertEquals(1, intAt(encoded, 56));
        assertArrayEquals(source.walPrefixDigest(), Arrays.copyOfRange(encoded, 64, 96));
        assertArrayEquals(source.canonicalCheckpointDigest(), Arrays.copyOfRange(encoded, 96, 128));

        final Snapshot decoded = codec.decode(encoded);
        assertEquals(source, decoded);
        assertEquals(1L, longAt(encoded, 128));
        assertEquals(1, Byte.toUnsignedInt(encoded[136]));
        assertEquals(101L, longAt(encoded, 144));
        assertEquals(5L, longAt(encoded, 152));
        assertEquals(5L, longAt(encoded, 160));
        assertEquals(2L, longAt(encoded, 168));
    }

    @Test
    void rejectsVersionFlagsReservedBytesCrcAndDigestChanges() {
        final byte[] encoded = codec.encode(new Snapshot(checkpoint(), digest(7)));

        final byte[] version = encoded.clone();
        putInt(version, 8, 2);
        assertThrows(SnapshotFormatException.class, () -> codec.decode(version));

        final byte[] flags = encoded.clone();
        putInt(flags, 60, 1);
        assertThrows(SnapshotFormatException.class, () -> codec.decode(flags));

        final byte[] reserved = encoded.clone();
        reserved[137] = 1;
        assertThrows(SnapshotFormatException.class, () -> codec.decode(reserved));

        final byte[] crc = encoded.clone();
        crc[crc.length - 1] ^= 1;
        assertThrows(SnapshotFormatException.class, () -> codec.decode(crc));

        final byte[] digest = encoded.clone();
        digest[96] ^= 1;
        rewriteCrc(digest);
        assertThrows(SnapshotFormatException.class, () -> codec.decode(digest));
    }

    @Test
    void rejectsDuplicateAndNonCanonicalOrdersBeforeRestore() {
        final byte[] duplicate = codec.encode(new Snapshot(checkpoint(), digest(7)));
        System.arraycopy(duplicate, 128, duplicate, 176, 48);
        rewriteCrc(duplicate);
        assertThrows(SnapshotFormatException.class, () -> codec.decode(duplicate));

        final OrderBookCheckpoint.RestingOrderCheckpoint first = order(1, Side.BUY, 100, 1);
        final OrderBookCheckpoint.RestingOrderCheckpoint second = order(2, Side.BUY, 101, 2);
        assertThrows(
                IllegalArgumentException.class,
                () -> new OrderBookCheckpoint(List.of(first, second), List.of()));
    }

    @Test
    void enforcesConfiguredLimitsBeforeDecodingOrderRecords() {
        final SnapshotCodec limited = new SnapshotCodec(new SnapshotLimits(0, 180));
        final byte[] encoded = codec.encode(new Snapshot(checkpoint(), digest(7)));

        assertThrows(SnapshotFormatException.class, () -> limited.decode(encoded));
    }

    private static MatchingEngineCheckpoint checkpoint() {
        return new MatchingEngineCheckpoint(
                9,
                4,
                6,
                new OrderBookCheckpoint(
                        List.of(order(1, Side.BUY, 101, 2), order(2, Side.BUY, 100, 3)),
                        List.of()));
    }

    private static OrderBookCheckpoint.RestingOrderCheckpoint order(
            final long id,
            final Side side,
            final long price,
            final long sequence) {
        return new OrderBookCheckpoint.RestingOrderCheckpoint(
                new OrderId(id),
                side,
                new Price(price),
                new Quantity(5),
                new Quantity(5),
                new Sequence(sequence));
    }

    private static byte[] digest(final int value) {
        final byte[] digest = new byte[Snapshot.SHA256_LENGTH];
        Arrays.fill(digest, (byte) value);
        return digest;
    }

    private static int intAt(final byte[] bytes, final int offset) {
        return ByteBuffer.wrap(bytes, offset, Integer.BYTES).order(ByteOrder.BIG_ENDIAN).getInt();
    }

    private static long longAt(final byte[] bytes, final int offset) {
        return ByteBuffer.wrap(bytes, offset, Long.BYTES).order(ByteOrder.BIG_ENDIAN).getLong();
    }

    private static void putInt(final byte[] bytes, final int offset, final int value) {
        ByteBuffer.wrap(bytes, offset, Integer.BYTES).order(ByteOrder.BIG_ENDIAN).putInt(value);
    }

    private static void rewriteCrc(final byte[] bytes) {
        final java.util.zip.CRC32C crc = new java.util.zip.CRC32C();
        crc.update(bytes, 0, bytes.length - SnapshotCodec.FOOTER_LENGTH);
        putInt(bytes, bytes.length - SnapshotCodec.FOOTER_LENGTH, (int) crc.getValue());
    }
}
