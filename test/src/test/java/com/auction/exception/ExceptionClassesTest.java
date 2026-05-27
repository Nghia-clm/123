package com.auction.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Domain exceptions")
class ExceptionClassesTest {

    @Test
    @DisplayName("AuctionClosedException stores message, cause and auction id")
    void auctionClosedExceptionStoresDetails() {
        RuntimeException cause = new RuntimeException("root");
        AuctionClosedException ex = new AuctionClosedException("auction-1", "closed", cause);

        assertInstanceOf(Exception.class, ex);
        assertEquals("closed", ex.getMessage());
        assertEquals("auction-1", ex.getAuctionId());
        assertSame(cause, ex.getCause());
    }

    @Test
    @DisplayName("InvalidBidException stores bid amounts")
    void invalidBidExceptionStoresAmounts() {
        InvalidBidException ex = new InvalidBidException(90, 100);

        assertInstanceOf(Exception.class, ex);
        assertTrue(ex.getMessage().contains("90.00"));
        assertEquals(90, ex.getAttemptedAmount(), 0.01);
        assertEquals(100, ex.getCurrentPrice(), 0.01);
    }

    @Test
    @DisplayName("UserNotFoundException stores identifier and factory message")
    void userNotFoundExceptionStoresIdentifier() {
        UserNotFoundException ex = UserNotFoundException.forIdentifier("alice");

        assertInstanceOf(Exception.class, ex);
        assertEquals("alice", ex.getIdentifier());
        assertTrue(ex.getMessage().contains("alice"));
    }
}
