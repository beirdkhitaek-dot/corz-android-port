# CorzClient — Zalith Launcher 2 compatibility edition

This is a **source-level Android compatibility pass** for the supplied CorzClient
project. Target game/runtime remains Minecraft **1.21.11**, Fabric, Java **21**.

## Changes in this edition

- Added `com.corz.client.platform.AndroidRuntime` to identify Android/ART and
  Android-hosted Java runtimes.
- Discord Rich Presence is not started on Android. Desktop Discord IPC depends
  on a local desktop IPC endpoint which Zalith normally does not expose.
- Windows-only file attribute and PowerShell cleanup commands are skipped on
  Android. These calls are not appropriate for Android app storage.
- Config import no longer opens the desktop AWT file picker on Android. It
  displays an in-client notice instead. Desktop file picking is invoked
  reflectively so Android does not directly link `java.awt.FileDialog` from
  that method.
- The existing Minecraft/Fabric target and Java 21 bytecode target are retained.

## Install in Zalith Launcher 2

1. Install Minecraft **1.21.11** with **Fabric Loader** in Zalith.
2. Use a Java 21 runtime profile supported by your Zalith installation.
3. Put the built `corz-client-2.0.1.jar` into that instance's `mods` folder.
4. Install the matching Fabric API for Minecraft 1.21.11.
5. Launch once. If it crashes, capture the complete `latest.log` and crash report.

## Important compatibility limits

This is not a guarantee that every feature works on every Android device.
The original source contains extensive desktop Java AWT usage (including font
atlas generation and icon rasterization), GLFW keyboard/mouse assumptions,
and native OpenGL rendering paths. Zalith's runtime and rendering backend must
provide the APIs those code paths require. This pass does **not** replace the
font renderer, rewrite every input path for touch, or convert desktop OpenGL
code to a different renderer.

Features that depend on desktop-only behavior may remain unavailable:
- Discord Rich Presence (intentionally disabled on Android).
- Desktop file-picker import (intentionally replaced by an in-client notice).
- Keyboard/mouse binding workflows may require an external keyboard or
  Zalith's key-mapping layer; touchscreen controls are not automatically mapped.
- Spotify cover-art processing and other AWT-dependent rendering paths require
  the launcher's Java runtime to provide the relevant `java.desktop` APIs.

## Build

The project uses Gradle Wrapper and Fabric Loom. From the project directory:

```bash
./gradlew clean build
```

On Windows:

```bat
gradlew.bat clean build
```

The resulting mod should be under `build/libs/`. Use the remapped Fabric JAR,
not the `-dev` JAR, in the Zalith `mods` folder.

## Build verification status

The source archive was inspected and patched. A Gradle build could not be run
in the current build environment because the Gradle distribution host
(`services.gradle.org`) was unreachable. Therefore this archive is **not
represented as a successfully compiled or device-tested JAR**.
