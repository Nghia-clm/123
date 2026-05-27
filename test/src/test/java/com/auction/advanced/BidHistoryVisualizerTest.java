package com.auction.advanced;

import com.auction.BidHistoryVisualizer;
import com.auction.model.Auction;
import com.auction.model.BidTransaction;
import com.auction.model.item.Electronics;
import com.auction.model.item.Item;
import com.auction.model.user.Seller;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BidHistoryVisualizer - chart data")
class BidHistoryVisualizerTest {

    @Test
    @DisplayName("addPoint stores point and renders chart JSON")
    void addPointStoresPointAndRendersJson() {
        String auctionId = "chart-auction-add";
        BidHistoryVisualizer visualizer = BidHistoryVisualizer.getInstance();
        visualizer.evict(auctionId);
        LocalDateTime time = LocalDateTime.of(2026, 5, 24, 10, 30);

        visualizer.addPoint(auctionId, 150, "bidder-1", time);

        assertEquals(1, visualizer.getPoints(auctionId).size());
        BidHistoryVisualizer.DataPoint point = visualizer.getPoints(auctionId).get(0);
        assertEquals(time, point.getTimestamp());
        assertEquals(150, point.getPrice(), 0.01);
        assertEquals("bidder-1", point.getBidderId());

        JSONObject json = new JSONObject(visualizer.getChartDataJson(auctionId));
        assertEquals(auctionId, json.getString("auctionId"));
        assertEquals(1, json.getInt("totalBids"));
        assertEquals(150, json.getDouble("currentPrice"), 0.01);
        assertEquals("bidder-1", json.getJSONArray("points").getJSONObject(0).getString("bidderId"));

        visualizer.evict(auctionId);
    }

    @Test
    @DisplayName("onNewBid records transaction timestamp and amount")
    void onNewBidRecordsTransactionData() {
        String auctionId = "chart-auction-observer";
        BidHistoryVisualizer visualizer = BidHistoryVisualizer.getInstance();
        visualizer.evict(auctionId);
        LocalDateTime bidTime = LocalDateTime.of(2026, 5, 24, 11, 0);
        BidTransaction tx = new BidTransaction("tx-1", auctionId, "bidder-1", 175, bidTime);

        visualizer.onNewBid(auction(auctionId), tx);

        assertEquals(1, visualizer.getPoints(auctionId).size());
        BidHistoryVisualizer.DataPoint point = visualizer.getPoints(auctionId).get(0);
        assertEquals(bidTime, point.getTimestamp());
        assertEquals(175, point.getPrice(), 0.01);
        assertEquals("bidder-1", point.getBidderId());

        visualizer.evict(auctionId);
    }

    @Test
    @DisplayName("onBidUpdated does not duplicate points")
    void onBidUpdatedDoesNotDuplicatePoints() {
        String auctionId = "chart-auction-update";
        BidHistoryVisualizer visualizer = BidHistoryVisualizer.getInstance();
        visualizer.evict(auctionId);
        visualizer.addPoint(auctionId, 100, "bidder-1", LocalDateTime.now());

        visualizer.onBidUpdated(auctionId, 120, "bidder-2");

        assertEquals(1, visualizer.getPoints(auctionId).size());
        assertEquals(100, visualizer.getPoints(auctionId).get(0).getPrice(), 0.01);

        visualizer.evict(auctionId);
    }

    @Test
    @DisplayName("getPoints returns unmodifiable list")
    void getPointsReturnsUnmodifiableList() {
        String auctionId = "chart-auction-copy";
        BidHistoryVisualizer visualizer = BidHistoryVisualizer.getInstance();
        visualizer.evict(auctionId);
        visualizer.addPoint(auctionId, 100, "bidder-1", LocalDateTime.now());

        assertThrows(UnsupportedOperationException.class,
                () -> visualizer.getPoints(auctionId).clear());

        visualizer.evict(auctionId);
    }

    @Test
    @DisplayName("evict removes tracked auction data")
    void evictRemovesTrackedAuctionData() {
        String auctionId = "chart-auction-evict";
        BidHistoryVisualizer visualizer = BidHistoryVisualizer.getInstance();
        visualizer.evict(auctionId);
        visualizer.addPoint(auctionId, 100, "bidder-1", LocalDateTime.now());

        visualizer.evict(auctionId);

        assertTrue(visualizer.getPoints(auctionId).isEmpty());
        JSONObject json = new JSONObject(visualizer.getChartDataJson(auctionId));
        assertEquals(0, json.getInt("totalBids"));
        assertEquals(0, json.getDouble("currentPrice"), 0.01);
    }

    private static Auction auction(String auctionId) {
        Seller seller = new Seller("seller-1", "seller", "hash", "seller@test.com");
        Item item = new Electronics("item-1", "Phone", "New", 100, seller.getId());
        item.setSellerId(seller.getId());
        return new Auction(auctionId, item, seller, 100,
                LocalDateTime.now().minusMinutes(1),
                LocalDateTime.now().plusMinutes(1));
    }
}
