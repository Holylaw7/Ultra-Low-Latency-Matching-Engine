package com.ultralatency.matching.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * Mutable order aggregate with controlled lifecycle transitions.
 */
public final class Order {

    private final OrderId orderId;
    private final Side side;
    private final OrderType type;
    private final Optional<Price> limitPrice;
    private final Quantity originalQuantity;
    private final Sequence sequence;
    private long remainingQuantityUnits;
    private OrderStatus status;

    private Order(
            final OrderId orderId,
            final Side side,
            final OrderType type,
            final Optional<Price> limitPrice,
            final Quantity quantity,
            final Sequence sequence) {
        this.orderId = Objects.requireNonNull(orderId, "orderId");
        this.side = Objects.requireNonNull(side, "side");
        this.type = Objects.requireNonNull(type, "type");
        this.limitPrice = Objects.requireNonNull(limitPrice, "limitPrice");
        this.originalQuantity = Objects.requireNonNull(quantity, "quantity");
        this.sequence = Objects.requireNonNull(sequence, "sequence");
        this.remainingQuantityUnits = quantity.units();
        this.status = OrderStatus.NEW;
    }

    /**
     * Creates a limit order.
     *
     * @param orderId stable order identifier
     * @param side order side
     * @param price fixed-scale limit price
     * @param quantity positive order quantity
     * @param sequence logical input sequence
     * @return new limit order
     */
    public static Order limit(
            final OrderId orderId,
            final Side side,
            final Price price,
            final Quantity quantity,
            final Sequence sequence) {
        return new Order(
                orderId,
                side,
                OrderType.LIMIT,
                Optional.of(Objects.requireNonNull(price, "price")),
                quantity,
                sequence);
    }

    /**
     * Creates a market order without a limit price.
     *
     * @param orderId stable order identifier
     * @param side order side
     * @param quantity positive order quantity
     * @param sequence logical input sequence
     * @return new market order
     */
    public static Order market(
            final OrderId orderId,
            final Side side,
            final Quantity quantity,
            final Sequence sequence) {
        return new Order(
                orderId,
                side,
                OrderType.MARKET,
                Optional.empty(),
                quantity,
                sequence);
    }

    public OrderId orderId() {
        return orderId;
    }

    public Side side() {
        return side;
    }

    public OrderType type() {
        return type;
    }

    public Optional<Price> limitPrice() {
        return limitPrice;
    }

    public Quantity originalQuantity() {
        return originalQuantity;
    }

    public long remainingQuantityUnits() {
        return remainingQuantityUnits;
    }

    public Sequence sequence() {
        return sequence;
    }

    public OrderStatus status() {
        return status;
    }

    public boolean isActive() {
        return status.isActive();
    }

    /**
     * Applies one positive execution to the order.
     *
     * @param executedQuantity quantity executed
     * @throws IllegalStateException when the order is no longer active
     * @throws IllegalArgumentException when the execution exceeds the remainder
     */
    public void applyExecution(final Quantity executedQuantity) {
        Objects.requireNonNull(executedQuantity, "executedQuantity");
        if (!isActive()) {
            throw new IllegalStateException("Order is not active");
        }
        if (executedQuantity.units() > remainingQuantityUnits) {
            throw new IllegalArgumentException("Execution exceeds remaining quantity");
        }
        remainingQuantityUnits -= executedQuantity.units();
        status = remainingQuantityUnits == 0
                ? OrderStatus.FILLED
                : OrderStatus.PARTIALLY_FILLED;
    }

    /**
     * Cancels the order. Repeated cancellation is idempotent.
     *
     * @return true when the state changed, false when already canceled
     * @throws IllegalStateException when the order is already filled
     */
    public boolean cancel() {
        if (status == OrderStatus.CANCELED) {
            return false;
        }
        if (status == OrderStatus.FILLED) {
            throw new IllegalStateException("Filled order cannot be canceled");
        }
        status = OrderStatus.CANCELED;
        return true;
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Order)) {
            return false;
        }
        final Order that = (Order) other;
        return orderId.equals(that.orderId);
    }

    @Override
    public int hashCode() {
        return orderId.hashCode();
    }
}
