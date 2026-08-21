package com.ultralatency.matching.persistence.wal;

import com.ultralatency.matching.engine.EngineCommand;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Strict reader for a closed segmented command WAL. */
public final class CommandWalReader {

    private final WalConfiguration configuration;
    private final WalCommandCodec codec;

    /**
     * Creates a strict reader using the standard codec.
     *
     * @param configuration WAL configuration
     */
    public CommandWalReader(final WalConfiguration configuration) {
        this(configuration, new WalCommandCodec());
    }

    /**
     * Creates a strict reader with an explicit codec.
     *
     * @param configuration WAL configuration
     * @param codec version-1 command codec
     */
    public CommandWalReader(
            final WalConfiguration configuration,
            final WalCommandCodec codec) {
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.codec = Objects.requireNonNull(codec, "codec");
    }

    /**
     * Reads every command in strict physical and logical order.
     *
     * @return immutable ordered command list
     * @throws IOException when the directory cannot be read or strict validation fails
     */
    public List<EngineCommand> read() throws IOException {
        final WalScanResult result = WalStorageScanner.scan(
                configuration.directory(),
                configuration,
                codec);
        if (result.emptyTrailingSegment()) {
            throw new WalCorruptionException(
                    result.tailPath(),
                    WalCommandCodec.SEGMENT_HEADER_LENGTH,
                    "Empty trailing WAL segment; explicit reopen is required",
                    true);
        }
        if (result.hasTail()) {
            throw new WalCorruptionException(
                    result.tailPath(),
                    result.tailOffset(),
                    "Incomplete final WAL record; explicit reopen is required",
                    true);
        }
        return result.commands();
    }

    /**
     * Reads a directory with the standard codec.
     *
     * @param configuration WAL configuration
     * @return immutable ordered command list
     * @throws IOException when strict validation fails
     */
    public static List<EngineCommand> read(final WalConfiguration configuration) throws IOException {
        return new CommandWalReader(configuration).read();
    }

    /**
     * Reads a directory with default storage settings.
     *
     * @param directory WAL directory
     * @return immutable ordered command list
     * @throws IOException when strict validation fails
     */
    public static List<EngineCommand> read(final Path directory) throws IOException {
        return read(WalConfiguration.defaults(directory));
    }
}
