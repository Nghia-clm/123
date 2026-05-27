package com.auction.exception;

/*
Được ném ra khi có người đặt giá thầu trong một phiên đấu giá không ở trạng thái ĐANG CHẠY,
hoặc khi thời gian kết thúc của phiên đấu giá đã trôi qua.
*/
public class AuctionClosedException extends Exception {

    private final String auctionId;

    public AuctionClosedException(String message) {
        super(message);
        this.auctionId = null;
    }

    public AuctionClosedException(String auctionId, String message) {
        super(message);
        this.auctionId = auctionId;
    }

    public AuctionClosedException(String auctionId, String message, Throwable cause) {
        super(message, cause);
        this.auctionId = auctionId;
    }

    /**
     * @return the ID of the closed auction, or null if not specified.
     */
    public String getAuctionId() {
        return auctionId;
    }
}
