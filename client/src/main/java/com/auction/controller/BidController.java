package com.auction.controller;

import com.auction.chart.BidHistoryChart;
import com.auction.controller.LoginController.Session;
import com.auction.network.ServerConnection;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;
import java.util.logging.Logger;

/**
 * BidController - Màn hình đấu giá trực tiếp (realtime bidding).
 *
 * Chức năng:
 *   - Hiển thị thông tin phiên đấu giá (item, giá hiện tại, thời gian còn lại)
 *   - Đặt giá (PLACE_BID)
 *   - Nhận cập nhật realtime từ server (broadcast NEW_BID, AUCTION_FINISHED,
 *     AUCTION_EXTENDED) qua ServerConnection.setBroadcastHandler()
 *   - Hiển thị lịch sử bid (GET_BID_HISTORY)
 *   - Đồng hồ đếm ngược thời gian kết thúc phiên
 *   - Nút "Quay lại" danh sách
 */
public class BidController implements Initializable {

    private static final Logger LOGGER = Logger.getLogger(BidController.class.getName());

    // ── FXML fields ────────────────────────────────────────────────────────
    // Thông tin phiên
    @FXML private Label auctionIdLabel;
    @FXML private Label itemNameLabel;
    @FXML private Label itemTypeLabel;
    @FXML private Label itemDescLabel;
    @FXML private Label startingPriceLabel;
    @FXML private Label currentPriceLabel;
    @FXML private Label statusLabel;
    @FXML private Label countdownLabel;
    @FXML private Label currentLeaderLabel;
    @FXML private Label endTimeLabel;
    @FXML private Label startCountdownTitleLabel;
    @FXML private Label startCountdownLabel;

    // Đặt giá
    @FXML private TextField  bidAmountField;
    @FXML private Button     placeBidButton;
    @FXML private Label      bidResultLabel;

    // Auto-bid
    @FXML private TextField autoMaxBidField;
    @FXML private TextField autoIncrementField;
    @FXML private Button registerAutoBidButton;
    @FXML private Button cancelAutoBidButton;
    @FXML private Label autoBidStatusLabel;

    // Lịch sử bid
    @FXML private TableView<BidRow>         historyTable;
    @FXML private TableColumn<BidRow, String> colBidder;
    @FXML private TableColumn<BidRow, String> colAmount;
    @FXML private TableColumn<BidRow, String> colTime;

    // Navigation
    @FXML private Button backButton;

    // Biểu đồ giá
    @FXML private javafx.scene.layout.StackPane chartContainer;
    private BidHistoryChart bidHistoryChart;

    // ── State ──────────────────────────────────────────────────────────────
    private String auctionId;
    private String endTimeStr;
    private String startTimeStr;
    private boolean auctionFinished = false;

    private final ObservableList<BidRow> bidHistory = FXCollections.observableArrayList();
    private javafx.animation.Timeline countdownTimeline;
    private javafx.animation.Timeline startCountdownTimeline;

    // ── Initializable ──────────────────────────────────────────────────────

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        // Setup bảng lịch sử
        colBidder.setCellValueFactory(new PropertyValueFactory<>("bidder"));
        colAmount.setCellValueFactory(new PropertyValueFactory<>("amount"));
        colTime.setCellValueFactory(new PropertyValueFactory<>("time"));
        historyTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        historyTable.setItems(bidHistory);

        bidResultLabel.setVisible(false);
        autoBidStatusLabel.setVisible(false);
        hideStartCountdown();

        // Chỉ Bidder mới được đặt giá
        boolean canBid = Session.getInstance().isBidder();
        placeBidButton.setDisable(!canBid);
        bidAmountField.setDisable(!canBid);
        registerAutoBidButton.setDisable(!canBid);
        cancelAutoBidButton.setDisable(!canBid);
        autoMaxBidField.setDisable(!canBid);
        autoIncrementField.setDisable(!canBid);
        if (!canBid) {
            bidResultLabel.setText("Chỉ tài khoản BIDDER mới có thể đặt giá.");
            bidResultLabel.setVisible(true);
            autoBidStatusLabel.setText("Chỉ tài khoản BIDDER mới có thể dùng auto-bid.");
            autoBidStatusLabel.setVisible(true);
        }

        // Khởi tạo biểu đồ giá và nhúng vào chartContainer
        bidHistoryChart = new BidHistoryChart();
        if (chartContainer != null) {
            bidHistoryChart.getChart().setMaxHeight(Double.MAX_VALUE);
            bidHistoryChart.getChart().setMaxWidth(Double.MAX_VALUE);
            javafx.scene.layout.VBox.setVgrow(bidHistoryChart.getChart(), javafx.scene.layout.Priority.ALWAYS);
            chartContainer.getChildren().add(bidHistoryChart.getChart());
        }

        // Đăng ký nhận broadcast từ server
        ServerConnection.getInstance().setBroadcastHandler(this::handleBroadcast);
    }

    /**
     * Được gọi từ AuctionListController sau khi load FXML.
     * Truyền auctionId và load dữ liệu phiên.
     */
    public void setAuctionId(String auctionId) {
        this.auctionId = auctionId;
        auctionIdLabel.setText("Phiên: " + auctionId);
        new Thread(() -> {
            sendJoinAuctionRoom();
            loadAuctionDetailRequest();
            loadBidHistoryRequest();
            try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
            loadAutoBidStatusRequest();
        }, "load-bid-screen").start();
    }

    // ── Join auction room ──────────────────────────────────────────────────

    private void sendJoinAuctionRoom() {
        JSONObject data = new JSONObject();
        data.put("auctionId", auctionId);
        ServerConnection.getInstance().sendRequest("JOIN_AUCTION", data);
    }

    // ── Load dữ liệu ──────────────────────────────────────────────────────

    private void loadAuctionDetail() {
        new Thread(this::loadAuctionDetailRequest, "load-auction").start();
    }

    private void loadAuctionDetailRequest() {
        JSONObject data = new JSONObject();
        data.put("auctionId", auctionId);
        JSONObject response = ServerConnection.getInstance().sendRequest("GET_AUCTION", data);

        Platform.runLater(() -> {
            if (ServerConnection.isOk(response)) {
                JSONObject d = response.optJSONObject("data");
                if (d != null) updateAuctionUI(d);
            } else {
                showBidResult("Lỗi tải thông tin phiên đấu giá: " + response.optString("message"), false);
            }
        });
    }

    private void updateAuctionUI(JSONObject d) {
        itemNameLabel.setText(d.optString("itemName", "-"));
        String description = d.optString("itemDescription", "").trim();
        itemDescLabel.setText(description.isEmpty() ? "Chưa có mô tả sản phẩm." : description);
        itemTypeLabel.setText(d.optString("itemType", "-"));
        startingPriceLabel.setText(String.format("%.0f đ", d.optDouble("startingPrice")));
        currentPriceLabel.setText(String.format("%.0f đ", d.optDouble("currentPrice")));
        statusLabel.setText(d.optString("status", "-"));

        String status = d.optString("status");
        startTimeStr = d.optString("startTime");
        endTimeStr = d.optString("endTime");
        endTimeLabel.setText(formatDateTime(endTimeStr));

        String winnerId = d.optString("winnerId", "");
        String winnerName = d.optString("winnerName", winnerId);
        currentLeaderLabel.setText(winnerName.isEmpty() ? "Chưa có" : winnerName);

        // Màu trạng thái
        switch (status) {
            case "RUNNING"  -> {
                statusLabel.setTextFill(Color.GREEN);
                hideStartCountdown();
            }
            case "FINISHED" -> { statusLabel.setTextFill(Color.RED); finishAuction(d); }
            case "CANCELED", "PAID" -> { statusLabel.setTextFill(Color.RED); finishAuction(d); }
            case "OPEN"     -> {
                statusLabel.setTextFill(Color.ORANGE);
                startStartCountdown(startTimeStr);
            }
            default         -> statusLabel.setTextFill(Color.GRAY);
        }

        if ("RUNNING".equals(status)) {
            startCountdown(endTimeStr);
        } else if ("OPEN".equals(status)) {
            if (countdownTimeline != null) countdownTimeline.stop();
            countdownLabel.setText("Chưa bắt đầu");
            countdownLabel.setTextFill(Color.GRAY);
        }

        updateBidControlsForStatus(status);

        // Gợi ý giá đặt tối thiểu
        if (Session.getInstance().isBidder()
                && !"FINISHED".equals(status)
                && !"CANCELED".equals(status)
                && !"PAID".equals(status)) {
            double minBid = d.optDouble("currentPrice") + 1;
            bidAmountField.setPromptText("Tối thiểu: " + String.format("%.0f", minBid));
        }
    }

    private void loadBidHistory() {
        new Thread(this::loadBidHistoryRequest, "load-history").start();
    }

    private void loadBidHistoryRequest() {
        JSONObject data = new JSONObject();
        data.put("auctionId", auctionId);
        JSONObject response = ServerConnection.getInstance().sendRequest("GET_BID_HISTORY", data);

        Platform.runLater(() -> {
            if (ServerConnection.isOk(response)) {
                JSONObject d = response.optJSONObject("data");
                if (d != null) {
                    JSONArray history = d.optJSONArray("history");
                    parseHistory(history);
                }
            }
        });
    }

    private void parseHistory(JSONArray arr) {
        bidHistory.clear();
        if (arr == null) return;
        // Load vào biểu đồ
        java.util.List<BidHistoryChart.BidPoint> points = new java.util.ArrayList<>();
        // Hiển thị mới nhất lên đầu → duyệt ngược
        for (int i = arr.length() - 1; i >= 0; i--) {
            JSONObject tx = arr.optJSONObject(i);
            if (tx == null) continue;
            double amount = tx.optDouble("bidAmount");
            String tsStr  = tx.optString("timestamp");
            bidHistory.add(new BidRow(
                tx.optString("bidderName", tx.optString("bidderId")),
                String.format("%.0f đ", amount),
                formatDateTime(tsStr)
            ));
            // Thêm vào danh sách cho biểu đồ (parse LocalDateTime)
            try {
                java.time.LocalDateTime ts =
                    java.time.LocalDateTime.parse(tsStr.replace(" ", "T"));
                points.add(new BidHistoryChart.BidPoint(amount, ts));
            } catch (Exception ignored) {}
        }
        // Vẽ toàn bộ lịch sử lên biểu đồ (tự sort theo thời gian)
        bidHistoryChart.loadHistory(points);
    }

    // ── Place Bid ──────────────────────────────────────────────────────────

    @FXML
    private void handlePlaceBid(ActionEvent event) {
        String amountStr = bidAmountField.getText().trim();
        if (amountStr.isEmpty()) {
            showBidResult("Vui lòng nhập số tiền muốn đặt.", false);
            return;
        }

        long amount;
        try {
            amount = Long.parseLong(amountStr);
        } catch (NumberFormatException e) {
            showBidResult("Số tiền không hợp lệ, vui lòng chỉ nhập số nguyên.", false);
            return;
        }

        placeBidButton.setDisable(true);
        placeBidButton.setText("Đang xử lý...");
        bidResultLabel.setVisible(false);

        new Thread(() -> {
            JSONObject data = new JSONObject();
            data.put("auctionId", auctionId);
            data.put("bidAmount", amount);
            JSONObject response = ServerConnection.getInstance().sendRequest("PLACE_BID", data);

            Platform.runLater(() -> {
                placeBidButton.setDisable(false);
                placeBidButton.setText("Đặt giá");

                if (ServerConnection.isOk(response)) {
                    bidAmountField.clear();
                    showBidResult("✓ Đặt giá thành công: " + String.format("%.0f đ", amount), true);
                    loadBidHistory(); // Refresh lịch sử
                } else {
                    showBidResult("✗ " + response.optString("message", "Đặt giá thất bại."), false);
                }
            });
        }, "place-bid").start();
    }

    // ── Broadcast Handler (realtime) ───────────────────────────────────────

    /**
     * Nhận broadcast từ server (chạy trên JavaFX thread nhờ Platform.runLater
     * đã được xử lý trong ServerConnection).
     */
    private void handleBroadcast(JSONObject json) {
        String event = json.optString("event");
        String broadcastAuctionId = json.optString("auctionId");

        // Chỉ xử lý broadcast của phiên mình đang xem
        if (!auctionId.equals(broadcastAuctionId)) return;

        switch (event) {
            case "NEW_BID" -> {
                double newPrice    = json.optDouble("bidAmount");
                String bidderName  = json.optString("bidderName");
                String timestamp   = json.optString("timestamp");

                // Cập nhật giá realtime
                currentPriceLabel.setText(String.format("%.0f đ", newPrice));
                currentLeaderLabel.setText(bidderName);

                // Cập nhật biểu đồ realtime
                bidHistoryChart.addBidNow(newPrice);

                // Thêm vào đầu bảng lịch sử
                bidHistory.add(0, new BidRow(
                    bidderName,
                    String.format("%.0f đ", newPrice),
                    formatDateTime(timestamp)
                ));

                // Flash thông báo
                showBidResult("🔔 Bid mới: " + bidderName + " đặt "
                        + String.format("%.0f đ", newPrice), true);
            }
            case "AUTO_BID" -> {
                double newPrice = json.optDouble("amount");
                String bidderName = json.optString("bidderName", json.optString("bidderId"));
                String timestamp = json.optString("timestamp");

                currentPriceLabel.setText(String.format("%.0f đ", newPrice));
                currentLeaderLabel.setText(bidderName);
                bidHistoryChart.addBidNow(newPrice);

                bidHistory.add(0, new BidRow(
                    bidderName + " (auto)",
                    String.format("%.0f đ", newPrice),
                    formatDateTime(timestamp)
                ));

                showBidResult("Auto-bid: " + bidderName + " đặt "
                        + String.format("%.0f đ", newPrice), true);
            }
            case "AUCTION_FINISHED" -> {
                double finalPrice = json.optDouble("finalPrice");
                String winnerId   = json.optString("winnerName", json.optString("winnerId", "Không có"));
                String status     = json.optString("status", "FINISHED");
                finishAuctionByBroadcast(finalPrice, winnerId, status);
            }
            case "AUCTION_DELETED" -> {
                statusLabel.setText("DELETED");
                statusLabel.setTextFill(Color.RED);
                finishAuctionControls();
                showBidResult("Phiên đấu giá đã bị xóa.", false);
            }
            case "AUCTION_PAID" -> {
                statusLabel.setText("PAID");
                statusLabel.setTextFill(Color.GREEN);
                showBidResult("Phiên đã được xác nhận thanh toán.", true);
            }
            case "AUCTION_EXTENDED" -> {
                String newEndTime   = json.optString("newEndTime");
                int    extraSeconds = json.optInt("extraSeconds");
                endTimeStr = newEndTime;
                endTimeLabel.setText(formatDateTime(newEndTime));
                startCountdown(newEndTime); // Reset đồng hồ
                showBidResult("⏱ Phiên được gia hạn thêm " + extraSeconds + " giây!", true);
            }
        }
    }

    @FXML
    private void handleRegisterAutoBid(ActionEvent event) {
        String maxBidStr = autoMaxBidField.getText().trim();
        String incrementStr = autoIncrementField.getText().trim();

        if (maxBidStr.isEmpty() || incrementStr.isEmpty()) {
            showAutoBidStatus("Vui lòng nhập giá tối đa và bước giá.", false);
            return;
        }

        long maxBid;
        long increment;
        try {
            maxBid = Long.parseLong(maxBidStr);
            increment = Long.parseLong(incrementStr);
        } catch (NumberFormatException e) {
            showAutoBidStatus("Giá trị auto-bid phải là số nguyên.", false);
            return;
        }

        registerAutoBidButton.setDisable(true);
        registerAutoBidButton.setText("...");

        new Thread(() -> {
            JSONObject data = new JSONObject();
            data.put("auctionId", auctionId);
            data.put("maxBid", maxBid);
            data.put("increment", increment);
            JSONObject response = ServerConnection.getInstance().sendRequest("REGISTER_AUTO_BID", data);

            Platform.runLater(() -> {
                if (ServerConnection.isOk(response)) {
                    autoMaxBidField.clear();
                    autoIncrementField.clear();
                    registerAutoBidButton.setDisable(true);
                    registerAutoBidButton.setText("Bật");
                    cancelAutoBidButton.setDisable(false);
                    showAutoBidStatus("✓ Auto-bid đang bật. Max: "
                            + String.format("%.0f", maxBid)
                            + ", bước: " + String.format("%.0f", increment), true);
                    loadAutoBidStatusRequest();
                } else {
                    registerAutoBidButton.setDisable(false);
                    registerAutoBidButton.setText("Bật");
                    showAutoBidStatus("✗ " + response.optString("message", "Không thể bật auto-bid."), false);
                }
            });
        }, "register-auto-bid").start();
    }

    @FXML
    private void handleCancelAutoBid(ActionEvent event) {
        cancelAutoBidButton.setDisable(true);
        cancelAutoBidButton.setText("...");

        new Thread(() -> {
            JSONObject data = new JSONObject();
            data.put("auctionId", auctionId);
            JSONObject response = ServerConnection.getInstance().sendRequest("CANCEL_AUTO_BID", data);

            Platform.runLater(() -> {
                if (ServerConnection.isOk(response)) {
                    autoMaxBidField.clear();
                    autoIncrementField.clear();
                    cancelAutoBidButton.setDisable(true);
                    cancelAutoBidButton.setText("Tắt");
                    registerAutoBidButton.setDisable(false);
                    autoBidStatusLabel.setVisible(false);
                    autoBidStatusLabel.setText("");
                } else {
                    cancelAutoBidButton.setDisable(false);
                    cancelAutoBidButton.setText("Tắt");
                    showAutoBidStatus("✗ " + response.optString("message", "Không thể tắt auto-bid."), false);
                }
            });
        }, "cancel-auto-bid").start();
    }

    // ── Countdown Timer ────────────────────────────────────────────────────

    private void startCountdown(String endTimeRaw) {
        if (countdownTimeline != null) countdownTimeline.stop();

        countdownTimeline = new javafx.animation.Timeline(
            new javafx.animation.KeyFrame(
                javafx.util.Duration.seconds(1),
                e -> updateCountdown(endTimeRaw)
            )
        );
        countdownTimeline.setCycleCount(javafx.animation.Animation.INDEFINITE);
        countdownTimeline.play();
    }

    private void updateCountdown(String endTimeRaw) {
        try {
            java.time.LocalDateTime endTime =
                java.time.LocalDateTime.parse(endTimeRaw.replace(" ", "T"));
            java.time.Duration remaining =
                java.time.Duration.between(java.time.LocalDateTime.now(), endTime);

            if (remaining.isNegative()) {
                countdownLabel.setText("Đã kết thúc");
                countdownLabel.setTextFill(Color.RED);
                if (countdownTimeline != null) countdownTimeline.stop();
                loadAuctionDetail();
            } else {
                long hours   = remaining.toHours();
                long minutes = remaining.toMinutesPart();
                long seconds = remaining.toSecondsPart();
                countdownLabel.setText(String.format("%02d:%02d:%02d", hours, minutes, seconds));
                // Đổi màu khi < 60 giây
                countdownLabel.setTextFill(remaining.getSeconds() < 60 ? Color.RED : Color.BLACK);
            }
        } catch (Exception ex) {
            countdownLabel.setText("--:--:--");
        }
    }

    private void startStartCountdown(String startTimeRaw) {
        if (startCountdownTimeline != null) startCountdownTimeline.stop();

        startCountdownTitleLabel.setVisible(true);
        startCountdownTitleLabel.setManaged(true);
        startCountdownLabel.setVisible(true);
        startCountdownLabel.setManaged(true);
        updateStartCountdown(startTimeRaw);

        startCountdownTimeline = new javafx.animation.Timeline(
            new javafx.animation.KeyFrame(
                javafx.util.Duration.seconds(1),
                e -> updateStartCountdown(startTimeRaw)
            )
        );
        startCountdownTimeline.setCycleCount(javafx.animation.Animation.INDEFINITE);
        startCountdownTimeline.play();
    }

    private void updateStartCountdown(String startTimeRaw) {
        try {
            java.time.LocalDateTime startTime =
                java.time.LocalDateTime.parse(startTimeRaw.replace(" ", "T"));
            java.time.Duration remaining =
                java.time.Duration.between(java.time.LocalDateTime.now(), startTime);

            if (remaining.isNegative() || remaining.isZero()) {
                hideStartCountdown();
                loadAuctionDetail();
                return;
            }

            long hours = remaining.toHours();
            long minutes = remaining.toMinutesPart();
            long seconds = remaining.toSecondsPart();
            startCountdownLabel.setText(String.format("%02d:%02d:%02d", hours, minutes, seconds));
            startCountdownLabel.setTextFill(remaining.getSeconds() < 60 ? Color.RED : Color.ORANGE);
        } catch (Exception ex) {
            startCountdownLabel.setText("--:--:--");
        }
    }

    private void hideStartCountdown() {
        if (startCountdownTimeline != null) {
            startCountdownTimeline.stop();
            startCountdownTimeline = null;
        }
        if (startCountdownTitleLabel != null) {
            startCountdownTitleLabel.setVisible(false);
            startCountdownTitleLabel.setManaged(false);
        }
        if (startCountdownLabel != null) {
            startCountdownLabel.setVisible(false);
            startCountdownLabel.setManaged(false);
        }
    }

    // ── Finish Auction ─────────────────────────────────────────────────────

    private void finishAuction(JSONObject d) {
        if (auctionFinished) return;
        auctionFinished = true;
        finishAuctionControls();
    }

    private void finishAuctionByBroadcast(double finalPrice, String winnerId, String status) {
        if (auctionFinished && !status.equals(statusLabel.getText())) {
            statusLabel.setText(status);
        } else if (auctionFinished) {
            return;
        }
        auctionFinished = true;
        finishAuctionControls();
        statusLabel.setText(status);
        statusLabel.setTextFill(Color.RED);
        currentPriceLabel.setText(String.format("%.0f đ", finalPrice));
        currentLeaderLabel.setText(winnerId);
        countdownLabel.setText("Đã kết thúc");
        countdownLabel.setTextFill(Color.RED);
        if ("CANCELED".equals(status)) {
            showBidResult("Phiên đã kết thúc và bị hủy vì chưa có người thắng.", false);
        } else {
            showBidResult("Phiên kết thúc! Người thắng: " + winnerId
                    + " | Giá cuối: " + String.format("%.0f đ", finalPrice), true);
        }
    }

    private void finishAuctionControls() {
        if (countdownTimeline != null) countdownTimeline.stop();
        hideStartCountdown();
        placeBidButton.setDisable(true);
        bidAmountField.setDisable(true);
        registerAutoBidButton.setDisable(true);
        cancelAutoBidButton.setDisable(true);
        autoMaxBidField.setDisable(true);
        autoIncrementField.setDisable(true);
        countdownLabel.setText("Đã kết thúc");
        countdownLabel.setTextFill(Color.RED);
    }

    // ── Navigation ─────────────────────────────────────────────────────────

    @FXML
    private void handleBack(ActionEvent event) {
        // Rời khỏi phòng đấu giá
        new Thread(() -> {
            ServerConnection.getInstance().sendRequest("LEAVE_AUCTION", null);
        }).start();

        // Xóa broadcast handler và reset chart
        ServerConnection.getInstance().setBroadcastHandler(null);
        bidHistoryChart.reset();
        if (countdownTimeline != null) countdownTimeline.stop();
        hideStartCountdown();

        try {
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/com/auction/view/auction_list.fxml"));
            Parent root = loader.load();
            Stage stage = (Stage) backButton.getScene().getWindow();
            SceneUtil.setScene(stage, root, "Auction System - Danh sách đấu giá",
                    1000, 650, 900, 600);
        } catch (IOException e) {
            LOGGER.severe("Không thể tải giao diện danh sách đấu giá: " + e.getMessage());
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private void showBidResult(String message, boolean success) {
        bidResultLabel.setText(message);
        bidResultLabel.setTextFill(success ? Color.GREEN : Color.RED);
        bidResultLabel.setVisible(true);
    }

    private void showAutoBidStatus(String message, boolean success) {
        autoBidStatusLabel.setText(message);
        autoBidStatusLabel.setTextFill(success ? Color.GREEN : Color.RED);
        autoBidStatusLabel.setVisible(true);
        autoBidStatusLabel.setManaged(true);
    }

    private void loadAutoBidStatusRequest() {
        JSONObject data = new JSONObject();
        data.put("auctionId", auctionId);
        JSONObject response = ServerConnection.getInstance().sendRequest("GET_AUTO_BID_STATUS", data);

        Platform.runLater(() -> {
            if (ServerConnection.isOk(response)) {
                JSONObject d = response.optJSONObject("data");
                if (d != null && d.optBoolean("registered", false)) {
                    registerAutoBidButton.setDisable(true);
                    cancelAutoBidButton.setDisable(false);
                    showAutoBidStatus("Auto-bid đang bật. Max: "
                            + String.format("%.0f", d.optDouble("maxBid"))
                            + ", bước: " + String.format("%.0f", d.optDouble("increment")), true);
                } else {
                    registerAutoBidButton.setDisable(false);
                    cancelAutoBidButton.setDisable(true);
                }
            }
        });
    }

    private String formatDateTime(String raw) {
        if (raw == null || raw.isEmpty()) return "-";
        return raw.replace("T", " ").substring(0, Math.min(raw.length(), 16));
    }

    private void updateBidControlsForStatus(String status) {
        boolean bidderCanAct = Session.getInstance().isBidder()
                && "RUNNING".equals(status)
                && !auctionFinished;

        placeBidButton.setDisable(!bidderCanAct);
        bidAmountField.setDisable(!bidderCanAct);
        registerAutoBidButton.setDisable(!bidderCanAct);
        cancelAutoBidButton.setDisable(!bidderCanAct);
        autoMaxBidField.setDisable(!bidderCanAct);
        autoIncrementField.setDisable(!bidderCanAct);
    }

    // ── Inner class: BidRow ────────────────────────────────────────────────

    public static class BidRow {
        private final SimpleStringProperty bidder;
        private final SimpleStringProperty amount;
        private final SimpleStringProperty time;

        public BidRow(String bidder, String amount, String time) {
            this.bidder = new SimpleStringProperty(bidder);
            this.amount = new SimpleStringProperty(amount);
            this.time   = new SimpleStringProperty(time);
        }

        public String getBidder() { return bidder.get(); }
        public String getAmount() { return amount.get(); }
        public String getTime()   { return time.get(); }
    }
}
