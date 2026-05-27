package com.auction.service;

import com.auction.AntiSnipingTimer;
import com.auction.AutoBidder;
import com.auction.BidHistoryVisualizer;
import com.auction.dao.AuctionDAO;
import com.auction.dao.BidTransactionDAO;
import com.auction.dao.DatabaseConnection;
import com.auction.dao.ItemDAO;
import com.auction.dao.UserDAO;
import com.auction.exception.AuctionClosedException;
import com.auction.exception.InvalidBidException;
import com.auction.exception.UserNotFoundException;
import com.auction.manager.AuctionManager;
import com.auction.model.Auction;
import com.auction.model.AuctionStatus;
import com.auction.model.BidTransaction;
import com.auction.model.item.Item;
import com.auction.model.user.User;
import com.auction.observer.BidObserver;
import org.json.JSONObject;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * AuctionService - Xử lý logic nghiệp vụ đấu giá.
 * Thread-safe: dùng ReentrantReadWriteLock per auction từ AuctionManager.
 */
public class AuctionService {

    private static final Logger LOGGER = Logger.getLogger(AuctionService.class.getName());
    private static final int EMPTY_AUCTION_CANCEL_DELAY_SECONDS = 3;

    // Cấu hình anti-sniping được xử lý bởi AntiSnipingTimer (Observer Pattern)
    // Xem: advanced/src/main/java/com/auction/AntiSnipingTimer.java

    @FunctionalInterface
    interface ConnectionProvider {
        Connection getConnection() throws SQLException;
    }

    interface AuctionCoordinator {
        ReentrantReadWriteLock.WriteLock getWriteLock(String auctionId);
        void addAuction(Auction auction);
        default void removeAuction(String auctionId) {}
        void broadcastToRoom(String auctionId, String message);
    }

    private static final class AuctionManagerCoordinator implements AuctionCoordinator {
        private final AuctionManager auctionManager;

        private AuctionManagerCoordinator(AuctionManager auctionManager) {
            this.auctionManager = auctionManager;
        }

        @Override
        public ReentrantReadWriteLock.WriteLock getWriteLock(String auctionId) {
            return auctionManager.getWriteLock(auctionId);
        }

        @Override
        public void addAuction(Auction auction) {
            auctionManager.addAuction(auction);
        }

        @Override
        public void removeAuction(String auctionId) {
            auctionManager.removeAuction(auctionId);
        }

        @Override
        public void broadcastToRoom(String auctionId, String message) {
            auctionManager.broadcastToRoom(auctionId, message);
        }
    }

    private final AuctionDAO         auctionDAO;
    private final BidTransactionDAO  bidTransactionDAO;
    private final ItemDAO            itemDAO;
    private final UserDAO            userDAO;
    private final AuctionCoordinator auctionManager;
    private final ConnectionProvider connectionProvider;

    private static final List<BidObserver> DEFAULT_OBSERVERS = new CopyOnWriteArrayList<>();
    private static volatile boolean defaultObserversRegistered = false;
    private static volatile boolean registeringDefaultObservers = false;

    // Danh sách observer riêng cho service instance này (nếu cần mở rộng)
    private final List<BidObserver> observers = new CopyOnWriteArrayList<>();

    public AuctionService() {
        this(new AuctionDAO(),
                new BidTransactionDAO(),
                new ItemDAO(),
                new UserDAO(),
                new AuctionManagerCoordinator(AuctionManager.getInstance()),
                DatabaseConnection::getConnection,
                true);
    }

    AuctionService(AuctionDAO auctionDAO,
                   BidTransactionDAO bidTransactionDAO,
                   ItemDAO itemDAO,
                   UserDAO userDAO,
                   AuctionCoordinator auctionManager,
                   ConnectionProvider connectionProvider,
                   boolean registerDefaultObservers) {
        this.auctionDAO = auctionDAO;
        this.bidTransactionDAO = bidTransactionDAO;
        this.itemDAO = itemDAO;
        this.userDAO = userDAO;
        this.auctionManager = auctionManager;
        this.connectionProvider = connectionProvider;

        if (registerDefaultObservers) {
            registerDefaultObservers();
        }
    }

    private static synchronized void registerDefaultObservers() {
        if (defaultObserversRegistered || registeringDefaultObservers) return;

        registeringDefaultObservers = true;
        try {
            DEFAULT_OBSERVERS.add(new AntiSnipingTimer());
            DEFAULT_OBSERVERS.add(BidHistoryVisualizer.getInstance());
            DEFAULT_OBSERVERS.add(AutoBidder.getInstance());
            defaultObserversRegistered = true;
        } finally {
            registeringDefaultObservers = false;
        }
    }

    // ── OBSERVER ──────────────────────────────────────────────────────────

    public void addObserver(BidObserver observer) {
        observers.add(observer);
    }

    public void removeObserver(BidObserver observer) {
        observers.remove(observer);
    }

    private void notifyObservers(Auction auction, BidTransaction tx) {
        List<BidObserver> allObservers = new ArrayList<>(DEFAULT_OBSERVERS);
        allObservers.addAll(observers);

        for (BidObserver obs : allObservers) {
            try {
                obs.onNewBid(auction, tx);
                obs.onBidUpdated(auction.getId(), auction.getCurrentPrice(), auction.getCurrentWinnerId());
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Observer notification failed", e);
            }
        }
    }

    // ── CREATE AUCTION ────────────────────────────────────────────────────

    /**
     * Tạo phiên đấu giá mới.
     */
    public Auction createAuction(String itemId, String sellerId,
                                  double startingPrice,
                                  String startTimeStr, String endTimeStr) {
        Item item = itemDAO.findById(itemId);
        if (item == null) throw new IllegalArgumentException("Không tìm thấy sản phẩm: " + itemId);

        User seller = userDAO.findById(sellerId);
        if (seller == null) throw new IllegalArgumentException("Không tìm thấy người bán: " + sellerId);
        if (!"ADMIN".equals(seller.getRole())
                && (item.getSellerId() == null || !item.getSellerId().equals(sellerId))) {
            throw new IllegalStateException("Không thể tạo phiên đấu giá từ vật phẩm của người khác");
        }

        if (startingPrice <= 0) throw new IllegalArgumentException("Giá khởi điểm phải lớn hơn 0");
        if (!isWholeMoney(startingPrice)) throw new IllegalArgumentException("Giá khởi điểm phải là số nguyên");
        if (startingPrice < item.getStartingPrice()) {
            throw new IllegalArgumentException("Giá khởi điểm phiên không được thấp hơn giá khởi điểm sản phẩm");
        }

        LocalDateTime startTime = LocalDateTime.parse(startTimeStr);
        LocalDateTime endTime   = LocalDateTime.parse(endTimeStr);

        if (!endTime.isAfter(startTime)) {
            throw new IllegalArgumentException("Thời gian kết thúc phải sau thời gian bắt đầu");
        }

        String auctionId = UUID.randomUUID().toString();
        Auction auction = new Auction(auctionId, item, seller, startingPrice, startTime, endTime);

        for (Auction active : auctionDAO.findAll()) {
            if (active.getItem() == null
                    || !itemId.equals(active.getItem().getId())
                    || (active.getStatus() != AuctionStatus.OPEN
                            && active.getStatus() != AuctionStatus.RUNNING)) {
                continue;
            }
            auctionDAO.delete(active.getId());
            auctionManager.removeAuction(active.getId());
            AutoBidder.getInstance().clearAuction(active.getId());
        }

        auctionDAO.insert(auction);
        auctionManager.addAuction(auction);

        LOGGER.info("Auction created: " + auctionId + " | Item: " + item.getName());
        return auction;
    }

    // ── PLACE BID ─────────────────────────────────────────────────────────

    /**
     * Đặt giá - thread-safe với ReentrantWriteLock per auction.
     * Tránh: lost update, race condition, hai người cùng thắng.
     *
     * @throws AuctionClosedException nếu phiên đã đóng
     * @throws InvalidBidException    nếu giá không hợp lệ
     * @throws UserNotFoundException  nếu bidder không tồn tại
     */
    public BidTransaction placeBid(String auctionId, String bidderId, double bidAmount)
            throws AuctionClosedException, InvalidBidException, UserNotFoundException {
        return placeBid(auctionId, bidderId, bidAmount, false);
    }

    public BidTransaction placeBid(String auctionId, String bidderId, double bidAmount, boolean autoBid)
            throws AuctionClosedException, InvalidBidException, UserNotFoundException {

        // Lấy write lock của auction này (chỉ 1 thread xử lý bid tại một thời điểm)
        ReentrantReadWriteLock.WriteLock lock = auctionManager.getWriteLock(auctionId);
        Auction auction;
        BidTransaction tx;
        User bidder;

        lock.lock();

        try {
            try (Connection conn = connectionProvider.getConnection()) {
                try {
                    conn.setAutoCommit(false);

                    // 1. Load auction mới nhất từ DB (tránh stale data)
                    auction = auctionDAO.findById(conn, auctionId);
                    if (auction == null) throw new IllegalArgumentException("Không tìm thấy phiên đấu giá: " + auctionId);
                    auction = syncAuctionTimeState(conn, auction);

                    // 2. Kiểm tra trạng thái phiên
                    if (auction.getStatus() == AuctionStatus.FINISHED
                            || auction.getStatus() == AuctionStatus.CANCELED
                            || auction.getStatus() == AuctionStatus.PAID) {
                        throw new AuctionClosedException("Phiên đấu giá đã đóng: " + auctionId);
                    }

                    if (LocalDateTime.now().isBefore(auction.getStartTime())) {
                        throw new AuctionClosedException("Phiên đấu giá chưa đến thời gian bắt đầu");
                    }

                    // 3. Kiểm tra giá đấu hợp lệ
                    if (!isWholeMoney(bidAmount)) {
                        throw new InvalidBidException("Số tiền đặt giá phải là số nguyên");
                    }

                    if (bidAmount <= auction.getCurrentPrice()) {
                        throw new InvalidBidException(
                            "Bid amount " + bidAmount + " must be greater than current price " + auction.getCurrentPrice()
                        );
                    }

                    // 4. Kiểm tra bidder tồn tại và không bị ban
                    bidder = userDAO.findById(conn, bidderId);
                    if (bidder == null) throw new UserNotFoundException("Không tìm thấy người đấu giá: " + bidderId);
                    if (bidder.isBanned()) throw new IllegalStateException("Tài khoản người đấu giá đã bị khóa");

                    // 5. Không cho seller tự đấu giá sản phẩm của mình
                    if (auction.getSeller().getId().equals(bidderId)) {
                        throw new InvalidBidException("Người bán không thể tự đặt giá cho phiên của mình");
                    }

                    // 6. Cập nhật giá hiện tại
                    auction.setCurrentPrice(bidAmount);
                    auction.setWinner(bidder);
                    if (auction.getStatus() == AuctionStatus.OPEN) {
                        auction.setStatus(AuctionStatus.RUNNING);
                        auctionDAO.updateStatus(conn, auctionId, AuctionStatus.RUNNING);
                    }
                    auctionDAO.updateCurrentPrice(conn, auctionId, bidAmount, bidderId);

                    // 7. Lưu transaction
                    String txId = UUID.randomUUID().toString();
                    tx = new BidTransaction(txId, auctionId, bidderId, bidAmount, LocalDateTime.now());
                    tx.setAutoBid(autoBid);
                    bidTransactionDAO.insert(conn, tx);

                    conn.commit();
                } catch (SQLException | RuntimeException | AuctionClosedException
                         | InvalidBidException | UserNotFoundException e) {
                    rollbackQuietly(conn);
                    throw e;
                }
            } catch (SQLException e) {
                throw new RuntimeException("Lỗi cơ sở dữ liệu khi đặt giá", e);
            }

            // 8. Cập nhật cache trong AuctionManager
            auctionManager.addAuction(auction);
        } finally {
            lock.unlock();
        }

        // Broadcast sau khi release bid lock. Gửi network I/O trong critical section
        // sẽ làm mọi bid khác bị kẹt nếu một client chậm hoặc mất kết nối.
        broadcastBid(auction, tx, bidder);

        // Observer có thể gọi thêm logic dùng lock/DB như anti-sniping hoặc auto-bid.
        // Chạy sau khi release bid lock để tránh giữ critical section quá lâu.
        notifyObservers(auction, tx);

        LOGGER.info("Bid placed: auction=" + auctionId
                + " | bidder=" + bidderId + " | amount=" + bidAmount);
        return tx;
    }

    // ── GET AUCTIONS ──────────────────────────────────────────────────────

    public List<Auction> getAllAuctions() {
        List<Auction> auctions = auctionDAO.findAll();
        List<Auction> synced = new ArrayList<>();
        for (Auction auction : auctions) {
            synced.add(syncAuctionTimeState(auction));
        }
        return synced;
    }

    public List<Auction> getRunningAuctions() {
        return getAllAuctions().stream()
                .filter(auction -> auction.getStatus() == AuctionStatus.RUNNING)
                .toList();
    }

    public Auction getAuctionById(String auctionId) {
        Auction auction = auctionDAO.findById(auctionId);
        if (auction == null) throw new IllegalArgumentException("Không tìm thấy phiên đấu giá: " + auctionId);
        return syncAuctionTimeState(auction);
    }

    public List<Auction> getAuctionsBySeller(String sellerId) {
        List<Auction> auctions = auctionDAO.findBySeller(sellerId);
        List<Auction> synced = new ArrayList<>();
        for (Auction auction : auctions) {
            synced.add(syncAuctionTimeState(auction));
        }
        return synced;
    }

    // ── BID HISTORY ───────────────────────────────────────────────────────

    public List<BidTransaction> getBidHistory(String auctionId) {
        return bidTransactionDAO.findByAuction(auctionId);
    }

    // ── CLOSE AUCTION ─────────────────────────────────────────────────────

    /**
     * Đóng phiên thủ công (Admin hoặc hết giờ).
     */
    public void closeAuction(String auctionId) {
        ReentrantReadWriteLock.WriteLock lock = auctionManager.getWriteLock(auctionId);
        lock.lock();
        try {
            Auction auction = auctionDAO.findById(auctionId);
            if (auction == null) throw new IllegalArgumentException("Không tìm thấy phiên đấu giá");
            if (auction.getStatus() == AuctionStatus.FINISHED) return;

            auction.setStatus(AuctionStatus.FINISHED);
            auctionDAO.updateStatus(auctionId, AuctionStatus.FINISHED);
            auctionManager.addAuction(auction);
            LOGGER.info("Auction manually closed: " + auctionId);
        } finally {
            lock.unlock();
        }
    }

    public void cancelAuction(String auctionId) {
        cancelAuction(auctionId, null);
    }

    public void cancelAuction(String auctionId, String requesterId) {
        ReentrantReadWriteLock.WriteLock lock = auctionManager.getWriteLock(auctionId);
        lock.lock();
        try {
            Auction auction = auctionDAO.findById(auctionId);
            if (auction == null) throw new IllegalArgumentException("Không tìm thấy phiên đấu giá");

            if (requesterId != null
                    && auction.getSeller() != null
                    && !requesterId.equals(auction.getSeller().getId())) {
                User requester = userDAO.findById(requesterId);
                if (requester == null || !"ADMIN".equals(requester.getRole())) {
                    throw new IllegalStateException("Chỉ người bán của phiên hoặc ADMIN mới được hủy phiên");
                }
            }

            if (auction.getStatus() != AuctionStatus.OPEN
                    && auction.getStatus() != AuctionStatus.RUNNING
                    && auction.getStatus() != AuctionStatus.FINISHED) {
                throw new IllegalStateException("Chỉ có thể hủy phiên OPEN, RUNNING hoặc FINISHED không có người thắng");
            }
            if (auction.getStatus() == AuctionStatus.FINISHED && auction.getCurrentWinnerId() != null) {
                throw new IllegalStateException("Phiên đã có người thắng, không thể hủy");
            }

            auctionDAO.updateStatus(auctionId, AuctionStatus.CANCELED);
            auction.setStatus(AuctionStatus.CANCELED);
            auctionManager.addAuction(auction);
            LOGGER.info("Auction canceled: " + auctionId);
        } finally {
            lock.unlock();
        }
    }

    public void deleteAuction(String auctionId, String requesterId) {
        ReentrantReadWriteLock.WriteLock lock = auctionManager.getWriteLock(auctionId);
        lock.lock();
        try {
            Auction auction = auctionDAO.findById(auctionId);
            if (auction == null) throw new IllegalArgumentException("Không tìm thấy phiên đấu giá");

            if (requesterId != null
                    && auction.getSeller() != null
                    && !requesterId.equals(auction.getSeller().getId())) {
                User requester = userDAO.findById(requesterId);
                if (requester == null || !"ADMIN".equals(requester.getRole())) {
                    throw new IllegalStateException("Chỉ người bán của phiên hoặc ADMIN mới được xóa phiên");
                }
            }

            if (!canDeleteAuctionWithStatus(auction, bidTransactionDAO.hasBids(auctionId))) {
                throw new IllegalStateException(deleteAuctionBlockedMessage(auction));
            }

            auctionDAO.delete(auctionId);
            auctionManager.removeAuction(auctionId);
            AutoBidder.getInstance().clearAuction(auctionId);
            LOGGER.info("Auction deleted: " + auctionId);
        } finally {
            lock.unlock();
        }
    }

    public void markAuctionPaid(String auctionId, String payerId) {
        ReentrantReadWriteLock.WriteLock lock = auctionManager.getWriteLock(auctionId);
        lock.lock();
        try {
            Auction auction = auctionDAO.findById(auctionId);
            if (auction == null) throw new IllegalArgumentException("Không tìm thấy phiên đấu giá");
            auction = syncAuctionTimeState(auction);

            if (auction.getStatus() != AuctionStatus.FINISHED) {
                throw new IllegalStateException("Chỉ phiên FINISHED mới có thể thanh toán");
            }
            if (auction.getCurrentWinnerId() == null) {
                throw new IllegalStateException("Phiên chưa có người thắng để thanh toán");
            }

            User payer = userDAO.findById(payerId);
            if (payer == null) throw new UserNotFoundException("Không tìm thấy người thanh toán: " + payerId);
            if (!auction.getCurrentWinnerId().equals(payerId) && !"ADMIN".equals(payer.getRole())) {
                throw new IllegalStateException("Chỉ người thắng hoặc ADMIN mới được xác nhận thanh toán");
            }

            auction.setStatus(AuctionStatus.PAID);
            auctionDAO.updateStatus(auctionId, AuctionStatus.PAID);
            auctionManager.addAuction(auction);
            LOGGER.info("Auction paid: " + auctionId + " | payer=" + payerId);
        } catch (UserNotFoundException e) {
            throw new IllegalStateException(e.getMessage(), e);
        } finally {
            lock.unlock();
        }
    }

    private Auction syncAuctionTimeState(Auction auction) {
        LocalDateTime now = LocalDateTime.now();
        AuctionStatus newStatus = computeTimeState(auction, now);

        if (newStatus != auction.getStatus()) {
            auction.setStatus(newStatus);
            // Read-only sync: chỉ cập nhật object, KHÔNG ghi DB
        }

        return auction;
    }

    private Auction syncAuctionTimeState(Connection conn, Auction auction) throws SQLException {
        LocalDateTime now = LocalDateTime.now();
        AuctionStatus newStatus = computeTimeState(auction, now);

        if (newStatus != auction.getStatus()) {
            auction.setStatus(newStatus);
            auctionDAO.updateStatus(conn, auction.getId(), newStatus);
            auctionManager.addAuction(auction);
        }

        return auction;
    }

    /**
     * Tính trạng thái auction theo thời gian (không ghi DB).
     * Hỗ trợ chuyển 2 bước trong 1 lần: OPEN/RUNNING → FINISHED → CANCELED.
     */
    private AuctionStatus computeTimeState(Auction auction, LocalDateTime now) {
        AuctionStatus status = auction.getStatus();

        // Bước 1: Nếu đang mở/chạy mà đã hết giờ → FINISHED
        if ((status == AuctionStatus.OPEN || status == AuctionStatus.RUNNING)
                && !now.isBefore(auction.getEndTime())) {
            status = AuctionStatus.FINISHED;
        } else if (status == AuctionStatus.OPEN
                && !now.isBefore(auction.getStartTime())
                && now.isBefore(auction.getEndTime())) {
            // OPEN → RUNNING khi đến giờ bắt đầu
            return AuctionStatus.RUNNING;
        }

        // Bước 2: Nếu FINISHED mà không có winner và đã qua delay → CANCELED
        // (áp dụng ngay cả khi vừa chuyển từ OPEN/RUNNING ở bước 1)
        if (status == AuctionStatus.FINISHED
                && auction.getCurrentWinnerId() == null
                && now.isAfter(auction.getEndTime().plusSeconds(EMPTY_AUCTION_CANCEL_DELAY_SECONDS))) {
            return AuctionStatus.CANCELED;
        }

        return status;
    }

    private void rollbackQuietly(Connection conn) {
        try {
            conn.rollback();
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Rollback failed", e);
        }
    }

    private void broadcastBid(Auction auction, BidTransaction tx, User bidder) {
        JSONObject msg = new JSONObject();
        msg.put("event", tx.isAutoBid() ? "AUTO_BID" : "NEW_BID");
        msg.put("auctionId", auction.getId());
        msg.put("bidderId", bidder.getId());
        msg.put("bidderName", bidder.getUsername());
        msg.put("bidAmount", tx.getBidAmount());
        msg.put("amount", tx.getBidAmount());
        msg.put("timestamp", tx.getTimestamp().toString());
        auctionManager.broadcastToRoom(auction.getId(), msg.toString());
    }

    private boolean isWholeMoney(double amount) {
        return amount > 0 && Math.rint(amount) == amount;
    }

    public boolean canDeleteAuction(Auction auction) {
        return canDeleteAuctionWithStatus(auction, bidTransactionDAO.hasBids(auction.getId()));
    }

    public String getDeleteAuctionBlockedMessage(Auction auction) {
        return deleteAuctionBlockedMessage(auction);
    }

    private boolean canDeleteAuctionWithStatus(Auction auction, boolean hasBids) {
        if (auction.getStatus() == AuctionStatus.CANCELED || auction.getStatus() == AuctionStatus.PAID) {
            return true;
        }
        return !hasBids;
    }

    private String deleteAuctionBlockedMessage(Auction auction) {
        if (auction.getStatus() == AuctionStatus.FINISHED && auction.getCurrentWinnerId() != null) {
            return "Phiên đã kết thúc và có người thắng, vui lòng đợi người thắng thanh toán trước khi xóa";
        }
        return "Chỉ xóa được phiên đã CANCELED/PAID hoặc phiên chưa có bid";
    }
}