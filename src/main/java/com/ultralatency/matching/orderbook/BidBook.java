package com.ultralatency.matching.orderbook;

import java.util.Comparator;

import com.ultralatency.matching.domain.Side;

/**
 * Buy-side price book ordered from highest to lowest price.
 */
final class BidBook extends SideBook {

    BidBook() {
        super(Side.BUY, Comparator.reverseOrder());
    }
}
