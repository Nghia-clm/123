package com.auction.model.item;

import com.auction.model.entity.Entity;

/**
 * Item - Abstract class đại diện cho sản phẩm đấu giá.
 *
 * Kế thừa từ Entity, cung cấp 3 constructor:
 *  - Item()                              → tự sinh UUID (dùng khi tạo mới không có id)
 *  - Item(id, name, description, ...)   → nhận id từ ngoài (dùng trong Factory và DAO)
 *  - Item(name, description, ...)       → tương thích ngược với code cũ
 */
public abstract class Item extends Entity {

    protected String name;
    protected String description;
    protected double startingPrice;
    protected String sellerId;
    protected String category;

    // ── Constructors ──────────────────────────────────────────────────────

    /** Constructor không tham số — Entity tự sinh UUID. */
    public Item() {
        super();
    }

    /**
     * Constructor đầy đủ nhận id từ ngoài.
     * Dùng trong: ItemFactory.createItem(), ItemDAO.mapRow()
     */
    public Item(String id, String name, String description,
                double startingPrice, String sellerId) {
        super(id);                         // gán id cho Entity
        this.name          = name;
        this.description   = description;
        this.startingPrice = startingPrice;
        this.sellerId      = sellerId;
    }

    /**
     * Constructor tương thích ngược (không nhận id — Entity tự sinh UUID).
     * Giữ lại để subclass cũ (Electronics, ArtItem, Vehicle) không bị lỗi.
     */
    public Item(String name, String description,
                double startingPrice, String sellerId) {
        super();
        this.name          = name;
        this.description   = description;
        this.startingPrice = startingPrice;
        this.sellerId      = sellerId;
    }

    // ── Getters ───────────────────────────────────────────────────────────

    public String getName()          { return name; }
    public String getDescription()   { return description; }
    public double getStartingPrice() { return startingPrice; }
    public String getSellerId()      { return sellerId; }
    public String getCategory()      { return category; }
    public String getType()          { return category; }   // alias dùng trong DAO

    // ── Setters ───────────────────────────────────────────────────────────

    public void setName(String name)               { this.name = name; }
    public void setDescription(String description) { this.description = description; }
    public void setStartingPrice(double price)     { this.startingPrice = price; }
    public void setSellerId(String sellerId)       { this.sellerId = sellerId; }
    public void setCategory(String category)       { this.category = category; }

    // ── Abstract ──────────────────────────────────────────────────────────

    /** Subclass tự định nghĩa rule validate riêng theo loại sản phẩm. */
    public abstract boolean validate();

    @Override
    public String toString() {
        return "Item{id='" + id
               + "', name='" + name
               + "', category='" + category
               + "', startingPrice=" + startingPrice + "}";
    }
}
