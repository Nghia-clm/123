package com.auction.network;

import com.auction.manager.AuctionManager;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ServerMain - Khởi động Socket Server, lắng nghe kết nối từ client.
 * Mỗi client được xử lý trong một thread riêng (thread pool).
 */
public class ServerMain {

    private static final Logger LOGGER = Logger.getLogger(ServerMain.class.getName());
    private static final int PORT = 9999;
    private static final int THREAD_POOL_SIZE = 50;

    private final int port;
    private final ExecutorService threadPool;
    private volatile boolean running = false;
    private ServerSocket serverSocket;

    public ServerMain(int port) {
        this.port = port;
        this.threadPool = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
    }

    /**
     * Bắt đầu lắng nghe kết nối từ client.
     */
    public void start() {
        running = true;
        // Khởi tạo AuctionManager (Singleton)
        AuctionManager.getInstance();

        try {
            serverSocket = new ServerSocket(port);
            LOGGER.info("=== Auction Server started on port " + port + " ===");

            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    LOGGER.info("New client connected: " + clientSocket.getInetAddress());

                    ClientHandler handler = new ClientHandler(clientSocket);
                    threadPool.submit(handler);
                } catch (IOException e) {
                    if (running) {
                        LOGGER.log(Level.WARNING, "Error accepting client connection", e);
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Cannot start server on port " + port, e);
        } finally {
            stop();
        }
    }

    /**
     * Dừng server và giải phóng tài nguyên.
     */
    public void stop() {
        running = false;
        threadPool.shutdown();
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
                LOGGER.info("Server stopped.");
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Error closing server socket", e);
            }
        }
    }

    public static void main(String[] args) {
        ServerMain server = new ServerMain(PORT);

        // Graceful shutdown khi nhận Ctrl+C
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOGGER.info("Shutdown signal received. Stopping server...");
            server.stop();
        }));

        server.start();
    }
}

