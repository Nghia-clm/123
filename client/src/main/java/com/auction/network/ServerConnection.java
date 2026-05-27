package com.auction.network;
 
import org.json.JSONObject;
 
import java.io.*;
import java.net.Socket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
 
/**
 * ServerConnection - Singleton quản lý kết nối Socket tới Server.
 *
 * FIX: Dùng một listener thread DUY NHẤT đọc tất cả message từ server.
 * - Nếu là broadcast (có "event") → gọi broadcastHandler
 * - Nếu là response (có "status") → đưa vào responseQueue
 * sendRequest() được serialize để mỗi thời điểm chỉ có một request chờ response.
 * Vì giao thức hiện chưa có requestId, không được để nhiều request pending cùng lúc.
 */
public class ServerConnection {
 
    private static final Logger LOGGER = Logger.getLogger(ServerConnection.class.getName());
 
    private static final String HOST = System.getProperty("server.host", "localhost");
    private static final int    PORT            = 9999;
    private static final int    TIMEOUT_SECONDS = 10; // timeout chờ response
 
    // ── Singleton ────────────────────────────────────────────────────────
    private static volatile ServerConnection instance;
 
    public static ServerConnection getInstance() {
        if (instance == null) {
            synchronized (ServerConnection.class) {
                if (instance == null) instance = new ServerConnection();
            }
        }
        return instance;
    }
 
    // ── Fields ───────────────────────────────────────────────────────────
    private Socket         socket;
    private BufferedReader reader;
    private PrintWriter    writer;
    private volatile boolean connected = false;
 
    /**
     * Queue nhận response từ server.
     * sendRequest() gửi rồi poll queue này — listener thread đẩy vào.
     */
    private final BlockingQueue<JSONObject> responseQueue = new LinkedBlockingQueue<>();

    /**
     * Giao thức hiện không có requestId/correlationId. Lock này đảm bảo request A
     * luôn nhận đúng response A bằng cách chỉ cho một request pending tại một thời điểm.
     */
    private final Object requestLock = new Object();
 
    /** Callback nhận broadcast (NEW_BID, AUCTION_FINISHED, AUCTION_EXTENDED...) */
    private Consumer<JSONObject> broadcastHandler;
 
    private ServerConnection() {}
 
    // ── Connect / Disconnect ─────────────────────────────────────────────
 
    public synchronized boolean connect() {
        try {
            socket    = new Socket(HOST, PORT);
            reader    = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            writer    = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);
            connected = true;
            responseQueue.clear();
            startListenerThread();
            LOGGER.info("Connected to server at " + HOST + ":" + PORT);
            return true;
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Cannot connect to server", e);
            connected = false;
            return false;
        }
    }
 
    public synchronized void disconnect() {
        connected = false;
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Error closing socket", e);
        }
    }
 
    public boolean isConnected() { return connected; }
 
    // ── Send Request ─────────────────────────────────────────────────────
 
    /**
     * Gửi request JSON đồng bộ và trả về response JSON.
     * KHÔNG đọc reader trực tiếp nữa — chỉ poll từ responseQueue.
     * Listener thread là nơi duy nhất đọc reader.
     */
    public JSONObject sendRequest(String action, JSONObject data) {
        synchronized (requestLock) {
            if (!connected) return errorJson("Chưa kết nối tới server");

            try {
                // Loại bỏ response cũ còn sót lại sau timeout/disconnect trước đó.
                responseQueue.clear();

                // 1. Build và gửi request
                JSONObject request = new JSONObject();
                request.put("action", action);
                request.put("data", data != null ? data : new JSONObject());

                writer.println(request.toString());

                // 2. Chờ response từ queue (listener thread sẽ đẩy vào)
                JSONObject response = responseQueue.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                if (response == null) {
                    LOGGER.warning("Timeout waiting for response to action: " + action);
                    return errorJson("Server không phản hồi. Vui lòng thử lại.");
                }
                return response;

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return errorJson("Request bị gián đoạn.");
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Request failed: " + action, e);
                connected = false;
                return errorJson("Lỗi mạng: " + e.getMessage());
            }
        }
    }
 
    // ── Listener Thread ──────────────────────────────────────────────────
 
    /**
     * Thread DUY NHẤT đọc tất cả message từ server.
     * Phân loại:
     *   - có "event"  → broadcast → gọi broadcastHandler
     *   - có "status" → response  → đẩy vào responseQueue
     */
    private void startListenerThread() {
        Thread listener = new Thread(() -> {
            try {
                String line;
                while (connected && (line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) continue;
 
                    try {
                        JSONObject json = new JSONObject(line);
 
                        if (json.has("event")) {
                            // Là broadcast từ server
                            handleBroadcast(json);
                        } else if (json.has("status")) {
                            // Là response cho request đang chờ
                            responseQueue.put(json);
                        } else {
                            LOGGER.warning("Unknown message from server: " + line);
                        }
 
                    } catch (Exception e) {
                        LOGGER.log(Level.WARNING, "Error parsing server message: " + line, e);
                    }
                }
            } catch (IOException e) {
                if (connected) LOGGER.log(Level.WARNING, "Server connection lost", e);
            } finally {
                connected = false;
                // Đẩy error vào queue để sendRequest() không bị block mãi
                try {
                    responseQueue.put(errorJson("Server connection closed"));
                } catch (InterruptedException ignored) {}
            }
        }, "server-listener");
 
        listener.setDaemon(true);
        listener.start();
    }
 
    private void handleBroadcast(JSONObject json) {
        if (broadcastHandler != null) {
            javafx.application.Platform.runLater(() -> broadcastHandler.accept(json));
        }
    }
 
    // ── Helpers ──────────────────────────────────────────────────────────
 
    public void setBroadcastHandler(Consumer<JSONObject> handler) {
        this.broadcastHandler = handler;
    }
 
    public static JSONObject errorJson(String message) {
        JSONObject j = new JSONObject();
        j.put("status", "ERROR");
        j.put("message", message);
        return j;
    }
 
    public static boolean isOk(JSONObject response) {
        return response != null && "OK".equals(response.optString("status"));
    }
}
