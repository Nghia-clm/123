package com.auction.exception;

/*
 Ngoại lệ này được ném ra khi số tiền đặt giá không hợp lệ.
 Các lý do thường gặp:
 - Giá đặt không cao hơn giá hiện tại.
 - Số tiền đặt giá bằng không hoặc âm.
 - Giá đặt giá tự động tối đa thấp hơn giá hiện tại.
*/
public class InvalidBidException extends Exception {

    private final double attemptedAmount;
    private final double currentPrice;

    public InvalidBidException(String message) {
        super(message);
        this.attemptedAmount = -1;
        this.currentPrice = -1;
    }

    public InvalidBidException(double attemptedAmount, double currentPrice) {
        super(String.format(
                "Invalid bid: attempted %.2f but current price is %.2f.",
                attemptedAmount, currentPrice));
        this.attemptedAmount = attemptedAmount;
        this.currentPrice = currentPrice;
    }

    public InvalidBidException(String message, double attemptedAmount, double currentPrice) {
        super(message);
        this.attemptedAmount = attemptedAmount;
        this.currentPrice = currentPrice;
    }

    public InvalidBidException(String message, Throwable cause) {
        super(message, cause);
        this.attemptedAmount = -1;
        this.currentPrice = -1;
    }

    /**
     * @return the bid amount that was rejected, or -1 if not specified.
     */
    public double getAttemptedAmount() {
        return attemptedAmount;
    }

    /**
     * @return the current leading price at time of rejection, or -1 if not specified.
     */
    public double getCurrentPrice() {
        return currentPrice;
    }
}
