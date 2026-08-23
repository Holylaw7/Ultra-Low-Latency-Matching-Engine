package com.ultralatency.matching.qualification;

/**
 * Public-protocol observation of one ordered match result.
 *
 * @param eventSequence output event sequence
 * @param tradeId trade identity
 * @param price execution price in ticks
 * @param quantity execution quantity in units
 * @param makerOrderId resting maker order identity
 * @param takerOrderId incoming taker order identity
 */
public record QualificationMatch(
        long eventSequence,
        long tradeId,
        long price,
        long quantity,
        long makerOrderId,
        long takerOrderId) {

    /** Creates a validated public match observation. */
    public QualificationMatch {
        if (eventSequence <= 0 || tradeId <= 0 || price <= 0 || quantity <= 0
                || makerOrderId <= 0 || takerOrderId <= 0) {
            throw new IllegalArgumentException("match observation values must be positive");
        }
    }
}
