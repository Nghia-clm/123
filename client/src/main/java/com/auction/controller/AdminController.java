package com.auction.controller;

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
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;
import java.util.logging.Logger;

public class AdminController implements Initializable {

    private static final Logger LOGGER = Logger.getLogger(AdminController.class.getName());

    @FXML private Button backButton;
    @FXML private Button refreshButton;
    @FXML private Button banUserButton;
    @FXML private Button unbanUserButton;
    @FXML private Label statusLabel;

    @FXML private TableView<UserRow> userTable;
    @FXML private TableColumn<UserRow, String> colUserId;
    @FXML private TableColumn<UserRow, String> colUsername;
    @FXML private TableColumn<UserRow, String> colRole;
    @FXML private TableColumn<UserRow, String> colEmail;
    @FXML private TableColumn<UserRow, String> colStatus;

    private final ObservableList<UserRow> userRows = FXCollections.observableArrayList();

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        colUserId.setCellValueFactory(new PropertyValueFactory<>("userId"));
        colUsername.setCellValueFactory(new PropertyValueFactory<>("username"));
        colRole.setCellValueFactory(new PropertyValueFactory<>("role"));
        colEmail.setCellValueFactory(new PropertyValueFactory<>("email"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));

        userTable.setItems(userRows);
        banUserButton.setDisable(true);
        unbanUserButton.setDisable(true);
        userTable.getSelectionModel().selectedItemProperty().addListener(
            (obs, old, selected) -> updateActionButtons(selected)
        );

        loadUsers();
    }

    @FXML
    private void handleRefresh(ActionEvent event) {
        loadUsers();
    }

    @FXML
    private void handleBanUser(ActionEvent event) {
        UserRow selected = userTable.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
            "Ban user \"" + selected.getUsername() + "\"?",
            ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Xac nhan ban user");
        confirm.showAndWait().ifPresent(bt -> {
            if (bt == ButtonType.YES) {
                new Thread(() -> {
                    JSONObject data = new JSONObject();
                    data.put("userId", selected.getUserId());
                    JSONObject response = ServerConnection.getInstance().sendRequest("BAN_USER", data);

                    Platform.runLater(() -> {
                        if (ServerConnection.isOk(response)) {
                            setStatus("Da ban user: " + selected.getUsername());
                            loadUsers();
                        } else {
                            setStatus("Loi: " + response.optString("message"));
                        }
                    });
                }, "ban-user").start();
            }
        });
    }

    @FXML
    private void handleUnbanUser(ActionEvent event) {
        UserRow selected = userTable.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
            "Unban user \"" + selected.getUsername() + "\"?",
            ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Xac nhan unban user");
        confirm.showAndWait().ifPresent(bt -> {
            if (bt == ButtonType.YES) {
                new Thread(() -> {
                    JSONObject data = new JSONObject();
                    data.put("userId", selected.getUserId());
                    JSONObject response = ServerConnection.getInstance().sendRequest("UNBAN_USER", data);

                    Platform.runLater(() -> {
                        if (ServerConnection.isOk(response)) {
                            setStatus("Da unban user: " + selected.getUsername());
                            loadUsers();
                        } else {
                            setStatus("Loi: " + response.optString("message"));
                        }
                    });
                }, "unban-user").start();
            }
        });
    }

    @FXML
    private void handleBack(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/com/auction/view/auction_list.fxml"));
            Parent root = loader.load();
            Stage stage = (Stage) backButton.getScene().getWindow();
            SceneUtil.setScene(stage, root, "Auction System - Danh sach dau gia",
                    1000, 650, 900, 600);
        } catch (IOException e) {
            LOGGER.severe("Khong the tai giao dien danh sach dau gia: " + e.getMessage());
        }
    }

    private void loadUsers() {
        setStatus("Dang tai danh sach user...");
        refreshButton.setDisable(true);

        new Thread(() -> {
            JSONObject response = ServerConnection.getInstance().sendRequest("GET_ALL_USERS", null);

            Platform.runLater(() -> {
                refreshButton.setDisable(false);
                if (ServerConnection.isOk(response)) {
                    JSONObject data = response.optJSONObject("data");
                    JSONArray arr = data != null ? data.optJSONArray("users") : null;
                    userRows.clear();
                    if (arr != null) {
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject u = arr.optJSONObject(i);
                            if (u == null) continue;
                            userRows.add(new UserRow(
                                u.optString("userId"),
                                u.optString("username"),
                                u.optString("role"),
                                u.optString("email"),
                                u.optBoolean("banned")
                            ));
                        }
                    }
                    setStatus("Tong " + userRows.size() + " user.");
                } else {
                    setStatus("Loi: " + response.optString("message"));
                }
            });
        }, "load-admin-users").start();
    }

    private void setStatus(String message) {
        statusLabel.setText(message);
    }

    private void updateActionButtons(UserRow selected) {
        banUserButton.setDisable(selected == null || selected.isBanned());
        unbanUserButton.setDisable(selected == null || !selected.isBanned());
    }

    public static class UserRow {
        private final SimpleStringProperty userId;
        private final SimpleStringProperty username;
        private final SimpleStringProperty role;
        private final SimpleStringProperty email;
        private final SimpleStringProperty status;
        private final boolean banned;

        public UserRow(String userId, String username, String role, String email, boolean banned) {
            this.userId = new SimpleStringProperty(userId);
            this.username = new SimpleStringProperty(username);
            this.role = new SimpleStringProperty(role);
            this.email = new SimpleStringProperty(email);
            this.banned = banned;
            this.status = new SimpleStringProperty(banned ? "BANNED" : "ACTIVE");
        }

        public String getUserId() { return userId.get(); }
        public String getUsername() { return username.get(); }
        public String getRole() { return role.get(); }
        public String getEmail() { return email.get(); }
        public String getStatus() { return status.get(); }
        public boolean isBanned() { return banned; }
    }
}
