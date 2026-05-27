package com.auction.dao;
 
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;
 
public class DatabaseConnection {
 
    private static final Logger LOGGER = Logger.getLogger(DatabaseConnection.class.getName());
 
    private static final String URL      = "jdbc:mysql://localhost:3306/auction_db"
                                         + "?useSSL=false&serverTimezone=UTC"
                                         + "&allowPublicKeyRetrieval=true";
    private static final String USER     = "root";
    private static final String PASSWORD = "n05122007"; // đổi theo mật khẩu MySQL trên máy chạy
 
    // Singleton
    private static volatile DatabaseConnection instance;
    private final HikariDataSource dataSource;
 
    private DatabaseConnection() {
        dataSource = createDataSource();
    }
 
    public static DatabaseConnection getInstance() {
        if (instance == null) {
            synchronized (DatabaseConnection.class) {
                if (instance == null) {
                    instance = new DatabaseConnection();
                }
            }
        }
        return instance;
    }
 
    private HikariDataSource createDataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(URL);
        config.setUsername(USER);
        config.setPassword(PASSWORD);
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        config.setMaximumPoolSize(20);
        config.setMinimumIdle(5);
        config.setConnectionTimeout(30_000);
        config.setIdleTimeout(600_000);
        config.setMaxLifetime(1_800_000);
        config.setPoolName("AuctionSystemPool");

        LOGGER.info("Database connection pool initialized.");
        return new HikariDataSource(config);
    }

    public static Connection getConnection() throws SQLException {
        return getInstance().dataSource.getConnection();
    }

    public HikariDataSource getDataSource() {
        return dataSource;
    }

    public void closeConnection() {
        try {
            if (dataSource != null && !dataSource.isClosed()) {
                dataSource.close();
                LOGGER.info("Database connection pool closed.");
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error closing connection pool", e);
        }
    }
}
