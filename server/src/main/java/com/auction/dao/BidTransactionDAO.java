package com.auction.dao;

import com.auction.model.BidTransaction;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * BidTransactionDAO - Thao tác CRUD với bảng bid_transactions.
 */
public class BidTransactionDAO {

    private static final Logger LOGGER = Logger.getLogger(BidTransactionDAO.class.getName());

    // ── CREATE ────────────────────────────────────────────────────────────

    public boolean insert(BidTransaction tx) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            return insert(conn, tx);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Insert BidTransaction failed", e);
            return false;
        }
    }

    public boolean insert(Connection conn, BidTransaction tx) throws SQLException {
        String sql = "INSERT INTO bid_transactions (transaction_id, auction_id, bidder_id, bid_amount, bid_time, is_auto_bid) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tx.getId());
            ps.setString(2, tx.getAuctionId());
            ps.setString(3, tx.getBidderId());
            ps.setDouble(4, tx.getBidAmount());
            ps.setTimestamp(5, Timestamp.valueOf(tx.getTimestamp()));
            ps.setBoolean(6, tx.isAutoBid());
            return ps.executeUpdate() > 0;
        }
    }

    // ── READ ──────────────────────────────────────────────────────────────

    public List<BidTransaction> findByAuction(String auctionId) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            return findByAuction(conn, auctionId);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "findByAuction failed: " + auctionId, e);
            return new ArrayList<>();
        }
    }

    public List<BidTransaction> findByAuction(Connection conn, String auctionId) throws SQLException {
        List<BidTransaction> list = new ArrayList<>();
        String sql = "SELECT * FROM bid_transactions WHERE auction_id = ? ORDER BY bid_time ASC";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, auctionId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(mapRow(rs));
        }
        return list;
    }

    public List<BidTransaction> findByBidder(String bidderId) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            return findByBidder(conn, bidderId);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "findByBidder failed: " + bidderId, e);
            return new ArrayList<>();
        }
    }

    public List<BidTransaction> findByBidder(Connection conn, String bidderId) throws SQLException {
        List<BidTransaction> list = new ArrayList<>();
        String sql = "SELECT * FROM bid_transactions WHERE bidder_id = ? ORDER BY bid_time DESC";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, bidderId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(mapRow(rs));
        }
        return list;
    }

    public BidTransaction findHighestBid(String auctionId) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            return findHighestBid(conn, auctionId);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "findHighestBid failed: " + auctionId, e);
        }
        return null;
    }

    public BidTransaction findHighestBid(Connection conn, String auctionId) throws SQLException {
        String sql = "SELECT * FROM bid_transactions WHERE auction_id = ? ORDER BY bid_amount DESC LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, auctionId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return mapRow(rs);
        }
        return null;
    }

    public boolean hasBids(String auctionId) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            return hasBids(conn, auctionId);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "hasBids failed: " + auctionId, e);
            return true;
        }
    }

    public boolean hasBids(Connection conn, String auctionId) throws SQLException {
        String sql = "SELECT 1 FROM bid_transactions WHERE auction_id = ? LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, auctionId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    // ── HELPER ────────────────────────────────────────────────────────────

    private BidTransaction mapRow(ResultSet rs) throws SQLException {
        String id        = rs.getString("transaction_id");
        String auctionId = rs.getString("auction_id");
        String bidderId  = rs.getString("bidder_id");
        double amount    = rs.getDouble("bid_amount");
        LocalDateTime ts = rs.getTimestamp("bid_time").toLocalDateTime();
        BidTransaction tx = new BidTransaction(id, auctionId, bidderId, amount, ts);
        tx.setAutoBid(rs.getBoolean("is_auto_bid"));
        return tx;
    }
}
