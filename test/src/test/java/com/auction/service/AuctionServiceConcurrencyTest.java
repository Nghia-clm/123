package com.auction.service;

import com.auction.dao.AuctionDAO;
import com.auction.dao.BidTransactionDAO;
import com.auction.dao.ItemDAO;
import com.auction.dao.UserDAO;
import com.auction.exception.AuctionClosedException;
import com.auction.model.Auction;
import com.auction.model.AuctionStatus;
import com.auction.model.BidTransaction;
import com.auction.model.item.Electronics;
import com.auction.model.item.Item;
import com.auction.model.user.Bidder;
import com.auction.model.user.Seller;
import com.auction.model.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("AuctionService - concurrent bidding")
class AuctionServiceConcurrencyTest {

    @Test
    @DisplayName("placeBid serializes concurrent bids and keeps the highest valid winner")
    void placeBidSerializesConcurrentBids() throws Exception {
        String auctionId = "concurrent-auction";
        Seller seller = new Seller("seller-1", "seller", "pass", "seller@test.com");
        Item item = new Electronics("item-1", "Phone", "New", 100, seller.getId());
        item.setSellerId(seller.getId());

        Auction auction = new Auction(
                auctionId,
                item,
                seller,
                100,
                LocalDateTime.now().minusMinutes(1),
                LocalDateTime.now().plusMinutes(10)
        );
        auction.setStatus(AuctionStatus.RUNNING);

        Map<String, User> users = new ConcurrentHashMap<>();
        users.put(seller.getId(), seller);
        for (int amount = 101; amount <= 112; amount++) {
            String bidderId = "bidder-" + amount;
            users.put(bidderId, new Bidder(bidderId, bidderId, "pass", bidderId + "@test.com"));
        }

        FakeAuctionDAO auctionDAO = new FakeAuctionDAO(auction, users);
        FakeBidTransactionDAO bidTransactionDAO = new FakeBidTransactionDAO();
        AuctionService service = new AuctionService(
                auctionDAO,
                bidTransactionDAO,
                new ItemDAO(),
                new FakeUserDAO(users),
                new FakeAuctionCoordinator(),
                AuctionServiceConcurrencyTest::fakeConnection,
                false
        );

        int threadCount = 12;
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);

        for (int amount = 101; amount <= 112; amount++) {
            int bidAmount = amount;
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    service.placeBid(auctionId, "bidder-" + bidAmount, bidAmount);
                } catch (Exception ignored) {
                    // Lower bids may become invalid if a higher concurrent bid wins first.
                }
            });
        }

        assertTrue(ready.await(2, TimeUnit.SECONDS));
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));

        Auction stored = auctionDAO.currentAuction();
        assertEquals(112, stored.getCurrentPrice(), 0.01);
        assertEquals("bidder-112", stored.getCurrentWinnerId());
        assertTrue(bidTransactionDAO.insertedCount() >= 1);
        assertEquals(112, bidTransactionDAO.highestAmount(), 0.01);
        assertTrue(isStrictlyIncreasing(bidTransactionDAO.insertedAmounts()));
    }

    @Test
    @DisplayName("placeBid allows only one winner when concurrent bidders use the same amount")
    void placeBidAllowsOnlyOneWinnerForSameConcurrentAmount() throws Exception {
        String auctionId = "same-amount-auction";
        Seller seller = new Seller("seller-1", "seller", "pass", "seller@test.com");
        Item item = new Electronics("item-1", "Phone", "New", 100, seller.getId());
        item.setSellerId(seller.getId());

        Auction auction = new Auction(
                auctionId,
                item,
                seller,
                100,
                LocalDateTime.now().minusMinutes(1),
                LocalDateTime.now().plusMinutes(10)
        );
        auction.setStatus(AuctionStatus.RUNNING);

        Map<String, User> users = new ConcurrentHashMap<>();
        users.put(seller.getId(), seller);
        users.put("bidder-a", new Bidder("bidder-a", "alice", "pass", "alice@test.com"));
        users.put("bidder-b", new Bidder("bidder-b", "bob", "pass", "bob@test.com"));

        FakeAuctionDAO auctionDAO = new FakeAuctionDAO(auction, users);
        FakeBidTransactionDAO bidTransactionDAO = new FakeBidTransactionDAO();
        AuctionService service = new AuctionService(
                auctionDAO,
                bidTransactionDAO,
                new ItemDAO(),
                new FakeUserDAO(users),
                new FakeAuctionCoordinator(),
                AuctionServiceConcurrencyTest::fakeConnection,
                false
        );

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        for (String bidderId : List.of("bidder-a", "bidder-b")) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    service.placeBid(auctionId, bidderId, 120);
                } catch (Exception ignored) {
                    // One bid must lose because the equal amount is no longer greater than current price.
                }
            });
        }

        assertTrue(ready.await(2, TimeUnit.SECONDS));
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));

        Auction stored = auctionDAO.currentAuction();
        assertEquals(120, stored.getCurrentPrice(), 0.01);
        assertTrue(List.of("bidder-a", "bidder-b").contains(stored.getCurrentWinnerId()));
        assertEquals(1, bidTransactionDAO.insertedCount());
    }

    @Test
    @DisplayName("placeBid rejects auction that expires before the bid is processed")
    void placeBidRejectsExpiredAuctionAtProcessingTime() {
        String auctionId = "expired-auction";
        Seller seller = new Seller("seller-1", "seller", "pass", "seller@test.com");
        Bidder bidder = new Bidder("bidder-1", "alice", "pass", "alice@test.com");
        Item item = new Electronics("item-1", "Phone", "New", 100, seller.getId());
        item.setSellerId(seller.getId());

        Auction auction = new Auction(
                auctionId,
                item,
                seller,
                100,
                LocalDateTime.now().minusMinutes(10),
                LocalDateTime.now().minusNanos(1)
        );
        auction.setStatus(AuctionStatus.RUNNING);

        Map<String, User> users = new ConcurrentHashMap<>();
        users.put(seller.getId(), seller);
        users.put(bidder.getId(), bidder);

        FakeAuctionDAO auctionDAO = new FakeAuctionDAO(auction, users);
        FakeBidTransactionDAO bidTransactionDAO = new FakeBidTransactionDAO();
        AuctionService service = new AuctionService(
                auctionDAO,
                bidTransactionDAO,
                new ItemDAO(),
                new FakeUserDAO(users),
                new FakeAuctionCoordinator(),
                AuctionServiceConcurrencyTest::fakeConnection,
                false
        );

        assertThrows(AuctionClosedException.class, () -> service.placeBid(auctionId, bidder.getId(), 120));
        assertEquals(0, bidTransactionDAO.insertedCount());
        assertEquals(AuctionStatus.CANCELED, auctionDAO.currentAuction().getStatus());
    }

    private static Connection fakeConnection() {
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "setAutoCommit", "commit", "rollback", "close" -> null;
                    case "isClosed" -> false;
                    case "getAutoCommit" -> false;
                    default -> defaultValue(method.getReturnType());
                }
        );
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        if (type == double.class) return 0D;
        if (type == char.class) return '\0';
        return null;
    }

    private static boolean isStrictlyIncreasing(List<Double> amounts) {
        for (int i = 1; i < amounts.size(); i++) {
            if (amounts.get(i) <= amounts.get(i - 1)) return false;
        }
        return true;
    }

    private static final class FakeAuctionDAO extends AuctionDAO {
        private final Map<String, User> users;
        private Auction auction;

        private FakeAuctionDAO(Auction auction, Map<String, User> users) {
            this.auction = copyAuction(auction);
            this.users = users;
        }

        @Override
        public synchronized Auction findById(Connection conn, String auctionId) {
            if (!auction.getId().equals(auctionId)) return null;
            return copyAuction(auction);
        }

        @Override
        public synchronized boolean updateCurrentPrice(Connection conn, String auctionId, double newPrice, String winnerId) {
            if (!auction.getId().equals(auctionId)) return false;
            auction.setCurrentPrice(newPrice);
            auction.setWinner(users.get(winnerId));
            return true;
        }

        @Override
        public synchronized boolean updateStatus(Connection conn, String auctionId, AuctionStatus status) {
            if (!auction.getId().equals(auctionId)) return false;
            auction.setStatus(status);
            return true;
        }

        synchronized Auction currentAuction() {
            return copyAuction(auction);
        }

        private static Auction copyAuction(Auction source) {
            Auction copy = new Auction(
                    source.getId(),
                    source.getItem(),
                    source.getSeller(),
                    source.getStartingPrice(),
                    source.getStartTime(),
                    source.getEndTime()
            );
            copy.setCurrentPrice(source.getCurrentPrice());
            copy.setStatus(source.getStatus());
            copy.setWinner(source.getWinner());
            copy.setCurrentWinnerId(source.getCurrentWinnerId());
            return copy;
        }
    }

    private static final class FakeBidTransactionDAO extends BidTransactionDAO {
        private final List<BidTransaction> inserted = new ArrayList<>();

        @Override
        public synchronized boolean insert(Connection conn, BidTransaction tx) throws SQLException {
            inserted.add(tx);
            return true;
        }

        synchronized int insertedCount() {
            return inserted.size();
        }

        synchronized double highestAmount() {
            return inserted.stream()
                    .mapToDouble(BidTransaction::getBidAmount)
                    .max()
                    .orElse(0);
        }

        synchronized List<Double> insertedAmounts() {
            return inserted.stream()
                    .map(BidTransaction::getBidAmount)
                    .toList();
        }
    }

    private static final class FakeUserDAO extends UserDAO {
        private final Map<String, User> users;

        private FakeUserDAO(Map<String, User> users) {
            this.users = users;
        }

        @Override
        public User findById(Connection conn, String userId) {
            return users.get(userId);
        }
    }

    private static final class FakeAuctionCoordinator implements AuctionService.AuctionCoordinator {
        private final Map<String, ReentrantReadWriteLock> locks = new ConcurrentHashMap<>();

        @Override
        public ReentrantReadWriteLock.WriteLock getWriteLock(String auctionId) {
            return locks.computeIfAbsent(auctionId, ignored -> new ReentrantReadWriteLock()).writeLock();
        }

        @Override
        public void addAuction(Auction auction) {
        }

        @Override
        public void broadcastToRoom(String auctionId, String message) {
        }
    }
}
