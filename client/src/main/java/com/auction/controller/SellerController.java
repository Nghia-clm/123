package com.auction.controller;

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
import javafx.stage.Stage;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ResourceBundle;
import java.util.logging.Logger;

/**
 * SellerController - Quản lý sản phẩm và tạo phiên đấu giá.
 *
 * Chức năng:
 *   - Xem danh sách sản phẩm của mình (GET_ITEMS)
 *   - Thêm sản phẩm mới (CREATE_ITEM): tên, mô tả, loại, giá khởi điểm
 *   - Sửa sản phẩm (UPDATE_ITEM)
 *   - Xóa sản phẩm (DELETE_ITEM)
 *   - Tạo phiên đấu giá từ sản phẩm đã có (CREATE_AUCTION)
 *   - Admin thêm: xem tất cả user, ban user (GET_ALL_USERS / BAN_USER)
 *   - Nút quay lại danh sách đấu giá
 */
public class SellerController implements Initializable {

    private static final Logger LOGGER = Logger.getLogger(SellerController.class.getName());
    private static final DateTimeFormatter DT_FMT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    // ── FXML: Bảng sản phẩm ───────────────────────────────────────────────
    @FXML private TableView<ItemRow>         itemTable;
    @FXML private TableColumn<ItemRow, String> colItemId;
    @FXML private TableColumn<ItemRow, String> colName;
    @FXML private TableColumn<ItemRow, String> colType;
    @FXML private TableColumn<ItemRow, String> colStartPrice;
    @FXML private TableColumn<ItemRow, String> colDesc;

    @FXML private Button refreshItemsButton;
    @FXML private Button deleteItemButton;
    @FXML private Label  itemStatusLabel;

    // ── FXML: Form thêm / sửa sản phẩm ───────────────────────────────────
    @FXML private TextField     nameField;
    @FXML private TextArea      descField;
    @FXML private ComboBox<String> typeCombo;
    @FXML private TextField     startPriceField;
    @FXML private Button        saveItemButton;
    @FXML private Button        clearFormButton;
    @FXML private Label         formStatusLabel;

    // ── FXML: Form tạo phiên đấu giá ─────────────────────────────────────
    @FXML private TextField selectedItemIdField;  // readonly, điền tự động khi chọn item
    @FXML private TextField startTimeField;       // "yyyy-MM-ddTHH:mm:ss"
    @FXML private TextField endTimeField;
    @FXML private TextField auctionStartPriceField;
    @FXML private Button    createAuctionButton;
    @FXML private Label     auctionStatusLabel;

    // ── FXML: Navigation ──────────────────────────────────────────────────
    @FXML private Button backButton;

    // ── State ──────────────────────────────────────────────────────────────
    private final ObservableList<ItemRow> itemRows = FXCollections.observableArrayList();
    private String editingItemId = null; // null = thêm mới, non-null = đang sửa

    // ── Initializable ──────────────────────────────────────────────────────

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        // Setup bảng sản phẩm
        colItemId.setCellValueFactory(new PropertyValueFactory<>("itemId"));
        colName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colType.setCellValueFactory(new PropertyValueFactory<>("type"));
        colStartPrice.setCellValueFactory(new PropertyValueFactory<>("startingPrice"));
        colDesc.setCellValueFactory(new PropertyValueFactory<>("description"));
        itemTable.setItems(itemRows);

        // Các nút phụ thuộc vào selection
        deleteItemButton.setDisable(true);
        createAuctionButton.setDisable(true);

        itemTable.getSelectionModel().selectedItemProperty().addListener(
            (obs, old, selected) -> {
                updateSelectionActions(selected);
                if (selected != null && canManageItem(selected)) {
                    fillFormForEdit(selected);
                } else if (selected != null) {
                    resetEditForm();
                }
                fillAuctionFormFromSelection(selected);
            }
        );

        // Setup combo loại sản phẩm
        typeCombo.getItems().addAll("ELECTRONICS", "ART", "VEHICLE");
        typeCombo.setValue("ELECTRONICS");

        resetDefaultAuctionTimes();

        loadItems();
    }

    // ── Load sản phẩm ─────────────────────────────────────────────────────

    private void loadItems() {
        setItemStatus("Đang tải danh sách sản phẩm...");
        new Thread(() -> {
            JSONObject response = ServerConnection.getInstance().sendRequest("GET_ITEMS", null);
            Platform.runLater(() -> {
                if (ServerConnection.isOk(response)) {
                    JSONObject data = response.optJSONObject("data");
                    if (data != null) {
                        JSONArray arr = data.optJSONArray("items");
                        itemRows.clear();
                        if (arr != null) {
                            for (int i = 0; i < arr.length(); i++) {
                                JSONObject it = arr.optJSONObject(i);
                                if (it == null) continue;
                                itemRows.add(new ItemRow(
                                    it.optString("itemId"),
                                    it.optString("name"),
                                    it.optString("type"),
                                    String.format("%.0f đ", it.optDouble("startingPrice")),
                                    it.optString("description"),
                                    it.optString("sellerId")
                                ));
                            }
                        }
                        setItemStatus("Tổng " + itemRows.size() + " sản phẩm.");
                    }
                } else {
                    setItemStatus("Lỗi: " + response.optString("message"));
                }
            });
        }, "load-items").start();
    }

    // ── Thêm / Sửa sản phẩm ──────────────────────────────────────────────

    /**
     * Điền form để sửa sản phẩm đang chọn.
     */
    private void fillFormForEdit(ItemRow item) {
        editingItemId = item.getItemId();
        nameField.setText(item.getName());
        descField.setText(item.getDescription());
        typeCombo.setValue(item.getType());
        // startingPrice hiển thị dưới dạng "xxx đ" nên cần parse lại
        String priceRaw = item.getStartingPrice().replace(" đ", "").replace(",", "");
        startPriceField.setText(priceRaw);
        saveItemButton.setText("Cập nhật");
        setFormStatus("Đang chỉnh sửa: " + item.getName());
    }

    private void resetEditForm() {
        editingItemId = null;
        nameField.clear();
        descField.clear();
        typeCombo.setValue("ELECTRONICS");
        startPriceField.clear();
        saveItemButton.setText("Thêm sản phẩm");
    }

    @FXML
    private void handleClearForm(ActionEvent event) {
        resetEditForm();
        formStatusLabel.setVisible(false);
        itemTable.getSelectionModel().clearSelection();
    }

    @FXML
    private void handleSaveItem(ActionEvent event) {
        String name  = nameField.getText().trim();
        String desc  = descField.getText().trim();
        String type  = typeCombo.getValue();
        String priceStr = startPriceField.getText().trim();

        if (name.isEmpty() || priceStr.isEmpty()) {
            setFormStatus("Tên sản phẩm và giá khởi điểm không được để trống.");
            return;
        }
        long price;
        try {
            price = Long.parseLong(priceStr);
            if (price <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            setFormStatus("Giá khởi điểm phải là số nguyên dương.");
            return;
        }

        saveItemButton.setDisable(true);

        if (editingItemId == null) {
            // Thêm mới
            new Thread(() -> {
                JSONObject data = new JSONObject();
                data.put("type",         type);
                data.put("name",         name);
                data.put("description",  desc);
                data.put("startingPrice", price);
                JSONObject response = ServerConnection.getInstance().sendRequest("CREATE_ITEM", data);

                Platform.runLater(() -> {
                    saveItemButton.setDisable(false);
                    if (ServerConnection.isOk(response)) {
                        setFormStatus("✓ Thêm sản phẩm thành công!");
                        handleClearForm(null);
                        loadItems();
                    } else {
                        setFormStatus("✗ " + response.optString("message"));
                    }
                });
            }, "create-item").start();

        } else {
            // Cập nhật
            final String itemId = editingItemId;
            new Thread(() -> {
                JSONObject data = new JSONObject();
            data.put("itemId",      itemId);
            data.put("type",        type);
            data.put("name",        name);
            data.put("description", desc);
            data.put("startingPrice", price);
            JSONObject response = ServerConnection.getInstance().sendRequest("UPDATE_ITEM", data);

                Platform.runLater(() -> {
                    saveItemButton.setDisable(false);
                    if (ServerConnection.isOk(response)) {
                        setFormStatus("✓ Cập nhật thành công!");
                        handleClearForm(null);
                        loadItems();
                    } else {
                        setFormStatus("✗ " + response.optString("message"));
                    }
                });
            }, "update-item").start();
        }
    }

    // ── Xóa sản phẩm ──────────────────────────────────────────────────────

    @FXML
    private void handleDeleteItem(ActionEvent event) {
        ItemRow selected = itemTable.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        if (!canManageItem(selected)) return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
            "Xóa sản phẩm \"" + selected.getName() + "\"?",
            ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Xác nhận xóa");
        confirm.showAndWait().ifPresent(bt -> {
            if (bt == ButtonType.YES) {
                new Thread(() -> {
                    JSONObject data = new JSONObject();
                    data.put("itemId", selected.getItemId());
                    JSONObject response = ServerConnection.getInstance().sendRequest("DELETE_ITEM", data);

                    Platform.runLater(() -> {
                        if (ServerConnection.isOk(response)) {
                            setItemStatus("✓ Đã xóa sản phẩm: " + selected.getName());
                            handleClearForm(null);
                            loadItems();
                        } else {
                            setItemStatus("✗ " + response.optString("message"));
                        }
                    });
                }, "delete-item").start();
            }
        });
    }

    // ── Tạo phiên đấu giá ─────────────────────────────────────────────────

    private void fillAuctionFormFromSelection(ItemRow selected) {
        if (selected == null || !canManageItem(selected)) {
            selectedItemIdField.clear();
            auctionStartPriceField.clear();
            createAuctionButton.setDisable(true);
            return;
        }

        selectedItemIdField.setText(selected.getItemId());
        String priceRaw = selected.getStartingPrice().replace(" đ", "").replace(",", "");
        auctionStartPriceField.setText(priceRaw);
        resetDefaultAuctionTimes();
        createAuctionButton.setDisable(false);
    }

    @FXML
    private void handleCreateAuction(ActionEvent event) {
        String itemId     = selectedItemIdField.getText().trim();
        String startTime  = startTimeField.getText().trim();
        String endTime    = endTimeField.getText().trim();
        String priceStr   = auctionStartPriceField.getText().trim();

        if (itemId.isEmpty() || startTime.isEmpty() || endTime.isEmpty() || priceStr.isEmpty()) {
            setAuctionStatus("Vui lòng điền đầy đủ thông tin phiên đấu giá.");
            return;
        }
        long price;
        try {
            price = Long.parseLong(priceStr);
            if (price <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            setAuctionStatus("Giá khởi điểm phiên đấu giá phải là số nguyên dương.");
            return;
        }

        createAuctionButton.setDisable(true);

        new Thread(() -> {
            JSONObject data = new JSONObject();
            data.put("itemId",        itemId);
            data.put("startingPrice", price);
            data.put("startTime",     startTime);
            data.put("endTime",       endTime);
            JSONObject response = ServerConnection.getInstance().sendRequest("CREATE_AUCTION", data);

            Platform.runLater(() -> {
                createAuctionButton.setDisable(false);
                if (ServerConnection.isOk(response)) {
                    setAuctionStatus("✓ Tạo phiên đấu giá thành công!");
                    selectedItemIdField.clear();
                    auctionStartPriceField.clear();
                    resetDefaultAuctionTimes();
                } else {
                    setAuctionStatus("✗ " + response.optString("message"));
                }
            });
        }, "create-auction").start();
    }

    // ── Navigation ─────────────────────────────────────────────────────────

    @FXML
    private void handleRefreshItems(ActionEvent event) {
        loadItems();
    }

    private void updateSelectionActions(ItemRow selected) {
        boolean canManage = selected != null && canManageItem(selected);
        deleteItemButton.setDisable(!canManage);
        createAuctionButton.setDisable(!canManage);
    }

    private boolean canManageItem(ItemRow item) {
        if (item == null) return false;
        Session session = Session.getInstance();
        return session.isAdmin() || item.getSellerId().equals(session.getUserId());
    }

    @FXML
    private void handleBack(ActionEvent event) {
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

    private void setItemStatus(String msg)    { itemStatusLabel.setText(msg); }
    private void setFormStatus(String msg)    { formStatusLabel.setText(msg); formStatusLabel.setVisible(true); }
    private void setAuctionStatus(String msg) { auctionStatusLabel.setText(msg); }

    private void resetDefaultAuctionTimes() {
        LocalDateTime startTime = LocalDateTime.now().plusMinutes(1);
        LocalDateTime endTime = startTime.plusHours(1);
        startTimeField.setText(startTime.format(DT_FMT));
        endTimeField.setText(endTime.format(DT_FMT));
    }

    // ── Inner classes: Row models ──────────────────────────────────────────

    public static class ItemRow {
        private final SimpleStringProperty itemId;
        private final SimpleStringProperty name;
        private final SimpleStringProperty type;
        private final SimpleStringProperty startingPrice;
        private final SimpleStringProperty description;
        private final SimpleStringProperty sellerId;

        public ItemRow(String itemId, String name, String type,
                       String startingPrice, String description, String sellerId) {
            this.itemId        = new SimpleStringProperty(itemId);
            this.name          = new SimpleStringProperty(name);
            this.type          = new SimpleStringProperty(type);
            this.startingPrice = new SimpleStringProperty(startingPrice);
            this.description   = new SimpleStringProperty(description);
            this.sellerId      = new SimpleStringProperty(sellerId);
        }

        public String getItemId()       { return itemId.get(); }
        public String getName()         { return name.get(); }
        public String getType()         { return type.get(); }
        public String getStartingPrice(){ return startingPrice.get(); }
        public String getDescription()  { return description.get(); }
        public String getSellerId()     { return sellerId.get(); }
    }
}
