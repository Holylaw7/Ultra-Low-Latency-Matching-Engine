package com.ultralatency.matching.persistence.wal;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ultralatency.matching.domain.OrderId;
import com.ultralatency.matching.domain.Price;
import com.ultralatency.matching.domain.Quantity;
import com.ultralatency.matching.domain.Sequence;
import com.ultralatency.matching.domain.Side;
import com.ultralatency.matching.engine.CancelOrderCommand;
import com.ultralatency.matching.engine.EngineCommand;
import com.ultralatency.matching.engine.SubmitLimitCommand;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

class WalCommandCodecTest {

    private static final String SEGMENT_HEADER_HEX =
            "554c4d4557414c31000000010000002000000000000000010000000000000001";
    private static final String SUBMIT_RECORD_HEX =
            "000000340101000000000000000000010000000000000002010000000000000000000000000000640000000000000003d074639d";
    private static final String CANCEL_RECORD_HEX =
            "0000001c0102000000000000000000020000000000000002f0b6e42a";

    private final WalCommandCodec codec = new WalCommandCodec();

    @Test
    void encodesExactSegmentHeaderGoldenBytes() {
        final byte[] actual = codec.encodeSegmentHeader(1, new Sequence(1));

        assertEquals(SEGMENT_HEADER_HEX, HexFormat.of().formatHex(actual));
        assertEquals(
                new WalSegmentHeader(1, 1, new Sequence(1)),
                codec.decodeSegmentHeader(actual));
    }

    @Test
    void roundTripsSupportedCommands() {
        final EngineCommand submit = new SubmitLimitCommand(
                new Sequence(7),
                new OrderId(42),
                Side.SELL,
                new Price(101),
                new Quantity(9));
        final EngineCommand cancel = new CancelOrderCommand(new Sequence(8), new OrderId(42));

        assertEquals(submit, codec.decodeRecord(codec.encodeRecord(submit)));
        assertEquals(cancel, codec.decodeRecord(codec.encodeRecord(cancel)));
    }

    @Test
    void roundTripsMaximumPositiveDomainValues() {
        final EngineCommand command = new SubmitLimitCommand(
                new Sequence(Long.MAX_VALUE),
                new OrderId(Long.MAX_VALUE),
                Side.SELL,
                new Price(Long.MAX_VALUE),
                new Quantity(Long.MAX_VALUE));

        assertEquals(command, codec.decodeRecord(codec.encodeRecord(command)));
    }

    @Test
    void encodesExactCommandRecordsGoldenBytes() {
        final EngineCommand submit = new SubmitLimitCommand(
                new Sequence(1),
                new OrderId(2),
                Side.BUY,
                new Price(100),
                new Quantity(3));
        final EngineCommand cancel = new CancelOrderCommand(new Sequence(2), new OrderId(2));

        assertEquals(SUBMIT_RECORD_HEX, HexFormat.of().formatHex(codec.encodeRecord(submit)));
        assertEquals(CANCEL_RECORD_HEX, HexFormat.of().formatHex(codec.encodeRecord(cancel)));
    }

    @Test
    void preservesExactRecordLengthByCommandType() {
        final EngineCommand submit = new SubmitLimitCommand(
                new Sequence(1),
                new OrderId(2),
                Side.BUY,
                new Price(100),
                new Quantity(3));
        final EngineCommand cancel = new CancelOrderCommand(new Sequence(2), new OrderId(2));

        assertEquals(52, codec.encodeRecord(submit).length);
        assertEquals(28, codec.encodeRecord(cancel).length);
    }

    @Test
    void rejectsInvalidHeaderVersionLengthAndMagic() {
        final byte[] header = codec.encodeSegmentHeader(1, new Sequence(1));

        final byte[] invalidMagic = header.clone();
        invalidMagic[0] = 0;
        assertThrows(WalFormatException.class, () -> codec.decodeSegmentHeader(invalidMagic));

        final byte[] invalidVersion = header.clone();
        ByteBuffer.wrap(invalidVersion).order(ByteOrder.BIG_ENDIAN).putInt(8, 2);
        assertThrows(WalFormatException.class, () -> codec.decodeSegmentHeader(invalidVersion));

        final byte[] invalidLength = header.clone();
        ByteBuffer.wrap(invalidLength).order(ByteOrder.BIG_ENDIAN).putInt(12, 31);
        assertThrows(WalFormatException.class, () -> codec.decodeSegmentHeader(invalidLength));
    }

    @Test
    void rejectsInvalidRecordLengthVersionFlagsTypeSideReservedAndChecksum() {
        final byte[] record = codec.encodeRecord(new SubmitLimitCommand(
                new Sequence(1),
                new OrderId(2),
                Side.BUY,
                new Price(100),
                new Quantity(3)));

        final byte[] invalidLength = record.clone();
        ByteBuffer.wrap(invalidLength).order(ByteOrder.BIG_ENDIAN).putInt(0, 51);
        assertThrows(WalFormatException.class, () -> codec.decodeRecord(invalidLength));

        final byte[] invalidVersion = record.clone();
        invalidVersion[4] = 2;
        assertThrows(WalFormatException.class, () -> codec.decodeRecord(invalidVersion));

        final byte[] invalidFlags = record.clone();
        ByteBuffer.wrap(invalidFlags).order(ByteOrder.BIG_ENDIAN).putShort(6, (short) 1);
        assertThrows(WalFormatException.class, () -> codec.decodeRecord(invalidFlags));

        final byte[] invalidType = record.clone();
        invalidType[5] = 3;
        assertThrows(WalFormatException.class, () -> codec.decodeRecord(invalidType));

        final byte[] invalidSide = record.clone();
        invalidSide[24] = 3;
        assertThrows(WalFormatException.class, () -> codec.decodeRecord(invalidSide));

        final byte[] invalidReserved = record.clone();
        invalidReserved[25] = 1;
        assertThrows(WalFormatException.class, () -> codec.decodeRecord(invalidReserved));

        final byte[] invalidChecksum = record.clone();
        invalidChecksum[invalidChecksum.length - 1] ^= 1;
        assertThrows(WalFormatException.class, () -> codec.decodeRecord(invalidChecksum));

        final byte[] oversized = ByteBuffer.allocate(Integer.BYTES)
                .order(ByteOrder.BIG_ENDIAN)
                .putInt(WalCommandCodec.MAX_RECORD_LENGTH + 1)
                .array();
        assertThrows(WalFormatException.class, () -> codec.decodeRecord(oversized));
    }

    @Test
    void validatesConfigurationWithoutFilesystemAccess() {
        final WalConfiguration defaults = WalConfiguration.defaults(Path.of("wal"));

        assertEquals(WalDurabilityMode.SYNC_EACH_APPEND, defaults.durabilityMode());
        assertThrows(
                IllegalArgumentException.class,
                () -> new WalConfiguration(
                        Path.of("wal"),
                        WalCommandCodec.MIN_SEGMENT_SIZE_BYTES - 1,
                        WalDurabilityMode.BUFFERED));
        assertThrows(
                NullPointerException.class,
                () -> new WalConfiguration(Path.of("wal"), 4096, null));
        assertThrows(
                NullPointerException.class,
                () -> new WalConfiguration(null, WalCommandCodec.MIN_SEGMENT_SIZE_BYTES,
                        WalDurabilityMode.SYNC_EACH_APPEND));
    }

    @Test
    void returnsDefensiveEncodedArraysByCreatingFreshRecords() {
        final EngineCommand command = new CancelOrderCommand(new Sequence(1), new OrderId(2));

        final byte[] first = codec.encodeRecord(command);
        final byte[] second = codec.encodeRecord(command);
        first[0] = 0;

        assertArrayEquals(
                codec.encodeRecord(command),
                second);
    }
}
