package com.ultralatency.matching.network.protocol;

/**
 * Positive client request correlation identifier for one network session.
 *
 * @param value request identifier value
 */
public record ClientRequestId(long value) implements Comparable<ClientRequestId> {

    /**
     * Creates a validated request identifier.
     */
    public ClientRequestId {
        if (value <= 0) {
            throw new IllegalArgumentException("Client request ID must be positive");
        }
    }

    /**
     * Creates a request identifier from its primitive value.
     *
     * @param value request identifier value
     * @return validated request identifier
     */
    public static ClientRequestId of(final long value) {
        return new ClientRequestId(value);
    }

    @Override
    public int compareTo(final ClientRequestId other) {
        return Long.compare(value, other.value);
    }
}
