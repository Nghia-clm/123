package com.auction.model.user;

public class Bidder extends User {
    private double balance;
    private int totalBidsPlaced;

    public Bidder() {
        super();
        this.role = "BIDDER";
    }

    public Bidder(String id, String username, String password, String email) {
        super(id, username, password, email);
        this.role = "BIDDER";
    }

    public double getBalance()       { return balance; }
    public int getTotalBidsPlaced()  { return totalBidsPlaced; }
    public void setBalance(double b) { this.balance = b; }
    public void incrementBidCount()  { this.totalBidsPlaced++; }

    @Override
    public void printInfo() {
        System.out.println("=== BIDDER ===");
        System.out.println("ID       : " + id);
        System.out.println("Username : " + username);
        System.out.println("Email    : " + email);
        System.out.println("Balance  : " + balance);
        System.out.println("Bids     : " + totalBidsPlaced);
    }
}