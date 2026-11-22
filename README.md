# corz-android-port

CorzClient (Fabric, Minecraft 1.21.11, Java 21) with a source-level Android
compatibility pass for Zalith Launcher 2. See `ANDROID_ZALITH.md` for details
and known limits.

Build: GitHub Actions (`.github/workflows/build.yml`) or `./gradlew clean build`
locally. The jar to use is the remapped one in `build/libs/` (not `-dev`).

The original `CorzClientV2` jar is not stored in the repo (it is on the `Claude` release).
