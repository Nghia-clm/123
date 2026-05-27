package com.auction.factory;

import com.auction.model.item.ArtItem;
import com.auction.model.item.Electronics;
import com.auction.model.item.Item;
import com.auction.model.item.Vehicle;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * ItemFactoryTest - Kiểm thử Factory Method Pattern tạo Item.
 *
 * Covers:
 *  - Tạo đúng loại item từ type string
 *  - ID được gán đúng
 *  - Các trường name, description, startingPrice đúng
 *  - Type không hợp lệ ném IllegalArgumentException
 *  - Type null / blank ném IllegalArgumentException
 *  - getSupportedTypes() trả về đúng danh sách
 *  - Polymorphism: getCategory() / getType() đúng với từng loại
 */
@DisplayName("ItemFactory - Factory Method Pattern")
class ItemFactoryTest {

    private static final String TEST_ID    = "item-test-uuid";
    private static final String NAME       = "Test Item";
    private static final String DESC       = "Test Description";
    private static final double PRICE      = 500_000.0;

    // ── Tạo Electronics ───────────────────────────────────────────────────

    @Test
    @DisplayName("createItem(ELECTRONICS) trả về instance Electronics")
    void createElectronicsReturnsCorrectType() {
        Item item = ItemFactory.createItem(TEST_ID, "ELECTRONICS", NAME, DESC, PRICE);
        assertInstanceOf(Electronics.class, item);
    }

    @Test
    @DisplayName("Electronics có category = ELECTRONICS")
    void electronicsHasCorrectCategory() {
        Item item = ItemFactory.createItem(TEST_ID, "ELECTRONICS", NAME, DESC, PRICE);
        assertEquals("ELECTRONICS", item.getCategory());
        assertEquals("ELECTRONICS", item.getType()); // alias
    }

    @Test
    @DisplayName("Type string case-insensitive: 'electronics' → Electronics")
    void typeIsCaseInsensitive() {
        Item lower = ItemFactory.createItem(TEST_ID, "electronics", NAME, DESC, PRICE);
        Item mixed = ItemFactory.createItem(TEST_ID, "Electronics", NAME, DESC, PRICE);
        assertInstanceOf(Electronics.class, lower);
        assertInstanceOf(Electronics.class, mixed);
    }

    // ── Tạo ArtItem ───────────────────────────────────────────────────────

    @Test
    @DisplayName("createItem(ART) trả về instance ArtItem")
    void createArtItemReturnsCorrectType() {
        Item item = ItemFactory.createItem(TEST_ID, "ART", NAME, DESC, PRICE);
        assertInstanceOf(ArtItem.class, item);
    }

    @Test
    @DisplayName("ArtItem có category = ART")
    void artItemHasCorrectCategory() {
        Item item = ItemFactory.createItem(TEST_ID, "ART", NAME, DESC, PRICE);
        assertEquals("ART", item.getCategory());
    }

    // ── Tạo Vehicle ───────────────────────────────────────────────────────

    @Test
    @DisplayName("createItem(VEHICLE) trả về instance Vehicle")
    void createVehicleReturnsCorrectType() {
        Item item = ItemFactory.createItem(TEST_ID, "VEHICLE", NAME, DESC, PRICE);
        assertInstanceOf(Vehicle.class, item);
    }

    @Test
    @DisplayName("Vehicle có category = VEHICLE")
    void vehicleHasCorrectCategory() {
        Item item = ItemFactory.createItem(TEST_ID, "VEHICLE", NAME, DESC, PRICE);
        assertEquals("VEHICLE", item.getCategory());
    }

    // ── Kiểm tra fields được gán đúng ─────────────────────────────────────

    @Test
    @DisplayName("ID được gán đúng theo tham số")
    void itemIdIsSetCorrectly() {
        Item item = ItemFactory.createItem(TEST_ID, "ELECTRONICS", NAME, DESC, PRICE);
        assertEquals(TEST_ID, item.getId());
    }

    @Test
    @DisplayName("Name được gán đúng")
    void itemNameIsSetCorrectly() {
        Item item = ItemFactory.createItem(TEST_ID, "ART", NAME, DESC, PRICE);
        assertEquals(NAME, item.getName());
    }

    @Test
    @DisplayName("Description được gán đúng")
    void itemDescriptionIsSetCorrectly() {
        Item item = ItemFactory.createItem(TEST_ID, "VEHICLE", NAME, DESC, PRICE);
        assertEquals(DESC, item.getDescription());
    }

    @Test
    @DisplayName("StartingPrice được gán đúng")
    void itemStartingPriceIsSetCorrectly() {
        Item item = ItemFactory.createItem(TEST_ID, "ELECTRONICS", NAME, DESC, PRICE);
        assertEquals(PRICE, item.getStartingPrice(), 0.001);
    }

    // ── Type không hợp lệ ─────────────────────────────────────────────────

    @Test
    @DisplayName("Type không hợp lệ ném IllegalArgumentException")
    void unknownTypeThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> ItemFactory.createItem(TEST_ID, "FURNITURE", NAME, DESC, PRICE));
    }

    @Test
    @DisplayName("Type null ném IllegalArgumentException")
    void nullTypeThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> ItemFactory.createItem(TEST_ID, null, NAME, DESC, PRICE));
    }

    @Test
    @DisplayName("Type blank ném IllegalArgumentException")
    void blankTypeThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> ItemFactory.createItem(TEST_ID, "   ", NAME, DESC, PRICE));
    }

    // ── getSupportedTypes ─────────────────────────────────────────────────

    @Test
    @DisplayName("getSupportedTypes() trả về đúng 3 loại")
    void supportedTypesHasThreeEntries() {
        String[] types = ItemFactory.getSupportedTypes();
        assertEquals(3, types.length);
    }

    @Test
    @DisplayName("getSupportedTypes() chứa ELECTRONICS, ART, VEHICLE")
    void supportedTypesContainsAllTypes() {
        String[] types = ItemFactory.getSupportedTypes();
        assertArrayEquals(
            new String[]{"ELECTRONICS", "ART", "VEHICLE"},
            types
        );
    }

    // ── Polymorphism ──────────────────────────────────────────────────────

    @Test
    @DisplayName("Các item khác nhau từ factory đều là instance của Item (Polymorphism)")
    void allItemsAreInstanceOfItem() {
        Item e = ItemFactory.createItem("id1", "ELECTRONICS", NAME, DESC, PRICE);
        Item a = ItemFactory.createItem("id2", "ART",         NAME, DESC, PRICE);
        Item v = ItemFactory.createItem("id3", "VEHICLE",     NAME, DESC, PRICE);

        assertInstanceOf(Item.class, e);
        assertInstanceOf(Item.class, a);
        assertInstanceOf(Item.class, v);
    }

    @Test
    @DisplayName("Mỗi item đều gọi được getCategory() đúng (Polymorphism)")
    void polymorphicGetCategory() {
        Item[] items = {
            ItemFactory.createItem("id1", "ELECTRONICS", NAME, DESC, PRICE),
            ItemFactory.createItem("id2", "ART",         NAME, DESC, PRICE),
            ItemFactory.createItem("id3", "VEHICLE",     NAME, DESC, PRICE),
        };
        String[] expected = { "ELECTRONICS", "ART", "VEHICLE" };

        for (int i = 0; i < items.length; i++) {
            assertEquals(expected[i], items[i].getCategory(),
                "Category sai tại index " + i);
        }
    }
}
