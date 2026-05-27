package com.auction.dao;

import com.auction.factory.ItemFactory;
import com.auction.model.item.Item;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ItemDAO - Thao tác CRUD với bảng items trong database.
 */
public class ItemDAO {

    private static final Logger LOGGER = Logger.getLogger(ItemDAO.class.getName());

    // ── CREATE ────────────────────────────────────────────────────────────

    public boolean insert(Item item, String sellerId) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            return insert(conn, item, sellerId);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Insert item failed: " + item.getName(), e);
            return false;
        }
    }

    public boolean insert(Connection conn, Item item, String sellerId) throws SQLException {
        String sql = "INSERT INTO items (item_id, seller_id, type, name, description, starting_price) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, item.getId());
            ps.setString(2, sellerId);
            ps.setString(3, item.getType());
            ps.setString(4, item.getName());
            ps.setString(5, item.getDescription());
            ps.setDouble(6, item.getStartingPrice());
            return ps.executeUpdate() > 0;
        }
    }

    // ── READ ──────────────────────────────────────────────────────────────

    public Item findById(String itemId) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            return findById(conn, itemId);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "findById item failed: " + itemId, e);
        }
        return null;
    }

    public Item findById(Connection conn, String itemId) throws SQLException {
        String sql = "SELECT * FROM items WHERE item_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, itemId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return mapRow(rs);
        }
        return null;
    }

    public List<Item> findAll() {
        List<Item> items = new ArrayList<>();
        String sql = "SELECT * FROM items";
        try (Connection conn = DatabaseConnection.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) items.add(mapRow(rs));
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "findAll items failed", e);
        }
        return items;
    }

    public List<Item> findBySeller(String sellerId) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            return findBySeller(conn, sellerId);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "findBySeller failed: " + sellerId, e);
            return new ArrayList<>();
        }
    }

    public List<Item> findBySeller(Connection conn, String sellerId) throws SQLException {
        List<Item> items = new ArrayList<>();
        String sql = "SELECT * FROM items WHERE seller_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, sellerId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) items.add(mapRow(rs));
        }
        return items;
    }

    // ── UPDATE ────────────────────────────────────────────────────────────

    public boolean update(String itemId, String type, String name, String description, double startingPrice) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            return update(conn, itemId, type, name, description, startingPrice);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Update item failed: " + itemId, e);
            return false;
        }
    }

    public boolean update(Connection conn, String itemId, String type, String name, String description, double startingPrice) throws SQLException {
        String sql = "UPDATE items SET type = ?, name = ?, description = ?, starting_price = ? WHERE item_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, type);
            ps.setString(2, name);
            ps.setString(3, description);
            ps.setDouble(4, startingPrice);
            ps.setString(5, itemId);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean update(String itemId, String name, String description) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            return update(conn, itemId, name, description);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Update item failed: " + itemId, e);
            return false;
        }
    }

    public boolean update(Connection conn, String itemId, String name, String description) throws SQLException {
        String sql = "UPDATE items SET name = ?, description = ? WHERE item_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setString(2, description);
            ps.setString(3, itemId);
            return ps.executeUpdate() > 0;
        }
    }

    // ── DELETE ────────────────────────────────────────────────────────────

    public boolean delete(String itemId) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            return delete(conn, itemId);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Delete item failed: " + itemId, e);
            return false;
        }
    }

    public boolean delete(Connection conn, String itemId) throws SQLException {
        String sql = "DELETE FROM items WHERE item_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, itemId);
            return ps.executeUpdate() > 0;
        }
    }

    // ── HELPER ────────────────────────────────────────────────────────────

    private Item mapRow(ResultSet rs) throws SQLException {
        String id           = rs.getString("item_id");
        String type         = rs.getString("type");
        String name         = rs.getString("name");
        String description  = rs.getString("description");
        double startingPrice = rs.getDouble("starting_price");
        Item item = ItemFactory.createItem(id, type, name, description, startingPrice);
        item.setSellerId(rs.getString("seller_id"));
        return item;
    }
}
