package com.auction.dao;

import com.auction.model.user.Admin;
import com.auction.model.user.Bidder;
import com.auction.model.user.Seller;
import com.auction.model.user.User;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * UserDAO - Thao tác CRUD với bảng users trong database.
 */
public class UserDAO {

    private static final Logger LOGGER = Logger.getLogger(UserDAO.class.getName());

    // ── CREATE ────────────────────────────────────────────────────────────

    public boolean insert(User user) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            return insert(conn, user);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Insert user failed: " + user.getUsername(), e);
            return false;
        }
    }

    public boolean insert(Connection conn, User user) throws SQLException {
        String sql = "INSERT INTO users (user_id, username, password, email, role, is_banned) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, user.getId());
            ps.setString(2, user.getUsername());
            ps.setString(3, user.getPassword());
            ps.setString(4, user.getEmail());
            ps.setString(5, user.getRole());
            ps.setBoolean(6, false);
            return ps.executeUpdate() > 0;
        }
    }

    // ── READ ──────────────────────────────────────────────────────────────

    public User findById(String userId) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            return findById(conn, userId);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "findById failed: " + userId, e);
        }
        return null;
    }

    public User findById(Connection conn, String userId) throws SQLException {
        String sql = "SELECT * FROM users WHERE user_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return mapRow(rs);
        }
        return null;
    }

    public User findByUsername(String username) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            return findByUsername(conn, username);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "findByUsername failed: " + username, e);
        }
        return null;
    }

    public User findByUsername(Connection conn, String username) throws SQLException {
        String sql = "SELECT * FROM users WHERE username = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return mapRow(rs);
        }
        return null;
    }

    public List<User> findAll() {
        List<User> users = new ArrayList<>();
        String sql = "SELECT * FROM users";
        try (Connection conn = DatabaseConnection.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) users.add(mapRow(rs));
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "findAll users failed", e);
        }
        return users;
    }

    // ── UPDATE ────────────────────────────────────────────────────────────

    public boolean updateBanStatus(String userId, boolean isBanned) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            return updateBanStatus(conn, userId, isBanned);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "updateBanStatus failed: " + userId, e);
            return false;
        }
    }

    public boolean updateBanStatus(Connection conn, String userId, boolean isBanned) throws SQLException {
        String sql = "UPDATE users SET is_banned = ? WHERE user_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBoolean(1, isBanned);
            ps.setString(2, userId);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean updatePassword(String userId, String newHashedPassword) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            return updatePassword(conn, userId, newHashedPassword);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "updatePassword failed: " + userId, e);
            return false;
        }
    }

    public boolean updatePassword(Connection conn, String userId, String newHashedPassword) throws SQLException {
        String sql = "UPDATE users SET password = ? WHERE user_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newHashedPassword);
            ps.setString(2, userId);
            return ps.executeUpdate() > 0;
        }
    }

    // ── DELETE ────────────────────────────────────────────────────────────

    public boolean delete(String userId) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            return delete(conn, userId);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "delete user failed: " + userId, e);
            return false;
        }
    }

    public boolean delete(Connection conn, String userId) throws SQLException {
        String sql = "DELETE FROM users WHERE user_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            return ps.executeUpdate() > 0;
        }
    }

    // ── HELPER ────────────────────────────────────────────────────────────

    private User mapRow(ResultSet rs) throws SQLException {
        String id       = rs.getString("user_id");
        String username = rs.getString("username");
        String password = rs.getString("password");
        String email    = rs.getString("email");
        String role     = rs.getString("role");
        boolean banned  = rs.getBoolean("is_banned");

        User user = switch (role.toUpperCase()) {
            case "SELLER" -> new Seller(id, username, password, email);
            case "ADMIN"  -> new Admin(id, username, password, email);
            default       -> new Bidder(id, username, password, email);
        };
        user.setBanned(banned);
        return user;
    }
}
