# Android app foundation

## Status: 0.1.0 foundation

The repository now contains an Android Studio application module:
- Kotlin dashboard using native Android views.
- Read-only device identity, Android SDK, kernel release, ABI and SELinux display.
- Ares / MT6893 candidate check. This is a heuristic, not hardware attestation.
- Storage Access Framework GSI picker with private app-data staging, streaming SHA-256, Android sparse expansion, and filesystem detection for raw ext4/EROFS. It attempts a read-only metadata mount only inside a separate mount namespace; this is not a guest boot.
- Small arm64 JNI library built with CMake. It reports successful native loading and runs no privileged commands.
- Root-backed read-only Ares audit: identity/build properties, SELinux mode, self/init namespaces, Binder device visibility, init mount topology, `/data` free space and a non-mutating mount-namespace creation probe. The audit requests KernelSU approval through `su`; it does not change host state.
- Session-switch control is deliberately disabled.

## Build environment

Open the repository in Android Studio with JDK 17, Android SDK Platform 36, Android NDK and CMake 3.22.1. The project uses Android Gradle Plugin 8.10.0, Kotlin 2.0.21 and Gradle 8.11.1. A Gradle wrapper is not included yet.

## Not implemented

- Persistent root command broker or privileged daemon (the audit uses one fixed, bounded `su -c` request only).
- A complete guest image set and full Android GSI compatibility checks against Ares vendor/VINTF; the current read-only mount is only an inspection step.
- A disposable `pivot_root` test. The current audit proves mount-namespace creation only; the image inspector mounts a filesystem read-only in that isolated namespace but does not pivot the process root.
- Binder/service-manager isolation or ownership handling.
- Zygote or system_server control.
- Recovery controller and session switching.

## Safety

The app requests no broad storage permissions, does not change SELinux, and does not stop or signal Android host services. The disabled switch control must remain disabled until the Ares-specific recovery path and bring-up gates are demonstrated.

## M0/M1 audit behavior

The dashboard now has a **Run privileged Ares audit** action. It collects a bounded text report after the user approves the KernelSU prompt. Commands are fixed in the app; no arbitrary shell input is accepted. The only namespace operation is a child mount-namespace creation attempt (`unshare -m`) with no mount changes. A failed or unavailable probe is reported, not bypassed. The report is intended to decide the next Ares-specific M1 experiment; it is not evidence that pivot_root or Android guest startup works.


## GSI intake implementation

Selecting an image copies it into app-private `files/gsi-staging` and computes SHA-256 while streaming. Android sparse headers/chunk tables are validated, then the image is expanded with a free-space guard. The expanded payload is independently identified as ext4 or EROFS instead of assuming sparse means ext4. Metadata inspection drains root output concurrently to avoid a pipe-buffer deadlock, mounts read-only inside `unshare -m`, reads selected build properties and VINTF files, and unmounts on exit. The source `.img` is preserved. This is still only filesystem/metadata inspection; it does not establish compatibility or bootability.


## OneOS architecture findings and Ares port boundary

OneOS is not a generic launcher for an arbitrary `system.img`. Its documented model runs a prepared guest Android image alongside the host kernel, with its own zygote and system_server, and a root-controlled switch/return path. Its app launch path performs fresh device/stage/installd checks, evaluates explicit gates and acknowledgements, records a session, then delegates to a root launcher that re-checks before handoff. The return path and host-protection work are integral, not optional UI features.

AresDroid currently has none of that launch/return machinery. The Ares MT6893 audit establishes that a mount namespace can be created, but does not establish safe Binder ownership, service-manager isolation, zygote socket protection, guest classpaths/APEX compatibility, or a recovery path. OneOS's published device support does not establish Ares compatibility. Therefore:
- Do not enable a session-switch button based on a successful image mount.
- Do not stop host zygote, system_server, SurfaceFlinger, vold, installd or init during bring-up.
- Keep SELinux Enforcing and all initial experiments confined to disposable namespaces.
- Build an Ares-specific compatibility/profile and recovery controller before any host handoff.
- OneOS's `switcher/assets/` scripts are identified in its NOTICE as derived from OneLineage with reuse restrictions; AresDroid must implement its own independently written Ares runtime rather than copying those scripts.
