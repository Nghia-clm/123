package com.auction;

import com.auction.manager.AuctionManager;
import com.auction.model.Auction;
import com.auction.model.AuctionStatus;
import com.auction.observer.BidObserver;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * AntiSnipingTimer - Thuật toán gia hạn phiên đấu giá (Anti-sniping).
 *
 * Nguyên lý:
 *   Nếu một bid hợp lệ được đặt trong vòng {@code SNIPE_WINDOW_SECONDS} giây
 *   trước khi phiên kết thúc → tự động gia hạn thêm {@code EXTENSION_SECONDS} giây.
 *
 * Ví dụ (cấu hình mặc định):
 *   - Kết thúc dự kiến:  20:00:00
 *   - Bid lúc:           19:59:50  (còn 10 giây → nằm trong cửa sổ 30s)
 *   - Kết thúc mới:      20:01:00  (gia hạn thêm 60s)
 *
 * Tích hợp Observer Pattern: implements {@link BidObserver}, đăng ký vào
 * {@link com.auction.observer.AuctionEventPublisher} của từng phiên.
 *
 * Lưu ý: Lớp này chỉ chịu trách nhiệm quyết định CÓ gia hạn không.
 * Việc thực sự thay đổi endTime được ủy quyền cho {@link AuctionManager#extendAuctionTime}.
 * AuctionService đã gọi sẵn {@code extendAuctionTime} khi phát hiện anti-snipe,
 * class này là phiên bản standalone/observer để tích hợp vào luồng observer.
 *
 * <pre>
 * Cách dùng:
 *   AntiSnipingTimer timer = new AntiSnipingTimer();
 *   auctionEventPublisher.subscribe(timer);
 * </pre>
 */
public class AntiSnipingTimer implements BidObserver {

    private static final Logger LOGGER = Logger.getLogger(AntiSnipingTimer.class.getName());

    // ── Cấu hình ──────────────────────────────────────────────────────────

    /** Số giây cuối phiên mà nếu có bid sẽ bị gia hạn (cửa sổ anti-snipe). */
    private final int snipeWindowSeconds;

    /** Số giây gia hạn thêm khi phát hiện snipe. */
    private final int extensionSeconds;

    /** Số lần gia hạn tối đa cho một phiên (tránh gia hạn vô hạn). */
    private final int maxExtensions;

    // ── Trạng thái ─────────────────────────────────────────────────────────

    /** Map: auctionId → số lần đã gia hạn */
    private final ConcurrentHashMap<String, Integer> extensionCounts = new ConcurrentHashMap<>();

    public interface AuctionTimerRuntime {
        Auction getAuction(String auctionId);
        void extendAuctionTime(String auctionId, int extraSeconds);
    }

    private static final class AuctionManagerTimerRuntime implements AuctionTimerRuntime {
        private final AuctionManager auctionManager = AuctionManager.getInstance();

        @Override
        public Auction getAuction(String auctionId) {
            return auctionManager.getAuction(auctionId);
        }

        @Override
        public void extendAuctionTime(String auctionId, int extraSeconds) {
            auctionManager.extendAuctionTime(auctionId, extraSeconds);
        }
    }

    private final AuctionTimerRuntime auctionManager;

    // ── Constructors ───────────────────────────────────────────────────────

    /** Khởi tạo với giá trị mặc định theo đề bài (30s cửa sổ, +60s gia hạn, tối đa 10 lần). */
    public AntiSnipingTimer() {
        this(30, 60, 10);
    }

    /**
     * Khởi tạo với cấu hình tùy chỉnh.
     *
     * @param snipeWindowSeconds  số giây cuối để trigger gia hạn
     * @param extensionSeconds    số giây gia hạn thêm mỗi lần
     * @param maxExtensions       số lần gia hạn tối đa (0 = không giới hạn)
     */
    public AntiSnipingTimer(int snipeWindowSeconds, int extensionSeconds, int maxExtensions) {
        this(snipeWindowSeconds, extensionSeconds, maxExtensions, new AuctionManagerTimerRuntime());
    }

    public AntiSnipingTimer(int snipeWindowSeconds, int extensionSeconds, int maxExtensions,
                            AuctionTimerRuntime auctionManager) {
        if (snipeWindowSeconds <= 0) throw new IllegalArgumentException("snipeWindowSeconds phải > 0");
        if (extensionSeconds <= 0)   throw new IllegalArgumentException("extensionSeconds phải > 0");
        this.snipeWindowSeconds = snipeWindowSeconds;
        this.extensionSeconds   = extensionSeconds;
        this.maxExtensions      = maxExtensions;
        this.auctionManager     = auctionManager;
    }

    // ── BidObserver callback ───────────────────────────────────────────────

    /**
     * Được gọi sau mỗi bid hợp lệ.
     * Kiểm tra xem bid có nằm trong cửa sổ anti-snipe không và gia hạn nếu cần.
     */
    @Override
    public void onBidUpdated(String auctionId, double newPrice, String leadingBidderId) {
        checkAndExtend(auctionId);
    }

    // ── Core logic ─────────────────────────────────────────────────────────

    /**
     * Kiểm tra và gia hạn phiên nếu bid xảy ra trong cửa sổ anti-snipe.
     *
     * @param auctionId ID phiên cần kiểm tra
     */
    public void checkAndExtend(String auctionId) {
        Auction auction = auctionManager.getAuction(auctionId);
        if (auction == null) return;
        if (auction.getStatus() != AuctionStatus.RUNNING) return;

        LocalDateTime now    = LocalDateTime.now();
        LocalDateTime endTime = auction.getEndTime();

        // Tính số giây còn lại
        long secondsLeft = Duration.between(now, endTime).getSeconds();

        // Bid xảy ra sau khi phiên kết thúc → không gia hạn
        if (secondsLeft < 0) return;

        // Không nằm trong cửa sổ anti-snipe → không gia hạn
        if (secondsLeft > snipeWindowSeconds) return;

        // Kiểm tra đã gia hạn quá số lần tối đa chưa
        int count = extensionCounts.getOrDefault(auctionId, 0);
        if (maxExtensions > 0 && count >= maxExtensions) {
            LOGGER.info(String.format(
                "[AntiSnipe] auction=%s reached max extensions (%d). No more extensions.",
                auctionId, maxExtensions));
            return;
        }

        // Gia hạn
        extensionCounts.put(auctionId, count + 1);
        auctionManager.extendAuctionTime(auctionId, extensionSeconds);

        LOGGER.info(String.format(
            "[AntiSnipe] auction=%s | secondsLeft=%d | extended +%ds | extension #%d/%s",
            auctionId, secondsLeft, extensionSeconds,
            count + 1, maxExtensions > 0 ? String.valueOf(maxExtensions) : "∞"));
    }

    /**
     * Reset bộ đếm gia hạn khi phiên kết thúc hoặc bị hủy.
     */
    public void reset(String auctionId) {
        extensionCounts.remove(auctionId);
    }

    /**
     * Lấy số lần đã gia hạn của một phiên.
     */
    public int getExtensionCount(String auctionId) {
        return extensionCounts.getOrDefault(auctionId, 0);
    }

    // ── Getters ────────────────────────────────────────────────────────────

    public int getSnipeWindowSeconds() { return snipeWindowSeconds; }
    public int getExtensionSeconds()   { return extensionSeconds; }
    public int getMaxExtensions()      { return maxExtensions; }
}
