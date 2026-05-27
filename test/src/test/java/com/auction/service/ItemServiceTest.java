package com.auction.service;

import com.auction.dao.AuctionDAO;
import com.auction.dao.ItemDAO;
import com.auction.dao.UserDAO;
import com.auction.model.item.Electronics;
import com.auction.model.item.Item;
import com.auction.model.user.Admin;
import com.auction.model.user.Seller;
import com.auction.model.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import com.auction.model.Auction;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ItemService - CRUD and permissions")
class ItemServiceTest {

    private static final String SELLER_ID = "seller-1";
    private static final String OTHER_ID = "seller-2";
    private static final String ADMIN_ID = "admin-1";
    private static final String ITEM_ID = "item-1";

    private FakeItemDAO itemDAO;
    private FakeAuctionDAO auctionDAO;
    private FakeUserDAO userDAO;
    private ItemService service;

    @BeforeEach
    void setUp() {
        itemDAO = new FakeItemDAO();
        auctionDAO = new FakeAuctionDAO();
        userDAO = new FakeUserDAO();
        userDAO.users.put(SELLER_ID, new Seller(SELLER_ID, "seller", "pass", "seller@test.com"));
        userDAO.users.put(OTHER_ID, new Seller(OTHER_ID, "other", "pass", "other@test.com"));
        userDAO.users.put(ADMIN_ID, new Admin(ADMIN_ID, "admin", "pass", "admin@test.com"));
        service = new ItemService(itemDAO, auctionDAO, userDAO);
    }

    @Test
    @DisplayName("createItem rejects blank name and negative price")
    void createItemValidation() {
        assertThrows(IllegalArgumentException.class,
                () -> service.createItem("ELECTRONICS", " ", "desc", 100, SELLER_ID));
        assertThrows(IllegalArgumentException.class,
                () -> service.createItem("ELECTRONICS", "Phone", "desc", -1, SELLER_ID));
    }

    @Test
    @DisplayName("createItem stores item with seller id")
    void createItemSuccess() {
        Item item = service.createItem("ELECTRONICS", "Phone", "desc", 100, SELLER_ID);

        assertNotNull(item.getId());
        assertEquals(SELLER_ID, itemDAO.sellerByItemId.get(item.getId()));
        assertEquals("Phone", itemDAO.items.get(item.getId()).getName());
    }

    @Test
    @DisplayName("getItemById rejects missing item")
    void getItemByIdRejectsMissingItem() {
        assertThrows(IllegalArgumentException.class,
                () -> service.getItemById("missing-item"));
    }

    @Test
    @DisplayName("getAllItems returns DAO items")
    void getAllItemsReturnsDaoItems() {
        itemDAO.items.put(ITEM_ID, itemOwnedBySeller());

        List<Item> items = service.getAllItems();

        assertEquals(1, items.size());
        assertEquals(ITEM_ID, items.get(0).getId());
    }

    @Test
    @DisplayName("getItemsBySeller returns only seller items")
    void getItemsBySellerReturnsOnlySellerItems() {
        itemDAO.items.put(ITEM_ID, itemOwnedBySeller());
        Item other = new Electronics("item-2", "Laptop", "desc", 200, OTHER_ID);
        other.setSellerId(OTHER_ID);
        itemDAO.items.put(other.getId(), other);

        List<Item> items = service.getItemsBySeller(SELLER_ID);

        assertEquals(1, items.size());
        assertEquals(SELLER_ID, items.get(0).getSellerId());
    }

    @Test
    @DisplayName("updateItem rejects non-owner")
    void updateRejectsNonOwner() {
        itemDAO.items.put(ITEM_ID, itemOwnedBySeller());

        assertThrows(IllegalStateException.class,
                () -> service.updateItem(ITEM_ID, "Updated", "new", OTHER_ID));
    }

    @Test
    @DisplayName("updateItem allows admin to edit another seller item")
    void updateAllowsAdmin() {
        itemDAO.items.put(ITEM_ID, itemOwnedBySeller());

        service.updateItem(ITEM_ID, "Updated", "new", ADMIN_ID);

        assertEquals("Updated", itemDAO.items.get(ITEM_ID).getName());
        assertEquals("new", itemDAO.items.get(ITEM_ID).getDescription());
    }

    @Test
    @DisplayName("updateItem falls back to existing values when fields are null or blank")
    void updateFallsBackToExistingValues() {
        itemDAO.items.put(ITEM_ID, itemOwnedBySeller());

        service.updateItem(ITEM_ID, " ", null, SELLER_ID);

        assertEquals("Phone", itemDAO.items.get(ITEM_ID).getName());
        assertEquals("desc", itemDAO.items.get(ITEM_ID).getDescription());
    }

    @Test
    @DisplayName("deleteItem rejects item already used by auction")
    void deleteRejectsItemInAuction() {
        itemDAO.items.put(ITEM_ID, itemOwnedBySeller());
        auctionDAO.existsByItemId = true;

        assertThrows(IllegalStateException.class,
                () -> service.deleteItem(ITEM_ID, SELLER_ID));
        assertTrue(itemDAO.items.containsKey(ITEM_ID));
    }

    @Test
    @DisplayName("deleteItem allows owner when item is not used")
    void deleteAllowsOwner() {
        itemDAO.items.put(ITEM_ID, itemOwnedBySeller());

        service.deleteItem(ITEM_ID, SELLER_ID);

        assertFalse(itemDAO.items.containsKey(ITEM_ID));
    }

    @Test
    @DisplayName("deleteItem rejects missing item")
    void deleteRejectsMissingItem() {
        assertThrows(IllegalArgumentException.class,
                () -> service.deleteItem("missing-item", SELLER_ID));
    }

    private static Item itemOwnedBySeller() {
        Item item = new Electronics(ITEM_ID, "Phone", "desc", 100, SELLER_ID);
        item.setSellerId(SELLER_ID);
        return item;
    }

    private static final class FakeItemDAO extends ItemDAO {
        private final Map<String, Item> items = new HashMap<>();
        private final Map<String, String> sellerByItemId = new HashMap<>();

        @Override
        public boolean insert(Item item, String sellerId) {
            item.setSellerId(sellerId);
            items.put(item.getId(), item);
            sellerByItemId.put(item.getId(), sellerId);
            return true;
        }

        @Override
        public Item findById(String itemId) {
            return items.get(itemId);
        }

        @Override
        public List<Item> findAll() {
            return new ArrayList<>(items.values());
        }

        @Override
        public List<Item> findBySeller(String sellerId) {
            return items.values().stream()
                    .filter(item -> sellerId.equals(item.getSellerId()))
                    .toList();
        }

        @Override
        public boolean update(String itemId, String name, String description) {
            Item item = items.get(itemId);
            if (item == null) return false;
            item.setName(name);
            item.setDescription(description);
            return true;
        }

        @Override
        public boolean delete(String itemId) {
            return items.remove(itemId) != null;
        }
    }

    private static final class FakeAuctionDAO extends AuctionDAO {
        private boolean existsByItemId;

        @Override
        public boolean existsByItemId(String itemId) {
            return existsByItemId;
        }

        @Override
        public List<Auction> findByItemId(String itemId) {
            // Trả về list rỗng — simulate auction record đã bị soft-delete
            // nhưng existsByItemId vẫn = true (đã từng dùng).
            // Service sẽ rơi vào nhánh auctions.isEmpty() → throw IllegalStateException.
            return new ArrayList<>();
        }
    }

    private static final class FakeUserDAO extends UserDAO {
        private final Map<String, User> users = new HashMap<>();

        @Override
        public User findById(String userId) {
            return users.get(userId);
        }

        @Override
        public User findById(Connection conn, String userId) throws SQLException {
            return users.get(userId);
        }
    }
}