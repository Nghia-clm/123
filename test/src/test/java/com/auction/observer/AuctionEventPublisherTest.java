package com.auction.observer;

import com.auction.model.BidTransaction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AuctionEventPublisher - Observer Pattern")
class AuctionEventPublisherTest {

    @Test
    @DisplayName("subscribe ignores nulls and duplicate observers")
    void subscribeIgnoresNullsAndDuplicates() {
        AuctionEventPublisher publisher = new AuctionEventPublisher("auction-1");
        RecordingObserver observer = new RecordingObserver();

        publisher.subscribe(null);
        publisher.subscribe(observer);
        publisher.subscribe(observer);

        assertEquals(1, publisher.getObserverCount());
    }

    @Test
    @DisplayName("unsubscribe removes observer")
    void unsubscribeRemovesObserver() {
        AuctionEventPublisher publisher = new AuctionEventPublisher("auction-1");
        RecordingObserver observer = new RecordingObserver();
        publisher.subscribe(observer);

        publisher.unsubscribe(observer);
        publisher.publish(event("auction-1", 120));

        assertEquals(0, publisher.getObserverCount());
        assertEquals(0, observer.events.size());
    }

    @Test
    @DisplayName("publish notifies all subscribed observers")
    void publishNotifiesAllObservers() {
        AuctionEventPublisher publisher = new AuctionEventPublisher("auction-1");
        RecordingObserver first = new RecordingObserver();
        RecordingObserver second = new RecordingObserver();
        BidEvent event = event("auction-1", 120);

        publisher.subscribe(first);
        publisher.subscribe(second);
        publisher.publish(event);

        assertEquals(List.of(event), first.events);
        assertEquals(List.of(event), second.events);
        assertEquals("auction-1", publisher.getAuctionId());
    }

    @Test
    @DisplayName("observer exception does not block later observers")
    void observerExceptionDoesNotBlockLaterObservers() {
        AuctionEventPublisher publisher = new AuctionEventPublisher("auction-1");
        RecordingObserver afterFailure = new RecordingObserver();
        BidEvent event = event("auction-1", 120);

        publisher.subscribe(new ThrowingObserver());
        publisher.subscribe(afterFailure);
        publisher.publish(event);

        assertEquals(List.of(event), afterFailure.events);
    }

    @Test
    @DisplayName("CopyOnWrite subscription is safe while publish iterates")
    void subscriptionChangesAreSafeWhilePublishing() {
        AuctionEventPublisher publisher = new AuctionEventPublisher("auction-1");
        RecordingObserver stable = new RecordingObserver();
        RecordingObserver addedDuringPublish = new RecordingObserver();
        BidEvent event = event("auction-1", 120);

        publisher.subscribe(new BidObserver() {
            @Override
            public void onBidEvent(BidEvent event) {
                publisher.subscribe(addedDuringPublish);
                publisher.unsubscribe(this);
            }
        });
        publisher.subscribe(stable);

        assertDoesNotThrow(() -> publisher.publish(event));
        assertEquals(List.of(event), stable.events);
        assertEquals(2, publisher.getObserverCount());
    }

    @Test
    @DisplayName("concurrent subscribe and publish complete without data races")
    void concurrentSubscribeAndPublishCompletes() throws Exception {
        AuctionEventPublisher publisher = new AuctionEventPublisher("auction-1");
        AtomicInteger received = new AtomicInteger();
        int workers = 8;
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(workers);

        for (int i = 0; i < workers; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    publisher.subscribe(new BidObserver() {
                        @Override
                        public void onBidEvent(BidEvent event) {
                            received.incrementAndGet();
                        }
                    });
                    publisher.publish(event("auction-1", 120));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        assertTrue(ready.await(2, TimeUnit.SECONDS));
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        assertEquals(workers, publisher.getObserverCount());
        assertTrue(received.get() >= workers);
    }

    private static BidEvent event(String auctionId, double amount) {
        BidTransaction tx = new BidTransaction("tx-1", auctionId, "bidder-1", amount, LocalDateTime.now());
        return new BidEvent(AuctionEventType.BID_PLACED, auctionId, tx, amount, "bidder-1", null);
    }

    private static final class RecordingObserver implements BidObserver {
        private final List<BidEvent> events = new java.util.ArrayList<>();

        @Override
        public void onBidEvent(BidEvent event) {
            events.add(event);
        }
    }

    private static final class ThrowingObserver implements BidObserver {
        @Override
        public void onBidEvent(BidEvent event) {
            throw new IllegalStateException("observer failed");
        }
    }
}
