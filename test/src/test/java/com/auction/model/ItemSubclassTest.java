package com.auction.model;

import com.auction.model.entity.Entity;
import com.auction.model.item.ArtItem;
import com.auction.model.item.Electronics;
import com.auction.model.item.Item;
import com.auction.model.item.Vehicle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Item subclasses - abstraction and polymorphism")
class ItemSubclassTest {

    @Test
    @DisplayName("Item subclasses expose concrete categories through abstract base type")
    void itemSubclassesExposeCategoriesPolymorphically() {
        List<Item> items = List.of(
                new Electronics("item-1", "Phone", "desc", 100, "seller-1"),
                new ArtItem("item-2", "Painting", "desc", 200, "seller-1"),
                new Vehicle("item-3", "Car", "desc", 300, "seller-1")
        );

        assertTrue(items.stream().allMatch(Entity.class::isInstance));
        assertEquals(List.of("ELECTRONICS", "ART", "VEHICLE"),
                items.stream().map(Item::getCategory).toList());
        assertEquals(items.stream().map(Item::getCategory).toList(),
                items.stream().map(Item::getType).toList());
    }

    @Test
    @DisplayName("validate rejects blank names and non-positive prices")
    void validateRejectsInvalidCoreFields() {
        List<Item> validItems = List.of(
                new Electronics("item-1", "Phone", "desc", 100, "seller-1"),
                new ArtItem("item-2", "Painting", "desc", 200, "seller-1"),
                new Vehicle("item-3", "Car", "desc", 300, "seller-1")
        );
        assertTrue(validItems.stream().allMatch(Item::validate));

        Electronics blankName = new Electronics("item-4", " ", "desc", 100, "seller-1");
        ArtItem zeroPrice = new ArtItem("item-5", "Painting", "desc", 0, "seller-1");
        Vehicle negativePrice = new Vehicle("item-6", "Car", "desc", -1, "seller-1");

        assertFalse(blankName.validate());
        assertFalse(zeroPrice.validate());
        assertFalse(negativePrice.validate());
    }

    @Test
    @DisplayName("subclass-specific fields are stored")
    void subclassSpecificFieldsAreStored() {
        Electronics electronics = new Electronics("Phone", "desc", 100, "seller-1",
                "Apple", 12, "NEW");
        ArtItem art = new ArtItem("Painting", "desc", 200, "seller-1",
                "Artist", 2020, true);
        Vehicle vehicle = new Vehicle("Car", "desc", 300, "seller-1",
                "CAR", "Toyota", 2024, 1000);

        assertEquals("Apple", electronics.getBrand());
        assertEquals(12, electronics.getWarrantyMonths());
        assertEquals("NEW", electronics.getCondition());
        assertEquals("Artist", art.getArtist());
        assertEquals(2020, art.getYearCreated());
        assertTrue(art.isAuthenticated());
        assertEquals("CAR", vehicle.getVehicleType());
        assertEquals("Toyota", vehicle.getBrand());
        assertEquals(2024, vehicle.getYear());
        assertEquals(1000, vehicle.getMileage());
    }

    @Test
    @DisplayName("printInfo dispatches to concrete item subtype")
    void printInfoDispatchesToConcreteSubtype() {
        assertPrintContains(new Electronics("item-1", "Phone", "desc", 100, "seller-1"),
                "=== ELECTRONICS ===", "Phone");
        assertPrintContains(new ArtItem("item-2", "Painting", "desc", 200, "seller-1"),
                "=== ART ITEM ===", "Painting");
        assertPrintContains(new Vehicle("item-3", "Car", "desc", 300, "seller-1"),
                "=== VEHICLE ===", "Car");
    }

    private static void assertPrintContains(Item item, String... expected) {
        PrintStream original = System.out;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            item.printInfo();
        } finally {
            System.setOut(original);
        }

        String printed = output.toString(StandardCharsets.UTF_8);
        for (String text : expected) {
            assertTrue(printed.contains(text), "Expected printed output to contain: " + text);
        }
    }
}
