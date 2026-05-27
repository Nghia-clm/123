package com.auction.observer;

import java.io.PrintWriter;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Concrete {@link BidObserver} that forwards bid events to a connected client
 * over a Socket via a simple JSON message.
 *
 * Registered per-client by {@code ClientHandler} when the client opens a
 * bid-screen for an auction. Automatically unsubscribes itself when the
 * socket is closed.
 *
 * JSON format sent to client:
 * <pre>
 * {
 *   "event"      : "BID_PLACED",
 *   "auctionId"  : "uuid",
 *   "price"      : 1500.00,
 *   "leaderId"   : "bidder-uuid",
 *   "newEndTime" : "2026-05-01T20:01:00",   // only on AUCTION_EXTENDED
 *   "timestamp"  : "2026-05-01T19:59:50"
 * }
 * </pre>
 */
public class ClientNotifier implements BidObserver {

    private static final Logger LOGGER = Logger.getLogger(ClientNotifier.class.getName());

    private final Socket clientSocket;
    private final PrintWriter writer;
    private final AuctionEventPublisher publisher; // kept to self-unsubscribe

    public ClientNotifier(Socket clientSocket, AuctionEventPublisher publisher) throws java.io.IOException {
        this.clientSocket = clientSocket;
        this.writer = new PrintWriter(clientSocket.getOutputStream(), true);
        this.publisher = publisher;
    }

    @Override
    public void onBidEvent(BidEvent event) {
        if (clientSocket.isClosed()) {
            publisher.unsubscribe(this); // clean up silently
            return;
        }

        try {
            String json = buildJson(event);
            writer.println(json);

            if (writer.checkError()) {
                // PrintWriter swallows IOExceptions; checkError() detects them
                LOGGER.warning("Write error to client – unsubscribing.");
                publisher.unsubscribe(this);
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to send event to client.", e);
            publisher.unsubscribe(this);
        }
    }

    // ------------------------------------------------------------------ //
    //  JSON builder (no external library required)                         //
    // ------------------------------------------------------------------ //

    private String buildJson(BidEvent event) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        appendField(sb, "event", event.getType().name()); sb.append(",");
        appendField(sb, "auctionId", event.getAuctionId()); sb.append(",");
        sb.append("\"price\":").append(event.getCurrentPrice()).append(",");
        appendField(sb, "leaderId",
                event.getLeadingBidderId() != null ? event.getLeadingBidderId() : ""); sb.append(",");

        if (event.getNewEndTime() != null) {
            appendField(sb, "newEndTime", event.getNewEndTime().toString()); sb.append(",");
        }

        appendField(sb, "timestamp", event.getOccurredAt().toString());
        sb.append("}");
        return sb.toString();
    }

    private void appendField(StringBuilder sb, String key, String value) {
        sb.append("\"").append(key).append("\":\"").append(escape(value)).append("\"");
    }

    private String escape(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
