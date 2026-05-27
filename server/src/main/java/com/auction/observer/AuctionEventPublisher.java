package com.auction.observer;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The Subject (Observable) side of the Observer pattern.
 *
 * Each {@link com.auction.model.Auction} owns one publisher.
 * Observers (clients, loggers, chart updaters, ...) subscribe and are notified
 * on every bid event without polling.
 *
 * Thread-safety: {@link CopyOnWriteArrayList} makes subscribe/unsubscribe safe
 * while {@code publish()} iterates — no extra locking needed.
 */
public class AuctionEventPublisher {

    private static final Logger LOGGER = Logger.getLogger(AuctionEventPublisher.class.getName());

    private final String auctionId;
    private final List<BidObserver> observers = new CopyOnWriteArrayList<>();

    public AuctionEventPublisher(String auctionId) {
        this.auctionId = auctionId;
    }

    // ------------------------------------------------------------------ //
    //  Subscription management                                             //
    // ------------------------------------------------------------------ //

    /**
     * Registers an observer. Safe to call from any thread.
     */
    public void subscribe(BidObserver observer) {
        if (observer != null && !observers.contains(observer)) {
            observers.add(observer);
            LOGGER.fine("Observer subscribed to auction " + auctionId + ": " + observer.getClass().getSimpleName());
        }
    }

    /**
     * Removes an observer (e.g. when the client disconnects).
     */
    public void unsubscribe(BidObserver observer) {
        observers.remove(observer);
        LOGGER.fine("Observer unsubscribed from auction " + auctionId + ": " + observer.getClass().getSimpleName());
    }

    public int getObserverCount() {
        return observers.size();
    }

    // ------------------------------------------------------------------ //
    //  Publishing                                                          //
    // ------------------------------------------------------------------ //

    /**
     * Notifies all registered observers of a bid event.
     * Each observer is called in sequence on the calling thread.
     * If an observer throws, the error is logged and the remaining
     * observers still receive the event.
     *
     * @param event the event to broadcast
     */
    public void publish(BidEvent event) {
        LOGGER.info("Publishing " + event);
        for (BidObserver observer : observers) {
            try {
                observer.onBidEvent(event);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING,
                        "Observer " + observer.getClass().getSimpleName() + " threw an exception.", e);
            }
        }
    }

    public String getAuctionId() {
        return auctionId;
    }
}
