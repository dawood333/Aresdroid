# AresDroid

An experimental, device-specific Android guest-runtime project for Xiaomi Ares
(MediaTek MT6893). The long-term goal is to run a second Android userspace
alongside the installed host OS and provide a recoverable switch-back path,
without flashing a second boot image.

> **Status: research / bring-up only.** Nothing in this repository currently
> switches Android sessions. Do not use it to stop `zygote`, `system_server`,
> `surfaceflinger`, alter SELinux mode, or change watchdog state.

## Target

- Device: Xiaomi Ares
- SoC: MediaTek MT6893 (`mediatek,MT6893`)
- Architecture: arm64
- Host: Android-based Xiaomi firmware
- Root: KernelSU environment (must be detected, not assumed)
- Kernel: device-specific; collect the running kernel release at probe time

## Design principles

1. **Read-only first.** Initial device probing must not change system state.
2. **Host remains authoritative.** The host init, vendor services, radio,
   thermal stack and kernel remain in control.
3. **No Samsung assumptions.** No One UI framework patches, Samsung watchdog
   paths, Samsung property handling, or Samsung-specific service names.
4. **Fail closed.** Unknown Binder, mount, SELinux, watchdog or vendor state
   means "unsupported for now", not "try anyway".
5. **Recoverability before guest boot.** A tested return path and external
   recovery procedure are prerequisites for any takeover experiment.
6. **No kernel modifications in phase 1.** First establish whether the existing
   Ares kernel exposes the required Linux primitives.

## Repository map

- `scripts/ares-probe.sh` — read-only device inventory; safe to run from Termux.
- `docs/ARCHITECTURE.md` — proposed runtime boundaries.
- `docs/BRINGUP.md` — staged test plan and go/no-go gates.
- `docs/DEVICE-DATA.md` — facts to collect from the actual phone.
- `docs/SAFETY.md` — prohibited operations and recovery requirements.

## First test

```sh
su -c 'sh ares-probe.sh'
```

Copy the generated report back for review. The probe intentionally does not
mount images, create loop devices, stop Android services, write watchdog nodes,
or change SELinux.

## Legal / provenance

This is a clean-room implementation plan. It does not copy OneOS or OneLineage
switcher code. Review upstream notices and licenses before reusing any code,
patches, assets, or binaries. See `docs/PROVENANCE.md`.

## Current milestone

**M0 — device capability inventory.** No guest image should be prepared until
the Ares report confirms the necessary primitives and the host/vendor layout
is understood.
