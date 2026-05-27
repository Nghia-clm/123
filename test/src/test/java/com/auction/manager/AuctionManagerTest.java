package com.auction.manager;

import com.auction.model.Auction;
import com.auction.model.item.Electronics;
import com.auction.model.item.Item;
import com.auction.model.user.Seller;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AuctionManager")
class AuctionManagerTest {

    @Test
    @DisplayName("addAuction caches auction and creates room and lock")
    void addAuctionCachesAuctionAndCreatesRoomAndLock() throws Exception {
        AuctionManager manager = isolatedManager();
        Auction auction = auction("auction-1");

        manager.addAuction(auction);

        assertSame(auction, manager.getAuction("auction-1"));
        assertEquals(1, manager.getAllActiveAuctions().size());
        assertNotNull(manager.getWriteLock("auction-1"));
        assertNotNull(manager.getReadLock("auction-1"));
    }

    @Test
    @DisplayName("getWriteLock returns same per-auction lock")
    void getWriteLockReturnsSamePerAuctionLock() throws Exception {
        AuctionManager manager = isolatedManager();

        ReentrantReadWriteLock.WriteLock first = manager.getWriteLock("auction-1");
        ReentrantReadWriteLock.WriteLock second = manager.getWriteLock("auction-1");
        ReentrantReadWriteLock.WriteLock other = manager.getWriteLock("auction-2");

        assertSame(first, second);
        assertNotSame(first, other);
    }

    @Test
    @DisplayName("removeAuction clears cache and lock")
    void removeAuctionClearsCacheAndLock() throws Exception {
        AuctionManager manager = isolatedManager();
        manager.addAuction(auction("auction-1"));
        ReentrantReadWriteLock.WriteLock oldLock = manager.getWriteLock("auction-1");

        manager.removeAuction("auction-1");
        ReentrantReadWriteLock.WriteLock newLock = manager.getWriteLock("auction-1");

        assertNull(manager.getAuction("auction-1"));
        assertNotSame(oldLock, newLock);
    }

    private static Auction auction(String id) {
        Seller seller = new Seller("seller-1", "seller", "hash", "seller@test.com");
        Item item = new Electronics("item-1", "Phone", "New", 100, seller.getId());
        item.setSellerId(seller.getId());
        return new Auction(id, item, seller, 100,
                LocalDateTime.now().minusMinutes(1),
                LocalDateTime.now().plusMinutes(1));
    }

    private static AuctionManager isolatedManager() throws Exception {
        AuctionManager manager = allocate(AuctionManager.class);
        setField(manager, "activeAuctions", new ConcurrentHashMap<String, Auction>());
        setField(manager, "auctionRooms", new ConcurrentHashMap<String, Set<?>>());
        setField(manager, "connectedClients", Collections.newSetFromMap(new ConcurrentHashMap<>()));
        setField(manager, "auctionLocks", new ConcurrentHashMap<String, ReentrantReadWriteLock>());
        return manager;
    }

    @SuppressWarnings("unchecked")
    private static <T> T allocate(Class<T> type) throws Exception {
        Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) field.get(null);
        return (T) unsafe.allocateInstance(type);
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
