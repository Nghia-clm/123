package com.auction.dao;

import com.auction.model.Auction;
import com.auction.model.AuctionStatus;
import com.auction.model.BidTransaction;
import com.auction.model.item.Electronics;
import com.auction.model.item.Item;
import com.auction.model.user.Bidder;
import com.auction.model.user.Seller;
import com.auction.model.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DAO JDBC SQL and mapping")
class DaoJdbcMappingTest {

    @Test
    @DisplayName("UserDAO maps role and is_banned from ResultSet")
    void userDaoMapsRoleAndBannedFlag() throws Exception {
        JdbcStub jdbc = JdbcStub.withRows(List.of(row(
                "user_id", "seller-1",
                "username", "seller",
                "password", "hash",
                "email", "seller@test.com",
                "role", "SELLER",
                "is_banned", true
        )));

        User user = new UserDAO().findById(jdbc.connection(), "seller-1");

        assertInstanceOf(Seller.class, user);
        assertTrue(user.isBanned());
        assertEquals("seller-1", user.getId());
        assertTrue(jdbc.lastSql.contains("FROM users"));
        assertEquals("seller-1", jdbc.lastParams.get(0));
    }

    @Test
    @DisplayName("UserDAO findByUsername queries username and maps user")
    void userDaoFindByUsernameQueriesUsernameAndMapsUser() throws Exception {
        JdbcStub jdbc = JdbcStub.withRows(List.of(row(
                "user_id", "bidder-1",
                "username", "alice",
                "password", "hash",
                "email", "alice@test.com",
                "role", "BIDDER",
                "is_banned", false
        )));

        User user = new UserDAO().findByUsername(jdbc.connection(), "alice");

        assertInstanceOf(Bidder.class, user);
        assertEquals("bidder-1", user.getId());
        assertEquals("alice", user.getUsername());
        assertFalse(user.isBanned());
        assertTrue(jdbc.lastSql.contains("WHERE username = ?"));
        assertEquals("alice", jdbc.lastParams.get(0));
    }

    @Test
    @DisplayName("UserDAO insert writes user fields and default banned flag")
    void userDaoInsertWritesFields() throws Exception {
        JdbcStub jdbc = JdbcStub.withUpdateCount(1);
        User user = new Bidder("bidder-1", "alice", "hash", "alice@test.com");

        boolean inserted = new UserDAO().insert(jdbc.connection(), user);

        assertTrue(inserted);
        assertTrue(jdbc.lastSql.contains("is_banned"));
        assertEquals("bidder-1", jdbc.lastParams.get(0));
        assertEquals("alice", jdbc.lastParams.get(1));
        assertEquals("hash", jdbc.lastParams.get(2));
        assertEquals("alice@test.com", jdbc.lastParams.get(3));
        assertEquals("BIDDER", jdbc.lastParams.get(4));
        assertEquals(false, jdbc.lastParams.get(5));
    }

    @Test
    @DisplayName("ItemDAO maps item type and seller_id")
    void itemDaoMapsTypeAndSellerId() throws Exception {
        JdbcStub jdbc = JdbcStub.withRows(List.of(row(
                "item_id", "item-1",
                "seller_id", "seller-1",
                "type", "ELECTRONICS",
                "name", "Phone",
                "description", "New",
                "starting_price", 100.0
        )));

        Item item = new ItemDAO().findById(jdbc.connection(), "item-1");

        assertInstanceOf(Electronics.class, item);
        assertEquals("seller-1", item.getSellerId());
        assertEquals("ELECTRONICS", item.getType());
        assertEquals(100, item.getStartingPrice(), 0.01);
        assertTrue(jdbc.lastSql.contains("FROM items"));
    }

    @Test
    @DisplayName("ItemDAO insert writes item_type and seller_id")
    void itemDaoInsertWritesTypeAndSellerId() throws Exception {
        JdbcStub jdbc = JdbcStub.withUpdateCount(1);
        Item item = new Electronics("item-1", "Phone", "New", 100, "seller-1");

        boolean inserted = new ItemDAO().insert(jdbc.connection(), item, "seller-1");

        assertTrue(inserted);
        assertTrue(jdbc.lastSql.contains("type"));
        assertEquals("item-1", jdbc.lastParams.get(0));
        assertEquals("seller-1", jdbc.lastParams.get(1));
        assertEquals("ELECTRONICS", jdbc.lastParams.get(2));
        assertEquals("Phone", jdbc.lastParams.get(3));
    }

    @Test
    @DisplayName("AuctionDAO maps joined item, seller, winner and winner_id")
    void auctionDaoMapsJoinedFields() throws Exception {
        LocalDateTime start = LocalDateTime.now().minusMinutes(5);
        LocalDateTime end = LocalDateTime.now().plusMinutes(5);
        JdbcStub jdbc = JdbcStub.withRows(List.of(row(
                "auction_id", "auction-1",
                "auction_starting_price", 100.0,
                "current_price", 150.0,
                "status", "RUNNING",
                "start_time", Timestamp.valueOf(start),
                "end_time", Timestamp.valueOf(end),
                "item_id", "item-1",
                "item_seller_id", "seller-1",
                "item_type", "ELECTRONICS",
                "item_name", "Phone",
                "item_description", "New",
                "item_starting_price", 90.0,
                "seller_user_id", "seller-1",
                "seller_username", "seller",
                "seller_password", "hash",
                "seller_email", "seller@test.com",
                "seller_role", "SELLER",
                "seller_is_banned", false,
                "winner_user_id", "winner-1",
                "winner_username", "winner",
                "winner_password", "hash",
                "winner_email", "winner@test.com",
                "winner_role", "BIDDER",
                "winner_is_banned", true
        )));

        Auction auction = new AuctionDAO().findById(jdbc.connection(), "auction-1");

        assertEquals(AuctionStatus.RUNNING, auction.getStatus());
        assertEquals(150, auction.getCurrentPrice(), 0.01);
        assertEquals("ELECTRONICS", auction.getItem().getType());
        assertEquals("seller-1", auction.getSeller().getId());
        assertEquals("winner-1", auction.getCurrentWinnerId());
        assertTrue(auction.getWinner().isBanned());
        assertTrue(jdbc.lastSql.contains("winner_id"));
    }

    @Test
    @DisplayName("AuctionDAO insert writes winner_id when winner exists")
    void auctionDaoInsertWritesWinnerId() throws Exception {
        JdbcStub jdbc = JdbcStub.withUpdateCount(1);
        Seller seller = new Seller("seller-1", "seller", "hash", "seller@test.com");
        Bidder winner = new Bidder("winner-1", "winner", "hash", "winner@test.com");
        Item item = new Electronics("item-1", "Phone", "New", 100, "seller-1");
        Auction auction = new Auction("auction-1", item, seller, 100,
                LocalDateTime.now().minusMinutes(1), LocalDateTime.now().plusMinutes(1));
        auction.setWinner(winner);

        boolean inserted = new AuctionDAO().insert(jdbc.connection(), auction);

        assertTrue(inserted);
        assertTrue(jdbc.lastSql.contains("winner_id"));
        assertEquals("auction-1", jdbc.lastParams.get(0));
        assertEquals("item-1", jdbc.lastParams.get(1));
        assertEquals("seller-1", jdbc.lastParams.get(2));
        assertEquals("winner-1", jdbc.lastParams.get(8));
    }

    @Test
    @DisplayName("AuctionDAO updateCurrentPrice writes winner_id")
    void auctionDaoUpdateCurrentPriceWritesWinnerId() throws Exception {
        JdbcStub jdbc = JdbcStub.withUpdateCount(1);

        boolean updated = new AuctionDAO().updateCurrentPrice(
                jdbc.connection(), "auction-1", 200, "winner-1");

        assertTrue(updated);
        assertTrue(jdbc.lastSql.contains("winner_id"));
        assertEquals(200.0, (double) jdbc.lastParams.get(0), 0.01);
        assertEquals("winner-1", jdbc.lastParams.get(1));
        assertEquals("auction-1", jdbc.lastParams.get(2));
    }

    @Test
    @DisplayName("BidTransactionDAO maps bid_time and is_auto_bid")
    void bidTransactionDaoMapsTimestampAndAutoBid() throws Exception {
        LocalDateTime bidTime = LocalDateTime.now();
        JdbcStub jdbc = JdbcStub.withRows(List.of(row(
                "transaction_id", "tx-1",
                "auction_id", "auction-1",
                "bidder_id", "bidder-1",
                "bid_amount", 150.0,
                "bid_time", Timestamp.valueOf(bidTime),
                "is_auto_bid", true
        )));

        List<BidTransaction> history = new BidTransactionDAO()
                .findByAuction(jdbc.connection(), "auction-1");

        assertEquals(1, history.size());
        assertEquals("tx-1", history.get(0).getId());
        assertEquals(150, history.get(0).getBidAmount(), 0.01);
        assertEquals(bidTime, history.get(0).getTimestamp());
        assertTrue(history.get(0).isAutoBid());
        assertTrue(jdbc.lastSql.contains("ORDER BY bid_time ASC"));
    }

    @Test
    @DisplayName("BidTransactionDAO insert writes all transaction fields")
    void bidTransactionDaoInsertWritesFields() throws Exception {
        LocalDateTime bidTime = LocalDateTime.now();
        BidTransaction tx = new BidTransaction("tx-1", "auction-1", "bidder-1", 150, bidTime);
        tx.setAutoBid(true);
        JdbcStub jdbc = JdbcStub.withUpdateCount(1);

        boolean inserted = new BidTransactionDAO().insert(jdbc.connection(), tx);

        assertTrue(inserted);
        assertTrue(jdbc.lastSql.contains("is_auto_bid"));
        assertEquals("tx-1", jdbc.lastParams.get(0));
        assertEquals("auction-1", jdbc.lastParams.get(1));
        assertEquals("bidder-1", jdbc.lastParams.get(2));
        assertEquals(150.0, (double) jdbc.lastParams.get(3), 0.01);
        assertEquals(Timestamp.valueOf(bidTime), jdbc.lastParams.get(4));
        assertEquals(true, jdbc.lastParams.get(5));
    }

    private static Map<String, Object> row(Object... keyValues) {
        Map<String, Object> row = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            row.put((String) keyValues[i], keyValues[i + 1]);
        }
        return row;
    }

    private static final class JdbcStub {
        private final List<Map<String, Object>> rows;
        private final int updateCount;
        private String lastSql = "";
        private List<Object> lastParams = new ArrayList<>();

        private JdbcStub(List<Map<String, Object>> rows, int updateCount) {
            this.rows = rows;
            this.updateCount = updateCount;
        }

        static JdbcStub withRows(List<Map<String, Object>> rows) {
            return new JdbcStub(rows, 0);
        }

        static JdbcStub withUpdateCount(int updateCount) {
            return new JdbcStub(List.of(), updateCount);
        }

        Connection connection() {
            return (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(),
                    new Class<?>[]{Connection.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "prepareStatement" -> {
                            lastSql = (String) args[0];
                            lastParams = new ArrayList<>();
                            yield preparedStatement();
                        }
                        case "createStatement" -> statement();
                        case "close" -> null;
                        case "isClosed" -> false;
                        default -> defaultValue(method.getReturnType());
                    });
        }

        private PreparedStatement preparedStatement() {
            return (PreparedStatement) Proxy.newProxyInstance(
                    PreparedStatement.class.getClassLoader(),
                    new Class<?>[]{PreparedStatement.class},
                    (proxy, method, args) -> {
                        String name = method.getName();
                        if (name.startsWith("set")) {
                            int index = (int) args[0] - 1;
                            while (lastParams.size() <= index) lastParams.add(null);
                            lastParams.set(index, args[1]);
                            return null;
                        }
                        return switch (name) {
                            case "executeQuery" -> resultSet();
                            case "executeUpdate" -> updateCount;
                            case "close" -> null;
                            default -> defaultValue(method.getReturnType());
                        };
                    });
        }

        private Statement statement() {
            return (Statement) Proxy.newProxyInstance(
                    Statement.class.getClassLoader(),
                    new Class<?>[]{Statement.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "executeQuery" -> {
                            lastSql = (String) args[0];
                            yield resultSet();
                        }
                        case "close" -> null;
                        default -> defaultValue(method.getReturnType());
                    });
        }

        private ResultSet resultSet() {
            return (ResultSet) Proxy.newProxyInstance(
                    ResultSet.class.getClassLoader(),
                    new Class<?>[]{ResultSet.class},
                    new java.lang.reflect.InvocationHandler() {
                        private int index = -1;

                        @Override
                        public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) {
                            String name = method.getName();
                            if ("next".equals(name)) {
                                index++;
                                return index < rows.size();
                            }
                            if ("close".equals(name)) return null;
                            Object value = rows.get(index).get((String) args[0]);
                            return switch (name) {
                                case "getString" -> value == null ? null : value.toString();
                                case "getDouble" -> ((Number) value).doubleValue();
                                case "getBoolean" -> Boolean.TRUE.equals(value);
                                case "getTimestamp" -> value;
                                default -> defaultValue(method.getReturnType());
                            };
                        }
                    });
        }

        private static Object defaultValue(Class<?> type) {
            if (!type.isPrimitive()) return null;
            if (type == boolean.class) return false;
            if (type == int.class) return 0;
            if (type == long.class) return 0L;
            if (type == double.class) return 0D;
            return null;
        }
    }
}
