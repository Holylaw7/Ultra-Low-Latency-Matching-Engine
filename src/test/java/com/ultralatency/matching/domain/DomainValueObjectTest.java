package com.ultralatency.matching.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class DomainValueObjectTest {

    @Test
    void rejectsNonPositiveDomainValues() {
        assertThrows(IllegalArgumentException.class, () -> new OrderId(0));
        assertThrows(IllegalArgumentException.class, () -> new Price(-1));
        assertThrows(IllegalArgumentException.class, () -> new Quantity(0));
        assertThrows(IllegalArgumentException.class, () -> new Sequence(-1));
        assertThrows(IllegalArgumentException.class, () -> new TradeId(0));
    }

    @Test
    void preservesIntegerDomainSemanticsAndOrdering() {
        assertEquals(10025L, Price.of(10025).ticks());
        assertEquals(1L, Quantity.of(1).units());
        assertEquals(-1, new Sequence(1).compareTo(new Sequence(2)));
        assertEquals(new Sequence(2), new Sequence(1).next());
        assertEquals(0, OrderId.of(7).compareTo(new OrderId(7)));
    }

    @Test
    void rejectsSequenceOverflow() {
        assertThrows(
                ArithmeticException.class,
                () -> new Sequence(Long.MAX_VALUE).next());
    }
}
