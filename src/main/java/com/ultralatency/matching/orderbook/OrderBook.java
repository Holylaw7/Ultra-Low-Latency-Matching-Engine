package com.ultralatency.matching.orderbook;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.ultralatency.matching.domain.Order;
import com.ultralatency.matching.domain.OrderId;
import com.ultralatency.matching.domain.Price;
import com.ultralatency.matching.domain.Quantity;
import com.ultralatency.matching.domain.Side;

/**
 * Aggregate for the two side-specific books and the active order index.
 */
public final class OrderBook {

    private final BidBook bidBook;
    private final AskBook askBook;
    private final Map<OrderId, OrderNode> activeOrders;

    /**
     * Creates an empty order book.
     */
    public OrderBook() {
        bidBook = new BidBook();
        askBook = new AskBook();
        activeOrders = new HashMap<>();
    }

    /**
     * Adds an active limit order to its side-specific price book.
     *
     * @param order order to rest
     * @throws IllegalArgumentException when the order identifier is active or
     *     the order cannot rest
     * @throws IllegalStateException when the order is not active
     */
    public void add(final Order order) {
        Objects.requireNonNull(order, "order");
        final OrderId orderId = order.orderId();
        if (activeOrders.containsKey(orderId)) {
            throw new IllegalArgumentException(
                    "OrderId is already active: " + orderId.value());
        }

        final SideBook sideBook = sideBook(order.side());
        final OrderNode node = sideBook.add(order);
        activeOrders.put(orderId, node);
    }

    /**
     * Cancels an active order by identifier.
     *
     * @param orderId identifier to cancel
     * @return true when an active order was canceled, false when absent
     */
    public boolean cancel(final OrderId orderId) {
        Objects.requireNonNull(orderId, "orderId");
        final OrderNode node = activeOrders.get(orderId);
        if (node == null) {
            return false;
        }

        final boolean changed = sideBook(node.order().side()).cancel(node);
        if (changed) {
            activeOrders.remove(orderId);
        }
        return changed;
    }

    /**
     * Returns the active order for an identifier.
     *
     * @param orderId identifier to look up
     * @return the live order, or empty when it is not indexed
     */
    public Optional<Order> activeOrder(final OrderId orderId) {
        Objects.requireNonNull(orderId, "orderId");
        final OrderNode node = activeOrders.get(orderId);
        return node == null
                ? Optional.empty()
                : Optional.of(node.order());
    }

    /**
     * Returns the current best bid price.
     *
     * @return highest live bid price, or empty
     */
    public Optional<Price> bestBid() {
        return bidBook.bestPrice();
    }

    /**
     * Returns the current best ask price.
     *
     * @return lowest live ask price, or empty
     */
    public Optional<Price> bestAsk() {
        return askBook.bestPrice();
    }

    /**
     * Returns the number of active orders.
     *
     * @return active order count
     */
    public int activeOrderCount() {
        return activeOrders.size();
    }

    /**
     * Returns the number of live bid price levels.
     *
     * @return bid price-level count
     */
    public int bidPriceLevelCount() {
        return bidBook.priceLevelCount();
    }

    /**
     * Returns the number of live ask price levels.
     *
     * @return ask price-level count
     */
    public int askPriceLevelCount() {
        return askBook.priceLevelCount();
    }

    /**
     * Applies a controlled execution to one indexed order.
     *
     * <p>This is an internal state-transition primitive for the future
     * structural matching operation. It does not perform matching or create
     * trade events.</p>
     *
     * @param orderId identifier to update
     * @param executedQuantity executed quantity
     * @throws IllegalArgumentException when the order is absent
     */
    void applyExecution(
            final OrderId orderId,
            final Quantity executedQuantity) {
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(executedQuantity, "executedQuantity");
        final OrderNode node = activeOrders.get(orderId);
        if (node == null) {
            throw new IllegalArgumentException("OrderId is not active");
        }

        final SideBook sideBook = sideBook(node.order().side());
        sideBook.applyExecution(node, executedQuantity);
        if (!node.isLinked()) {
            activeOrders.remove(orderId);
        }
    }

    Optional<OrderNode> activeNode(final OrderId orderId) {
        Objects.requireNonNull(orderId, "orderId");
        return Optional.ofNullable(activeOrders.get(orderId));
    }

    Optional<PriceLevel> bidLevelAt(final Price price) {
        return bidBook.levelAt(price);
    }

    Optional<PriceLevel> askLevelAt(final Price price) {
        return askBook.levelAt(price);
    }

    private SideBook sideBook(final Side side) {
        return side == Side.BUY ? bidBook : askBook;
    }
}
