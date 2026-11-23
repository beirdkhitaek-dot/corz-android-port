# Zalith Launcher 2 / Android port status

This source tree is **not yet an Android-ready EchoClient build**. This patch adds Android ARM64 runtime detection and reserves the native resource directory name `native/android-arm64-v8a` in `NativeLibraries`.

## Remaining blockers found in the supplied source

- The project targets Minecraft 1.21.11, Fabric Loader 0.19.3, Fabric API 0.141.6+1.21.11, and Java 21.
- The source imports desktop `java.awt` APIs across settings, GUI widgets, panels, rendering, and SVG rasterization. Android's runtime does not provide the desktop AWT implementation. These call sites need to be ported to Minecraft/LWJGL-compatible color and image APIs before an Android runtime can load all affected classes.
- The project references LWJGL modules including GLFW, OpenAL, OpenGL, Vulkan, SDL, VMA, shaderc, jemalloc, FreeType, and tinyfd. Zalith's Android-provided native/runtime support must be checked per module; desktop assumptions cannot be treated as Android-compatible.
- The supplied archive contains no bundled native `.so` files under `src/main/resources/native/`. The new Android resource mapping therefore does not itself provide native binaries.
- The current source does not include an Android-specific input/touch adaptation or a verified Zalith runtime integration.

## What this patch changes

- Detects Android using `java.vm.name` / `java.runtime.name`, rather than relying on `os.name` (which commonly reports Linux on Android).
- Maps Android ARM64 to `android-arm64-v8a` and `.so` library naming.
- Leaves existing Windows, macOS, and desktop Linux mappings unchanged.

## Honest build status

No `EchoClient-Zalith-Android.jar` is produced by this patch. Packaging the desktop project as a JAR or changing Fabric metadata would not resolve the AWT and native-library blockers above. A genuine Zalith-compatible build requires porting those dependencies and then compiling and testing inside the target launcher/runtime.
