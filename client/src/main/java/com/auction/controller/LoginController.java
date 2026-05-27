package com.auction.controller;
 
import com.auction.network.ServerConnection;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.json.JSONObject;
 
import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;
import java.util.logging.Logger;
 
/**
 * LoginController - Xử lý màn hình đăng nhập và đăng ký.
 *
 * Giao tiếp với server qua 2 action:
 *   LOGIN    → { username, password }
 *   REGISTER → { username, password, email, role }
 *
 * Sau khi login thành công:
 *   - Lưu thông tin user vào Session
 *   - Chuyển sang AuctionListController
 */
public class LoginController implements Initializable {
 
    private static final Logger LOGGER = Logger.getLogger(LoginController.class.getName());
 
    // ── FXML fields ────────────────────────────────────────────────────────
    @FXML private TabPane    tabPane;
 
    // Tab Login
    @FXML private TextField  loginUsernameField;
    @FXML private PasswordField loginPasswordField;
    @FXML private Button     loginButton;
    @FXML private Label      loginErrorLabel;
 
    // Tab Register
    @FXML private TextField  regUsernameField;
    @FXML private PasswordField regPasswordField;
    @FXML private TextField  regEmailField;
    @FXML private ComboBox<String> regRoleCombo;
    @FXML private Button     registerButton;
    @FXML private Label      regErrorLabel;
    @FXML private Label      regSuccessLabel;
 
    // ── Initializable ──────────────────────────────────────────────────────
 
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        // Khởi tạo danh sách role cho ComboBox
        regRoleCombo.getItems().addAll("BIDDER", "SELLER");
        regRoleCombo.setValue("BIDDER");
 
        // Ẩn label thông báo ban đầu
        hideLoginError();
        regErrorLabel.setVisible(false);
        regSuccessLabel.setVisible(false);
 
        // Kết nối server nếu chưa kết nối
        ServerConnection conn = ServerConnection.getInstance();
        if (!conn.isConnected()) {
            boolean ok = conn.connect();
            if (!ok) {
                showLoginError("Không thể kết nối tới server. Hãy kiểm tra server đang chạy.");
                loginButton.setDisable(true);
                registerButton.setDisable(true);
            }
        }
 
        // Enter để submit
        loginPasswordField.setOnAction(e -> handleLogin(e));
        regEmailField.setOnAction(e -> handleRegister(e));
    }
 
    // ── Login ──────────────────────────────────────────────────────────────
 
    @FXML
    private void handleLogin(ActionEvent event) {
        String username = loginUsernameField.getText().trim();
        String password = loginPasswordField.getText();
 
        if (username.isEmpty() || password.isEmpty()) {
            showLoginError("Vui lòng nhập đầy đủ tên đăng nhập và mật khẩu.");
            return;
        }
 
        loginButton.setDisable(true);
        loginButton.setText("Đang đăng nhập...");
        hideLoginError();
 
        // Chạy network trên thread riêng, không block UI
        new Thread(() -> {
            JSONObject data = new JSONObject();
            data.put("username", username);
            data.put("password", password);
 
            JSONObject response = ServerConnection.getInstance().sendRequest("LOGIN", data);
 
            Platform.runLater(() -> {
                loginButton.setDisable(false);
                loginButton.setText("Đăng nhập");
 
                if (ServerConnection.isOk(response)) {
                    JSONObject userData = response.optJSONObject("data");
                    if (userData != null) {
                        // Lưu thông tin session
                        Session.getInstance().login(
                            userData.optString("userId"),
                            userData.optString("username"),
                            userData.optString("role")
                        );
                        navigateToAuctionList();
                    }
                } else {
                    showLoginError(response.optString("message", "Đăng nhập thất bại."));
                }
            });
        }, "login-thread").start();
    }
 
    // ── Register ───────────────────────────────────────────────────────────
 
    @FXML
    private void handleRegister(ActionEvent event) {
        String username = regUsernameField.getText().trim();
        String password = regPasswordField.getText();
        String email    = regEmailField.getText().trim();
        String role     = regRoleCombo.getValue();
 
        // Validate client-side
        if (username.isEmpty() || password.isEmpty() || email.isEmpty()) {
            showRegError("Vui lòng nhập đầy đủ thông tin.");
            return;
        }
        if (username.length() < 3) {
            showRegError("Tên đăng nhập tối thiểu 3 ký tự.");
            return;
        }
        if (password.length() < 6) {
            showRegError("Mật khẩu phải có ít nhất 6 ký tự.");
            return;
        }
        if (!email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
            showRegError("Email không hợp lệ.");
            return;
        }
 
        registerButton.setDisable(true);
        registerButton.setText("Đang đăng ký...");
        regErrorLabel.setVisible(false);
        regSuccessLabel.setVisible(false);
 
        new Thread(() -> {
            JSONObject data = new JSONObject();
            data.put("username", username);
            data.put("password", password);
            data.put("email",    email);
            data.put("role",     role);
 
            JSONObject response = ServerConnection.getInstance().sendRequest("REGISTER", data);
 
            Platform.runLater(() -> {
                registerButton.setDisable(false);
                registerButton.setText("Đăng ký");
 
                if (ServerConnection.isOk(response)) {
                    regSuccessLabel.setText("Đăng ký thành công! Hãy đăng nhập.");
                    regSuccessLabel.setVisible(true);
                    regSuccessLabel.setManaged(true);
                    // Tự động chuyển sang tab Login
                    tabPane.getSelectionModel().selectFirst();
                    loginUsernameField.setText(username);
                    loginPasswordField.clear();
                } else {
                    showRegError(response.optString("message", "Đăng ký thất bại."));
                }
            });
        }, "register-thread").start();
    }
 
    // ── Navigation ─────────────────────────────────────────────────────────
 
    private void navigateToAuctionList() {
        try {
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/com/auction/view/auction_list.fxml"));
            Parent root = loader.load();
            Stage stage = (Stage) loginButton.getScene().getWindow();
            SceneUtil.setScene(stage, root, "Auction System - Danh sách đấu giá",
                    1000, 650, 900, 600);
        } catch (IOException e) {
            LOGGER.severe("Không thể tải giao diện danh sách đấu giá: " + e.getMessage());
            showLoginError("Lỗi tải giao diện: " + e.getMessage());
        }
    }
 
    // ── Helpers ────────────────────────────────────────────────────────────
 
    private void showLoginError(String message) {
        loginErrorLabel.setText(message);
        loginErrorLabel.setVisible(true);
        loginErrorLabel.setManaged(true);   // cho label chiếm chỗ trong layout
    }
 
    private void hideLoginError() {
        loginErrorLabel.setVisible(false);
        loginErrorLabel.setManaged(false);
    }
 
    private void showRegError(String message) {
        regErrorLabel.setText(message);
        regErrorLabel.setVisible(true);
        regErrorLabel.setManaged(true);
        regSuccessLabel.setVisible(false);
        regSuccessLabel.setManaged(false);
    }
 
 
 
    // ── Inner class: Session ───────────────────────────────────────────────
 
    /**
     * Session - Lưu thông tin user đang đăng nhập (dùng chung toàn app).
     * Singleton đơn giản, không cần DB.
     */
    public static class Session {
        private String userId;
        private String username;
        private String role;
 
        private Session() {}

        private static class Holder {
            private static final Session INSTANCE = new Session();
        }
 
        public static Session getInstance() {
            return Holder.INSTANCE;
        }
 
        public void login(String userId, String username, String role) {
            this.userId   = userId;
            this.username = username;
            this.role     = role;
        }
 
        public void logout() {
            this.userId   = null;
            this.username = null;
            this.role     = null;
        }
 
        public String getUserId()   { return userId; }
        public String getUsername() { return username; }
        public String getRole()     { return role; }
        public boolean isLoggedIn() { return userId != null; }
        public boolean isBidder()   { return "BIDDER".equals(role); }
        public boolean isSeller()   { return "SELLER".equals(role); }
        public boolean isAdmin()    { return "ADMIN".equals(role); }
    }
}
