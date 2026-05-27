package com.auction.service;

import com.auction.dao.UserDAO;
import com.auction.exception.UserNotFoundException;
import com.auction.model.user.Admin;
import com.auction.model.user.Bidder;
import com.auction.model.user.Seller;
import com.auction.model.user.User;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * UserService - Xử lý logic nghiệp vụ liên quan đến người dùng.
 * Bao gồm: đăng ký, đăng nhập, quản lý tài khoản.
 */
public class UserService {

    private static final Logger LOGGER = Logger.getLogger(UserService.class.getName());
    private final UserDAO userDAO = new UserDAO();

    // ── REGISTER ──────────────────────────────────────────────────────────

    /**
     * Đăng ký tài khoản mới.
     * @throws IllegalArgumentException nếu username đã tồn tại hoặc dữ liệu không hợp lệ
     */
    public User register(String username, String password, String email, String role) {
        // Validate
        if (username == null || username.isBlank()) throw new IllegalArgumentException("Tên đăng nhập không được để trống");
        if (username.length() < 3) throw new IllegalArgumentException("Tên đăng nhập tối thiểu 3 ký tự");
        if (password == null || password.isBlank()) throw new IllegalArgumentException("Mật khẩu không được để trống");
        if (password.length() < 6) throw new IllegalArgumentException("Mật khẩu phải có ít nhất 6 ký tự");
        if (!isValidEmail(email)) throw new IllegalArgumentException("Định dạng email không hợp lệ");

        // Kiểm tra trùng username
        if (userDAO.findByUsername(username) != null) {
            throw new IllegalArgumentException("Tên đăng nhập đã tồn tại: " + username);
        }

        String id             = UUID.randomUUID().toString();
        String hashedPassword = hashPassword(password);

        User user = switch (role.toUpperCase()) {
            case "SELLER" -> new Seller(id, username, hashedPassword, email);
            case "ADMIN"  -> new Admin(id, username, hashedPassword, email);
            default       -> new Bidder(id, username, hashedPassword, email);
        };

        boolean saved = userDAO.insert(user);
        if (!saved) throw new RuntimeException("Lưu tài khoản vào cơ sở dữ liệu thất bại");

        LOGGER.info("New user registered: " + username + " [" + role + "]");
        return user;
    }

    // ── LOGIN ─────────────────────────────────────────────────────────────

    /**
     * Đăng nhập.
     * @throws UserNotFoundException nếu sai username/password
     * @throws IllegalStateException nếu tài khoản bị banned
     */
    public User login(String username, String password) throws UserNotFoundException {
        User user = userDAO.findByUsername(username);

        if (user == null || !user.getPassword().equals(hashPassword(password))) {
            throw new UserNotFoundException("Tên đăng nhập hoặc mật khẩu không đúng");
        }

        if (user.isBanned()) {
            throw new IllegalStateException("Tài khoản của bạn đã bị khóa. Vui lòng liên hệ quản trị viên");
        }

        LOGGER.info("User logged in: " + username);
        return user;
    }

    // ── GETTERS ───────────────────────────────────────────────────────────

    public User getUserById(String userId) throws UserNotFoundException {
        User user = userDAO.findById(userId);
        if (user == null) throw new UserNotFoundException("Không tìm thấy người dùng: " + userId);
        return user;
    }

    public List<User> getAllUsers() {
        return userDAO.findAll();
    }

    // ── ADMIN ACTIONS ─────────────────────────────────────────────────────

    public void banUser(String userId) throws UserNotFoundException {
        User user = userDAO.findById(userId);
        if (user == null) throw new UserNotFoundException("Không tìm thấy người dùng: " + userId);
        userDAO.updateBanStatus(userId, true);
        LOGGER.info("User banned: " + userId);
    }

    public void unbanUser(String userId) throws UserNotFoundException {
        User user = userDAO.findById(userId);
        if (user == null) throw new UserNotFoundException("Không tìm thấy người dùng: " + userId);
        userDAO.updateBanStatus(userId, false);
        LOGGER.info("User unbanned: " + userId);
    }

    public void changePassword(String userId, String oldPassword, String newPassword) throws UserNotFoundException {
        User user = userDAO.findById(userId);
        if (user == null) throw new UserNotFoundException("Không tìm thấy người dùng: " + userId);
        if (!user.getPassword().equals(hashPassword(oldPassword))) {
            throw new IllegalArgumentException("Mật khẩu cũ không đúng");
        }
        if (newPassword.length() < 6) {
            throw new IllegalArgumentException("Mật khẩu mới phải có ít nhất 6 ký tự");
        }
        userDAO.updatePassword(userId, hashPassword(newPassword));
        LOGGER.info("Password changed for user: " + userId);
    }

    // ── UTILITY ───────────────────────────────────────────────────────────

    /**
     * Hash mật khẩu bằng SHA-256.
     */
    private String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes());
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            LOGGER.log(Level.SEVERE, "SHA-256 not available", e);
            throw new RuntimeException("Lỗi mã hóa mật khẩu", e);
        }
    }

    private boolean isValidEmail(String email) {
        return email != null
                && email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    }
}
