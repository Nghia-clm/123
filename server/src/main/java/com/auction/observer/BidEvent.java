package com.auction.observer;

import com.auction.model.BidTransaction;

import java.time.LocalDateTime;

/**
 * Immutable data object passed to every {@link BidObserver} when an event fires.
 *
 * Contains everything an observer needs to react without querying the server again.
 */
public class BidEvent {

    private final AuctionEventType type;
    private final String auctionId;
    private final BidTransaction transaction; // may be null for non-bid events
    private final double currentPrice;
    private final String leadingBidderId;     // may be null
    private final LocalDateTime newEndTime;  // populated when AUCTION_EXTENDED fires
    private final LocalDateTime occurredAt;

    /**
     * Full constructor – use for BID_PLACED and AUTO_BID_TRIGGERED events.
     */
    public BidEvent(AuctionEventType type,
                    String auctionId,
                    BidTransaction transaction,
                    double currentPrice,
                    String leadingBidderId,
                    LocalDateTime newEndTime) {
        this.type = type;
        this.auctionId = auctionId;
        this.transaction = transaction;
        this.currentPrice = currentPrice;
        this.leadingBidderId = leadingBidderId;
        this.newEndTime = newEndTime;
        this.occurredAt = LocalDateTime.now();
    }

    /**
     * Convenience constructor for status-only events (FINISHED, CANCELED, EXTENDED).
     */
    public BidEvent(AuctionEventType type,
                    String auctionId,
                    double currentPrice,
                    String leadingBidderId,
                    LocalDateTime newEndTime) {
        this(type, auctionId, null, currentPrice, leadingBidderId, newEndTime);
    }

    // ------------------------------------------------------------------ //
    //  Getters                                                             //
    // ------------------------------------------------------------------ //

    public AuctionEventType getType() { return type; }
    public String getAuctionId() { return auctionId; }
    public BidTransaction getTransaction() { return transaction; }
    public double getCurrentPrice() { return currentPrice; }
    public String getLeadingBidderId() { return leadingBidderId; }
    public LocalDateTime getNewEndTime() { return newEndTime; }
    public LocalDateTime getOccurredAt() { return occurredAt; }

    @Override
    public String toString() {
        return String.format("BidEvent{type=%s, auction='%s', price=%.2f, leader='%s', at=%s}",
                type, auctionId, currentPrice, leadingBidderId, occurredAt);
    }
}
