package com.mpzlog.gui;

import javafx.application.Platform;
import javafx.geometry.Rectangle2D;
import javafx.stage.Screen;
import javafx.stage.Stage;

public final class WindowStateManager {

    private WindowStateManager() {
    }

    public static void restoreWindowBounds(Stage stage, GuiSettings settings) {
        Rectangle2D vb = Screen.getPrimary().getVisualBounds();
        double x = settings.getWindowX();
        double y = settings.getWindowY();
        if (!Double.isNaN(x) && !Double.isNaN(y)
                && x >= vb.getMinX() - 60 && y >= vb.getMinY() - 40
                && x < vb.getMaxX() - 60 && y < vb.getMaxY() - 40) {
            stage.setX(x);
            stage.setY(y);
        }
        double w = settings.getWindowWidth();
        double h = settings.getWindowHeight();
        if (!Double.isNaN(w) && !Double.isNaN(h) && w >= 300 && h >= 200) {
            stage.setWidth(w);
            stage.setHeight(h);
        }
        stage.setMaximized(settings.isWindowMaximized());
    }

    public static void bindWindowBounds(Stage stage, GuiSettings settings) {
        stage.xProperty().addListener((obs, oldV, newV) -> saveBounds(stage, settings));
        stage.yProperty().addListener((obs, oldV, newV) -> saveBounds(stage, settings));
        stage.widthProperty().addListener((obs, oldV, newV) -> saveBounds(stage, settings));
        stage.heightProperty().addListener((obs, oldV, newV) -> saveBounds(stage, settings));
        stage.maximizedProperty().addListener((obs, oldV, newV) -> saveBounds(stage, settings));
    }

    private static void saveBounds(Stage stage, GuiSettings settings) {
        settings.setWindowMaximized(stage.isMaximized());
        if (!stage.isMaximized()) {
            settings.setWindowBounds(stage.getX(), stage.getY(), stage.getWidth(), stage.getHeight());
        }
    }

}
