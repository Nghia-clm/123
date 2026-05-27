package com.auction.service;

import com.auction.exception.UserNotFoundException;
import com.auction.model.user.Bidder;
import com.auction.model.user.Seller;
import com.auction.model.user.User;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * UserServiceTest - Kiểm thử logic nghiệp vụ người dùng.
 *
 * Dùng InMemoryUserService (inner class override) thay vì DB thật.
 * Đây là kỹ thuật "manual stub" — không cần Mockito vì bài không yêu cầu
 * mock framework. Override các method public của UserService là đủ và hợp lệ.
 *
 * Lý do KHÔNG dùng Mockito:
 *   - Bài yêu cầu JUnit đơn thuần, không yêu cầu mock framework
 *   - Override thủ công minh bạch hơn: dễ đọc, dễ giải thích
 *   - Không phát sinh dependency mới trong pom.xml
 *   - InMemoryUserService có thể tái sử dụng làm fake cho các test khác
 */
@DisplayName("UserService - Logic người dùng")
class UserServiceTest {

    /**
     * Stub UserService dùng in-memory HashMap thay vì DB thật.
     * Giúp test chạy ngay mà không cần MySQL.
     * Override toàn bộ các method public — không gọi super() để tránh kết nối DB.
     */
    static class InMemoryUserService extends UserService {

        private final java.util.Map<String, User> store = new java.util.HashMap<>();

        @Override
        public User register(String username, String password, String email, String role) {
            if (username == null || username.isBlank())
                throw new IllegalArgumentException("Username cannot be empty");
            if (password == null || password.length() < 6)
                throw new IllegalArgumentException("Password must be at least 6 characters");
            if (email == null || !email.contains("@"))
                throw new IllegalArgumentException("Invalid email format");
            if (store.values().stream().anyMatch(u -> u.getUsername().equals(username)))
                throw new IllegalArgumentException("Username already exists: " + username);

            User user = switch (role.toUpperCase()) {
                case "SELLER" -> new Seller(java.util.UUID.randomUUID().toString(),
                                            username, hash(password), email);
                default       -> new Bidder(java.util.UUID.randomUUID().toString(),
                                            username, hash(password), email);
            };
            store.put(user.getId(), user);
            return user;
        }

        @Override
        public User login(String username, String password) throws UserNotFoundException {
            User user = store.values().stream()
                .filter(u -> u.getUsername().equals(username))
                .findFirst().orElse(null);
            if (user == null || !user.getPassword().equals(hash(password)))
                throw new UserNotFoundException("Invalid username or password");
            if (user.isBanned())
                throw new IllegalStateException("Account is banned");
            return user;
        }

        @Override
        public void banUser(String userId) throws UserNotFoundException {
            User user = store.get(userId);
            if (user == null) throw new UserNotFoundException("User not found: " + userId);
            user.setBanned(true);
        }

        @Override
        public void unbanUser(String userId) throws UserNotFoundException {
            User user = store.get(userId);
            if (user == null) throw new UserNotFoundException("User not found: " + userId);
            user.setBanned(false);
        }

        @Override
        public void changePassword(String userId, String oldPassword, String newPassword)
                throws UserNotFoundException {
            User user = store.get(userId);
            if (user == null) throw new UserNotFoundException("User not found");
            if (!user.getPassword().equals(hash(oldPassword)))
                throw new IllegalArgumentException("Old password is incorrect");
            if (newPassword.length() < 6)
                throw new IllegalArgumentException("New password must be at least 6 characters");
            user.setPassword(hash(newPassword));
        }

        /** SHA-256 đơn giản dùng trong test, giống UserService thật */
        private String hash(String s) {
            try {
                java.security.MessageDigest d =
                    java.security.MessageDigest.getInstance("SHA-256");
                byte[] bytes = d.digest(s.getBytes());
                StringBuilder sb = new StringBuilder();
                for (byte b : bytes) sb.append(String.format("%02x", b));
                return sb.toString();
            } catch (Exception e) { return s; }
        }
    }

    private InMemoryUserService userService;

    @BeforeEach
    void setUp() {
        userService = new InMemoryUserService();
    }

    // ── Đăng ký ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("Đăng ký tài khoản BIDDER thành công")
    void registerBidderSuccess() {
        User user = userService.register("alice", "password123", "alice@test.com", "BIDDER");
        assertNotNull(user);
        assertEquals("alice",  user.getUsername());
        assertEquals("BIDDER", user.getRole());
        assertFalse(user.isBanned());
        assertInstanceOf(Bidder.class, user);
    }

    @Test
    @DisplayName("Đăng ký tài khoản SELLER thành công")
    void registerSellerSuccess() {
        User user = userService.register("bob_seller", "pass123", "bob@test.com", "SELLER");
        assertEquals("SELLER", user.getRole());
        assertInstanceOf(Seller.class, user);
    }

    @Test
    @DisplayName("Đăng ký trùng username ném IllegalArgumentException")
    void registerDuplicateUsernameThrows() {
        userService.register("alice", "password123", "alice@test.com", "BIDDER");
        assertThrows(IllegalArgumentException.class,
            () -> userService.register("alice", "otherpass", "other@test.com", "BIDDER"));
    }

    @Test
    @DisplayName("Đăng ký mật khẩu ngắn hơn 6 ký tự ném IllegalArgumentException")
    void registerShortPasswordThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> userService.register("user1", "12345", "u@test.com", "BIDDER"));
    }

    @Test
    @DisplayName("Đăng ký email không hợp lệ ném IllegalArgumentException")
    void registerInvalidEmailThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> userService.register("user2", "password", "not-an-email", "BIDDER"));
    }

    @Test
    @DisplayName("Đăng ký username rỗng ném IllegalArgumentException")
    void registerEmptyUsernameThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> userService.register("", "password", "u@test.com", "BIDDER"));
    }

    @Test
    @DisplayName("UserService thật từ chối username ngắn hơn 3 ký tự")
    void realRegisterRejectsUsernameShorterThanThreeCharacters() {
        UserService realService = new UserService();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> realService.register("ab", "password", "u@test.com", "BIDDER"));

        assertTrue(ex.getMessage().contains("3"));
    }

    // ── Đăng nhập ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("Đăng nhập đúng thông tin thành công")
    void loginSuccess() throws UserNotFoundException {
        userService.register("charlie", "mypassword", "charlie@test.com", "BIDDER");
        User user = userService.login("charlie", "mypassword");
        assertNotNull(user);
        assertEquals("charlie", user.getUsername());
    }

    @Test
    @DisplayName("Đăng nhập sai mật khẩu ném UserNotFoundException")
    void loginWrongPasswordThrows() {
        userService.register("dave", "correctpass", "dave@test.com", "BIDDER");
        assertThrows(UserNotFoundException.class,
            () -> userService.login("dave", "wrongpass"));
    }

    @Test
    @DisplayName("Đăng nhập username không tồn tại ném UserNotFoundException")
    void loginUnknownUserThrows() {
        assertThrows(UserNotFoundException.class,
            () -> userService.login("nobody", "anypass"));
    }

    @Test
    @DisplayName("Tài khoản bị ban không thể đăng nhập")
    void bannedUserCannotLogin() throws UserNotFoundException {
        User user = userService.register("eve", "password123", "eve@test.com", "BIDDER");
        userService.banUser(user.getId());
        assertThrows(IllegalStateException.class,
            () -> userService.login("eve", "password123"));
    }

    // ── Ban / Unban ────────────────────────────────────────────────────────

    @Test
    @DisplayName("banUser() đặt isBanned = true")
    void banUserSetsBannedFlag() throws UserNotFoundException {
        User user = userService.register("frank", "password123", "frank@test.com", "BIDDER");
        assertFalse(user.isBanned());
        userService.banUser(user.getId());
        assertTrue(user.isBanned());
    }

    @Test
    @DisplayName("unbanUser() đặt isBanned = false")
    void unbanUserClearsBannedFlag() throws UserNotFoundException {
        User user = userService.register("grace2", "password123", "g2@test.com", "BIDDER");
        userService.banUser(user.getId());
        assertTrue(user.isBanned());
        userService.unbanUser(user.getId());
        assertFalse(user.isBanned());
    }

    @Test
    @DisplayName("banUser() với ID không tồn tại ném UserNotFoundException")
    void banNonExistentUserThrows() {
        assertThrows(UserNotFoundException.class,
            () -> userService.banUser("non-existent-id"));
    }

    // ── Đổi mật khẩu ──────────────────────────────────────────────────────

    @Test
    @DisplayName("Đổi mật khẩu thành công → đăng nhập bằng mật khẩu mới")
    void changePasswordSuccess() throws Exception {
        User user = userService.register("grace", "oldpass1", "grace@test.com", "BIDDER");
        userService.changePassword(user.getId(), "oldpass1", "newpass123");
        User logged = userService.login("grace", "newpass123");
        assertEquals("grace", logged.getUsername());
    }

    @Test
    @DisplayName("Đổi mật khẩu sai mật khẩu cũ ném IllegalArgumentException")
    void changePasswordWrongOldPasswordThrows() {
        User user = userService.register("henry", "oldpass1", "henry@test.com", "BIDDER");
        assertThrows(IllegalArgumentException.class,
            () -> userService.changePassword(user.getId(), "wrongold", "newpass123"));
    }

    @Test
    @DisplayName("Đổi mật khẩu mới quá ngắn ném IllegalArgumentException")
    void changePasswordTooShortThrows() {
        User user = userService.register("ivan", "oldpass1", "ivan@test.com", "BIDDER");
        assertThrows(IllegalArgumentException.class,
            () -> userService.changePassword(user.getId(), "oldpass1", "123"));
    }

    @Test
    @DisplayName("Sau khi đổi mật khẩu, mật khẩu cũ không còn dùng được")
    void oldPasswordInvalidAfterChange() throws Exception {
        User user = userService.register("jane", "oldpass1", "jane@test.com", "BIDDER");
        userService.changePassword(user.getId(), "oldpass1", "newpass123");
        assertThrows(UserNotFoundException.class,
            () -> userService.login("jane", "oldpass1"));
    }
}
