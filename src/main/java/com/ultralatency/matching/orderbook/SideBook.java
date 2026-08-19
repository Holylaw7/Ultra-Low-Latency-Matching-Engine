package com.ultralatency.matching.orderbook;

import java.util.Comparator;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

import com.ultralatency.matching.domain.Order;
import com.ultralatency.matching.domain.OrderType;
import com.ultralatency.matching.domain.Price;
import com.ultralatency.matching.domain.Quantity;
import com.ultralatency.matching.domain.Side;

/**
 * Ordered price-level index for one order side.
 */
abstract class SideBook {

    private final Side side;
    private final Comparator<Price> priorityComparator;
    private final NavigableMap<Price, PriceLevel> levels;
    private PriceLevel bestLevel;

    SideBook(final Side side, final Comparator<Price> priorityComparator) {
        this.side = Objects.requireNonNull(side, "side");
        this.priorityComparator = Objects.requireNonNull(
                priorityComparator,
                "priorityComparator");
        this.levels = new TreeMap<>(priorityComparator);
    }

    OrderNode add(final Order order) {
        validateRestingOrder(order);
        final Price price = order.limitPrice().orElseThrow();
        PriceLevel level = levels.get(price);
        final boolean created = level == null;
        if (created) {
            level = new PriceLevel(price);
            levels.put(price, level);
        }

        try {
            final OrderNode node = level.add(order);
            if (bestLevel == null
                    || priorityComparator.compare(price, bestLevel.price()) < 0) {
                bestLevel = level;
            }
            return node;
        } catch (RuntimeException exception) {
            if (created && level.isEmpty()) {
                levels.remove(price);
            }
            throw exception;
        }
    }

    boolean cancel(final OrderNode node) {
        final PriceLevel level = ownedLevel(node);
        if (level == null) {
            return false;
        }
        final boolean changed = level.cancel(node);
        removeEmptyLevel(level);
        return changed;
    }

    void applyExecution(final OrderNode node, final Quantity executedQuantity) {
        final PriceLevel level = requireOwnedLevel(node);
        level.applyExecution(node, executedQuantity);
        removeEmptyLevel(level);
    }

    Optional<Price> bestPrice() {
        return bestLevel == null
                ? Optional.empty()
                : Optional.of(bestLevel.price());
    }

    Optional<PriceLevel> bestLevel() {
        return Optional.ofNullable(bestLevel);
    }

    Optional<PriceLevel> levelAt(final Price price) {
        Objects.requireNonNull(price, "price");
        return Optional.ofNullable(levels.get(price));
    }

    int priceLevelCount() {
        return levels.size();
    }

    boolean isEmpty() {
        return levels.isEmpty();
    }

    private void validateRestingOrder(final Order order) {
        Objects.requireNonNull(order, "order");
        if (order.type() != OrderType.LIMIT) {
            throw new IllegalArgumentException("Only limit orders may rest in a book");
        }
        if (order.side() != side) {
            throw new IllegalArgumentException("Order side does not match this book");
        }
        if (!order.isActive()) {
            throw new IllegalStateException("Only active orders may rest in a book");
        }
    }

    private PriceLevel ownedLevel(final OrderNode node) {
        if (node == null || node.order().side() != side) {
            return null;
        }
        final PriceLevel level = node.owner();
        if (level == null || levels.get(level.price()) != level) {
            return null;
        }
        return level;
    }

    private PriceLevel requireOwnedLevel(final OrderNode node) {
        final PriceLevel level = ownedLevel(node);
        if (level == null) {
            throw new IllegalArgumentException("OrderNode does not belong to this book");
        }
        return level;
    }

    private void removeEmptyLevel(final PriceLevel level) {
        if (!level.isEmpty() && levels.get(level.price()) == level) {
            return;
        }
        if (levels.get(level.price()) != level) {
            return;
        }
        levels.remove(level.price());
        if (bestLevel == level) {
            bestLevel = levels.isEmpty()
                    ? null
                    : levels.firstEntry().getValue();
        }
    }
}
