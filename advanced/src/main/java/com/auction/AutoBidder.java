package com.auction;

import com.auction.exception.AuctionClosedException;
import com.auction.exception.InvalidBidException;
import com.auction.exception.UserNotFoundException;
import com.auction.manager.AuctionManager;
import com.auction.model.Auction;
import com.auction.model.AuctionStatus;
import com.auction.observer.BidObserver;
import com.auction.service.AuctionService;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

/**
 * AutoBidder - Hệ thống đấu giá tự động (Auto-Bidding).
 *
 * Cho phép người dùng đặt:
 *   - maxBid:     giá tối đa sẵn sàng trả
 *   - increment:  bước giá mỗi lần tự động tăng
 *
 * Khi có bid mới từ đối thủ:
 *   1. Hệ thống tự động phản hồi với giá = currentPrice + increment
 *   2. Nếu giá đó vượt maxBid → dừng auto-bid
 *   3. Ưu tiên theo thời điểm đăng ký (FIFO – người đăng ký trước thắng khi bằng giá)
 *   4. Xử lý xung đột bid đồng thời bằng ReentrantLock per auction
 *
 * Tích hợp Observer Pattern: AutoBidder implements {@link BidObserver},
 * đăng ký vào {@link com.auction.observer.AuctionEventPublisher} của từng phiên.
 *
 * <pre>
 * Cách dùng trong AuctionService / ClientHandler:
 *
 *   AutoBidder autoBidder = AutoBidder.getInstance();
 *
 *   // Bidder đăng ký auto-bid
 *   autoBidder.register("auction-uuid", "bidder-uuid", 5_000_000, 100_000);
 *
 *   // Hủy auto-bid
 *   autoBidder.cancel("auction-uuid", "bidder-uuid");
 * </pre>
 */
public class AutoBidder implements BidObserver {

    private static final Logger LOGGER = Logger.getLogger(AutoBidder.class.getName());

    // ── Singleton ──────────────────────────────────────────────────────────
    private static volatile AutoBidder instance;

    public static AutoBidder getInstance() {
        if (instance == null) {
            synchronized (AutoBidder.class) {
                if (instance == null) instance = new AutoBidder();
            }
        }
        return instance;
    }

    // ── Inner class: AutoBidEntry ──────────────────────────────────────────

    /**
     * Thông tin một đăng ký auto-bid của một bidder trong một phiên.
     * So sánh theo registeredAt để ưu tiên người đăng ký sớm hơn.
     */
    public static class AutoBidEntry implements Comparable<AutoBidEntry> {
        private final String   bidderId;
        private final double   maxBid;
        private final double   increment;
        private final long     registeredAt; // System.nanoTime() – dùng để phá tie
        private volatile boolean active;

        public AutoBidEntry(String bidderId, double maxBid, double increment) {
            this.bidderId     = bidderId;
            this.maxBid       = maxBid;
            this.increment    = increment;
            this.registeredAt = System.nanoTime();
            this.active       = true;
        }

        /** Bid thấp hơn (cho PriorityQueue min-heap) → ưu tiên giá cao hơn */
        @Override
        public int compareTo(AutoBidEntry other) {
            // Ưu tiên maxBid cao hơn trước; nếu bằng nhau → đăng ký sớm hơn thắng
            int cmp = Double.compare(other.maxBid, this.maxBid);
            if (cmp != 0) return cmp;
            return Long.compare(this.registeredAt, other.registeredAt);
        }

        public String  getBidderId()    { return bidderId; }
        public double  getMaxBid()      { return maxBid; }
        public double  getIncrement()   { return increment; }
        public boolean isActive()       { return active; }
        public void    deactivate()     { this.active = false; }
    }

    // ── Fields ─────────────────────────────────────────────────────────────

    /**
     * Map: auctionId → PriorityQueue<AutoBidEntry>
     * PriorityQueue sắp xếp theo maxBid giảm dần (người bid cao nhất lên đầu).
     */
    private final ConcurrentHashMap<String, PriorityQueue<AutoBidEntry>> registrations =
            new ConcurrentHashMap<>();

    /**
     * Lock per auction để tránh race condition khi nhiều auto-bid kích hoạt cùng lúc.
     */
    private final ConcurrentHashMap<String, ReentrantLock> auctionLocks =
            new ConcurrentHashMap<>();

    /**
     * Thread pool để xử lý auto-bid bất đồng bộ (không block luồng gọi).
     */
    private final Executor executor;

    public interface AuctionRuntime {
        Auction getAuction(String auctionId);
        void addAuction(Auction auction);
    }

    public interface BidPlacer {
        void placeBid(String auctionId, String bidderId, double bidAmount, boolean autoBid)
                throws AuctionClosedException, InvalidBidException, UserNotFoundException;
    }

    private static final class AuctionManagerRuntime implements AuctionRuntime {
        private final AuctionManager auctionManager = AuctionManager.getInstance();

        @Override
        public Auction getAuction(String auctionId) {
            return auctionManager.getAuction(auctionId);
        }

        @Override
        public void addAuction(Auction auction) {
            auctionManager.addAuction(auction);
        }
    }

    private final AuctionRuntime auctionManager;
    private final BidPlacer bidPlacer;
    private volatile AuctionService auctionService;

    private AutoBidder() {
        this(new AuctionManagerRuntime(), (AuctionService) null, Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "auto-bidder");
            t.setDaemon(true);
            return t;
        }));
    }

    public AutoBidder(AuctionRuntime auctionManager, AuctionService auctionService, Executor executor) {
        this(auctionManager, null, auctionService, executor);
    }

    public AutoBidder(AuctionRuntime auctionManager, BidPlacer bidPlacer, Executor executor) {
        this(auctionManager, bidPlacer, null, executor);
    }

    private AutoBidder(AuctionRuntime auctionManager, BidPlacer bidPlacer,
                       AuctionService auctionService, Executor executor) {
        this.auctionManager = auctionManager;
        this.bidPlacer = bidPlacer;
        this.auctionService = auctionService;
        this.executor = executor;
    }

    private AuctionService getAuctionService() {
        if (auctionService == null) {
            synchronized (this) {
                if (auctionService == null) {
                    auctionService = new AuctionService();
                }
            }
        }
        return auctionService;
    }

    // ── Public API ─────────────────────────────────────────────────────────

    /**
     * Đăng ký auto-bid cho một bidder trong một phiên đấu giá.
     *
     * @param auctionId  ID phiên đấu giá
     * @param bidderId   ID người dùng
     * @param maxBid     giá tối đa sẵn sàng trả (phải > giá hiện tại)
     * @param increment  bước tăng giá mỗi lần tự động đặt
     * @throws IllegalArgumentException nếu tham số không hợp lệ
     */
    public void register(String auctionId, String bidderId, double maxBid, double increment) {
        if (maxBid <= 0)     throw new IllegalArgumentException("maxBid phải dương");
        if (increment <= 0)  throw new IllegalArgumentException("increment phải dương");

        Auction auction = auctionManager.getAuction(auctionId);
        if (auction == null) {
            auction = getAuctionService().getAuctionById(auctionId);
            auctionManager.addAuction(auction);
        }
        if (auction.getStatus() == AuctionStatus.FINISHED
                || auction.getStatus() == AuctionStatus.CANCELED) {
            throw new IllegalStateException("Phiên đấu giá đã kết thúc");
        }
        if (maxBid <= auction.getCurrentPrice()) {
            throw new IllegalArgumentException(
                "maxBid (" + maxBid + ") phải lớn hơn giá hiện tại (" + auction.getCurrentPrice() + ")");
        }

        PriorityQueue<AutoBidEntry> queue = registrations.computeIfAbsent(
            auctionId, k -> new PriorityQueue<>());
        auctionLocks.computeIfAbsent(auctionId, k -> new ReentrantLock());

        synchronized (queue) {
            // Xóa đăng ký cũ của bidder này nếu có
            queue.removeIf(e -> e.getBidderId().equals(bidderId));
            queue.add(new AutoBidEntry(bidderId, maxBid, increment));
        }

        LOGGER.info(String.format("[AutoBidder] Registered: bidder=%s | auction=%s | maxBid=%.0f | inc=%.0f",
                bidderId, auctionId, maxBid, increment));

        // Thử kích hoạt ngay nếu người này chưa dẫn đầu.
        // Dùng executor (async) để server kịp gửi response OK cho client TRƯỚC khi
        // broadcast AUTO_BID được gửi đi — tránh client nhận broadcast trước response.
        final double currentPrice = auction.getCurrentPrice();
        final String currentWinner = auction.getCurrentWinnerId();
        executor.execute(() -> triggerIfNeeded(auctionId, currentPrice, currentWinner));
    }

    /**
     * Hủy đăng ký auto-bid.
     */
    public void cancel(String auctionId, String bidderId) {
        PriorityQueue<AutoBidEntry> queue = registrations.get(auctionId);
        if (queue == null) return;
        synchronized (queue) {
            queue.removeIf(e -> e.getBidderId().equals(bidderId));
        }
        LOGGER.info("[AutoBidder] Cancelled: bidder=" + bidderId + " | auction=" + auctionId);
    }

    /**
     * Xóa toàn bộ auto-bid của một phiên (gọi khi phiên FINISHED/CANCELED).
     */
    public void clearAuction(String auctionId) {
        registrations.remove(auctionId);
        auctionLocks.remove(auctionId);
        LOGGER.info("[AutoBidder] Cleared all registrations for auction=" + auctionId);
    }

    /**
     * Kiểm tra bidder có đang đăng ký auto-bid trong phiên không.
     */
    public boolean isRegistered(String auctionId, String bidderId) {
        PriorityQueue<AutoBidEntry> queue = registrations.get(auctionId);
        if (queue == null) return false;
        synchronized (queue) {
            return queue.stream().anyMatch(e -> e.getBidderId().equals(bidderId));
        }
    }

    public Optional<AutoBidEntry> getRegistration(String auctionId, String bidderId) {
        PriorityQueue<AutoBidEntry> queue = registrations.get(auctionId);
        if (queue == null) return Optional.empty();
        synchronized (queue) {
            return queue.stream()
                    .filter(e -> e.getBidderId().equals(bidderId))
                    .findFirst();
        }
    }

    // ── BidObserver callback ───────────────────────────────────────────────

    /**
     * Được gọi sau mỗi bid hợp lệ từ {@link com.auction.observer.AuctionEventPublisher}.
     * Kích hoạt auto-bid bất đồng bộ để không block luồng xử lý chính.
     */
    @Override
    public void onBidUpdated(String auctionId, double newPrice, String leadingBidderId) {
        executor.execute(() -> triggerIfNeeded(auctionId, newPrice, leadingBidderId));
    }

    // ── Core logic ─────────────────────────────────────────────────────────

    /**
     * Xác định xem có auto-bidder nào cần kích hoạt không và đặt giá.
     *
     * Thuật toán:
     *   1. Lấy PriorityQueue của auction (đã sắp xếp theo maxBid giảm dần)
     *   2. Lấy entry đầu tiên (maxBid cao nhất)
     *   3. Nếu người đó ĐANG dẫn đầu → không làm gì
     *   4. Nếu không → tính nextBid = currentPrice + increment
     *   5. Nếu nextBid ≤ maxBid → đặt giá, nếu không → deactivate entry đó
     *   6. Xử lý xung đột: nếu 2 người cùng maxBid → người đăng ký sớm hơn thắng
     */
    private void triggerIfNeeded(String auctionId, double currentPrice, String leadingBidderId) {
        PriorityQueue<AutoBidEntry> queue = registrations.get(auctionId);
        if (queue == null || queue.isEmpty()) return;

        ReentrantLock lock = auctionLocks.computeIfAbsent(auctionId, k -> new ReentrantLock());

        // Thử lấy lock non-blocking để tránh deadlock khi nhiều trigger xảy ra cùng lúc
        if (!lock.tryLock()) return;

        try {
            // Re-read auction state inside lock
            Auction auction = auctionManager.getAuction(auctionId);
            if (auction == null
                    || auction.getStatus() == AuctionStatus.FINISHED
                    || auction.getStatus() == AuctionStatus.CANCELED) {
                clearAuction(auctionId);
                return;
            }

            double actualCurrentPrice   = auction.getCurrentPrice();
            String actualLeadingBidder  = auction.getCurrentWinnerId();

            AutoBidEntry best;
            synchronized (queue) {
                // Bỏ qua các entry đã bị deactivate
                while (!queue.isEmpty() && !queue.peek().isActive()) queue.poll();
                if (queue.isEmpty()) return;
                best = queue.peek();
            }

            // Người dẫn đầu không cần tự đấu lại
            if (best.getBidderId().equals(actualLeadingBidder)) return;

            double nextBid = actualCurrentPrice + best.getIncrement();

            if (nextBid > best.getMaxBid()) {
                // Vượt ngân sách → deactivate
                synchronized (queue) { queue.poll(); }
                best.deactivate();
                LOGGER.info(String.format("[AutoBidder] Max budget reached: bidder=%s | maxBid=%.0f",
                        best.getBidderId(), best.getMaxBid()));
                return;
            }

            // Đặt giá tự động
            try {
                if (bidPlacer != null) {
                    bidPlacer.placeBid(auctionId, best.getBidderId(), nextBid, true);
                } else {
                    getAuctionService().placeBid(auctionId, best.getBidderId(), nextBid, true);
                }

                LOGGER.info(String.format("[AutoBidder] Auto-bid placed: bidder=%s | auction=%s | amount=%.0f",
                        best.getBidderId(), auctionId, nextBid));

            } catch (AuctionClosedException e) {
                LOGGER.info("[AutoBidder] Auction closed during auto-bid: " + auctionId);
                clearAuction(auctionId);
            } catch (InvalidBidException e) {
                LOGGER.warning("[AutoBidder] Invalid auto-bid: " + e.getMessage());
            } catch (UserNotFoundException e) {
                LOGGER.warning("[AutoBidder] Bidder not found: " + best.getBidderId());
                synchronized (queue) { queue.poll(); }
                best.deactivate();
            }

        } finally {
            lock.unlock();
        }
    }
}