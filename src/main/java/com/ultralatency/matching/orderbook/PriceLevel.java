package com.ultralatency.matching.orderbook;

import java.util.Objects;

import com.ultralatency.matching.domain.Order;
import com.ultralatency.matching.domain.OrderStatus;
import com.ultralatency.matching.domain.OrderType;
import com.ultralatency.matching.domain.Price;
import com.ultralatency.matching.domain.Quantity;

/**
 * One price and its FIFO queue of live limit orders.
 */
final class PriceLevel {

    private final Price price;
    private final OrderQueue queue;
    private long totalQuantityUnits;

    PriceLevel(final Price price) {
        this.price = Objects.requireNonNull(price, "price");
        this.queue = new OrderQueue();
    }

    Price price() {
        return price;
    }

    OrderNode head() {
        return queue.head();
    }

    OrderNode tail() {
        return queue.tail();
    }

    int orderCount() {
        return queue.size();
    }

    long totalQuantityUnits() {
        return totalQuantityUnits;
    }

    boolean isEmpty() {
        return queue.isEmpty();
    }

    OrderNode add(final Order order) {
        validateRestingOrder(order);
        final long remainingQuantityUnits = order.remainingQuantityUnits();
        final long updatedTotal = Math.addExact(totalQuantityUnits, remainingQuantityUnits);
        final OrderNode node = new OrderNode(order);
        queue.append(node, this);
        totalQuantityUnits = updatedTotal;
        return node;
    }

    boolean cancel(final OrderNode node) {
        if (node == null || !node.belongsTo(this)) {
            return false;
        }
        if (node.order().status().isTerminal()) {
            if (node.order().status() != OrderStatus.CANCELED) {
                throw new IllegalStateException("Filled order cannot remain in a price level");
            }
            unlink(node);
            return true;
        }
        if (!node.order().cancel()) {
            return false;
        }
        unlink(node);
        return true;
    }

    void applyExecution(final OrderNode node, final Quantity executedQuantity) {
        requireOwned(node);
        Objects.requireNonNull(executedQuantity, "executedQuantity");

        node.order().applyExecution(executedQuantity);
        totalQuantityUnits = Math.subtractExact(totalQuantityUnits, executedQuantity.units());
        if (!node.order().isActive()) {
            unlink(node);
        }
    }

    void unlink(final OrderNode node) {
        requireOwned(node);
        final long updatedTotal = Math.subtractExact(
                totalQuantityUnits,
                node.order().remainingQuantityUnits());
        queue.unlink(node, this);
        totalQuantityUnits = updatedTotal;
    }

    private void validateRestingOrder(final Order order) {
        Objects.requireNonNull(order, "order");
        if (order.type() != OrderType.LIMIT) {
            throw new IllegalArgumentException("Only limit orders may rest at a price level");
        }
        if (!order.isActive()) {
            throw new IllegalStateException("Only active orders may rest at a price level");
        }
        if (!price.equals(order.limitPrice().orElseThrow())) {
            throw new IllegalArgumentException("Order price does not match the price level");
        }
    }

    private void requireOwned(final OrderNode node) {
        Objects.requireNonNull(node, "node");
        if (!node.belongsTo(this)) {
            throw new IllegalArgumentException("OrderNode does not belong to this price level");
        }
    }
}
