package com.auction.model.user;

public class Admin extends User {
    private String adminLevel; // SUPER, NORMAL

    public Admin() {
        super();
        this.role       = "ADMIN";
        this.adminLevel = "NORMAL";
    }

    public Admin(String id, String username, String password, String email) {
        super(id, username, password, email);
        this.role       = "ADMIN";
        this.adminLevel = "NORMAL";
    }

    public String getAdminLevel()           { return adminLevel; }
    public void setAdminLevel(String level) { this.adminLevel = level; }
    public boolean canCancelAuction()       { return true; }
    public boolean canDeleteUser()          { return adminLevel.equals("SUPER"); }

    @Override
    public void printInfo() {
        System.out.println("=== ADMIN ===");
        System.out.println("ID          : " + id);
        System.out.println("Username    : " + username);
        System.out.println("Email       : " + email);
        System.out.println("Admin Level : " + adminLevel);
    }
}
