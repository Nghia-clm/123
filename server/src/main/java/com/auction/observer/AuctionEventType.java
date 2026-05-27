package com.auction.observer;

/**
 * Types of events that can be published to {@link BidObserver} instances.
 */
public enum AuctionEventType {
    /** A valid bid was successfully placed. */
    BID_PLACED,

    /** The auction end time was extended due to the anti-sniping rule. */
    AUCTION_EXTENDED,

    /** The auction timer has expired and a winner has been determined. */
    AUCTION_FINISHED,

    /** The auction was cancelled by admin or seller. */
    AUCTION_CANCELED,

    /** An auto-bid was triggered on behalf of a bidder. */
    AUTO_BID_TRIGGERED
}
