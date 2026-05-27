package com.auction;

import com.auction.dao.BidTransactionDAO;
import com.auction.model.BidTransaction;
import com.auction.observer.BidObserver;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

/**
 * BidHistoryVisualizer - Server-side provider dữ liệu biểu đồ giá realtime.
 *
 * Chức năng:
 *   1. Lưu in-memory lịch sử giá của từng phiên (danh sách DataPoint)
 *   2. Implements {@link BidObserver} → tự động cập nhật khi có bid mới
 *   3. Cung cấp API trả về JSON để {@link com.auction.network.ClientHandler}
 *      gửi cho client (dùng trong BidController → BidHistoryChart)
 *   4. Hỗ trợ load lịch sử từ DB khi server khởi động lại
 *
 * JSON format trả về (dùng cho action GET_PRICE_CHART):
 * <pre>
 * {
 *   "auctionId": "uuid",
 *   "points": [
 *     { "t": "2026-05-01T19:50:00", "price": 1000000.0 },
 *     { "t": "2026-05-01T19:55:30", "price": 1200000.0 },
 *     ...
 *   ],
 *   "currentPrice": 1500000.0,
 *   "totalBids":    5
 * }
 * </pre>
 *
 * Tích hợp vào ClientHandler:
 * <pre>
 *   case "GET_PRICE_CHART" -> {
 *       String auctionId = data.getString("auctionId");
 *       String json = BidHistoryVisualizer.getInstance().getChartDataJson(auctionId);
 *       return successResponse(new JSONObject(json), "OK");
 *   }
 * </pre>
 */
public class BidHistoryVisualizer implements BidObserver {

    private static final Logger LOGGER = Logger.getLogger(BidHistoryVisualizer.class.getName());
    private static final DateTimeFormatter FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    // ── Singleton ──────────────────────────────────────────────────────────
    private static volatile BidHistoryVisualizer instance;

    public static BidHistoryVisualizer getInstance() {
        if (instance == null) {
            synchronized (BidHistoryVisualizer.class) {
                if (instance == null) instance = new BidHistoryVisualizer();
            }
        }
        return instance;
    }

    // ── Inner class: DataPoint ─────────────────────────────────────────────

    /**
     * Một điểm dữ liệu trên biểu đồ: (timestamp, price).
     */
    public static class DataPoint {
        private final LocalDateTime timestamp;
        private final double        price;
        private final String        bidderId;

        public DataPoint(LocalDateTime timestamp, double price, String bidderId) {
            this.timestamp = timestamp;
            this.price     = price;
            this.bidderId  = bidderId;
        }

        public LocalDateTime getTimestamp() { return timestamp; }
        public double        getPrice()     { return price; }
        public String        getBidderId()  { return bidderId; }

        public JSONObject toJson() {
            JSONObject obj = new JSONObject();
            obj.put("t",        timestamp.format(FMT));
            obj.put("price",    price);
            obj.put("bidderId", bidderId != null ? bidderId : "");
            return obj;
        }
    }

    // ── Fields ─────────────────────────────────────────────────────────────

    /**
     * Map: auctionId → List<DataPoint> (CopyOnWriteArrayList để thread-safe đọc/ghi đồng thời)
     */
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<DataPoint>> history =
            new ConcurrentHashMap<>();

    private final BidTransactionDAO bidTransactionDAO = new BidTransactionDAO();

    private BidHistoryVisualizer() {}

    // ── BidObserver callbacks ──────────────────────────────────────────────

    /**
     * Callback tối giản cho observer cũ. AuctionService gọi thêm onNewBid()
     * với BidTransaction đầy đủ, nên không ghi dữ liệu ở đây để tránh trùng điểm.
     */
    @Override
    public void onBidUpdated(String auctionId, double newPrice, String leadingBidderId) {
        // no-op: onNewBid() có timestamp chính xác từ BidTransaction.
    }

    /**
     * Gọi từ AuctionService.notifyObservers() với đầy đủ BidTransaction.
     * Override để dùng timestamp chính xác từ DB thay vì LocalDateTime.now().
     */
    @Override
    public void onNewBid(com.auction.model.Auction auction, BidTransaction tx) {
        addPoint(auction.getId(), tx.getAmount(), tx.getBidderId(), tx.getBidTime());
        LOGGER.fine(String.format("[Visualizer] New point: auction=%s price=%.0f",
                auction.getId(), tx.getAmount()));
    }

    // ── Public API ─────────────────────────────────────────────────────────

    /**
     * Thêm thủ công một điểm dữ liệu (dùng khi load lịch sử từ DB).
     */
    public void addPoint(String auctionId, double price, String bidderId, LocalDateTime timestamp) {
        history.computeIfAbsent(auctionId, k -> new CopyOnWriteArrayList<>())
               .add(new DataPoint(timestamp, price, bidderId));
    }

    /**
     * Load toàn bộ lịch sử bid từ DB vào memory cho một phiên.
     * Gọi khi server restart hoặc khi client mới JOIN auction.
     *
     * @param auctionId ID phiên
     */
    public void loadFromDatabase(String auctionId) {
        List<BidTransaction> txList = bidTransactionDAO.findByAuction(auctionId);
        CopyOnWriteArrayList<DataPoint> points = new CopyOnWriteArrayList<>();

        txList.stream()
              .sorted(Comparator.comparing(BidTransaction::getBidTime))
              .forEach(tx -> points.add(
                  new DataPoint(tx.getBidTime(), tx.getAmount(), tx.getBidderId())));

        history.put(auctionId, points);
        LOGGER.info(String.format("[Visualizer] Loaded %d points from DB for auction=%s",
                points.size(), auctionId));
    }

    /**
     * Trả về dữ liệu biểu đồ dạng JSON để gửi cho client.
     *
     * @param auctionId ID phiên
     * @return JSON string theo format đã mô tả ở Javadoc class
     */
    public String getChartDataJson(String auctionId) {
        CopyOnWriteArrayList<DataPoint> points = history.getOrDefault(
            auctionId, new CopyOnWriteArrayList<>());

        JSONObject result = new JSONObject();
        result.put("auctionId", auctionId);

        JSONArray arr = new JSONArray();
        double lastPrice = 0;
        for (DataPoint dp : points) {
            arr.put(dp.toJson());
            lastPrice = dp.getPrice();
        }

        result.put("points",       arr);
        result.put("currentPrice", lastPrice);
        result.put("totalBids",    points.size());
        return result.toString();
    }

    /**
     * Trả về danh sách DataPoint của một phiên (dùng nội bộ hoặc test).
     */
    public List<DataPoint> getPoints(String auctionId) {
        return Collections.unmodifiableList(
            history.getOrDefault(auctionId, new CopyOnWriteArrayList<>()));
    }

    /**
     * Xóa dữ liệu của một phiên khỏi memory (gọi khi phiên FINISHED/CANCELED
     * và không cần realtime nữa — dữ liệu đã có trong DB).
     */
    public void evict(String auctionId) {
        history.remove(auctionId);
        LOGGER.info("[Visualizer] Evicted chart data for auction=" + auctionId);
    }

    /**
     * Số phiên đang được theo dõi.
     */
    public int getTrackedAuctionCount() {
        return history.size();
    }
}
