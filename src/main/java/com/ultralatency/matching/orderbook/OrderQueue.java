package com.ultralatency.matching.orderbook;

import java.util.Objects;

/**
 * Intrusive FIFO queue for one price level.
 */
final class OrderQueue {

    private OrderNode head;
    private OrderNode tail;
    private int size;

    OrderNode head() {
        return head;
    }

    OrderNode tail() {
        return tail;
    }

    int size() {
        return size;
    }

    boolean isEmpty() {
        return size == 0;
    }

    void append(final OrderNode node, final PriceLevel owner) {
        Objects.requireNonNull(node, "node");
        Objects.requireNonNull(owner, "owner");
        if (tail == null) {
            if (head != null || size != 0) {
                throw new IllegalStateException("Queue state is inconsistent");
            }
            node.attach(owner);
            head = node;
            tail = node;
        } else {
            node.attach(owner);
            tail.next(node);
            node.previous(tail);
            tail = node;
        }
        size++;
    }

    void unlink(final OrderNode node, final PriceLevel owner) {
        Objects.requireNonNull(node, "node");
        Objects.requireNonNull(owner, "owner");
        if (!node.belongsTo(owner)) {
            throw new IllegalArgumentException("OrderNode does not belong to this price level");
        }
        if (size == 0) {
            throw new IllegalStateException("Cannot unlink from an empty queue");
        }

        final OrderNode previous = node.previous();
        final OrderNode next = node.next();
        if (previous == null) {
            if (head != node) {
                throw new IllegalStateException("Queue head is inconsistent");
            }
            head = next;
        } else {
            if (previous.next() != node) {
                throw new IllegalStateException("Previous link is inconsistent");
            }
            previous.next(next);
        }

        if (next == null) {
            if (tail != node) {
                throw new IllegalStateException("Queue tail is inconsistent");
            }
            tail = previous;
        } else {
            if (next.previous() != node) {
                throw new IllegalStateException("Next link is inconsistent");
            }
            next.previous(previous);
        }

        node.detach(owner);
        size--;
    }
}
