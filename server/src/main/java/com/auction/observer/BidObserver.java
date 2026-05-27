package com.auction.observer;

/**
 * Observer interface for the auction bid event system.
 *
 * Any component that wants to react to bid events (new bid placed,
 * auction finished, auction extended, etc.) must implement this interface
 * and register itself with an {@link AuctionEventPublisher}.
 *
 * Usage example:
 * <pre>
 *   AuctionEventPublisher publisher = new AuctionEventPublisher("auction-123");
 *   publisher.subscribe(new ClientNotifier(clientSocket));
 *   publisher.subscribe(new BidLogger());
 *
 *   // when a bid arrives:
 *   publisher.publish(new BidEvent(AuctionEventType.BID_PLACED, tx, 500.0));
 * </pre>
 */
public interface BidObserver {

    /**
     * Called by the publisher whenever a bid-related event occurs.
     */
    default void onBidEvent(BidEvent event) {
        switch (event.getType()) {
            case BID_PLACED:        onBidPlaced(event); break;
            case AUTO_BID_TRIGGERED: onAutoBidTriggered(event); break;
            case AUCTION_EXTENDED:  onAuctionExtended(event); break;
            case AUCTION_FINISHED:  onAuctionFinished(event); break;
            case AUCTION_CANCELED:  onAuctionCanceled(event); break;
            default:                onUnknownEvent(event); break;
        }
    }

    /**
     * Gọi từ AuctionService.notifyObservers() khi giá hiện tại thay đổi.
     * Cung cấp thông tin tối thiểu để update UI realtime.
     */
    default void onBidUpdated(String auctionId, double newPrice, String leadingBidderId) {
        // no-op by default
    }

    /**
     * Gọi từ AuctionService.notifyObservers() sau khi bid được lưu vào DB.
     * Cung cấp đầy đủ BidTransaction cho logging/chart.
     */
    default void onNewBid(com.auction.model.Auction auction, com.auction.model.BidTransaction tx) {
        // no-op by default
    }

    default void onBidPlaced(BidEvent event)        { /* no-op */ }
    default void onAutoBidTriggered(BidEvent event) { /* no-op */ }
    default void onAuctionExtended(BidEvent event)  { /* no-op */ }
    default void onAuctionFinished(BidEvent event)  { /* no-op */ }
    default void onAuctionCanceled(BidEvent event)  { /* no-op */ }
    default void onUnknownEvent(BidEvent event)     { /* no-op */ }
}
