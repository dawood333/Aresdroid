# Android app foundation

## Status: 0.1.0 foundation

The repository now contains an Android Studio application module:
- Kotlin dashboard using native Android views.
- Read-only device identity, Android SDK, kernel release, ABI and SELinux display.
- Ares / MT6893 candidate check. This is a heuristic, not hardware attestation.
- Storage Access Framework GSI picker with private app-data staging, streaming SHA-256, and offline format checks for Android sparse images (including chunk-table consistency), raw ext4 and raw EROFS. It does not mount, unpack or boot the image.
- Small arm64 JNI library built with CMake. It reports successful native loading and runs no privileged commands.
- Root-backed read-only Ares audit: identity/build properties, SELinux mode, self/init namespaces, Binder device visibility, init mount topology, `/data` free space and a non-mutating mount-namespace creation probe. The audit requests KernelSU approval through `su`; it does not change host state.
- Session-switch control is deliberately disabled.

## Build environment

Open the repository in Android Studio with JDK 17, Android SDK Platform 36, Android NDK and CMake 3.22.1. The project uses Android Gradle Plugin 8.10.0, Kotlin 2.0.21 and Gradle 8.11.1. A Gradle wrapper is not included yet.

## Not implemented

- Persistent root command broker or privileged daemon (the audit uses one fixed, bounded `su -c` request only).
- Filesystem mounting/unpacking and full Android GSI compatibility checks against Ares vendor/VINTF.
- A disposable `pivot_root` test. The current namespace probe only attempts creation of a separate mount namespace and reports the result; it does not mount or pivot anything.
- Binder/service-manager isolation or ownership handling.
- Zygote or system_server control.
- Recovery controller and session switching.

## Safety

The app requests no broad storage permissions, does not change SELinux, and does not stop or signal Android host services. The disabled switch control must remain disabled until the Ares-specific recovery path and bring-up gates are demonstrated.

## M0/M1 audit behavior

The dashboard now has a **Run privileged Ares audit** action. It collects a bounded text report after the user approves the KernelSU prompt. Commands are fixed in the app; no arbitrary shell input is accepted. The only namespace operation is a child mount-namespace creation attempt (`unshare -m`) with no mount changes. A failed or unavailable probe is reported, not bypassed. The report is intended to decide the next Ares-specific M1 experiment; it is not evidence that pivot_root or Android guest startup works.


## GSI intake implementation

Selecting an image now copies it into the app-private `files/gsi-staging` directory, computes SHA-256 while streaming, and checks the image structure without mounting it. Android sparse images are checked for header and chunk-table consistency; raw ext4 and raw EROFS superblock magic are recognized. The displayed result includes the staged path, byte size, expanded size where applicable, and digest. This is format validation only: it does not establish that the image is a compatible or bootable Android GSI. Unsupported formats are rejected and temporary files are removed.
