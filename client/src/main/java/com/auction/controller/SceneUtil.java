package com.auction.controller;

import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

final class SceneUtil {

    private SceneUtil() {}

    static void setScene(Stage stage, Parent root, String title,
                         double width, double height,
                         double minWidth, double minHeight) {
        boolean wasMaximized = stage.isMaximized();
        double currentWidth = stage.getScene() != null ? stage.getScene().getWidth() : stage.getWidth();
        double currentHeight = stage.getScene() != null ? stage.getScene().getHeight() : stage.getHeight();
        double targetWidth = Math.max(width, currentWidth);
        double targetHeight = Math.max(height, currentHeight);

        stage.setMinWidth(0);
        stage.setMinHeight(0);
        stage.setTitle(title);
        stage.setScene(new Scene(root, targetWidth, targetHeight));
        stage.setMinWidth(minWidth);
        stage.setMinHeight(minHeight);
        if (wasMaximized) {
            stage.setMaximized(true);
        } else {
            stage.setWidth(targetWidth);
            stage.setHeight(targetHeight);
        }
    }
}
