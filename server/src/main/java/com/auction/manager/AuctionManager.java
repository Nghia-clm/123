package com.auction.manager;


import com.auction.dao.AuctionDAO;
import com.auction.model.Auction;
import com.auction.model.AuctionStatus;
import com.auction.network.ClientHandler;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * AuctionManager - Singleton quản lý trung tâm toàn bộ hệ thống đấu giá.
 *
 * Chịu trách nhiệm:
 *  1. Lưu trữ danh sách auction đang hoạt động (in-memory cache)
 *  2. Quản lý "auction rooms" - danh sách client đang xem từng phiên
 *  3. Broadcast realtime khi có bid mới (Observer Pattern)
 *  4. Tự động kiểm tra và đóng phiên hết hạn (scheduler)
 *  5. Thread-safe cho concurrent bidding
 */
public class AuctionManager {

    private static final Logger LOGGER = Logger.getLogger(AuctionManager.class.getName());
    private static final int EMPTY_AUCTION_CANCEL_DELAY_SECONDS = 3;

    // ── Singleton ──────────────────────────────────────────────────────────
    private static volatile AuctionManager instance;

    private AuctionManager() {
        loadOpenAuctionsFromDatabase();
        startAuctionScheduler();
        LOGGER.info("AuctionManager initialized.");
    }

    /**
     * Double-checked locking để thread-safe Singleton.
     */
    public static AuctionManager getInstance() {
        if (instance == null) {
            synchronized (AuctionManager.class) {
                if (instance == null) {
                    instance = new AuctionManager();
                }
            }
        }
        return instance;
    }

    // ── Dữ liệu in-memory ─────────────────────────────────────────────────

    /**
     * Map: auctionId → Auction (cache in-memory cho nhanh)
     * Dùng ConcurrentHashMap để thread-safe khi nhiều thread đọc/ghi.
     */
    private final ConcurrentHashMap<String, Auction> activeAuctions = new ConcurrentHashMap<>();

    /**
     * Map: auctionId → Set<ClientHandler> (danh sách client đang xem phiên)
     * Dùng để broadcast realtime khi có bid mới.
     */
    private final ConcurrentHashMap<String, Set<ClientHandler>> auctionRooms = new ConcurrentHashMap<>();

    /**
     * Set tất cả client đang kết nối (dùng cho broadcast toàn hệ thống).
     */
    private final Set<ClientHandler> connectedClients =
            Collections.newSetFromMap(new ConcurrentHashMap<>());

    /**
     * Lock per-auction để tránh race condition khi nhiều bidder đặt giá cùng lúc.
     * Map: auctionId → ReentrantReadWriteLock
     */
    private final ConcurrentHashMap<String, ReentrantReadWriteLock> auctionLocks =
            new ConcurrentHashMap<>();

    // ── Scheduler tự động đóng phiên ──────────────────────────────────────
    private final ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(2);
    private final AuctionDAO auctionDAO = new AuctionDAO();

    // ─────────────────────────────────────────────────────────────────────
    //  AUCTION MANAGEMENT
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Thêm một phiên đấu giá vào manager (được gọi khi tạo auction mới).
     */
    public void addAuction(Auction auction) {
        if (auction == null) return;
        String id = auction.getId();
        // Luôn update auction object trong cache
        activeAuctions.put(id, auction);
        // putIfAbsent: chỉ tạo lock MỚI nếu chưa có
        // Tránh replace lock đang giữ bởi thread khác → deadlock / lost notify
        auctionLocks.putIfAbsent(id, new ReentrantReadWriteLock());
        // Tạo room nếu chưa có
        auctionRooms.putIfAbsent(id, Collections.newSetFromMap(new ConcurrentHashMap<>()));
        LOGGER.info("Auction updated in manager cache: " + id);
    }

    /**
     * Lấy auction theo ID.
     */
    public Auction getAuction(String auctionId) {
        return activeAuctions.get(auctionId);
    }

    /**
     * Lấy tất cả auction đang active.
     */
    public List<Auction> getAllActiveAuctions() {
        return new ArrayList<>(activeAuctions.values());
    }

    /**
     * Xóa auction khỏi cache (khi đã FINISHED/PAID/CANCELED).
     */
    public void removeAuction(String auctionId) {
        activeAuctions.remove(auctionId);
        auctionLocks.remove(auctionId);
        LOGGER.info("Auction removed from manager: " + auctionId);
    }

    // ─────────────────────────────────────────────────────────────────────
    //  LOCK PER AUCTION (Concurrent Bidding)
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Lấy write lock cho một auction cụ thể.
     * Dùng khi đặt giá (placeBid) để tránh race condition / lost update.
     *
     * Cách dùng trong AuctionService:
     *   ReentrantReadWriteLock.WriteLock lock = AuctionManager.getInstance().getWriteLock(auctionId);
     *   lock.lock();
     *   try { ... } finally { lock.unlock(); }
     */
    public ReentrantReadWriteLock.WriteLock getWriteLock(String auctionId) {
        auctionLocks.putIfAbsent(auctionId, new ReentrantReadWriteLock());
        return auctionLocks.get(auctionId).writeLock();
    }

    /**
     * Lấy read lock (cho các thao tác chỉ đọc, nhiều thread cùng đọc được).
     */
    public ReentrantReadWriteLock.ReadLock getReadLock(String auctionId) {
        auctionLocks.putIfAbsent(auctionId, new ReentrantReadWriteLock());
        return auctionLocks.get(auctionId).readLock();
    }

    // ─────────────────────────────────────────────────────────────────────
    //  CLIENT MANAGEMENT (Connected clients)
    // ─────────────────────────────────────────────────────────────────────

    /** Đăng ký client mới khi login thành công. */
    public void registerClient(ClientHandler client) {
        connectedClients.add(client);
        LOGGER.info("Client registered. Total connected: " + connectedClients.size());
    }

    /** Hủy đăng ký khi client disconnect hoặc logout. */
    public void unregisterClient(ClientHandler client) {
        connectedClients.remove(client);
        LOGGER.info("Client unregistered. Total connected: " + connectedClients.size());
    }

    public int getConnectedClientCount() {
        return connectedClients.size();
    }

    // ─────────────────────────────────────────────────────────────────────
    //  AUCTION ROOMS (Observer Pattern)
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Client tham gia vào "phòng" của một phiên đấu giá để nhận broadcast.
     */
    public void joinAuctionRoom(String auctionId, ClientHandler client) {
        auctionRooms.computeIfAbsent(
            auctionId,
            k -> Collections.newSetFromMap(new ConcurrentHashMap<>())
        ).add(client);
        LOGGER.info("Client joined auction room: " + auctionId
                + " | Room size: " + auctionRooms.get(auctionId).size());
    }

    /**
     * Client rời "phòng" (chuyển trang hoặc disconnect).
     */
    public void leaveAuctionRoom(String auctionId, ClientHandler client) {
        Set<ClientHandler> room = auctionRooms.get(auctionId);
        if (room != null) {
            room.remove(client);
            LOGGER.info("Client left auction room: " + auctionId
                    + " | Room size: " + room.size());
        }
    }

    /**
     * Broadcast message đến tất cả client đang xem phiên auctionId.
     * Dùng khi có bid mới → realtime update cho tất cả client (Observer).
     */
    public void broadcastToRoom(String auctionId, String message) {
        Set<ClientHandler> room = auctionRooms.get(auctionId);
        if (room == null || room.isEmpty()) return;

        LOGGER.info("Broadcasting to room " + auctionId
                + " | " + room.size() + " clients | msg: " + message);

        for (ClientHandler client : room) {
            try {
                client.sendMessage(message);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING,
                        "Failed to send message to client in room " + auctionId, e);
                // Xóa client lỗi khỏi room
                room.remove(client);
            }
        }
    }

    /**
     * Broadcast toàn bộ hệ thống (ví dụ: thông báo admin, server shutdown...).
     */
    public void broadcastToAll(String message) {
        LOGGER.info("Broadcasting to all " + connectedClients.size() + " clients.");
        for (ClientHandler client : connectedClients) {
            try {
                client.sendMessage(message);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to broadcast to client", e);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  ANTI-SNIPING SUPPORT
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Gia hạn thời gian kết thúc của auction.
     * Được gọi từ AntiSnipingTimer khi phát hiện bid trong X giây cuối.
     *
     * @param auctionId  ID phiên cần gia hạn
     * @param extraSeconds số giây gia hạn thêm
     */
    public void extendAuctionTime(String auctionId, int extraSeconds) {
        Auction auction = activeAuctions.get(auctionId);
        if (auction == null) {
            auction = auctionDAO.findById(auctionId);
            if (auction != null) addAuction(auction);
        }
        if (auction == null) return;

        ReentrantReadWriteLock.WriteLock lock = getWriteLock(auctionId);
        String broadcastMessage;
        lock.lock();
        try {
            LocalDateTime newEndTime = auction.getEndTime().plusSeconds(extraSeconds);
            auction.setEndTime(newEndTime);
            auctionDAO.updateEndTime(auctionId, newEndTime);
            LOGGER.info("Auction " + auctionId + " extended by "
                    + extraSeconds + "s. New end time: " + newEndTime);

            // Thông báo cho tất cả client trong phòng
            org.json.JSONObject msg = new org.json.JSONObject();
            msg.put("event", "AUCTION_EXTENDED");
            msg.put("auctionId", auctionId);
            msg.put("newEndTime", newEndTime.toString());
            msg.put("extraSeconds", extraSeconds);
            broadcastMessage = msg.toString();

        } finally {
            lock.unlock();
        }

        broadcastToRoom(auctionId, broadcastMessage);
    }

    // ─────────────────────────────────────────────────────────────────────
    //  SCHEDULER - Tự động đóng phiên hết hạn
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Khởi động scheduler: mỗi giây kiểm tra các phiên hết hạn.
     */
    private void startAuctionScheduler() {
        scheduler.scheduleAtFixedRate(
            this::checkAndCloseExpiredAuctions,
            1,
            1,
            TimeUnit.SECONDS
        );
        LOGGER.info("Auction scheduler started (1s interval).");
    }

    private void loadOpenAuctionsFromDatabase() {
        try {
            for (Auction auction : auctionDAO.findAll()) {
                if (auction.getStatus() == AuctionStatus.OPEN || auction.getStatus() == AuctionStatus.RUNNING) {
                    addAuction(auction);
                }
            }
            LOGGER.info("Open/running auctions loaded into manager cache: " + activeAuctions.size());
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Cannot preload auctions from database", e);
        }
    }

    /**
     * Kiểm tra tất cả auction đang active, đóng những phiên đã hết giờ.
     */
    private void checkAndCloseExpiredAuctions() {
        for (Map.Entry<String, Auction> entry : activeAuctions.entrySet()) {
            String auctionId = entry.getKey();
            Auction auction  = entry.getValue();

            if (auction.getStatus() == AuctionStatus.OPEN
                    || auction.getStatus() == AuctionStatus.RUNNING
                    || (auction.getStatus() == AuctionStatus.FINISHED
                            && auction.getCurrentWinnerId() == null)) {

                ReentrantReadWriteLock.WriteLock lock = getWriteLock(auctionId);
                String finishedMessage = null;
                boolean removeAfterBroadcast = false;
                lock.lock();
                try {
                    Auction latest = auctionDAO.findById(auctionId);
                    if (latest != null) {
                        auction = latest;
                        activeAuctions.put(auctionId, latest);
                    }

                    LocalDateTime checkedAt = LocalDateTime.now();
                    if (auction.getStatus() == AuctionStatus.OPEN
                            && !checkedAt.isBefore(auction.getStartTime())
                            && checkedAt.isBefore(auction.getEndTime())) {
                        auction.setStatus(AuctionStatus.RUNNING);
                        auctionDAO.updateStatus(auctionId, AuctionStatus.RUNNING);
                        LOGGER.info("Auction " + auctionId + " auto-started by scheduler.");
                    }

                    if (auction.getStatus() == AuctionStatus.FINISHED
                            && auction.getCurrentWinnerId() == null
                            && LocalDateTime.now().isAfter(
                                    auction.getEndTime().plusSeconds(EMPTY_AUCTION_CANCEL_DELAY_SECONDS))) {
                        auction.setStatus(AuctionStatus.CANCELED);
                        auctionDAO.updateStatus(auctionId, AuctionStatus.CANCELED);
                        LOGGER.info("Auction " + auctionId + " canceled after empty finish delay.");

                        org.json.JSONObject msg = new org.json.JSONObject();
                        msg.put("event", "AUCTION_FINISHED");
                        msg.put("auctionId", auctionId);
                        msg.put("status", AuctionStatus.CANCELED.name());
                        msg.put("finalPrice", auction.getCurrentPrice());
                        finishedMessage = msg.toString();
                        removeAfterBroadcast = true;
                    }

                    // Kiểm tra lại sau khi có lock (tránh double-close)
                    if ((auction.getStatus() == AuctionStatus.OPEN || auction.getStatus() == AuctionStatus.RUNNING)
                            && !auction.getEndTime().isAfter(LocalDateTime.now())) {

                        auction.setStatus(AuctionStatus.FINISHED);
                        auctionDAO.updateStatus(auctionId, AuctionStatus.FINISHED);

                        AuctionStatus finalStatus = AuctionStatus.FINISHED;
                        LOGGER.info("Auction " + auctionId + " auto-closed by scheduler: " + finalStatus);

                        // Broadcast kết thúc phiên
                        org.json.JSONObject msg = new org.json.JSONObject();
                        msg.put("event", "AUCTION_FINISHED");
                        msg.put("auctionId", auctionId);
                        msg.put("status", finalStatus.name());
                        msg.put("finalPrice", auction.getCurrentPrice());
                        if (auction.getCurrentWinnerId() != null) {
                            msg.put("winnerId", auction.getCurrentWinnerId());
                            if (auction.getWinner() != null) {
                                msg.put("winnerName", auction.getWinner().getUsername());
                            }
                        }
                        finishedMessage = msg.toString();
                        removeAfterBroadcast = auction.getCurrentWinnerId() != null;
                    }
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "Error closing auction: " + auctionId, e);
                } finally {
                    lock.unlock();
                }

                if (finishedMessage != null) {
                    broadcastToRoom(auctionId, finishedMessage);
                }
                if (removeAfterBroadcast) {
                    removeAuction(auctionId);
                }
            }
        }
    }

    /**
     * Dừng scheduler khi server shutdown.
     */
    public void shutdown() {
        scheduler.shutdown();
        LOGGER.info("AuctionManager scheduler stopped.");
    }
}
