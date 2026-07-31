package com.mpzlog.gui;

import java.util.prefs.Preferences;

/**
 * Сохранение состояния GUI через Preferences (на Windows — реестр):
 * последняя папка открытого файла и положение/размер окна.
 */
public class GuiSettings {

    private static final String LAST_DIR = "lastDir";
    private static final String WIN_X = "winX";
    private static final String WIN_Y = "winY";
    private static final String WIN_W = "winW";
    private static final String WIN_H = "winH";

    private final Preferences prefs = Preferences.userNodeForPackage(GuiSettings.class);

    public String getLastDirectory() {
        return prefs.get(LAST_DIR, null);
    }

    public void setLastDirectory(String path) {
        prefs.put(LAST_DIR, path);
    }

    public double getWindowX() {
        return prefs.getDouble(WIN_X, Double.NaN);
    }

    public double getWindowY() {
        return prefs.getDouble(WIN_Y, Double.NaN);
    }

    public double getWindowWidth() {
        return prefs.getDouble(WIN_W, Double.NaN);
    }

    public double getWindowHeight() {
        return prefs.getDouble(WIN_H, Double.NaN);
    }

    public void setWindowBounds(double x, double y, double width, double height) {
        prefs.putDouble(WIN_X, x);
        prefs.putDouble(WIN_Y, y);
        prefs.putDouble(WIN_W, width);
        prefs.putDouble(WIN_H, height);
    }

    public void flush() {
        try {
            prefs.flush();
        } catch (java.util.prefs.BackingStoreException e) {
            // игнорируем: состояние сохранится при следующем изменении
        }
    }
}
