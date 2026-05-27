package com.auction.advanced;

import com.auction.AutoBidder;
import com.auction.exception.AuctionClosedException;
import com.auction.exception.InvalidBidException;
import com.auction.exception.UserNotFoundException;
import com.auction.model.Auction;
import com.auction.model.AuctionStatus;
import com.auction.model.item.Electronics;
import com.auction.model.item.Item;
import com.auction.model.user.Bidder;
import com.auction.model.user.Seller;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AutoBidder - automatic bid logic")
class AutoBidderTest {

    private static final String AUCTION_ID = "auction-1";
    private FakeAuctionRuntime runtime;
    private RecordingBidPlacer bidPlacer;
    private AutoBidder autoBidder;

    @BeforeEach
    void setUp() {
        runtime = new FakeAuctionRuntime();
        bidPlacer = new RecordingBidPlacer(runtime);
        autoBidder = new AutoBidder(runtime, bidPlacer, Runnable::run);
        runtime.addAuction(runningAuction(100, "external"));
    }

    @Test
    @DisplayName("does not place auto-bid above maxBid")
    void doesNotExceedMaxBid() {
        autoBidder.register(AUCTION_ID, "bidder-a", 105, 10);

        assertTrue(bidPlacer.placed.isEmpty());
        assertFalse(autoBidder.isRegistered(AUCTION_ID, "bidder-a"));
    }

    @Test
    @DisplayName("same maxBid keeps FIFO priority")
    void sameMaxBidUsesFifoPriority() {
        autoBidder.register(AUCTION_ID, "bidder-a", 200, 10);
        autoBidder.register(AUCTION_ID, "bidder-b", 200, 10);

        runtime.auctions.get(AUCTION_ID).setCurrentWinnerId("external");
        autoBidder.onBidUpdated(AUCTION_ID, 110, "external");

        assertFalse(bidPlacer.placed.isEmpty());
        assertTrue(bidPlacer.placed.stream().allMatch(bid -> bid.bidderId.equals("bidder-a")));
    }

    @Test
    @DisplayName("does not bid again when best auto-bidder is already winner")
    void doesNotBidWhenAlreadyWinner() {
        runtime.addAuction(runningAuction(100, "bidder-a"));
        autoBidder.register(AUCTION_ID, "bidder-a", 200, 10);
        bidPlacer.placed.clear();

        autoBidder.onBidUpdated(AUCTION_ID, 100, "bidder-a");

        assertTrue(bidPlacer.placed.isEmpty());
    }

    @Test
    @DisplayName("cancel removes registration")
    void cancelRemovesRegistration() {
        autoBidder.register(AUCTION_ID, "bidder-a", 200, 10);

        autoBidder.cancel(AUCTION_ID, "bidder-a");

        assertFalse(autoBidder.isRegistered(AUCTION_ID, "bidder-a"));
    }

    private static Auction runningAuction(double currentPrice, String winnerId) {
        Seller seller = new Seller("seller-1", "seller", "pass", "seller@test.com");
        Item item = new Electronics("item-1", "Phone", "desc", 100, seller.getId());
        Auction auction = new Auction(AUCTION_ID, item, seller, 100,
                LocalDateTime.now().minusMinutes(1),
                LocalDateTime.now().plusHours(1));
        auction.setStatus(AuctionStatus.RUNNING);
        auction.setCurrentPrice(currentPrice);
        auction.setCurrentWinnerId(winnerId);
        if (winnerId != null && winnerId.startsWith("bidder")) {
            auction.setWinner(new Bidder(winnerId, winnerId, "pass", winnerId + "@test.com"));
        }
        return auction;
    }

    private static final class FakeAuctionRuntime implements AutoBidder.AuctionRuntime {
        private final Map<String, Auction> auctions = new ConcurrentHashMap<>();

        @Override
        public Auction getAuction(String auctionId) {
            return auctions.get(auctionId);
        }

        @Override
        public void addAuction(Auction auction) {
            auctions.put(auction.getId(), auction);
        }
    }

    private static final class RecordingBidPlacer implements AutoBidder.BidPlacer {
        private final FakeAuctionRuntime runtime;
        private final List<PlacedBid> placed = new ArrayList<>();

        private RecordingBidPlacer(FakeAuctionRuntime runtime) {
            this.runtime = runtime;
        }

        @Override
        public void placeBid(String auctionId, String bidderId, double bidAmount, boolean autoBid)
                throws AuctionClosedException, InvalidBidException, UserNotFoundException {
            placed.add(new PlacedBid(bidderId, bidAmount, autoBid));
            Auction auction = runtime.auctions.get(auctionId);
            auction.setCurrentPrice(bidAmount);
            auction.setCurrentWinnerId(bidderId);
        }
    }

    private record PlacedBid(String bidderId, double amount, boolean autoBid) {}
}
