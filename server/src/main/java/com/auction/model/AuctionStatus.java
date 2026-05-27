package com.auction.model;

public enum AuctionStatus {
    OPEN,      // mới tạo, chưa bắt đầu
    RUNNING,   // đang diễn ra
    FINISHED,  // đã hết giờ, chưa thanh toán/chưa chốt hủy
    PAID,      // người thắng đã thanh toán
    CANCELED   // bị hủy
}
