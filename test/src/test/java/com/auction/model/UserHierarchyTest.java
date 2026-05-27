package com.auction.model;

import com.auction.model.entity.Entity;
import com.auction.model.user.Admin;
import com.auction.model.user.Bidder;
import com.auction.model.user.Seller;
import com.auction.model.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("User hierarchy - OOP behavior")
class UserHierarchyTest {

    @Test
    @DisplayName("Bidder, Seller and Admin are polymorphic User and Entity instances")
    void userSubclassesArePolymorphic() {
        List<User> users = List.of(
                new Bidder("bidder-1", "alice", "hash", "alice@test.com"),
                new Seller("seller-1", "seller", "hash", "seller@test.com"),
                new Admin("admin-1", "admin", "hash", "admin@test.com")
        );

        assertTrue(users.stream().allMatch(Entity.class::isInstance));
        assertEquals(List.of("BIDDER", "SELLER", "ADMIN"),
                users.stream().map(User::getRole).toList());
    }

    @Test
    @DisplayName("Bidder tracks balance and bid count")
    void bidderTracksBalanceAndBidCount() {
        Bidder bidder = new Bidder("bidder-1", "alice", "hash", "alice@test.com");

        bidder.setBalance(500);
        bidder.incrementBidCount();
        bidder.incrementBidCount();

        assertEquals(500, bidder.getBalance(), 0.01);
        assertEquals(2, bidder.getTotalBidsPlaced());
    }

    @Test
    @DisplayName("Seller tracks listed items and revenue")
    void sellerTracksItemsAndRevenue() {
        Seller seller = new Seller("seller-1", "seller", "hash", "seller@test.com");

        seller.incrementItemCount();
        seller.addRevenue(100);
        seller.addRevenue(50);

        assertEquals(1, seller.getTotalItemsListed());
        assertEquals(150, seller.getTotalRevenue(), 0.01);
    }

    @Test
    @DisplayName("Admin permissions depend on admin level")
    void adminPermissionsDependOnLevel() {
        Admin admin = new Admin("admin-1", "admin", "hash", "admin@test.com");

        assertEquals("NORMAL", admin.getAdminLevel());
        assertTrue(admin.canCancelAuction());
        assertFalse(admin.canDeleteUser());

        admin.setAdminLevel("SUPER");

        assertTrue(admin.canDeleteUser());
    }

    @Test
    @DisplayName("printInfo dispatches to concrete user subtype")
    void printInfoDispatchesToConcreteSubtype() {
        assertPrintContains(new Bidder("bidder-1", "alice", "hash", "alice@test.com"), "=== BIDDER ===", "alice");
        assertPrintContains(new Seller("seller-1", "seller", "hash", "seller@test.com"), "=== SELLER ===", "seller");
        assertPrintContains(new Admin("admin-1", "admin", "hash", "admin@test.com"), "=== ADMIN ===", "NORMAL");
    }

    private static void assertPrintContains(User user, String... expected) {
        PrintStream original = System.out;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            user.printInfo();
        } finally {
            System.setOut(original);
        }

        String printed = output.toString(StandardCharsets.UTF_8);
        for (String text : expected) {
            assertTrue(printed.contains(text), "Expected printed output to contain: " + text);
        }
    }
}
