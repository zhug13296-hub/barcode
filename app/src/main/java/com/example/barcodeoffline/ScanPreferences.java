package com.example.barcodeoffline;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;

public class ScanPreferences {

    private static final String PREF_NAME = "barcode_scanner_prefs";

    private static final String KEY_VIBRATION_ENABLED = "vibration_enabled";
    private static final String KEY_SOUND_ENABLED = "sound_enabled";
    private static final String KEY_AUTO_SCAN_ENABLED = "auto_scan_enabled";
    private static final String KEY_CUSTOM_PREFIX = "custom_prefix";
    private static final String KEY_CUSTOM_SUFFIX = "custom_suffix";
    private static final String KEY_QR_FG_COLOR = "qr_fg_color";
    private static final String KEY_QR_BG_COLOR = "qr_bg_color";

    private static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    // Vibration
    public static boolean getVibrationEnabled(Context context) {
        return getPrefs(context).getBoolean(KEY_VIBRATION_ENABLED, true);
    }

    public static void setVibrationEnabled(Context context, boolean enabled) {
        getPrefs(context).edit().putBoolean(KEY_VIBRATION_ENABLED, enabled).apply();
    }

    // Sound
    public static boolean getSoundEnabled(Context context) {
        return getPrefs(context).getBoolean(KEY_SOUND_ENABLED, true);
    }

    public static void setSoundEnabled(Context context, boolean enabled) {
        getPrefs(context).edit().putBoolean(KEY_SOUND_ENABLED, enabled).apply();
    }

    // Auto scan
    public static boolean getAutoScanEnabled(Context context) {
        return getPrefs(context).getBoolean(KEY_AUTO_SCAN_ENABLED, false);
    }

    public static void setAutoScanEnabled(Context context, boolean enabled) {
        getPrefs(context).edit().putBoolean(KEY_AUTO_SCAN_ENABLED, enabled).apply();
    }

    // Custom prefix
    public static String getCustomPrefix(Context context) {
        return getPrefs(context).getString(KEY_CUSTOM_PREFIX, "");
    }

    public static void setCustomPrefix(Context context, String prefix) {
        getPrefs(context).edit().putString(KEY_CUSTOM_PREFIX, prefix).apply();
    }

    // Custom suffix
    public static String getCustomSuffix(Context context) {
        return getPrefs(context).getString(KEY_CUSTOM_SUFFIX, "");
    }

    public static void setCustomSuffix(Context context, String suffix) {
        getPrefs(context).edit().putString(KEY_CUSTOM_SUFFIX, suffix).apply();
    }

    // QR foreground color
    public static int getQrForegroundColor(Context context) {
        return getPrefs(context).getInt(KEY_QR_FG_COLOR, Color.BLACK);
    }

    public static void setQrForegroundColor(Context context, int color) {
        getPrefs(context).edit().putInt(KEY_QR_FG_COLOR, color).apply();
    }

    // QR background color
    public static int getQrBackgroundColor(Context context) {
        return getPrefs(context).getInt(KEY_QR_BG_COLOR, Color.WHITE);
    }

    public static void setQrBackgroundColor(Context context, int color) {
        getPrefs(context).edit().putInt(KEY_QR_BG_COLOR, color).apply();
    }
}
