package com.ultralatency.matching.network.protocol;

import com.ultralatency.matching.domain.EventSequence;
import com.ultralatency.matching.domain.OrderId;
import com.ultralatency.matching.domain.Price;
import com.ultralatency.matching.domain.Quantity;
import com.ultralatency.matching.domain.Sequence;
import com.ultralatency.matching.domain.TradeId;
import java.util.Objects;

/**
 * One ordered match response frame.
 *
 * @param requestId client request correlation identifier
 * @param commandSequence applied engine command sequence
 * @param matchIndex zero-based index in the command result
 * @param totalMatchCount total ordered match count
 * @param eventSequence matching output sequence
 * @param tradeId trade identity
 * @param price execution price
 * @param quantity execution quantity
 * @param makerOrderId resting order identifier
 * @param makerRemainingQuantityUnits maker remainder after execution
 * @param takerOrderId incoming order identifier
 * @param takerRemainingQuantityUnits taker remainder after execution
 */
public record MatchResultResponse(
        ClientRequestId requestId,
        Sequence commandSequence,
        int matchIndex,
        int totalMatchCount,
        EventSequence eventSequence,
        TradeId tradeId,
        Price price,
        Quantity quantity,
        OrderId makerOrderId,
        long makerRemainingQuantityUnits,
        OrderId takerOrderId,
        long takerRemainingQuantityUnits) implements ProtocolResponse {

    /**
     * Validates response fields and match ordering metadata.
     */
    public MatchResultResponse {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(commandSequence, "commandSequence");
        Objects.requireNonNull(eventSequence, "eventSequence");
        Objects.requireNonNull(tradeId, "tradeId");
        Objects.requireNonNull(price, "price");
        Objects.requireNonNull(quantity, "quantity");
        Objects.requireNonNull(makerOrderId, "makerOrderId");
        Objects.requireNonNull(takerOrderId, "takerOrderId");
        if (matchIndex < 0 || totalMatchCount <= 0 || matchIndex >= totalMatchCount) {
            throw new IllegalArgumentException("Invalid match ordering metadata");
        }
        if (makerRemainingQuantityUnits < 0 || takerRemainingQuantityUnits < 0) {
            throw new IllegalArgumentException("Remaining quantities must not be negative");
        }
    }
}
