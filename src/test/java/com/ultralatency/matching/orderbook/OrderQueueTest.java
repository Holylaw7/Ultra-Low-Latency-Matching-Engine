package com.ultralatency.matching.orderbook;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.ultralatency.matching.domain.Order;
import com.ultralatency.matching.domain.OrderId;
import com.ultralatency.matching.domain.Price;
import com.ultralatency.matching.domain.Quantity;
import com.ultralatency.matching.domain.Sequence;
import com.ultralatency.matching.domain.Side;

class OrderQueueTest {

    @Test
    void appendsInFifoOrderAndMaintainsHeadTailLinks() {
        final PriceLevel owner = new PriceLevel(new Price(100));
        final OrderQueue queue = new OrderQueue();
        final OrderNode first = new OrderNode(order(1, 10, 1));
        final OrderNode second = new OrderNode(order(2, 20, 2));
        final OrderNode third = new OrderNode(order(3, 30, 3));

        queue.append(first, owner);
        queue.append(second, owner);
        queue.append(third, owner);

        assertSame(first, queue.head());
        assertSame(third, queue.tail());
        assertSame(second, first.next());
        assertSame(first, second.previous());
        assertSame(third, second.next());
        assertSame(second, third.previous());
        assertNull(first.previous());
        assertNull(third.next());
        assertEquals(3, queue.size());
        assertFalse(queue.isEmpty());
    }

    @Test
    void unlinksHeadMiddleAndTailInConstantLinkOperations() {
        final PriceLevel owner = new PriceLevel(new Price(100));
        final OrderQueue queue = new OrderQueue();
        final OrderNode first = new OrderNode(order(1, 10, 1));
        final OrderNode second = new OrderNode(order(2, 20, 2));
        final OrderNode third = new OrderNode(order(3, 30, 3));
        queue.append(first, owner);
        queue.append(second, owner);
        queue.append(third, owner);

        queue.unlink(second, owner);
        assertSame(first, queue.head());
        assertSame(third, queue.tail());
        assertSame(third, first.next());
        assertSame(first, third.previous());
        assertFalse(second.isLinked());
        assertEquals(2, queue.size());

        queue.unlink(first, owner);
        assertSame(third, queue.head());
        assertSame(third, queue.tail());
        assertNull(third.previous());
        assertEquals(1, queue.size());

        queue.unlink(third, owner);
        assertNull(queue.head());
        assertNull(queue.tail());
        assertEquals(0, queue.size());
        assertTrue(queue.isEmpty());
    }

    @Test
    void rejectsRepeatedAppendAndWrongOwnerUnlink() {
        final PriceLevel owner = new PriceLevel(new Price(100));
        final PriceLevel wrongOwner = new PriceLevel(new Price(100));
        final OrderQueue queue = new OrderQueue();
        final OrderNode node = new OrderNode(order(1, 10, 1));

        queue.append(node, owner);

        assertThrows(IllegalStateException.class, () -> queue.append(node, owner));
        assertThrows(IllegalArgumentException.class, () -> queue.unlink(node, wrongOwner));
        queue.unlink(node, owner);
        assertThrows(IllegalArgumentException.class, () -> queue.unlink(node, owner));
    }

    private static Order order(final long id, final long quantity, final long sequence) {
        return Order.limit(
                new OrderId(id),
                Side.BUY,
                new Price(100),
                new Quantity(quantity),
                new Sequence(sequence));
    }
}
