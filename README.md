# AresDroid

An experimental Android guest-runtime project for Xiaomi Ares (MediaTek MT6893). The long-term goal is a recoverable hot userspace switch between the installed Android host and a staged Android GSI session.

> **Status: Android app foundation 0.1.0 + runtime research.** The dashboard and diagnostic JNI library are present. No guest Android session can be started yet; session switching is intentionally disabled.

## Target

- Device: Xiaomi Ares (ares)
- SoC: MediaTek MT6893 (mediatek,MT6893)
- Architecture: arm64
- Host baseline: Android 16 / SDK 36, vendor API 30 (recheck on-device)
- Root: KernelSU environment (must be detected, not assumed)
- Kernel: device-specific 4.14.x baseline (read at runtime)

## Android application

The Android Studio project is in the repository root:

- app/ — Kotlin dashboard and arm64 JNI library.
- docs/APP-STATUS.md — application scope and build instructions.
- scripts/ares-probe.sh — read-only device inventory; safe to run from Termux.
- docs/ARCHITECTURE.md — proposed OneOS-style runtime boundaries.
- docs/BRINGUP.md — staged test plan and go/no-go gates.

The first app version displays device and security information, allows the user to select a GSI document through Android's document picker, and reports whether the native diagnostic library loaded. Selecting a file does not import or validate it. Session switching remains disabled.

## Build

Open the repository in Android Studio with JDK 17, Android SDK Platform 36, Android NDK and CMake 3.22.1. The project uses Android Gradle Plugin 8.10.0 and Kotlin 2.0.21. Gradle 8.11.1 is required. A Gradle wrapper is not committed yet.

## Design principles

1. **Read-only first.** Initial device probing must not change system state.
2. **Host remains authoritative.** The host init, vendor services, radio, thermal stack and kernel remain in control.
3. **No Samsung assumptions.** No One UI framework patches, Samsung watchdog paths, Samsung property handling, or Samsung-specific service names.
4. **Fail closed.** Unknown Binder, mount, SELinux, watchdog or vendor state means "unsupported for now", not "try anyway".
5. **Recoverability before guest boot.** A tested return path and external recovery procedure are prerequisites for any takeover experiment.
6. **No kernel modifications in phase 1.** First establish whether the existing Ares kernel exposes the required Linux primitives.

## Legal / provenance

This is a clean-room implementation. It does not copy OneOS, OneLineage or AnyGSI code. Review upstream notices and licenses before reusing any code, patches, assets or binaries. See docs/PROVENANCE.md and docs/ONEOS-PORT-AUDIT.md.

## Runtime roadmap

- M0: device inventory and compatibility.
- M1: isolated namespace and pivot_root lab.
- M2: guest image validation.
- M3: Binder and service ownership design.
- M4: independent recovery controller.
- M5: supervised Android framework bootstrap.

No host framework services are stopped during the current bring-up phase.
