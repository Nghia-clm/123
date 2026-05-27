package com.auction.observer;

import java.util.logging.Logger;

/**
 * Concrete {@link BidObserver} that logs every auction event to the
 * server's standard logger.
 *
 * Useful for debugging, audit trails, and CI test output.
 */
public class BidLogger implements BidObserver {

    private static final Logger LOGGER = Logger.getLogger(BidLogger.class.getName());

    @Override
    public void onBidEvent(BidEvent event) {
        switch (event.getType()) {
            case BID_PLACED:
                LOGGER.info(String.format(
                        "[BID]      auction=%s | bidder=%s | amount=%.2f | auto=%b",
                        event.getAuctionId(),
                        event.getLeadingBidderId(),
                        event.getCurrentPrice(),
                        event.getTransaction() != null && event.getTransaction().isAutoBid()));
                break;

            case AUTO_BID_TRIGGERED:
                LOGGER.info(String.format(
                        "[AUTO-BID] auction=%s | auto-bidder=%s | amount=%.2f",
                        event.getAuctionId(),
                        event.getLeadingBidderId(),
                        event.getCurrentPrice()));
                break;

            case AUCTION_EXTENDED:
                LOGGER.info(String.format(
                        "[EXTENDED] auction=%s | new end time=%s",
                        event.getAuctionId(),
                        event.getNewEndTime()));
                break;

            case AUCTION_FINISHED:
                LOGGER.info(String.format(
                        "[FINISHED] auction=%s | winner=%s | final price=%.2f",
                        event.getAuctionId(),
                        event.getLeadingBidderId(),
                        event.getCurrentPrice()));
                break;

            case AUCTION_CANCELED:
                LOGGER.warning(String.format(
                        "[CANCELED] auction=%s",
                        event.getAuctionId()));
                break;

            default:
                LOGGER.fine("Unhandled event type: " + event.getType());
        }
    }
}
