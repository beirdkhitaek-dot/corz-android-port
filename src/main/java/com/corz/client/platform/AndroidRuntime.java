package com.corz.client.platform;

/**
 * Runtime checks and conservative defaults for Android Java launchers such as
 * Zalith Launcher 2. This does not make desktop-only Java APIs available;
 * it only avoids optional desktop integrations when running on Android.
 */
public final class AndroidRuntime {
    private static final boolean ANDROID = detectAndroid();

    private AndroidRuntime() {
    }

    private static boolean detectAndroid() {
        String runtime = System.getProperty("java.runtime.name", "").toLowerCase();
        String vm = System.getProperty("java.vm.name", "").toLowerCase();
        String vendor = System.getProperty("java.vendor", "").toLowerCase();

        if (runtime.contains("android") || vm.contains("dalvik") || vm.contains("art")) {
            return true;
        }

        // Zalith/Pojav-style launchers can expose an OpenJDK VM on Android.
        try {
            Class.forName("android.os.Build", false, AndroidRuntime.class.getClassLoader());
            return true;
        } catch (Throwable ignored) {
            // Not running on Android.
        }

        return vendor.contains("android");
    }

    public static boolean isAndroid() {
        return ANDROID;
    }

    /**
     * Android launchers do not normally provide a desktop Discord IPC socket.
     */
    public static boolean shouldUseDiscordRpc() {
        return !ANDROID;
    }

    /**
     * Keep expensive optional features conservative on low-memory phones.
     */
    public static boolean isLowMemoryRuntime() {
        return ANDROID && Runtime.getRuntime().maxMemory() <= 2_500_000_000L;
    }
}
