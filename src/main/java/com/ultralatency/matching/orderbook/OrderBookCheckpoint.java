package com.ultralatency.matching.orderbook;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.ultralatency.matching.domain.Order;
import com.ultralatency.matching.domain.OrderId;
import com.ultralatency.matching.domain.Price;
import com.ultralatency.matching.domain.Quantity;
import com.ultralatency.matching.domain.Sequence;
import com.ultralatency.matching.domain.Side;

/**
 * Immutable canonical representation of the active state of one order book.
 *
 * <p>Bids are ordered by descending price and asks by ascending price. Orders
 * at one price are ordered by their original command sequence and then by
 * identifier as a defensive canonical tie-breaker.</p>
 */
public final class OrderBookCheckpoint {

    private final List<RestingOrderCheckpoint> bidOrders;
    private final List<RestingOrderCheckpoint> askOrders;

    /**
     * Creates a validated canonical checkpoint.
     *
     * @param bidOrders canonical active bids
     * @param askOrders canonical active asks
     */
    public OrderBookCheckpoint(
            final List<RestingOrderCheckpoint> bidOrders,
            final List<RestingOrderCheckpoint> askOrders) {
        this.bidOrders = List.copyOf(Objects.requireNonNull(bidOrders, "bidOrders"));
        this.askOrders = List.copyOf(Objects.requireNonNull(askOrders, "askOrders"));
        final Set<OrderId> orderIds = new HashSet<>();
        validateSide(this.bidOrders, Side.BUY, true, orderIds);
        validateSide(this.askOrders, Side.SELL, false, orderIds);
    }

    /**
     * Returns canonical active bids.
     *
     * @return immutable bid list
     */
    public List<RestingOrderCheckpoint> bidOrders() {
        return bidOrders;
    }

    /**
     * Returns canonical active asks.
     *
     * @return immutable ask list
     */
    public List<RestingOrderCheckpoint> askOrders() {
        return askOrders;
    }

    /**
     * Returns all records in canonical side order.
     *
     * @return immutable bid-then-ask list
     */
    public List<RestingOrderCheckpoint> allOrders() {
        final List<RestingOrderCheckpoint> result = new ArrayList<>(activeOrderCount());
        result.addAll(bidOrders);
        result.addAll(askOrders);
        return List.copyOf(result);
    }

    /**
     * Returns the active order count.
     *
     * @return active order count
     */
    public int activeOrderCount() {
        return Math.addExact(bidOrders.size(), askOrders.size());
    }

    private static void validateSide(
            final List<RestingOrderCheckpoint> orders,
            final Side expectedSide,
            final boolean descendingPrice,
            final Set<OrderId> orderIds) {
        RestingOrderCheckpoint previous = null;
        for (final RestingOrderCheckpoint order : orders) {
            if (order.side() != expectedSide) {
                throw new IllegalArgumentException("Checkpoint side does not match its book");
            }
            if (!orderIds.add(order.orderId())) {
                throw new IllegalArgumentException("Checkpoint contains duplicate OrderId");
            }
            if (previous != null
                    && compareCanonical(previous, order, descendingPrice) >= 0) {
                throw new IllegalArgumentException("Checkpoint order is not canonical");
            }
            previous = order;
        }
    }

    private static int compareCanonical(
            final RestingOrderCheckpoint left,
            final RestingOrderCheckpoint right,
            final boolean descendingPrice) {
        int result = descendingPrice
                ? right.price().compareTo(left.price())
                : left.price().compareTo(right.price());
        if (result == 0) {
            result = left.originalCommandSequence().compareTo(right.originalCommandSequence());
        }
        if (result == 0) {
            result = left.orderId().compareTo(right.orderId());
        }
        return result;
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof OrderBookCheckpoint that)) {
            return false;
        }
        return bidOrders.equals(that.bidOrders) && askOrders.equals(that.askOrders);
    }

    @Override
    public int hashCode() {
        return Objects.hash(bidOrders, askOrders);
    }

    /**
     * One immutable active resting order in canonical checkpoint form.
     *
     * @param orderId stable order identifier
     * @param side order side
     * @param price limit price
     * @param originalQuantity original quantity
     * @param remainingQuantity active remaining quantity
     * @param originalCommandSequence command that created the order
     */
    public record RestingOrderCheckpoint(
            OrderId orderId,
            Side side,
            Price price,
            Quantity originalQuantity,
            Quantity remainingQuantity,
            Sequence originalCommandSequence) {

        /**
         * Validates one active checkpoint record.
         */
        public RestingOrderCheckpoint {
            Objects.requireNonNull(orderId, "orderId");
            Objects.requireNonNull(side, "side");
            Objects.requireNonNull(price, "price");
            Objects.requireNonNull(originalQuantity, "originalQuantity");
            Objects.requireNonNull(remainingQuantity, "remainingQuantity");
            Objects.requireNonNull(originalCommandSequence, "originalCommandSequence");
            if (remainingQuantity.units() > originalQuantity.units()) {
                throw new IllegalArgumentException("Remaining quantity exceeds original quantity");
            }
        }

        /**
         * Captures one active domain order.
         *
         * @param order active limit order
         * @return immutable checkpoint record
         */
        static RestingOrderCheckpoint fromOrder(final Order order) {
            Objects.requireNonNull(order, "order");
            if (!order.isActive() || order.limitPrice().isEmpty()) {
                throw new IllegalArgumentException("Only active limit orders can be checkpointed");
            }
            return new RestingOrderCheckpoint(
                    order.orderId(),
                    order.side(),
                    order.limitPrice().orElseThrow(),
                    order.originalQuantity(),
                    new Quantity(order.remainingQuantityUnits()),
                    order.sequence());
        }

        /**
         * Reconstructs a fresh active domain order without mutating this value.
         *
         * @return active order with the recorded lifecycle state
         */
        public Order toOrder() {
            final Order order = Order.limit(
                    orderId,
                    side,
                    price,
                    originalQuantity,
                    originalCommandSequence);
            final long executedUnits = originalQuantity.units() - remainingQuantity.units();
            if (executedUnits > 0) {
                order.applyExecution(new Quantity(executedUnits));
            }
            return order;
        }
    }
}
