package com.ultralatency.matching.orderbook;

import java.util.Comparator;

import com.ultralatency.matching.domain.Side;

/**
 * Sell-side price book ordered from lowest to highest price.
 */
final class AskBook extends SideBook {

    AskBook() {
        super(Side.SELL, Comparator.naturalOrder());
    }
}
