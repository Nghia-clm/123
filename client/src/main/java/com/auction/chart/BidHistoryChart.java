package com.auction.chart;

import javafx.application.Platform;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Tooltip;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * BidHistoryChart - Biểu đồ đường (Line Chart) hiển thị lịch sử giá đấu
 * theo thời gian thực.
 *
 * Trục X: Số giây kể từ bid đầu tiên (tự động scale).
 * Trục Y: Giá đấu (đồng).
 *
 * Cách dùng trong BidController:
 * <pre>
 *   BidHistoryChart chart = new BidHistoryChart();
 *   chartContainer.getChildren().add(chart.getChart());
 *
 *   // Khi có bid cũ (load lịch sử):
 *   chart.addBid(amount, timestamp);
 *
 *   // Khi có bid mới realtime:
 *   chart.addBidNow(amount);
 *
 *   // Reset khi chuyển phiên:
 *   chart.reset();
 * </pre>
 */
public class BidHistoryChart {

    private final LineChart<Number, Number> lineChart;
    private final XYChart.Series<Number, Number> series;
    private final NumberAxis xAxis;
    private final NumberAxis yAxis;

    /** Thời điểm bid đầu tiên — dùng làm gốc trục X (= 0 giây). */
    private LocalDateTime firstBidTime = null;

    /** Đếm số bid đã thêm (để đặt tên điểm nếu cần). */
    private final AtomicLong bidCount = new AtomicLong(0);

    public BidHistoryChart() {
        // ── Trục X: thời gian (giây) ──
        xAxis = new NumberAxis();
        xAxis.setLabel("Thời gian (giây)");
        xAxis.setAutoRanging(false);
        xAxis.setLowerBound(0);
        xAxis.setUpperBound(60);
        xAxis.setTickUnit(15);
        xAxis.setForceZeroInRange(false);
        xAxis.setMinorTickVisible(false);
        xAxis.setTickLabelRotation(-35);
        xAxis.setTickLabelFormatter(new javafx.util.StringConverter<Number>() {
            @Override public String toString(Number n) {
                long s = n.longValue();
                if (s < 60) return s + "s";
                return (s / 60) + "m" + (s % 60 == 0 ? "" : (s % 60) + "s");
            }
            @Override public Number fromString(String s) { return 0; }
        });

        // ── Trục Y: giá (đồng) ──
        yAxis = new NumberAxis();
        yAxis.setLabel("Giá đấu (đồng)");
        yAxis.setAutoRanging(true);
        yAxis.setForceZeroInRange(false);
        yAxis.setTickLabelFormatter(new javafx.util.StringConverter<Number>() {
            @Override public String toString(Number n) {
                long val = n.longValue();
                if (val >= 1_000_000) return String.format("%.1fM", val / 1_000_000.0);
                if (val >= 1_000)     return String.format("%.0fK", val / 1_000.0);
                return String.valueOf(val);
            }
            @Override public Number fromString(String s) { return 0; }
        });

        // ── Series ──
        series = new XYChart.Series<>();
        series.setName("Giá đấu");

        // ── LineChart ──
        lineChart = new LineChart<>(xAxis, yAxis);
        lineChart.setTitle("Biểu đồ giá theo thời gian");
        lineChart.setAnimated(false);        // Tắt animation để update nhanh
        lineChart.setCreateSymbols(true);    // Hiện chấm tròn tại mỗi điểm bid
        lineChart.setLegendVisible(false);
        lineChart.setHorizontalGridLinesVisible(true);
        lineChart.setVerticalGridLinesVisible(true);
        lineChart.getData().add(series);

        // Style
        lineChart.setStyle("-fx-background-color: transparent;");
        lineChart.setMinHeight(220);
        lineChart.setMaxWidth(Double.MAX_VALUE);
        lineChart.lookup(".chart-plot-background")
                 .setStyle("-fx-background-color: #fafafa;");

        // Tô màu đường giá
        applySeriesStyle();
    }

    // ── Public API ─────────────────────────────────────────────────────────

    /**
     * Trả về node LineChart để nhúng vào FXML (chartContainer).
     */
    public LineChart<Number, Number> getChart() {
        return lineChart;
    }

    /**
     * Thêm điểm bid với timestamp tùy ý (dùng khi load lịch sử cũ).
     * Phải gọi trên JavaFX thread hoặc wrap bằng Platform.runLater().
     *
     * @param bidAmount  số tiền đặt giá
     * @param bidTime    thời điểm đặt giá
     */
    public void addBid(double bidAmount, LocalDateTime bidTime) {
        ensureOnFxThread(() -> {
            if (firstBidTime == null) firstBidTime = bidTime;
            long secondsFromStart = ChronoUnit.SECONDS.between(firstBidTime, bidTime);
            addDataPoint(secondsFromStart, bidAmount);
        });
    }

    /**
     * Thêm điểm bid tại thời điểm hiện tại (dùng khi nhận broadcast NEW_BID).
     *
     * @param bidAmount  số tiền đặt giá
     */
    public void addBidNow(double bidAmount) {
        addBid(bidAmount, LocalDateTime.now());
    }

    /**
     * Xóa toàn bộ dữ liệu (gọi khi thoát phiên hoặc đổi phiên).
     */
    public void reset() {
        ensureOnFxThread(() -> {
            series.getData().clear();
            firstBidTime = null;
            bidCount.set(0);
            resetXAxisBounds();
        });
    }

    /**
     * Load toàn bộ lịch sử bid (gọi một lần sau GET_BID_HISTORY).
     * Tự động sort theo thời gian tăng dần.
     *
     * @param bids  danh sách cặp (amount, timestamp)
     */
    public void loadHistory(java.util.List<BidPoint> bids) {
        ensureOnFxThread(() -> {
            series.getData().clear();
            firstBidTime = null;
            bidCount.set(0);
            resetXAxisBounds();
            // Sắp xếp tăng dần theo thời gian trước khi vẽ
            bids.stream()
                .sorted(java.util.Comparator.comparing(BidPoint::getTime))
                .forEach(p -> addBid(p.getAmount(), p.getTime()));
        });
    }

    // ── Private helpers ────────────────────────────────────────────────────

    private void addDataPoint(long xSeconds, double yAmount) {
        XYChart.Data<Number, Number> dataPoint =
            new XYChart.Data<>(xSeconds, yAmount);
        series.getData().add(dataPoint);
        bidCount.incrementAndGet();
        updateXAxisBounds(xSeconds);

        // Tooltip hiện khi hover vào chấm
        installTooltip(dataPoint, xSeconds, yAmount);
    }

    private void updateXAxisBounds(long xSeconds) {
        long upper = Math.max(60, roundUp(xSeconds + 10, 10));
        xAxis.setLowerBound(0);
        xAxis.setUpperBound(upper);
        xAxis.setTickUnit(chooseTickUnit(upper));
    }

    private void resetXAxisBounds() {
        xAxis.setLowerBound(0);
        xAxis.setUpperBound(60);
        xAxis.setTickUnit(15);
    }

    private long roundUp(long value, long step) {
        return ((value + step - 1) / step) * step;
    }

    private long chooseTickUnit(long upper) {
        if (upper <= 60) return 15;
        if (upper <= 180) return 30;
        if (upper <= 600) return 60;
        if (upper <= 1800) return 300;
        return 600;
    }

    private void installTooltip(XYChart.Data<Number, Number> dataPoint,
                                 long xSeconds, double yAmount) {
        // Tooltip gắn sau khi node được tạo
        dataPoint.nodeProperty().addListener((obs, oldNode, newNode) -> {
            if (newNode != null) {
                Tooltip tip = new Tooltip(
                    String.format("Giá: %,.0f đ%nThời điểm: +%ds", yAmount, xSeconds)
                );
                tip.setShowDelay(javafx.util.Duration.millis(100));
                Tooltip.install(newNode, tip);

                // Highlight khi hover
                newNode.setOnMouseEntered(e ->
                    newNode.setStyle("-fx-background-color: #e74c3c, white; -fx-background-radius: 6px;"));
                newNode.setOnMouseExited(e ->
                    newNode.setStyle(""));
            }
        });
    }

    private void applySeriesStyle() {
        // Chạy sau khi scene được attach (dùng listener)
        lineChart.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                Platform.runLater(() -> {
                    // Đường giá màu đỏ cam
                    javafx.scene.Node line = lineChart.lookup(".chart-series-line");
                    if (line != null) {
                        line.setStyle("-fx-stroke: #e74c3c; -fx-stroke-width: 2.5px;");
                    }
                });
            }
        });
    }

    private void ensureOnFxThread(Runnable action) {
        if (Platform.isFxApplicationThread()) {
            action.run();
        } else {
            Platform.runLater(action);
        }
    }

    // ── Inner class: BidPoint ──────────────────────────────────────────────

    /**
     * DTO đơn giản chứa một lần đặt giá, dùng trong loadHistory().
     */
    public static class BidPoint {
        private final double        amount;
        private final LocalDateTime time;

        public BidPoint(double amount, LocalDateTime time) {
            this.amount = amount;
            this.time   = time;
        }

        public double        getAmount() { return amount; }
        public LocalDateTime getTime()   { return time; }
    }
}
