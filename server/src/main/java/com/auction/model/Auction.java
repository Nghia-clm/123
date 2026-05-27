package com.auction.model;

import com.auction.model.item.Item;
import com.auction.model.user.User;

import java.time.LocalDateTime;

public class Auction {
    private String id;
    private Item item;
    private User seller;
    private User winner;
    private double startingPrice;
    private double currentPrice;
    private String currentWinnerId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private AuctionStatus status;

    public Auction() {}

    // Constructor đầy đủ dùng trong AuctionService
    public Auction(String id, Item item, User seller, double startingPrice,
                   LocalDateTime startTime, LocalDateTime endTime) {
        this.id            = id;
        this.item          = item;
        this.seller        = seller;
        this.startingPrice = startingPrice;
        this.currentPrice  = startingPrice;
        this.startTime     = startTime;
        this.endTime       = endTime;
        this.status        = AuctionStatus.OPEN;
    }

    // Constructor không cần seller (dùng khi tạo đơn giản)
    public Auction(String id, Item item, double startingPrice,
                   LocalDateTime startTime, LocalDateTime endTime) {
        this(id, item, null, startingPrice, startTime, endTime);
    }

    // ── State transitions ─────────────────────────────────────
    public synchronized void start() {
        if (status != AuctionStatus.OPEN) {
            throw new IllegalStateException("Chỉ bắt đầu được khi trạng thái là OPEN");
        }
        this.status = AuctionStatus.RUNNING;
    }

    public synchronized void finish() {
        if (status != AuctionStatus.RUNNING) return;
        this.status = AuctionStatus.FINISHED;
    }

    public synchronized void cancel() {
        this.status = AuctionStatus.CANCELED;
    }

    public synchronized void markPaid() {
        if (status == AuctionStatus.FINISHED) {
            this.status = AuctionStatus.PAID;
        }
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(endTime);
    }

    // ── Getters ───────────────────────────────────────────────
    public String getId()              { return id; }
    public Item getItem()              { return item; }
    public User getSeller()            { return seller; }
    public User getWinner()            { return winner; }
    public double getStartingPrice()   { return startingPrice; }
    public double getCurrentPrice()    { return currentPrice; }
    public String getCurrentWinnerId() { return currentWinnerId; }
    public LocalDateTime getStartTime(){ return startTime; }
    public LocalDateTime getEndTime()  { return endTime; }
    public AuctionStatus getStatus()   { return status; }

    // ── Setters ───────────────────────────────────────────────
    public void setId(String id)                 { this.id = id; }
    public void setItem(Item item)               { this.item = item; }
    public void setSeller(User seller)           { this.seller = seller; }
    public void setWinner(User winner)           { this.winner = winner; this.currentWinnerId = winner != null ? winner.getId() : null; }
    public void setStartingPrice(double p)       { this.startingPrice = p; }
    public void setCurrentPrice(double p)        { this.currentPrice = p; }
    public void setCurrentWinnerId(String id)    { this.currentWinnerId = id; }
    public void setStartTime(LocalDateTime t)    { this.startTime = t; }
    public void setEndTime(LocalDateTime t)      { this.endTime = t; }
    public void setStatus(AuctionStatus s)       { this.status = s; }

    @Override
    public String toString() {
        return "Auction{id='" + id
               + "', item='" + (item != null ? item.getName() : "null")
               + "', currentPrice=" + currentPrice
               + ", status=" + status + "}";
    }
}
