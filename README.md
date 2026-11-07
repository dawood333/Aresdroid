# AresDroid

An experimental, device-specific Android userspace switcher for Xiaomi Ares (MediaTek MT6893).

> **Status: app bootstrap / read-only bring-up.** The Android application is scaffolded. It does not switch Android sessions; guest handoff remains disabled.

## Android application

The initial Android app is a Kotlin + Jetpack Compose project:
- Device dashboard for model, codename, platform, Android SDK and kernel.
- Read-only SELinux, root, BinderFS and /data inventory.
- No special Android permissions are requested.
- No host framework services are stopped.
- No SELinux, watchdog, boot, vendor, dtbo or super state is modified.

Build a debug APK with Gradle 8.9 and JDK 17:

    gradle --no-daemon :app:assembleDebug

GitHub Actions builds and uploads the debug APK on pushes to the application branch.

## Target
- Device: Xiaomi Ares
- SoC: MediaTek MT6893 (mediatek,MT6893)
- Architecture: arm64
- Root: KernelSU environment (detected at runtime, never assumed)

## Runtime direction

AresDroid follows the high-level hot-userspace-switch model studied in OneOS. OneOS remains the architectural reference; AnyGSI is an additional implementation reference. The implementation is clean-room and does not copy upstream switcher scripts.

The first release is deliberately limited to device inspection. Image staging, namespace tests, Binder/service ownership, guest bootstrap and recovery are separate milestones. The switch action must not be enabled until recovery and isolation have been demonstrated on the exact Ares build.

See docs/ARCHITECTURE.md, docs/BRINGUP.md, docs/SAFETY.md and docs/PROVENANCE.md.
