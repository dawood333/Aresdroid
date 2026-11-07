# Android app foundation

## Status: 0.1.0 foundation

The repository now contains an Android Studio application module:
- Kotlin dashboard using native Android views.
- Read-only device identity, Android SDK, kernel release, ABI and SELinux display.
- Ares / MT6893 candidate check. This is a heuristic, not hardware attestation.
- Storage Access Framework GSI document picker. It retains a read grant when supported; it does not copy, mount, unpack or validate the image.
- Small arm64 JNI library built with CMake. It reports successful native loading and runs no privileged commands.
- Session-switch control is deliberately disabled.

## Build environment

Open the repository in Android Studio with JDK 17, Android SDK Platform 36, Android NDK and CMake 3.22.1. The project uses Android Gradle Plugin 8.10.0, Kotlin 2.0.21 and Gradle 8.11.1. A Gradle wrapper is not included yet.

## Not implemented

- Root command broker or privileged daemon.
- GSI staging, copying, hashing or filesystem inspection.
- Namespace creation or pivot_root.
- Binder/service-manager isolation or ownership handling.
- Zygote or system_server control.
- Recovery controller and session switching.

## Safety

The app requests no broad storage permissions, does not change SELinux, and does not stop or signal Android host services. The disabled switch control must remain disabled until the Ares-specific recovery path and bring-up gates are demonstrated.
