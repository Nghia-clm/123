package com.auction.model.item;

public class ArtItem extends Item {

    private String  artist;
    private int     yearCreated;
    private boolean authenticated;

    // ── Constructors ──────────────────────────────────────────────────────

    public ArtItem() {
        super();
        this.category = "ART";
    }

    /**
     * Constructor nhận id — dùng trong ItemFactory và DAO.
     */
    public ArtItem(String id, String name, String description,
                   double startingPrice, String sellerId) {
        super(id, name, description, startingPrice, sellerId);
        this.category = "ART";
    }

    /** Constructor đầy đủ với thông tin nghệ thuật. */
    public ArtItem(String name, String description,
                   double startingPrice, String sellerId,
                   String artist, int yearCreated, boolean authenticated) {
        super(name, description, startingPrice, sellerId);
        this.category      = "ART";
        this.artist        = artist;
        this.yearCreated   = yearCreated;
        this.authenticated = authenticated;
    }

    // ── Getters / Setters ─────────────────────────────────────────────────

    public String  getArtist()              { return artist; }
    public int     getYearCreated()         { return yearCreated; }
    public boolean isAuthenticated()        { return authenticated; }

    public void setArtist(String artist)    { this.artist = artist; }
    public void setYearCreated(int year)    { this.yearCreated = year; }
    public void setAuthenticated(boolean a) { this.authenticated = a; }

    // ── Abstract implementations ──────────────────────────────────────────

    @Override
    public boolean validate() {
        if (name == null || name.isBlank()) return false;
        if (startingPrice <= 0)             return false;
        return true;
    }

    @Override
    public void printInfo() {
        System.out.println("=== ART ITEM ===");
        System.out.println("ID             : " + id);
        System.out.println("Name           : " + name);
        System.out.println("Artist         : " + artist);
        System.out.println("Year Created   : " + yearCreated);
        System.out.println("Authenticated  : " + authenticated);
        System.out.println("Starting Price : " + startingPrice);
        System.out.println("Description    : " + description);
    }
}
