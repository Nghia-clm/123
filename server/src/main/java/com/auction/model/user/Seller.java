package com.auction.model.user;

public class Seller extends User {
    private int totalItemsListed;
    private double totalRevenue;

    public Seller() {
        super();
        this.role = "SELLER";
    }

    public Seller(String id, String username, String password, String email) {
        super(id, username, password, email);
        this.role = "SELLER";
    }

    public int getTotalItemsListed()      { return totalItemsListed; }
    public double getTotalRevenue()       { return totalRevenue; }
    public void setTotalRevenue(double r) { this.totalRevenue = r; }
    public void incrementItemCount()      { this.totalItemsListed++; }
    public void addRevenue(double amount) { this.totalRevenue += amount; }

    @Override
    public void printInfo() {
        System.out.println("=== SELLER ===");
        System.out.println("ID           : " + id);
        System.out.println("Username     : " + username);
        System.out.println("Email        : " + email);
        System.out.println("Items Listed : " + totalItemsListed);
        System.out.println("Revenue      : " + totalRevenue);
    }
}
