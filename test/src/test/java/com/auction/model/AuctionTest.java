package com.auction.model;

import com.auction.model.item.Electronics;
import com.auction.model.user.Bidder;
import com.auction.model.user.Seller;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;

/**
 * AuctionTest - Kiểm thử model Auction.
 *
 * Auction hiện chỉ giữ dữ liệu và trạng thái vòng đời phiên đấu giá.
 * Luồng đặt giá, validate bid, transaction DB và observer nằm ở AuctionService.
 */
@DisplayName("Auction - Model và vòng đời phiên")
class AuctionTest {

    private Auction auction;
    private Bidder  bidder1;
    private Seller  seller;

    @BeforeEach
    void setUp() {
        seller  = new Seller("seller-1", "seller1", "pass", "seller@test.com");
        bidder1 = new Bidder("bidder-1", "alice", "pass", "alice@test.com");

        Electronics phone = new Electronics();
        phone.setName("iPhone 15");
        phone.setDescription("Brand new");
        phone.setStartingPrice(10_000_000);
        phone.setSellerId(seller.getId());

        auction = new Auction(
            "auction-test-1",
            phone,
            seller,
            10_000_000,
            LocalDateTime.now().minusMinutes(1),
            LocalDateTime.now().plusHours(1)
        );
    }

    @Test
    @DisplayName("Trạng thái ban đầu phải là OPEN")
    void initialStatusIsOpen() {
        assertEquals(AuctionStatus.OPEN, auction.getStatus());
    }

    @Test
    @DisplayName("start() chuyển OPEN sang RUNNING")
    void startChangesStatusToRunning() {
        auction.start();
        assertEquals(AuctionStatus.RUNNING, auction.getStatus());
    }

    @Test
    @DisplayName("start() từ RUNNING phải ném IllegalStateException")
    void startFromRunningThrows() {
        auction.start();
        assertThrows(IllegalStateException.class, auction::start);
    }

    @Test
    @DisplayName("finish() chuyển RUNNING sang FINISHED")
    void finishFromRunningIsFinished() {
        auction.start();
        auction.finish();
        assertEquals(AuctionStatus.FINISHED, auction.getStatus());
    }

    @Test
    @DisplayName("finish() không thay đổi phiên chưa RUNNING")
    void finishOpenAuctionDoesNothing() {
        auction.finish();
        assertEquals(AuctionStatus.OPEN, auction.getStatus());
    }

    @Test
    @DisplayName("cancel() chuyển phiên sang CANCELED")
    void cancelSetsCanceled() {
        auction.start();
        auction.cancel();
        assertEquals(AuctionStatus.CANCELED, auction.getStatus());
    }

    @Test
    @DisplayName("markPaid() chỉ chuyển FINISHED sang PAID")
    void markPaidAfterFinished() {
        auction.start();
        auction.finish();
        auction.markPaid();
        assertEquals(AuctionStatus.PAID, auction.getStatus());
    }

    @Test
    @DisplayName("markPaid() không đổi trạng thái nếu chưa FINISHED")
    void markPaidBeforeFinishedDoesNothing() {
        auction.start();
        auction.markPaid();
        assertEquals(AuctionStatus.RUNNING, auction.getStatus());
    }

    @Test
    @DisplayName("Setter winner cập nhật currentWinnerId")
    void setWinnerUpdatesCurrentWinnerId() {
        auction.setWinner(bidder1);
        assertEquals(bidder1, auction.getWinner());
        assertEquals(bidder1.getId(), auction.getCurrentWinnerId());
    }

    @Test
    @DisplayName("isExpired() = false khi chưa hết giờ")
    void isExpiredFalseWhenNotExpired() {
        assertFalse(auction.isExpired());
    }

    @Test
    @DisplayName("isExpired() = true khi đã quá giờ")
    void isExpiredTrueWhenPastEndTime() {
        Auction expired = new Auction(
            "expired-auction",
            auction.getItem(),
            seller,
            10_000_000,
            LocalDateTime.now().minusHours(2),
            LocalDateTime.now().minusSeconds(1)
        );
        assertTrue(expired.isExpired());
    }
}
