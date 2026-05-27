package com.auction.network;

import com.auction.AutoBidder;
import com.auction.BidHistoryVisualizer;
import com.auction.manager.AuctionManager;
import com.auction.model.Auction;
import com.auction.model.BidTransaction;
import com.auction.model.user.User;
import com.auction.service.AuctionService;
import com.auction.service.UserService;
import com.auction.service.ItemService;
import com.auction.exception.AuctionClosedException;
import com.auction.exception.InvalidBidException;
import com.auction.exception.UserNotFoundException;

import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;

import java.io.*;
import java.net.Socket;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ClientHandler - Xử lý giao tiếp với một client cụ thể.
 * Chạy trong thread riêng, đọc request JSON và trả về response JSON.
 *
 * Giao thức: mỗi message là một dòng JSON (newline-delimited JSON).
 * Request format:  { "action": "LOGIN", "data": { ... } }
 * Response format: { "status": "OK" | "ERROR", "data": { ... }, "message": "..." }
 */
public class ClientHandler implements Runnable {

    private static final Logger LOGGER = Logger.getLogger(ClientHandler.class.getName());

    private final Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;

    // Services
    private final UserService userService;
    private final AuctionService auctionService;
    private final ItemService itemService;
    private final AuctionManager auctionManager;
    private final AutoBidder autoBidder;

    // Trạng thái client hiện tại
    private User currentUser = null;
    private String currentAuctionId = null; // phiên đang xem (để nhận broadcast)

    public ClientHandler(Socket socket) {
        this.socket = socket;
        this.userService = new UserService();
        this.auctionService = new AuctionService();
        this.itemService = new ItemService();
        this.auctionManager = AuctionManager.getInstance();
        this.autoBidder = AutoBidder.getInstance();
    }

    @Override
    public void run() {
        try {
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);

            LOGGER.info("ClientHandler started for: " + socket.getInetAddress());

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                String response = handleRequest(line.trim());
                sendMessage(response); // dùng synchronized để tránh race với broadcast
            }

        } catch (IOException e) {
            LOGGER.log(Level.INFO, "Client disconnected: " + socket.getInetAddress());
        } finally {
            cleanup();
        }
    }

    /**
     * Phân tích request JSON và điều hướng đến handler tương ứng.
     */
    private String handleRequest(String rawJson) {
        try {
            JSONObject request = new JSONObject(rawJson);
            String action = request.getString("action").toUpperCase();
            JSONObject data = request.optJSONObject("data");
            if (data == null) data = new JSONObject();

            return switch (action) {
                // ── Auth ──
                case "LOGIN"    -> handleLogin(data);
                case "REGISTER" -> handleRegister(data);
                case "LOGOUT"   -> handleLogout();

                // ── Auction ──
                case "GET_AUCTIONS"      -> handleGetAuctions();
                case "GET_AUCTION"       -> handleGetAuction(data);
                case "CREATE_AUCTION"    -> handleCreateAuction(data);
                case "JOIN_AUCTION"      -> handleJoinAuction(data);
                case "LEAVE_AUCTION"     -> handleLeaveAuction();
                case "PLACE_BID"         -> handlePlaceBid(data);
                case "GET_BID_HISTORY"   -> handleGetBidHistory(data);
                case "GET_PRICE_CHART"   -> handleGetPriceChart(data);
                case "CANCEL_AUCTION"    -> handleCancelAuction(data);
                case "DELETE_AUCTION"    -> handleDeleteAuction(data);
                case "MARK_AUCTION_PAID" -> handleMarkAuctionPaid(data);
                case "REGISTER_AUTO_BID" -> handleRegisterAutoBid(data);
                case "CANCEL_AUTO_BID"   -> handleCancelAutoBid(data);
                case "GET_AUTO_BID_STATUS" -> handleGetAutoBidStatus(data);

                // ── Item ──
                case "GET_ITEMS"    -> handleGetItems();
                case "CREATE_ITEM"  -> handleCreateItem(data);
                case "UPDATE_ITEM"  -> handleUpdateItem(data);
                case "DELETE_ITEM"  -> handleDeleteItem(data);

                // ── Admin ──
                case "GET_ALL_USERS" -> handleGetAllUsers();
                case "BAN_USER"      -> handleBanUser(data);
                case "UNBAN_USER"    -> handleUnbanUser(data);

                default -> errorResponse("Hành động không được hỗ trợ: " + action);
            };

        } catch (JSONException e) {
            LOGGER.log(Level.WARNING, "Invalid JSON from client: " + rawJson, e);
            return errorResponse("Dữ liệu gửi lên không đúng định dạng");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Unexpected error handling request", e);
            return errorResponse("Lỗi máy chủ nội bộ: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────
    //  AUTH HANDLERS
    // ─────────────────────────────────────────────

    private String handleLogin(JSONObject data) {
        String username = data.optString("username", "");
        String password = data.optString("password", "");

        if (username.isBlank() || password.isBlank()) {
            return errorResponse("Vui lòng nhập tên đăng nhập và mật khẩu");
        }

        try {
            User user = userService.login(username, password);
            currentUser = user;
            // Đăng ký client với AuctionManager để nhận broadcast
            auctionManager.registerClient(this);

            JSONObject result = new JSONObject();
            result.put("userId", user.getId());
            result.put("username", user.getUsername());
            result.put("role", user.getRole());
            return successResponse(result, "Đăng nhập thành công");

        } catch (UserNotFoundException e) {
            return errorResponse("Tên đăng nhập hoặc mật khẩu không đúng");
        } catch (Exception e) {
            return errorResponse("Đăng nhập thất bại: " + e.getMessage());
        }
    }

    private String handleRegister(JSONObject data) {
        String username = data.optString("username", "");
        String password = data.optString("password", "");
        String email    = data.optString("email", "");
        String role     = data.optString("role", "BIDDER");

        if (username.isBlank() || password.isBlank() || email.isBlank()) {
            return errorResponse("Vui lòng nhập đầy đủ tên đăng nhập, mật khẩu và email");
        }

        try {
            userService.register(username, password, email, role);
            return successResponse(null, "Đăng ký thành công");
        } catch (Exception e) {
            return errorResponse("Đăng ký thất bại: " + e.getMessage());
        }
    }

    private String handleLogout() {
        if (currentUser != null) {
            auctionManager.unregisterClient(this);
            if (currentAuctionId != null) {
                auctionManager.leaveAuctionRoom(currentAuctionId, this);
                currentAuctionId = null;
            }
            currentUser = null;
        }
        return successResponse(null, "Đăng xuất thành công");
    }

    // ─────────────────────────────────────────────
    //  AUCTION HANDLERS
    // ─────────────────────────────────────────────

    private String handleGetAuctions() {
        try {
            List<Auction> auctions = auctionService.getAllAuctions();
            JSONArray arr = new JSONArray();
            for (Auction a : auctions) {
                auctionManager.addAuction(a);
                arr.put(auctionToJson(a));
            }
            JSONObject result = new JSONObject();
            result.put("auctions", arr);
            return successResponse(result, "OK");
        } catch (Exception e) {
            return errorResponse("Không thể tải danh sách phiên đấu giá: " + e.getMessage());
        }
    }

    private String handleGetAuction(JSONObject data) {
        String auctionId = data.optString("auctionId", "");
        if (auctionId.isBlank()) return errorResponse("Thiếu mã phiên đấu giá");

        try {
            Auction auction = auctionService.getAuctionById(auctionId);
            auctionManager.addAuction(auction);
            return successResponse(auctionToJson(auction), "OK");
        } catch (Exception e) {
            return errorResponse("Không tìm thấy phiên đấu giá: " + e.getMessage());
        }
    }

    private String handleCreateAuction(JSONObject data) {
        if (!isLoggedIn()) return errorResponse("Bạn chưa đăng nhập");
        if (!currentUser.getRole().equals("SELLER") && !currentUser.getRole().equals("ADMIN")) {
            return errorResponse("Chỉ SELLER hoặc ADMIN mới có thể tạo phiên đấu giá");
        }

        try {
            String itemId       = data.getString("itemId");
            double startingPrice = data.getDouble("startingPrice");
            String startTime    = data.getString("startTime");
            String endTime      = data.getString("endTime");

            if (!isWholeMoney(startingPrice)) {
                return errorResponse("Giá khởi điểm phiên đấu giá phải là số nguyên");
            }

            Auction auction = auctionService.createAuction(
                itemId, currentUser.getId(), startingPrice, startTime, endTime
            );
            return successResponse(auctionToJson(auction), "Tạo phiên đấu giá thành công");
        } catch (Exception e) {
            return errorResponse("Tạo phiên đấu giá thất bại: " + e.getMessage());
        }
    }

    /**
     * Client tham gia xem một phiên đấu giá (subscribe để nhận broadcast).
     */
    private String handleJoinAuction(JSONObject data) {
        String auctionId = data.optString("auctionId", "");
        if (auctionId.isBlank()) return errorResponse("Thiếu mã phiên đấu giá");

        // Rời phòng cũ nếu có
        if (currentAuctionId != null) {
            auctionManager.leaveAuctionRoom(currentAuctionId, this);
        }
        try {
            auctionManager.addAuction(auctionService.getAuctionById(auctionId));
        } catch (Exception e) {
            return errorResponse("Không tìm thấy phiên đấu giá: " + e.getMessage());
        }
        currentAuctionId = auctionId;
        auctionManager.joinAuctionRoom(auctionId, this);

        return successResponse(null, "Đã vào phòng đấu giá " + auctionId);
    }

    private String handleLeaveAuction() {
        if (currentAuctionId != null) {
            auctionManager.leaveAuctionRoom(currentAuctionId, this);
            currentAuctionId = null;
        }
        return successResponse(null, "Đã rời phòng đấu giá");
    }

    /**
     * Đặt giá - phần quan trọng nhất, cần thread-safe.
     */
    private String handlePlaceBid(JSONObject data) {
        if (!isLoggedIn()) return errorResponse("Bạn chưa đăng nhập");
        if (!currentUser.getRole().equals("BIDDER")) {
            return errorResponse("Chỉ tài khoản BIDDER mới có thể đặt giá");
        }

        String auctionId = data.optString("auctionId", "");
        double bidAmount = data.optDouble("bidAmount", -1);

        if (auctionId.isBlank()) return errorResponse("Thiếu mã phiên đấu giá");
        if (bidAmount <= 0)      return errorResponse("Số tiền đặt giá không hợp lệ, phải lớn hơn 0");
        if (!isWholeMoney(bidAmount)) return errorResponse("Số tiền đặt giá phải là số nguyên");

        try {
            BidTransaction tx = auctionService.placeBid(auctionId, currentUser.getId(), bidAmount);

            return successResponse(bidTransactionToJson(tx), "Đặt giá thành công");

        } catch (AuctionClosedException e) {
            return errorResponse("Phiên đấu giá đã đóng: " + e.getMessage());
        } catch (InvalidBidException e) {
            return errorResponse("Giá đặt không hợp lệ: " + e.getMessage());
        } catch (Exception e) {
            return errorResponse("Đặt giá thất bại: " + e.getMessage());
        }
    }

    private String handleGetBidHistory(JSONObject data) {
        String auctionId = data.optString("auctionId", "");
        if (auctionId.isBlank()) return errorResponse("Thiếu mã phiên đấu giá");

        try {
            List<BidTransaction> history = auctionService.getBidHistory(auctionId);
            JSONArray arr = new JSONArray();
            for (BidTransaction tx : history) arr.put(bidTransactionToJson(tx));
            JSONObject result = new JSONObject();
            result.put("history", arr);
            return successResponse(result, "OK");
        } catch (Exception e) {
            return errorResponse("Không thể tải lịch sử đặt giá: " + e.getMessage());
        }
    }

    private String handleGetPriceChart(JSONObject data) {
        String auctionId = data.optString("auctionId", "");
        if (auctionId.isBlank()) return errorResponse("Thiếu mã phiên đấu giá");

        try {
            BidHistoryVisualizer visualizer = BidHistoryVisualizer.getInstance();
            if (visualizer.getPoints(auctionId).isEmpty()) {
                visualizer.loadFromDatabase(auctionId);
            }
            return successResponse(new JSONObject(visualizer.getChartDataJson(auctionId)), "OK");
        } catch (Exception e) {
            return errorResponse("Không thể tải dữ liệu biểu đồ giá: " + e.getMessage());
        }
    }

    private String handleCancelAuction(JSONObject data) {
        if (!isLoggedIn()) return errorResponse("Bạn chưa đăng nhập");

        String auctionId = data.optString("auctionId", "");
        if (auctionId.isBlank()) return errorResponse("Thiếu mã phiên đấu giá");

        try {
            auctionService.cancelAuction(auctionId, currentUser.getId());

            JSONObject msg = new JSONObject();
            msg.put("event", "AUCTION_FINISHED");
            msg.put("auctionId", auctionId);
            msg.put("status", "CANCELED");
            msg.put("finalPrice", auctionService.getAuctionById(auctionId).getCurrentPrice());
            auctionManager.broadcastToRoom(auctionId, msg.toString());

            return successResponse(null, "Đã hủy phiên đấu giá");
        } catch (Exception e) {
            return errorResponse("Hủy phiên thất bại: " + e.getMessage());
        }
    }

    private String handleDeleteAuction(JSONObject data) {
        if (!isLoggedIn()) return errorResponse("Bạn chưa đăng nhập");

        String auctionId = data.optString("auctionId", "");
        if (auctionId.isBlank()) return errorResponse("Thiếu mã phiên đấu giá");

        try {
            auctionService.deleteAuction(auctionId, currentUser.getId());

            JSONObject msg = new JSONObject();
            msg.put("event", "AUCTION_DELETED");
            msg.put("auctionId", auctionId);
            auctionManager.broadcastToRoom(auctionId, msg.toString());

            return successResponse(null, "Đã xóa phiên đấu giá");
        } catch (Exception e) {
            return errorResponse("Xóa phiên thất bại: " + e.getMessage());
        }
    }

    private String handleMarkAuctionPaid(JSONObject data) {
        if (!isLoggedIn()) return errorResponse("Bạn chưa đăng nhập");

        String auctionId = data.optString("auctionId", "");
        if (auctionId.isBlank()) return errorResponse("Thiếu mã phiên đấu giá");

        try {
            auctionService.markAuctionPaid(auctionId, currentUser.getId());

            JSONObject msg = new JSONObject();
            msg.put("event", "AUCTION_PAID");
            msg.put("auctionId", auctionId);
            msg.put("status", "PAID");
            auctionManager.broadcastToRoom(auctionId, msg.toString());

            return successResponse(null, "Đã xác nhận thanh toán");
        } catch (Exception e) {
            return errorResponse("Thanh toán thất bại: " + e.getMessage());
        }
    }

    private String handleRegisterAutoBid(JSONObject data) {
        if (!isLoggedIn()) return errorResponse("Bạn chưa đăng nhập");
        if (!currentUser.getRole().equals("BIDDER")) {
            return errorResponse("Chỉ tài khoản BIDDER mới có thể đăng ký auto-bid");
        }

        String auctionId = data.optString("auctionId", "");
        double maxBid = data.optDouble("maxBid", -1);
        double increment = data.optDouble("increment", -1);

        if (auctionId.isBlank()) return errorResponse("Thiếu mã phiên đấu giá");
        if (maxBid <= 0) return errorResponse("Giá tối đa phải lớn hơn 0");
        if (increment <= 0) return errorResponse("Bước giá phải lớn hơn 0");
        if (!isWholeMoney(maxBid) || !isWholeMoney(increment)) {
            return errorResponse("Giá tối đa và bước giá phải là số nguyên");
        }

        try {
            autoBidder.register(auctionId, currentUser.getId(), maxBid, increment);
            JSONObject result = new JSONObject();
            result.put("auctionId", auctionId);
            result.put("maxBid", maxBid);
            result.put("increment", increment);
            return successResponse(result, "Đăng ký auto-bid thành công");
        } catch (Exception e) {
            return errorResponse("Đăng ký auto-bid thất bại: " + e.getMessage());
        }
    }

    private String handleCancelAutoBid(JSONObject data) {
        if (!isLoggedIn()) return errorResponse("Bạn chưa đăng nhập");
        if (!currentUser.getRole().equals("BIDDER")) {
            return errorResponse("Chỉ tài khoản BIDDER mới có thể hủy auto-bid");
        }

        String auctionId = data.optString("auctionId", "");
        if (auctionId.isBlank()) return errorResponse("Thiếu mã phiên đấu giá");

        autoBidder.cancel(auctionId, currentUser.getId());
        return successResponse(null, "Đã hủy auto-bid");
    }

    private String handleGetAutoBidStatus(JSONObject data) {
        if (!isLoggedIn()) return errorResponse("Bạn chưa đăng nhập");

        String auctionId = data.optString("auctionId", "");
        if (auctionId.isBlank()) return errorResponse("Thiếu mã phiên đấu giá");

        JSONObject result = new JSONObject();
        java.util.Optional<AutoBidder.AutoBidEntry> entry =
                autoBidder.getRegistration(auctionId, currentUser.getId());
        result.put("registered", entry.isPresent());
        entry.ifPresent(e -> {
            result.put("maxBid", e.getMaxBid());
            result.put("increment", e.getIncrement());
        });
        return successResponse(result, "OK");
    }

    // ─────────────────────────────────────────────
    //  ITEM HANDLERS
    // ─────────────────────────────────────────────

    private String handleGetItems() {
        if (!isLoggedIn()) return errorResponse("Bạn chưa đăng nhập");
        if (!currentUser.getRole().equals("SELLER") && !currentUser.getRole().equals("ADMIN")) {
            return errorResponse("Chỉ SELLER hoặc ADMIN mới có thể xem danh sách sản phẩm quản lý");
        }

        try {
            List<com.auction.model.item.Item> items = currentUser.getRole().equals("ADMIN")
                    ? itemService.getAllItems()
                    : itemService.getItemsBySeller(currentUser.getId());
            JSONArray arr = new JSONArray();
            for (com.auction.model.item.Item item : items) {
                JSONObject obj = new JSONObject();
                obj.put("itemId",        item.getId());
                obj.put("name",          item.getName());
                obj.put("description",   item.getDescription());
                obj.put("startingPrice", item.getStartingPrice());
                obj.put("type",          item.getType());
                obj.put("sellerId",      item.getSellerId() != null ? item.getSellerId() : "");
                arr.put(obj);
            }
            JSONObject result = new JSONObject();
            result.put("items", arr);
            return successResponse(result, "OK");
        } catch (Exception e) {
            return errorResponse("Không thể tải danh sách sản phẩm: " + e.getMessage());
        }
    }

    private String handleCreateItem(JSONObject data) {
        if (!isLoggedIn()) return errorResponse("Bạn chưa đăng nhập");
        if (!currentUser.getRole().equals("SELLER") && !currentUser.getRole().equals("ADMIN")) {
            return errorResponse("Chỉ SELLER hoặc ADMIN mới có thể thêm sản phẩm");
        }
        try {
            String type        = data.getString("type");
            String name        = data.getString("name");
            String description = data.getString("description");
            double startPrice  = data.getDouble("startingPrice");
            itemService.createItem(type, name, description, startPrice, currentUser.getId());
            return successResponse(null, "Thêm sản phẩm thành công");
        } catch (Exception e) {
            return errorResponse("Thêm sản phẩm thất bại: " + e.getMessage());
        }
    }

    private String handleUpdateItem(JSONObject data) {
        if (!isLoggedIn()) return errorResponse("Bạn chưa đăng nhập");
        try {
            String itemId      = data.getString("itemId");
            String type        = data.optString("type", null);
            String name        = data.optString("name", null);
            String description = data.optString("description", null);
            double startPrice  = data.optDouble("startingPrice", -1);
            if (!isWholeMoney(startPrice)) {
                return errorResponse("Giá khởi điểm phải là số nguyên");
            }
            itemService.updateItem(itemId, type, name, description, startPrice, currentUser.getId());
            return successResponse(null, "Cập nhật sản phẩm thành công");
        } catch (Exception e) {
            return errorResponse("Cập nhật sản phẩm thất bại: " + e.getMessage());
        }
    }

    private String handleDeleteItem(JSONObject data) {
        if (!isLoggedIn()) return errorResponse("Bạn chưa đăng nhập");
        try {
            String itemId = data.getString("itemId");
            itemService.deleteItem(itemId, currentUser.getId());
            return successResponse(null, "Xóa sản phẩm thành công");
        } catch (Exception e) {
            return errorResponse("Xóa sản phẩm thất bại: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────
    //  ADMIN HANDLERS
    // ─────────────────────────────────────────────

    private String handleGetAllUsers() {
        if (!isAdmin()) return errorResponse("Chỉ ADMIN mới có quyền thực hiện thao tác này");
        try {
            List<User> users = userService.getAllUsers();
            JSONArray arr = new JSONArray();
            for (User u : users) {
                JSONObject obj = new JSONObject();
                obj.put("userId", u.getId());
                obj.put("username", u.getUsername());
                obj.put("email", u.getEmail());
                obj.put("role", u.getRole());
                obj.put("banned", u.isBanned());
                arr.put(obj);
            }
            JSONObject result = new JSONObject();
            result.put("users", arr);
            return successResponse(result, "OK");
        } catch (Exception e) {
            return errorResponse("Không thể tải danh sách người dùng: " + e.getMessage());
        }
    }

    private String handleBanUser(JSONObject data) {
        if (!isAdmin()) return errorResponse("Chỉ ADMIN mới có quyền thực hiện thao tác này");
        try {
            String userId = data.getString("userId");
            userService.banUser(userId);
            return successResponse(null, "Đã khóa tài khoản người dùng thành công");
        } catch (Exception e) {
            return errorResponse("Khóa tài khoản thất bại: " + e.getMessage());
        }
    }

    private String handleUnbanUser(JSONObject data) {
        if (!isAdmin()) return errorResponse("Chỉ ADMIN mới có quyền thực hiện thao tác này");
        try {
            String userId = data.getString("userId");
            userService.unbanUser(userId);
            return successResponse(null, "Đã mở khóa tài khoản người dùng thành công");
        } catch (Exception e) {
            return errorResponse("Mở khóa tài khoản thất bại: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────
    //  BROADCAST (được gọi từ AuctionManager)
    // ─────────────────────────────────────────────

    /**
     * Gửi message broadcast đến client này (Observer Pattern).
     * Thread-safe vì PrintWriter.println là synchronized.
     */
    public synchronized void sendMessage(String message) {
        if (writer != null && !socket.isClosed()) {
            writer.println(message);
        }
    }

    // ─────────────────────────────────────────────
    //  UTILITIES
    // ─────────────────────────────────────────────

    private boolean isLoggedIn() {
        return currentUser != null;
    }

    private boolean isAdmin() {
        return isLoggedIn() && "ADMIN".equals(currentUser.getRole());
    }

    private void cleanup() {
        try {
            if (currentUser != null) {
                auctionManager.unregisterClient(this);
                if (currentAuctionId != null) {
                    auctionManager.leaveAuctionRoom(currentAuctionId, this);
                }
            }
            if (!socket.isClosed()) socket.close();
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Error during cleanup", e);
        }
    }

    private JSONObject auctionToJson(Auction a) {
        JSONObject obj = new JSONObject();
        obj.put("auctionId",     a.getId());
        obj.put("itemId",        a.getItem().getId());
        obj.put("itemName",      a.getItem().getName());
        obj.put("itemType",      a.getItem().getType());
        obj.put("itemDescription", a.getItem().getDescription() != null ? a.getItem().getDescription() : "");
        obj.put("sellerId",      a.getSeller() != null ? a.getSeller().getId() : "");
        obj.put("currentPrice",  a.getCurrentPrice());
        obj.put("startingPrice", a.getStartingPrice());
        obj.put("status",        a.getStatus().name());
        obj.put("startTime",     a.getStartTime().toString());
        obj.put("endTime",       a.getEndTime().toString());
        if (a.getWinner() != null) {
            obj.put("winnerId",   a.getWinner().getId());
            obj.put("winnerName", a.getWinner().getUsername());
        } else if (a.getCurrentWinnerId() != null) {
            obj.put("winnerId", a.getCurrentWinnerId());
        }
        return obj;
    }

    private JSONObject bidTransactionToJson(BidTransaction tx) {
        JSONObject obj = new JSONObject();
        obj.put("transactionId", tx.getId());
        obj.put("auctionId",     tx.getAuctionId());
        obj.put("bidderId",      tx.getBidderId());
        try {
            User bidder = userService.getUserById(tx.getBidderId());
            obj.put("bidderName", bidder.getUsername());
        } catch (UserNotFoundException e) {
            obj.put("bidderName", tx.getBidderId());
        }
        obj.put("bidAmount",     tx.getBidAmount());
        obj.put("timestamp",     tx.getTimestamp().toString());
        obj.put("autoBid",       tx.isAutoBid());
        return obj;
    }

    private boolean isWholeMoney(double amount) {
        return amount > 0 && Math.rint(amount) == amount;
    }

    private String successResponse(JSONObject data, String message) {
        JSONObject resp = new JSONObject();
        resp.put("status", "OK");
        resp.put("message", message);
        if (data != null) resp.put("data", data);
        return resp.toString();
    }

    private String errorResponse(String message) {
        JSONObject resp = new JSONObject();
        resp.put("status", "ERROR");
        resp.put("message", message);
        return resp.toString();
    }

    public User getCurrentUser() { return currentUser; }
    public String getCurrentAuctionId() { return currentAuctionId; }
}