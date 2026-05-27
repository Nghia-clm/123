package com.auction.service;

import com.auction.dao.AuctionDAO;
import com.auction.dao.BidTransactionDAO;
import com.auction.dao.ItemDAO;
import com.auction.dao.UserDAO;
import com.auction.exception.AuctionClosedException;
import com.auction.exception.InvalidBidException;
import com.auction.model.Auction;
import com.auction.model.AuctionStatus;
import com.auction.model.BidTransaction;
import com.auction.model.item.Electronics;
import com.auction.model.item.Item;
import com.auction.model.user.Admin;
import com.auction.model.user.Bidder;
import com.auction.model.user.Seller;
import com.auction.model.user.User;
import com.auction.observer.BidObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AuctionService - business rules")
class AuctionServiceBusinessTest {

    private static final String AUCTION_ID = "auction-1";
    private static final String ITEM_ID = "item-1";
    private static final String SELLER_ID = "seller-1";
    private static final String BIDDER_ID = "bidder-1";
    private static final String OTHER_ID = "other-1";
    private static final String ADMIN_ID = "admin-1";

    private FakeAuctionDAO auctionDAO;
    private FakeBidTransactionDAO bidDAO;
    private FakeItemDAO itemDAO;
    private FakeUserDAO userDAO;
    private FakeAuctionCoordinator coordinator;
    private AuctionService service;
    private Item item;
    private Seller seller;
    private Bidder bidder;

    @BeforeEach
    void setUp() {
        seller = new Seller(SELLER_ID, "seller", "pass", "seller@test.com");
        bidder = new Bidder(BIDDER_ID, "alice", "pass", "alice@test.com");
        Bidder other = new Bidder(OTHER_ID, "bob", "pass", "bob@test.com");
        Admin admin = new Admin(ADMIN_ID, "admin", "pass", "admin@test.com");

        item = new Electronics(ITEM_ID, "Phone", "New", 100, SELLER_ID);
        item.setSellerId(SELLER_ID);

        auctionDAO = new FakeAuctionDAO();
        bidDAO = new FakeBidTransactionDAO();
        itemDAO = new FakeItemDAO();
        userDAO = new FakeUserDAO();
        coordinator = new FakeAuctionCoordinator();

        itemDAO.items.put(ITEM_ID, item);
        userDAO.users.put(SELLER_ID, seller);
        userDAO.users.put(BIDDER_ID, bidder);
        userDAO.users.put(OTHER_ID, other);
        userDAO.users.put(ADMIN_ID, admin);

        service = new AuctionService(
                auctionDAO,
                bidDAO,
                itemDAO,
                userDAO,
                coordinator,
                AuctionServiceBusinessTest::fakeConnection,
                false
        );
    }

    @Test
    @DisplayName("placeBid rejects bid lower than or equal current price")
    void placeBidRejectsLowBid() {
        auctionDAO.save(runningAuction(150, bidder));

        assertThrows(InvalidBidException.class,
                () -> service.placeBid(AUCTION_ID, OTHER_ID, 150));
        assertEquals(0, bidDAO.inserted.size());
    }

    @Test
    @DisplayName("placeBid rejects seller bidding on own auction")
    void placeBidRejectsSellerSelfBid() {
        auctionDAO.save(runningAuction(100, null));

        assertThrows(InvalidBidException.class,
                () -> service.placeBid(AUCTION_ID, SELLER_ID, 120));
    }

    @Test
    @DisplayName("placeBid rejects banned bidder")
    void placeBidRejectsBannedBidder() {
        bidder.setBanned(true);
        auctionDAO.save(runningAuction(100, null));

        assertThrows(IllegalStateException.class,
                () -> service.placeBid(AUCTION_ID, BIDDER_ID, 120));
    }

    @Test
    @DisplayName("placeBid rejects finished and canceled auctions")
    void placeBidRejectsClosedAuctions() {
        Auction finished = runningAuction(100, null);
        finished.setStatus(AuctionStatus.FINISHED);
        auctionDAO.save(finished);
        assertThrows(AuctionClosedException.class,
                () -> service.placeBid(AUCTION_ID, BIDDER_ID, 120));

        Auction canceled = runningAuction(100, null);
        canceled.setStatus(AuctionStatus.CANCELED);
        auctionDAO.save(canceled);
        assertThrows(AuctionClosedException.class,
                () -> service.placeBid(AUCTION_ID, BIDDER_ID, 120));
    }

    @Test
    @DisplayName("placeBid rejects auction before start time")
    void placeBidRejectsBeforeStartTime() {
        Auction auction = new Auction(
                AUCTION_ID, item, seller, 100,
                LocalDateTime.now().plusMinutes(5),
                LocalDateTime.now().plusHours(1));
        auctionDAO.save(auction);

        assertThrows(AuctionClosedException.class,
                () -> service.placeBid(AUCTION_ID, BIDDER_ID, 120));
    }

    @Test
    @DisplayName("first valid bid changes OPEN auction to RUNNING")
    void firstValidBidStartsAuction() throws Exception {
        Auction auction = new Auction(
                AUCTION_ID, item, seller, 100,
                LocalDateTime.now().minusMinutes(1),
                LocalDateTime.now().plusHours(1));
        auctionDAO.save(auction);

        BidTransaction tx = service.placeBid(AUCTION_ID, BIDDER_ID, 120);

        Auction stored = auctionDAO.findById(AUCTION_ID);
        assertEquals(120, stored.getCurrentPrice(), 0.01);
        assertEquals(BIDDER_ID, stored.getCurrentWinnerId());
        assertEquals(AuctionStatus.RUNNING, stored.getStatus());
        assertEquals(tx, bidDAO.inserted.get(0));
        assertTrue(coordinator.broadcasts.stream().anyMatch(msg -> msg.contains("\"NEW_BID\"")));
    }

    @Test
    @DisplayName("successful bid notifies registered observers")
    void successfulBidNotifiesObservers() throws Exception {
        auctionDAO.save(runningAuction(100, null));
        RecordingObserver observer = new RecordingObserver();
        service.addObserver(observer);

        BidTransaction tx = service.placeBid(AUCTION_ID, BIDDER_ID, 120);

        assertSame(tx, observer.lastTransaction);
        assertEquals(AUCTION_ID, observer.lastAuction.getId());
        assertEquals(120, observer.updatedPrice, 0.01);
        assertEquals(BIDDER_ID, observer.updatedWinnerId);
    }

    @Test
    @DisplayName("createAuction validates item, owner, price and time")
    void createAuctionValidation() {
        assertThrows(IllegalArgumentException.class,
                () -> service.createAuction("missing", SELLER_ID, 100, future(1), future(2)));
        assertThrows(IllegalArgumentException.class,
                () -> service.createAuction(ITEM_ID, SELLER_ID, 0, future(1), future(2)));
        assertThrows(IllegalArgumentException.class,
                () -> service.createAuction(ITEM_ID, SELLER_ID, 100, future(2), future(1)));
        assertThrows(IllegalStateException.class,
                () -> service.createAuction(ITEM_ID, OTHER_ID, 100, future(1), future(2)));
    }

    @Test
    @DisplayName("createAuction allows owner and stores auction")
    void createAuctionSuccess() {
        Auction created = service.createAuction(ITEM_ID, SELLER_ID, 100, future(1), future(2));

        assertNotNull(created.getId());
        assertEquals(1, auctionDAO.inserted.size());
        assertSame(created, coordinator.added.get(0));
    }

    @Test
    @DisplayName("cancelAuction rejects finished auction that already has a winner")
    void cancelAuctionRejectsFinishedWithWinner() {
        Auction auction = runningAuction(150, bidder);
        auction.setStatus(AuctionStatus.FINISHED);
        auctionDAO.save(auction);

        assertThrows(IllegalStateException.class,
                () -> service.cancelAuction(AUCTION_ID, SELLER_ID));
    }

    @Test
    @DisplayName("cancelAuction overload cancels auction without requester")
    void cancelAuctionWithoutRequesterCancelsAuction() {
        auctionDAO.save(runningAuction(100, null));

        service.cancelAuction(AUCTION_ID);

        assertEquals(AuctionStatus.CANCELED, auctionDAO.findById(AUCTION_ID).getStatus());
    }

    @Test
    @DisplayName("cancelAuction allows admin requester")
    void cancelAuctionAllowsAdminRequester() {
        auctionDAO.save(runningAuction(100, null));

        service.cancelAuction(AUCTION_ID, ADMIN_ID);

        assertEquals(AuctionStatus.CANCELED, auctionDAO.findById(AUCTION_ID).getStatus());
    }

    @Test
    @DisplayName("closeAuction is idempotent for FINISHED auction")
    void closeAuctionFinishedIsIdempotent() {
        Auction auction = runningAuction(150, bidder);
        auction.setStatus(AuctionStatus.FINISHED);
        auctionDAO.save(auction);

        service.closeAuction(AUCTION_ID);

        assertEquals(0, auctionDAO.statusUpdates);
    }

    @Test
    @DisplayName("closeAuction rejects missing auction")
    void closeAuctionRejectsMissingAuction() {
        assertThrows(IllegalArgumentException.class,
                () -> service.closeAuction("missing-auction"));
    }

    @Test
    @DisplayName("getAllAuctions syncs expired auctions")
    void getAllAuctionsSyncsExpiredAuctions() {
        Auction expired = new Auction(
                AUCTION_ID, item, seller, 100,
                LocalDateTime.now().minusHours(2),
                LocalDateTime.now().minusHours(1));
        auctionDAO.save(expired);

        List<Auction> auctions = service.getAllAuctions();

        assertEquals(1, auctions.size());
        assertEquals(AuctionStatus.CANCELED, auctions.get(0).getStatus());
    }

    @Test
    @DisplayName("getRunningAuctions returns only running after sync")
    void getRunningAuctionsReturnsOnlyRunningAuctions() {
        auctionDAO.save(runningAuction(100, null));
        Auction openFuture = new Auction(
                "future-auction", item, seller, 100,
                LocalDateTime.now().plusHours(1),
                LocalDateTime.now().plusHours(2));
        auctionDAO.save(openFuture);

        List<Auction> auctions = service.getRunningAuctions();

        assertEquals(1, auctions.size());
        assertEquals(AUCTION_ID, auctions.get(0).getId());
    }

    @Test
    @DisplayName("getAuctionsBySeller delegates and syncs")
    void getAuctionsBySellerReturnsSellerAuctions() {
        auctionDAO.save(runningAuction(100, null));

        List<Auction> auctions = service.getAuctionsBySeller(SELLER_ID);

        assertEquals(1, auctions.size());
        assertEquals(SELLER_ID, auctions.get(0).getSeller().getId());
    }

    @Test
    @DisplayName("getBidHistory returns DAO transactions")
    void getBidHistoryReturnsDaoTransactions() {
        BidTransaction tx = new BidTransaction("tx-1", AUCTION_ID, BIDDER_ID, 120, LocalDateTime.now());
        bidDAO.history.add(tx);

        List<BidTransaction> history = service.getBidHistory(AUCTION_ID);

        assertEquals(List.of(tx), history);
    }

    @Test
    @DisplayName("markAuctionPaid only allows winner or admin")
    void markPaidRequiresWinnerOrAdmin() {
        Auction auction = runningAuction(150, bidder);
        auction.setStatus(AuctionStatus.FINISHED);
        auctionDAO.save(auction);

        assertThrows(IllegalStateException.class,
                () -> service.markAuctionPaid(AUCTION_ID, OTHER_ID));

        service.markAuctionPaid(AUCTION_ID, BIDDER_ID);
        assertEquals(AuctionStatus.PAID, auctionDAO.findById(AUCTION_ID).getStatus());

        auction.setStatus(AuctionStatus.FINISHED);
        auctionDAO.save(auction);
        service.markAuctionPaid(AUCTION_ID, ADMIN_ID);
        assertEquals(AuctionStatus.PAID, auctionDAO.findById(AUCTION_ID).getStatus());
    }

    @Test
    @DisplayName("read sync maps expired OPEN auction without winner to CANCELED")
    void syncExpiredOpenWithoutWinnerToCanceled() {
        Auction expired = new Auction(
                AUCTION_ID, item, seller, 100,
                LocalDateTime.now().minusHours(2),
                LocalDateTime.now().minusHours(1));
        auctionDAO.save(expired);

        Auction synced = service.getAuctionById(AUCTION_ID);

        assertEquals(AuctionStatus.CANCELED, synced.getStatus());
        assertEquals(0, auctionDAO.statusUpdates);
    }

    @Test
    @DisplayName("read sync maps expired OPEN auction with winner to FINISHED")
    void syncExpiredOpenWithWinnerToFinished() {
        Auction expired = new Auction(
                AUCTION_ID, item, seller, 100,
                LocalDateTime.now().minusHours(2),
                LocalDateTime.now().minusHours(1));
        expired.setWinner(bidder);
        auctionDAO.save(expired);

        Auction synced = service.getAuctionById(AUCTION_ID);

        assertEquals(AuctionStatus.FINISHED, synced.getStatus());
        assertEquals(BIDDER_ID, synced.getCurrentWinnerId());
        assertEquals(0, auctionDAO.statusUpdates);
    }

    private Auction runningAuction(double currentPrice, User winner) {
        Auction auction = new Auction(
                AUCTION_ID, item, seller, 100,
                LocalDateTime.now().minusMinutes(1),
                LocalDateTime.now().plusHours(1));
        auction.setStatus(AuctionStatus.RUNNING);
        auction.setCurrentPrice(currentPrice);
        auction.setWinner(winner);
        return auction;
    }

    private static String future(int hours) {
        return LocalDateTime.now().plusHours(hours).toString();
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

    private static Auction copyAuction(Auction source) {
        Auction copy = new Auction(source.getId(), source.getItem(), source.getSeller(),
                source.getStartingPrice(), source.getStartTime(), source.getEndTime());
        copy.setCurrentPrice(source.getCurrentPrice());
        copy.setStatus(source.getStatus());
        copy.setWinner(source.getWinner());
        copy.setCurrentWinnerId(source.getCurrentWinnerId());
        return copy;
    }

    private static final class FakeAuctionDAO extends AuctionDAO {
        private final Map<String, Auction> auctions = new HashMap<>();
        private final List<Auction> inserted = new ArrayList<>();
        private int statusUpdates;

        void save(Auction auction) {
            auctions.put(auction.getId(), copyAuction(auction));
        }

        @Override
        public synchronized boolean insert(Auction auction) {
            inserted.add(auction);
            save(auction);
            return true;
        }

        @Override
        public synchronized Auction findById(String auctionId) {
            Auction auction = auctions.get(auctionId);
            return auction == null ? null : copyAuction(auction);
        }

        @Override
        public synchronized Auction findById(Connection conn, String auctionId) {
            return findById(auctionId);
        }

        @Override
        public synchronized List<Auction> findAll() {
            return auctions.values().stream()
                    .map(AuctionServiceBusinessTest::copyAuction)
                    .toList();
        }

        @Override
        public synchronized List<Auction> findBySeller(String sellerId) {
            return auctions.values().stream()
                    .filter(auction -> auction.getSeller() != null
                            && sellerId.equals(auction.getSeller().getId()))
                    .map(AuctionServiceBusinessTest::copyAuction)
                    .toList();
        }

        @Override
        public synchronized boolean updateCurrentPrice(Connection conn, String auctionId, double newPrice, String winnerId) {
            Auction auction = auctions.get(auctionId);
            if (auction == null) return false;
            auction.setCurrentPrice(newPrice);
            auction.setCurrentWinnerId(winnerId);
            return true;
        }

        @Override
        public synchronized boolean updateStatus(String auctionId, AuctionStatus status) {
            return updateStatus(null, auctionId, status);
        }

        @Override
        public synchronized boolean updateStatus(Connection conn, String auctionId, AuctionStatus status) {
            Auction auction = auctions.get(auctionId);
            if (auction == null) return false;
            auction.setStatus(status);
            statusUpdates++;
            return true;
        }
    }

    private static final class FakeBidTransactionDAO extends BidTransactionDAO {
        private final List<BidTransaction> inserted = new ArrayList<>();
        private final List<BidTransaction> history = new ArrayList<>();

        @Override
        public synchronized boolean insert(Connection conn, BidTransaction tx) {
            inserted.add(tx);
            return true;
        }

        @Override
        public synchronized List<BidTransaction> findByAuction(String auctionId) {
            return history.stream()
                    .filter(tx -> auctionId.equals(tx.getAuctionId()))
                    .toList();
        }
    }

    private static final class FakeItemDAO extends ItemDAO {
        private final Map<String, Item> items = new HashMap<>();

        @Override
        public Item findById(String itemId) {
            return items.get(itemId);
        }
    }

    private static final class FakeUserDAO extends UserDAO {
        private final Map<String, User> users = new HashMap<>();

        @Override
        public User findById(String userId) {
            return users.get(userId);
        }

        @Override
        public User findById(Connection conn, String userId) {
            return users.get(userId);
        }
    }

    private static final class FakeAuctionCoordinator implements AuctionService.AuctionCoordinator {
        private final Map<String, ReentrantReadWriteLock> locks = new ConcurrentHashMap<>();
        private final List<Auction> added = new ArrayList<>();
        private final List<String> broadcasts = new ArrayList<>();

        @Override
        public ReentrantReadWriteLock.WriteLock getWriteLock(String auctionId) {
            return locks.computeIfAbsent(auctionId, ignored -> new ReentrantReadWriteLock()).writeLock();
        }

        @Override
        public void addAuction(Auction auction) {
            added.add(auction);
        }

        @Override
        public void broadcastToRoom(String auctionId, String message) {
            broadcasts.add(message);
        }
    }

    private static final class RecordingObserver implements BidObserver {
        private Auction lastAuction;
        private BidTransaction lastTransaction;
        private double updatedPrice;
        private String updatedWinnerId;

        @Override
        public void onBidUpdated(String auctionId, double newPrice, String bidderId) {
            this.updatedPrice = newPrice;
            this.updatedWinnerId = bidderId;
        }

        @Override
        public void onNewBid(Auction auction, BidTransaction tx) {
            this.lastAuction = auction;
            this.lastTransaction = tx;
        }
    }
}
