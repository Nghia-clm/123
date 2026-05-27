package com.auction;

import com.auction.network.ServerConnection;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * MainApp - Điểm khởi động JavaFX Client.
 */
public class MainApp extends Application {

    private static final Logger LOGGER = Logger.getLogger(MainApp.class.getName());

    @Override
    public void start(Stage primaryStage) {
        boolean connected = ServerConnection.getInstance().connect();
        if (!connected) {
            LOGGER.warning("Could not connect to server — continuing to login screen anyway.");
        }

        try {
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/com/auction/view/login.fxml"));
            Parent root = loader.load();

            primaryStage.setTitle("Auction System – Đăng nhập");
            // Tăng chiều cao để đủ chỗ hiển thị cả 2 tab
            primaryStage.setScene(new Scene(root, 520, 620));
            primaryStage.setMinWidth(480);
            primaryStage.setMinHeight(580);
            primaryStage.setResizable(true);

            primaryStage.setOnCloseRequest(e -> {
                ServerConnection.getInstance().disconnect();
                Platform.exit();
            });

            primaryStage.show();

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Cannot load login.fxml", e);
            Platform.exit();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
