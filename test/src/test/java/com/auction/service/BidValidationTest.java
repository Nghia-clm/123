package com.auction.service;

import com.auction.dao.AuctionDAO;
import com.auction.dao.BidTransactionDAO;
import com.auction.dao.ItemDAO;
import com.auction.dao.UserDAO;
import com.auction.exception.InvalidBidException;
import com.auction.model.Auction;
import com.auction.model.AuctionStatus;
import com.auction.model.BidTransaction;
import com.auction.model.item.Electronics;
import com.auction.model.item.Item;
import com.auction.model.user.Bidder;
import com.auction.model.user.Seller;
import com.auction.model.user.User;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * BidValidationTest - Kiểm thử dữ liệu bid và rule validate bid cốt lõi.
 */
@DisplayName("Bid validation")
class BidValidationTest {

    @Test
    @DisplayName("BidTransaction có đầy đủ thông tin sau khi tạo")
    void bidTransactionHasCorrectData() {
        LocalDateTime timestamp = LocalDateTime.now();
        BidTransaction tx = new BidTransaction("tx-1", "auction-1", "bidder-1", 1_000_000, timestamp);

        assertEquals("tx-1", tx.getId());
        assertEquals("auction-1", tx.getAuctionId());
        assertEquals("bidder-1", tx.getBidderId());
        assertEquals(1_000_000, tx.getAmount(), 0.01);
        assertEquals(1_000_000, tx.getBidAmount(), 0.01);
        assertEquals(timestamp, tx.getBidTime());
        assertEquals(timestamp, tx.getTimestamp());
        assertFalse(tx.isAutoBid());
    }

    @Test
    @DisplayName("BidTransaction isAutoBid = true khi set")
    void bidTransactionAutoBidFlag() {
        BidTransaction tx = new BidTransaction("tx-2", "auction-1", "bidder-1", 1_000_000,
            LocalDateTime.now());

        tx.setAutoBid(true);

        assertTrue(tx.isAutoBid());
    }

    @Test
    @DisplayName("BidTransaction constructor rỗng gán id, thời gian và autoBid mặc định")
    void bidTransactionNoArgConstructorSetsDefaults() {
        BidTransaction tx = new BidTransaction();

        assertNotNull(tx.getId());
        assertNotNull(tx.getTimestamp());
        assertFalse(tx.isAutoBid());
    }

    @Test
    @DisplayName("BidTransaction constructor tối giản gán dữ liệu và autoBid mặc định")
    void bidTransactionMinimalConstructorSetsDataAndDefaults() {
        BidTransaction tx = new BidTransaction("auction-1", "bidder-1", 1_000_000);

        assertNotNull(tx.getId());
        assertEquals("auction-1", tx.getAuctionId());
        assertEquals("bidder-1", tx.getBidderId());
        assertEquals(1_000_000, tx.getBidAmount(), 0.01);
        assertNotNull(tx.getTimestamp());
        assertFalse(tx.isAutoBid());
    }

    @Test
    @DisplayName("setAmount cập nhật amount và bidAmount")
    void setAmountUpdatesAliases() {
        BidTransaction tx = new BidTransaction("tx-3", "auction-1", "bidder-1", 1_000_000,
            LocalDateTime.now());

        tx.setAmount(1_500_000);

        assertEquals(1_500_000, tx.getAmount(), 0.01);
        assertEquals(1_500_000, tx.getBidAmount(), 0.01);
    }

    @Test
    @DisplayName("AuctionService từ chối bid bằng hoặc thấp hơn giá hiện tại")
    void bidMustBeGreaterThanCurrentPrice() {
        TestContext ctx = new TestContext();
        ctx.auctionDAO.auctions.put("auction-1", ctx.auction(1_000_000));

        assertThrows(InvalidBidException.class,
                () -> ctx.service.placeBid("auction-1", "bidder-1", 1_000_000));
        assertThrows(InvalidBidException.class,
                () -> ctx.service.placeBid("auction-1", "bidder-1", 999_999));
    }

    @Test
    @DisplayName("AuctionService từ chối bid <= 0")
    void bidMustBePositive() {
        TestContext ctx = new TestContext();
        ctx.auctionDAO.auctions.put("auction-1", ctx.auction(100));

        assertThrows(InvalidBidException.class,
                () -> ctx.service.placeBid("auction-1", "bidder-1", 0));
        assertThrows(InvalidBidException.class,
                () -> ctx.service.placeBid("auction-1", "bidder-1", -1));
    }

    private static final class TestContext {
        private final Seller seller = new Seller("seller-1", "seller", "pass", "seller@test.com");
        private final Bidder bidder = new Bidder("bidder-1", "alice", "pass", "alice@test.com");
        private final Item item = new Electronics("item-1", "Phone", "desc", 100, seller.getId());
        private final FakeAuctionDAO auctionDAO = new FakeAuctionDAO();
        private final FakeUserDAO userDAO = new FakeUserDAO();
        private final AuctionService service;

        private TestContext() {
            item.setSellerId(seller.getId());
            userDAO.users.put(seller.getId(), seller);
            userDAO.users.put(bidder.getId(), bidder);
            service = new AuctionService(
                    auctionDAO,
                    new BidTransactionDAO(),
                    new ItemDAO(),
                    userDAO,
                    new FakeAuctionCoordinator(),
                    BidValidationTest::fakeConnection,
                    false
            );
        }

        private Auction auction(double currentPrice) {
            Auction auction = new Auction("auction-1", item, seller, 100,
                    LocalDateTime.now().minusMinutes(1),
                    LocalDateTime.now().plusHours(1));
            auction.setStatus(AuctionStatus.RUNNING);
            auction.setCurrentPrice(currentPrice);
            return auction;
        }
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
                });
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == double.class) return 0D;
        return null;
    }

    private static final class FakeAuctionDAO extends AuctionDAO {
        private final Map<String, Auction> auctions = new HashMap<>();

        @Override
        public Auction findById(Connection conn, String auctionId) {
            return auctions.get(auctionId);
        }
    }

    private static final class FakeUserDAO extends UserDAO {
        private final Map<String, User> users = new HashMap<>();

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
