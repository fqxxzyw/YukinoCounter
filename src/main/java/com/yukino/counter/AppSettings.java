package com.yukino.counter;

import java.awt.Color;
import java.nio.file.Path;
import java.util.prefs.Preferences;

public final class AppSettings {
    private static final Preferences PREFS = Preferences.userNodeForPackage(AppSettings.class);

    private AppSettings() {}

    public static Color heatColor() {
        return new Color(PREFS.getInt("heatColor", new Color(255, 91, 126).getRGB()), true);
    }

    public static void heatColor(Color value) {
        PREFS.putInt("heatColor", value.getRGB());
    }

    public static Path backgroundImage() {
        String value = PREFS.get("backgroundImage", "");
        return value.isBlank() ? null : Path.of(value);
    }

    public static void backgroundImage(Path value) {
        if (value == null) PREFS.remove("backgroundImage");
        else PREFS.put("backgroundImage", value.toAbsolutePath().toString());
    }

    public static int backgroundOpacity() { return PREFS.getInt("backgroundOpacity", 35); }
    public static void backgroundOpacity(int value) { PREFS.putInt("backgroundOpacity", value); }
    public static int backgroundZoom() { return PREFS.getInt("backgroundZoom", 100); }
    public static void backgroundZoom(int value) { PREFS.putInt("backgroundZoom", value); }
    public static int backgroundOffsetX() { return PREFS.getInt("backgroundOffsetX", 0); }
    public static void backgroundOffsetX(int value) { PREFS.putInt("backgroundOffsetX", value); }
    public static int backgroundOffsetY() { return PREFS.getInt("backgroundOffsetY", 0); }
    public static void backgroundOffsetY(int value) { PREFS.putInt("backgroundOffsetY", value); }
}
