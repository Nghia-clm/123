package com.auction.dao;

import com.auction.factory.ItemFactory;
import com.auction.model.Auction;
import com.auction.model.AuctionStatus;
import com.auction.model.item.Item;
import com.auction.model.user.Admin;
import com.auction.model.user.Bidder;
import com.auction.model.user.Seller;
import com.auction.model.user.User;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * AuctionDAO - Thao tác CRUD với bảng auctions trong database.
 */
public class AuctionDAO {

    private static final Logger LOGGER = Logger.getLogger(AuctionDAO.class.getName());

    private static final String AUCTION_SELECT = """
        SELECT
            a.auction_id,
            a.starting_price AS auction_starting_price,
            a.current_price,
            a.status,
            a.start_time,
            a.end_time,
            i.item_id,
            i.seller_id AS item_seller_id,
            i.type AS item_type,
            i.name AS item_name,
            i.description AS item_description,
            i.starting_price AS item_starting_price,
            seller.user_id AS seller_user_id,
            seller.username AS seller_username,
            seller.password AS seller_password,
            seller.email AS seller_email,
            seller.role AS seller_role,
            seller.is_banned AS seller_is_banned,
            winner.user_id AS winner_user_id,
            winner.username AS winner_username,
            winner.password AS winner_password,
            winner.email AS winner_email,
            winner.role AS winner_role,
            winner.is_banned AS winner_is_banned
        FROM auctions a
        JOIN items i ON i.item_id = a.item_id
        JOIN users seller ON seller.user_id = a.seller_id
        LEFT JOIN users winner ON winner.user_id = a.winner_id
        """;

    // ── CREATE ────────────────────────────────────────────────────────────

    public boolean insert(Auction auction) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            return insert(conn, auction);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Insert auction failed: " + auction.getId(), e);
            return false;
        }
    }

    public boolean insert(Connection conn, Auction auction) throws SQLException {
        String sql = """
            INSERT INTO auctions
                (auction_id, item_id, seller_id, starting_price, current_price,
                 status, start_time, end_time, winner_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, auction.getId());
            ps.setString(2, auction.getItem().getId());
            ps.setString(3, auction.getSeller().getId());
            ps.setDouble(4, auction.getStartingPrice());
            ps.setDouble(5, auction.getCurrentPrice());
            ps.setString(6, auction.getStatus().name());
            ps.setTimestamp(7, Timestamp.valueOf(auction.getStartTime()));
            ps.setTimestamp(8, Timestamp.valueOf(auction.getEndTime()));
            ps.setString(9, auction.getWinner() != null ? auction.getWinner().getId() : null);
            return ps.executeUpdate() > 0;
        }
    }

    // ── READ ──────────────────────────────────────────────────────────────

    public Auction findById(String auctionId) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            return findById(conn, auctionId);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "findById auction failed: " + auctionId, e);
        }
        return null;
    }

    public Auction findById(Connection conn, String auctionId) throws SQLException {
        String sql = AUCTION_SELECT + " WHERE a.auction_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, auctionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        }
        return null;
    }

    public List<Auction> findAll() {
        List<Auction> auctions = new ArrayList<>();
        String sql = AUCTION_SELECT + " ORDER BY a.start_time DESC";
        try (Connection conn = DatabaseConnection.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) auctions.add(mapRow(rs));
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "findAll auctions failed", e);
        }
        return auctions;
    }

    public List<Auction> findByStatus(AuctionStatus status) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            return findByStatus(conn, status);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "findByStatus failed: " + status, e);
            return new ArrayList<>();
        }
    }

    public List<Auction> findByStatus(Connection conn, AuctionStatus status) throws SQLException {
        List<Auction> auctions = new ArrayList<>();
        String sql = AUCTION_SELECT + " WHERE a.status = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status.name());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) auctions.add(mapRow(rs));
            }
        }
        return auctions;
    }

    public List<Auction> findBySeller(String sellerId) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            return findBySeller(conn, sellerId);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "findBySeller failed: " + sellerId, e);
            return new ArrayList<>();
        }
    }

    public List<Auction> findBySeller(Connection conn, String sellerId) throws SQLException {
        List<Auction> auctions = new ArrayList<>();
        String sql = AUCTION_SELECT + " WHERE a.seller_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, sellerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) auctions.add(mapRow(rs));
            }
        }
        return auctions;
    }

    public boolean existsByItemId(String itemId) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            return existsByItemId(conn, itemId);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "existsByItemId failed: " + itemId, e);
            return false;
        }
    }

    public boolean existsByItemId(Connection conn, String itemId) throws SQLException {
        String sql = "SELECT 1 FROM auctions WHERE item_id = ? LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, itemId);
            ResultSet rs = ps.executeQuery();
            return rs.next();
        }
    }

    public List<Auction> findByItemId(String itemId) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            return findByItemId(conn, itemId);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "findByItemId failed: " + itemId, e);
            return new ArrayList<>();
        }
    }

    public List<Auction> findByItemId(Connection conn, String itemId) throws SQLException {
        List<Auction> auctions = new ArrayList<>();
        String sql = AUCTION_SELECT + " WHERE a.item_id = ? ORDER BY a.start_time DESC";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, itemId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) auctions.add(mapRow(rs));
            }
        }
        return auctions;
    }

    public List<Auction> findActiveByItemId(Connection conn, String itemId) throws SQLException {
        List<Auction> auctions = new ArrayList<>();
        String sql = AUCTION_SELECT + " WHERE a.item_id = ? AND a.status IN ('OPEN', 'RUNNING')";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, itemId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) auctions.add(mapRow(rs));
            }
        }
        return auctions;
    }

    // ── UPDATE ────────────────────────────────────────────────────────────

    public boolean updateCurrentPrice(String auctionId, double newPrice, String winnerId) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            return updateCurrentPrice(conn, auctionId, newPrice, winnerId);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "updateCurrentPrice failed: " + auctionId, e);
            return false;
        }
    }

    public boolean updateCurrentPrice(Connection conn, String auctionId, double newPrice, String winnerId) throws SQLException {
        String sql = "UPDATE auctions SET current_price = ?, winner_id = ? WHERE auction_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, newPrice);
            ps.setString(2, winnerId);
            ps.setString(3, auctionId);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean updateStatus(String auctionId, AuctionStatus status) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            return updateStatus(conn, auctionId, status);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "updateStatus failed: " + auctionId, e);
            return false;
        }
    }

    public boolean updateStatus(Connection conn, String auctionId, AuctionStatus status) throws SQLException {
        String sql = "UPDATE auctions SET status = ? WHERE auction_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status.name());
            ps.setString(2, auctionId);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean updateEndTime(String auctionId, LocalDateTime newEndTime) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            return updateEndTime(conn, auctionId, newEndTime);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "updateEndTime failed: " + auctionId, e);
            return false;
        }
    }

    public boolean updateEndTime(Connection conn, String auctionId, LocalDateTime newEndTime) throws SQLException {
        String sql = "UPDATE auctions SET end_time = ? WHERE auction_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.valueOf(newEndTime));
            ps.setString(2, auctionId);
            return ps.executeUpdate() > 0;
        }
    }

    // ── DELETE ────────────────────────────────────────────────────────────

    public boolean delete(String auctionId) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            return delete(conn, auctionId);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Delete auction failed: " + auctionId, e);
            return false;
        }
    }

    public boolean delete(Connection conn, String auctionId) throws SQLException {
        String sql = "DELETE FROM auctions WHERE auction_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, auctionId);
            return ps.executeUpdate() > 0;
        }
    }

    // ── HELPER ────────────────────────────────────────────────────────────

    private Auction mapRow(ResultSet rs) throws SQLException {
        String auctionId    = rs.getString("auction_id");
        double startPrice   = rs.getDouble("auction_starting_price");
        double currentPrice = rs.getDouble("current_price");
        AuctionStatus status = AuctionStatus.valueOf(rs.getString("status"));
        LocalDateTime startTime = rs.getTimestamp("start_time").toLocalDateTime();
        LocalDateTime endTime   = rs.getTimestamp("end_time").toLocalDateTime();

        Item item = ItemFactory.createItem(
                rs.getString("item_id"),
                rs.getString("item_type"),
                rs.getString("item_name"),
                rs.getString("item_description"),
                rs.getDouble("item_starting_price")
        );
        item.setSellerId(rs.getString("item_seller_id"));

        User seller = mapUser(rs, "seller_");
        User winner = mapUser(rs, "winner_");

        Auction auction = new Auction(auctionId, item, seller, startPrice, startTime, endTime);
        auction.setCurrentPrice(currentPrice);
        auction.setStatus(status);
        auction.setWinner(winner);
        return auction;
    }

    private User mapUser(ResultSet rs, String prefix) throws SQLException {
        String id = rs.getString(prefix + "user_id");
        if (id == null) return null;

        String username = rs.getString(prefix + "username");
        String password = rs.getString(prefix + "password");
        String email = rs.getString(prefix + "email");
        String role = rs.getString(prefix + "role");
        boolean banned = rs.getBoolean(prefix + "is_banned");

        User user = switch (role.toUpperCase()) {
            case "SELLER" -> new Seller(id, username, password, email);
            case "ADMIN" -> new Admin(id, username, password, email);
            default -> new Bidder(id, username, password, email);
        };
        user.setBanned(banned);
        return user;
    }
}
