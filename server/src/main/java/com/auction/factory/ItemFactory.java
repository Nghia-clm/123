package com.auction.factory;

import com.auction.model.item.ArtItem;
import com.auction.model.item.Electronics;
import com.auction.model.item.Item;
import com.auction.model.item.Vehicle;

/**
 * ItemFactory - Factory Method Pattern.
 *
 * Tạo đúng loại Item (Electronics, ArtItem, Vehicle) theo type string.
 * Dùng constructor Item(id, name, description, startingPrice, sellerId)
 * thay vì setId() rời rạc — nhất quán với Entity hierarchy.
 */
public class ItemFactory {

    private ItemFactory() {}

    /**
     * Tạo Item theo loại, gán id ngay trong constructor.
     *
     * @param id            ID duy nhất của item (UUID)
     * @param type          "ELECTRONICS" | "ART" | "VEHICLE"  (case-insensitive)
     * @param name          tên sản phẩm
     * @param description   mô tả
     * @param startingPrice giá khởi điểm
     * @return Item tương ứng với type
     * @throws IllegalArgumentException nếu type null, blank hoặc không hợp lệ
     */
    public static Item createItem(String id, String type,
                                   String name, String description,
                                   double startingPrice) {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("Loại mục không được phép là null hoặc rỗng.");
        }

        return switch (type.toUpperCase()) {
            case "ELECTRONICS" -> new Electronics(id, name, description, startingPrice, null);
            case "ART"         -> new ArtItem(id, name, description, startingPrice, null);
            case "VEHICLE"     -> new Vehicle(id, name, description, startingPrice, null);
            default -> throw new IllegalArgumentException(
                "Unknown item type: " + type + ". Valid types: ELECTRONICS, ART, VEHICLE"
            );
        };
    }

    /** Trả về danh sách các type hợp lệ (dùng cho ComboBox ở UI). */
    public static String[] getSupportedTypes() {
        return new String[]{"ELECTRONICS", "ART", "VEHICLE"};
    }
}
