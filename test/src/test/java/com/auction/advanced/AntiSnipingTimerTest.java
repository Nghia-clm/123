package com.auction.advanced;

import com.auction.AntiSnipingTimer;
import com.auction.model.Auction;
import com.auction.model.AuctionStatus;
import com.auction.model.item.Electronics;
import com.auction.model.item.Item;
import com.auction.model.user.Seller;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("AntiSnipingTimer - extension logic")
class AntiSnipingTimerTest {

    @Test
    @DisplayName("bid inside snipe window extends auction")
    void bidInsideWindowExtendsAuction() {
        FakeAuctionRuntime runtime = new FakeAuctionRuntime();
        Auction auction = runningAuction(LocalDateTime.now().plusSeconds(5));
        runtime.auctions.put(auction.getId(), auction);
        AntiSnipingTimer timer = new AntiSnipingTimer(10, 60, 3, runtime);

        timer.onBidUpdated(auction.getId(), 120, "bidder-1");

        assertEquals(1, runtime.extensionCalls);
        assertEquals(60, runtime.lastExtraSeconds);
        assertEquals(1, timer.getExtensionCount(auction.getId()));
    }

    @Test
    @DisplayName("bid outside snipe window does not extend")
    void bidOutsideWindowDoesNotExtend() {
        FakeAuctionRuntime runtime = new FakeAuctionRuntime();
        Auction auction = runningAuction(LocalDateTime.now().plusSeconds(30));
        runtime.auctions.put(auction.getId(), auction);
        AntiSnipingTimer timer = new AntiSnipingTimer(10, 60, 3, runtime);

        timer.onBidUpdated(auction.getId(), 120, "bidder-1");

        assertEquals(0, runtime.extensionCalls);
        assertEquals(0, timer.getExtensionCount(auction.getId()));
    }

    @Test
    @DisplayName("maxExtensions limits repeated extensions")
    void maxExtensionsLimitsRepeatedExtensions() {
        FakeAuctionRuntime runtime = new FakeAuctionRuntime();
        Auction auction = runningAuction(LocalDateTime.now().plusSeconds(5));
        runtime.auctions.put(auction.getId(), auction);
        AntiSnipingTimer timer = new AntiSnipingTimer(10, 60, 1, runtime);

        timer.onBidUpdated(auction.getId(), 120, "bidder-1");
        timer.onBidUpdated(auction.getId(), 130, "bidder-2");

        assertEquals(1, runtime.extensionCalls);
        assertEquals(1, timer.getExtensionCount(auction.getId()));
    }

    private static Auction runningAuction(LocalDateTime endTime) {
        Seller seller = new Seller("seller-1", "seller", "pass", "seller@test.com");
        Item item = new Electronics("item-1", "Phone", "desc", 100, seller.getId());
        Auction auction = new Auction("auction-1", item, seller, 100,
                LocalDateTime.now().minusMinutes(1), endTime);
        auction.setStatus(AuctionStatus.RUNNING);
        return auction;
    }

    private static final class FakeAuctionRuntime implements AntiSnipingTimer.AuctionTimerRuntime {
        private final Map<String, Auction> auctions = new HashMap<>();
        private int extensionCalls;
        private int lastExtraSeconds;

        @Override
        public Auction getAuction(String auctionId) {
            return auctions.get(auctionId);
        }

        @Override
        public void extendAuctionTime(String auctionId, int extraSeconds) {
            extensionCalls++;
            lastExtraSeconds = extraSeconds;
            Auction auction = auctions.get(auctionId);
            auction.setEndTime(auction.getEndTime().plusSeconds(extraSeconds));
        }
    }
}
