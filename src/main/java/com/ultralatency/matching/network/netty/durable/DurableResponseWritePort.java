package com.ultralatency.matching.network.netty.durable;

import com.ultralatency.matching.network.protocol.ProtocolResponse;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;

/**
 * Result-write boundary owned by the Phase 7 durable Gateway.
 *
 * <p>The production implementation delegates directly to Netty. A deterministic test
 * composition may return a pending or failed future at this boundary without changing the
 * Protocol v1 encoder or the Phase 6 Gateway.</p>
 */
@FunctionalInterface
interface DurableResponseWritePort {

    /**
     * Writes an intermediate response without flushing it.
     *
     * @param channel owning Netty channel
     * @param response response value
     */
    void write(Channel channel, ProtocolResponse response);

    /**
     * Writes and flushes the final response, returning its completion future.
     *
     * @param channel owning Netty channel
     * @param response response value
     * @return Netty completion future
     */
    default ChannelFuture writeAndFlush(
            final Channel channel,
            final ProtocolResponse response) {
        return channel.writeAndFlush(response);
    }

    /**
     * Returns the real production writer.
     *
     * @return direct Netty writer
     */
    static DurableResponseWritePort production() {
        return (channel, response) -> channel.write(response);
    }
}
