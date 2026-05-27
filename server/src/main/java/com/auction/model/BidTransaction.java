package com.auction.model;

import java.time.LocalDateTime;
import java.util.UUID;

public class BidTransaction {
    private String id;
    private String auctionId;
    private String bidderId;
    private double amount;
    private LocalDateTime bidTime;
    private boolean autoBid;

    public BidTransaction() {
        this.id      = UUID.randomUUID().toString();
        this.bidTime = LocalDateTime.now();
    }

    public BidTransaction(String auctionId, String bidderId, double amount) {
        this.id        = UUID.randomUUID().toString();
        this.auctionId = auctionId;
        this.bidderId  = bidderId;
        this.amount    = amount;
        this.bidTime   = LocalDateTime.now();
        this.autoBid   = false;
    }

    // Constructor đầy đủ dùng trong AuctionService và BidTransactionDAO
    public BidTransaction(String id, String auctionId, String bidderId,
                          double amount, LocalDateTime bidTime) {
        this.id        = id;
        this.auctionId = auctionId;
        this.bidderId  = bidderId;
        this.amount    = amount;
        this.bidTime   = bidTime;
        this.autoBid   = false;
    }

    public String getId()             { return id; }
    public String getAuctionId()      { return auctionId; }
    public String getBidderId()       { return bidderId; }
    public double getAmount()         { return amount; }
    public double getBidAmount()      { return amount; }          // alias dùng trong DAO/Handler
    public LocalDateTime getBidTime() { return bidTime; }
    public LocalDateTime getTimestamp() { return bidTime; }       // alias dùng trong Handler
    public boolean isAutoBid()        { return autoBid; }

    public void setId(String id)                 { this.id = id; }
    public void setAuctionId(String auctionId)   { this.auctionId = auctionId; }
    public void setBidderId(String bidderId)      { this.bidderId = bidderId; }
    public void setAmount(double amount)          { this.amount = amount; }
    public void setBidTime(LocalDateTime bidTime) { this.bidTime = bidTime; }
    public void setAutoBid(boolean autoBid)       { this.autoBid = autoBid; }

    @Override
    public String toString() {
        return "BidTransaction{id='" + id
               + "', auctionId='" + auctionId
               + "', bidderId='" + bidderId
               + "', amount=" + amount
               + ", bidTime=" + bidTime + "}";
    }
}
