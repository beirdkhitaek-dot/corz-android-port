package dev.jadyen.hack.echo;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

/** Loads bundled native libraries for desktop and Android runtimes. */
public final class NativeLibraries {
    private NativeLibraries() {}

    public static boolean load(String libraryName) throws IOException {
        String os = System.getProperty("os.name", "");
        String arch = System.getProperty("os.arch", "");
        String resource = resourcePath(libraryName, os, arch, isAndroidRuntime());
        String prefix = (isWindows(os) ? "" : "lib") + libraryName;
        try (InputStream input = NativeLibraries.class.getResourceAsStream(resource)) {
            if (input == null) {
                return false;
            }
            Path extracted = Files.createTempFile(prefix, libraryExtension(os));
            extracted.toFile().deleteOnExit();
            Files.copy(input, extracted, StandardCopyOption.REPLACE_EXISTING);
            System.load(extracted.toAbsolutePath().toString());
            return true;
        }
    }

    public static String resourcePath(String libraryName, String osName, String architecture) {
        return resourcePath(libraryName, osName, architecture, false);
    }

    /** Exposed separately so platform mapping can be unit-tested without an Android VM. */
    public static String resourcePath(String libraryName, String osName, String architecture,
                                      boolean androidRuntime) {
        String platform = platformDirectory(osName, architecture, androidRuntime);
        return "/native/" + platform + "/"
                + (isWindows(osName) ? "" : "lib") + libraryName + libraryExtension(osName);
    }

    public static String platformDirectory(String osName, String architecture) {
        return platformDirectory(osName, architecture, false);
    }

    public static String platformDirectory(String osName, String architecture, boolean androidRuntime) {
        String os = osName.toLowerCase(Locale.ROOT);
        String arch = architecture.toLowerCase(Locale.ROOT);
        String normalizedArch = switch (arch) {
            case "aarch64", "arm64" -> "aarch64";
            case "amd64", "x86_64" -> "x86_64";
            default -> throw new IllegalStateException("Unsupported arch: " + arch);
        };

        // Android reports "Linux" for os.name on common runtimes. Check the VM
        // identity explicitly so Android does not get mistaken for desktop Linux.
        if (androidRuntime) {
            if (!normalizedArch.equals("aarch64")) {
                throw new IllegalStateException("Android native libraries are supported only on arm64: " + arch);
            }
            return "android-arm64-v8a";
        }

        String platform;
        if (os.contains("mac") || os.contains("darwin")) {
            platform = "macos";
        } else if (os.contains("linux")) {
            platform = "linux";
        } else if (os.contains("windows")) {
            platform = "windows";
        } else {
            throw new IllegalStateException("Unsupported os: " + os);
        }
        return platform + "-" + normalizedArch;
    }

    public static boolean isAndroidRuntime() {
        String vm = System.getProperty("java.vm.name", "").toLowerCase(Locale.ROOT);
        String runtime = System.getProperty("java.runtime.name", "").toLowerCase(Locale.ROOT);
        return vm.contains("dalvik") || vm.contains("art") || runtime.contains("android");
    }

    public static boolean isWindows(String osName) {
        return osName.toLowerCase(Locale.ROOT).contains("windows");
    }

    public static String libraryExtension(String osName) {
        String os = osName.toLowerCase(Locale.ROOT);
        if (os.contains("mac") || os.contains("darwin")) {
            return ".dylib";
        }
        return isWindows(os) ? ".dll" : ".so";
    }
}
