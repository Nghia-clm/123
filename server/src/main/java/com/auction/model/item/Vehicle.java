package com.auction.model.item;

public class Vehicle extends Item {

    private String vehicleType; // CAR, MOTORBIKE, TRUCK
    private String brand;
    private int    year;
    private int    mileage;

    // ── Constructors ──────────────────────────────────────────────────────

    public Vehicle() {
        super();
        this.category = "VEHICLE";
    }

    /**
     * Constructor nhận id — dùng trong ItemFactory và DAO.
     */
    public Vehicle(String id, String name, String description,
                   double startingPrice, String sellerId) {
        super(id, name, description, startingPrice, sellerId);
        this.category = "VEHICLE";
    }

    /** Constructor đầy đủ với thông tin xe. */
    public Vehicle(String name, String description,
                   double startingPrice, String sellerId,
                   String vehicleType, String brand, int year, int mileage) {
        super(name, description, startingPrice, sellerId);
        this.category    = "VEHICLE";
        this.vehicleType = vehicleType;
        this.brand       = brand;
        this.year        = year;
        this.mileage     = mileage;
    }

    // ── Getters / Setters ─────────────────────────────────────────────────

    public String getVehicleType()          { return vehicleType; }
    public String getBrand()                { return brand; }
    public int    getYear()                 { return year; }
    public int    getMileage()              { return mileage; }

    public void setVehicleType(String type) { this.vehicleType = type; }
    public void setBrand(String brand)      { this.brand = brand; }
    public void setYear(int year)           { this.year = year; }
    public void setMileage(int mileage)     { this.mileage = mileage; }

    // ── Abstract implementations ──────────────────────────────────────────

    @Override
    public boolean validate() {
        if (name == null || name.isBlank()) return false;
        if (startingPrice <= 0)             return false;
        return true;
    }

    @Override
    public void printInfo() {
        System.out.println("=== VEHICLE ===");
        System.out.println("ID             : " + id);
        System.out.println("Name           : " + name);
        System.out.println("Type           : " + vehicleType);
        System.out.println("Brand          : " + brand);
        System.out.println("Year           : " + year);
        System.out.println("Mileage        : " + mileage + " km");
        System.out.println("Starting Price : " + startingPrice);
        System.out.println("Description    : " + description);
    }
}
