# OneOS source audit for the AresDroid port

**Audited upstream:** https://github.com/fahadalijaved/OneOS (default branch; inspected 2026-11-07).  
**Target:** Xiaomi Ares, MediaTek MT6893.  
**Purpose:** record the actual upstream runtime model and identify what can and cannot be carried into AresDroid. This is an engineering audit, not a claim that OneOS already supports Ares.

## Executive finding

OneOS is a **hot userspace takeover/switcher**, not a conventional LXC-style Android guest with a fully independent Android control plane.

It stops the host Android framework processes (notably host zygote and system_server), keeps the host kernel and selected vendor/radio services running, pivots into a staged Android root filesystem, then manually starts the guest framework. The guest and host share important kernel interfaces and Android service infrastructure. In particular, OneOS documents a **shared servicemanager** and shared /dev/Binder access; it does not establish a private Binder context manager for the guest.

That is the key architectural decision AresDroid must make explicitly: reproduce the proven hot-switch model, or design a genuinely isolated Android guest. These are different projects. AresDroid must not describe OneOS's shared-service arrangement as an independent Binder/Android VM.

## What the source actually does

### 1. Preflight and host handoff

switcher/assets/oneos_switch.sh performs extensive preflight before changing the phone. It checks root, root-manager BusyBox and resetprop, required applets, the init mount namespace, staged image and overlay integrity, prior-session mounts/bridges, and other host conditions.

After preflight it snapshots/restores host properties, records a baseline for return, and performs the framework handoff. The host kernel, vendor services and radio are intentionally retained while the host zygote and system_server are stopped. OneOS also contains device-specific display, audio, watchdog, networking and service handling.

**AresDroid implication:** the host handoff must remain disabled until Ares-specific probes and a tested external recovery path exist. Do not port the Samsung stop/restart sequence verbatim.

### 2. Mount namespace and guest filesystem

switcher/assets/guest_prepare.sh explicitly requires execution in PID 1's/init's mount namespace. Its comments explain that selected bridges are intended for host installd and artd, which run in that namespace. It validates that the stage is not mounted or symlink-redirected into host /data, verifies guarded overlay hashes, and prepares the staged guest filesystem and data layout.

switcher/assets/guest_start.sh makes mount propagation private, performs pivot_root, mounts a guest /proc, hides /proc/sysrq-trigger, detaches the old host root from the guest view, removes host storage views, and builds guest-facing /storage, /mnt, and /data paths.

**AresDroid implication:** a private mount namespace and a safe rootfs are necessary but insufficient. The Ares implementation must prove that no guest-visible path can write through to host /data, /storage, /metadata, or host block-backed mounts. The initial Ares tests must be disposable and read-only with respect to host data.

### 3. Framework bootstrap and Zygote

OneOS does not simply boot a second stock Android init alongside the first. The scripts prepare the rootfs and then launch the guest framework from the switched userspace. guest_start.sh derives classpaths from the guest's derive_classpath because inheriting the host's BOOTCLASSPATH and SYSTEMSERVERCLASSPATH caused guest-specific framework failures.

switcher/native/zygote_launcher.c creates the guest zygote and USAP sockets and launches the guest app_process64, passing the guest runtime environment rather than hardcoding one ROM's classpaths.

**AresDroid implication:** guest classpaths, linker namespaces, APEX paths and zygote socket lifecycle must be treated as image-specific inputs. Do not reuse OneOS's prebuilt launcher or assume its current Android 17 paths fit the Ares host/guest pair.

### 4. Binder and Android service ownership

OneOS's own docs/inplace-return.md says the guest shares the host servicemanager. The guest's /dev is also shared with the host; docs/zygote-sockets.md documents shared /dev/socket and the need to protect the guest zygote sockets from host init unlinking them.

This shared namespace caused a real vold ownership conflict. The guest initially registered vold over the host's service. OneOS later patched specific supported guest images so the guest registers gvld, and patched matching framework/library lookups. The patch is tied to exact image builds and verified hashes; it is not a generic rename recipe.

**AresDroid implication:** the Binder result already collected on Ares (Binder IPC/BinderFS enabled; binder, hwbinder and vndbinder visible in the container) proves device availability, not an independent guest servicemanager. Before Android framework startup, AresDroid must choose and prove one of:
- a genuinely separate Binder context/service-manager arrangement supported by this kernel and Android userspace; or
- a deliberately shared host service namespace with a complete, image-specific service ownership and conflict policy.

Do not launch guest vold, installd, keystore2, gatekeeper, SurfaceFlinger or other name-owning services until ownership and side effects are understood.

### 5. Properties and SELinux

guest_props.sh imports selected guest properties into the host property area because the guest has no independent init/property service in this model. It records added/overridden values and restores host values on return. The switch script also sets SELinux permissive during the takeover and relies on the return path to restore the original mode; the upstream comments explain that the guest userspace is not labelled for Samsung's policy.

**AresDroid implication:** neither global property mutation nor setenforce 0 is accepted as an initial Ares strategy. AresDroid must first map the host property contexts, property-area capacity, SELinux domains and vendor policy. Unknown policy or property behavior is a stop condition. A future controlled policy design is required before framework services can be tested.

### 6. Watchdog and device-specific hardware

OneOS has device-specific watchdog handling. Its current MediaTek support note in Profiles.kt is based on the Galaxy A07 (MT6789), with a device-specific composer service name and a warning that brightness may not be exposed through the expected backlight sysfs node. The source also documents a MediaTek A16 4G watchdog-node difference (/dev/softdog versus /dev/watchdog).

This is useful evidence that OneOS can run on some MediaTek phones, but it is **not evidence of MT6893 support**. MT6789 and MT6893 are different SoCs, and watchdog, display composer, audio, camera, power and vendor service behavior must be probed on Ares itself.

**AresDroid implication:** never write to a watchdog node during discovery. Identify each node through sysfs, device major/minor, holders and kernel logs; do not infer that /dev/watchdog or /dev/softdog has the same meaning as on a Samsung MediaTek device.

### 7. Return and recovery

return_to_oneui.sh has an elaborate in-place return path with a baseline of host service PIDs, properties, SELinux mode, APEX mounts and backing devices, loop/dm state, network rules and other state. It tears down the guest, checks that host-critical service ownership and mounts are intact, restores properties and SELinux, restarts host services, and verifies that the host framework reaches an unlocked/ready state. Failed checks use a reboot failsafe.

**AresDroid implication:** this is a design reference, not portable code. AresDroid needs its own Ares-specific baseline and recovery checks. A normal reboot fallback is not considered proven until the exact Ares boot/recovery path is tested.

## Porting matrix

| OneOS mechanism | AresDroid decision |
|---|---|
| Root-manager-aware preflight | Reimplement for KernelSU on Ares; detect, never assume paths |
| Stage/image SHA-256 and guard manifest | Adopt the integrity principle; define Ares-specific manifest |
| Init mount-namespace requirement | Probe and document Ares namespace topology before any mount |
| pivot_root and private mount propagation | Reimplement only after disposable namespace tests |
| Host /data bridges for host installd/artd | Do not copy by default; design Ares storage isolation from observed host behavior |
| Shared /dev and Binder | Not accepted without a per-device/per-service ownership map |
| Shared servicemanager | Explicit architecture decision; no implicit assumption |
| Guest property import into host property area | Reject for M0–M3; investigate only after property-service design |
| Global SELinux permissive | Reject |
| Samsung softdog/watchdog logic | Reject; identify Ares watchdogs independently |
| Samsung composer, audio, IMS, camera and display workarounds | Reject; derive Ares service inventory and test one HAL at a time |
| Zygote launcher | Reimplement/build for the chosen guest Android release and verified runtime environment |
| In-place return | Design from Ares baseline; reboot fallback only after tested recovery |
| Samsung-specific framework patches | Reject; patch only verified AOSP/ROM image builds and only for demonstrated hazards |

## Ares-specific known facts and unknowns

Known from the user's Ares investigation:
- Device-tree model/compatible identifies MT6893 (mediatek,MT6893).
- Kernel configuration includes CONFIG_ANDROID_BINDER_IPC=y and CONFIG_ANDROID_BINDERFS=y.
- Host BinderFS exposes binder, hwbinder, and vndbinder.
- Droidspaces' process has separate mount, PID and IPC namespaces, while sharing the host user namespace.
- Binder devices are visible in the Droidspaces mount namespace through the same Binder filesystem superblock.

These facts do **not** yet establish:
- a second Android init or property service;
- an isolated Binder context manager;
- safe guest vold/installd/keystore ownership;
- APEX activation compatibility;
- guest zygote/system_server compatibility with the installed vendor;
- safe display/audio/radio HAL reuse;
- a tested in-place return or reboot recovery.

## Immediate work order

1. Finish M0 using the Ares probe and the actual host build: Android release/API, vendor API, kernel release/config, mount topology, Binder contexts, APEX inventory, VINTF/HAL inventory, watchdog identity and free space.
2. Add an upstream-to-Ares compatibility record for every mechanism; unknown means blocked.
3. Run M1 disposable namespace/mount tests only. No host framework process is stopped.
4. Select the Binder/service-manager architecture before attempting any guest Android service.
5. Validate a guest image offline against the Ares host/vendor and Android API levels.
6. Implement a supervised guest bootstrap only after storage isolation, property/SELinux strategy and recovery are proven.
7. Attempt guest zygote/system_server last; integrate HALs incrementally.

## Upstream files reviewed

- README.md
- switcher/assets/oneos_switch.sh
- switcher/assets/guest_prepare.sh
- switcher/assets/guest_start.sh
- switcher/assets/guest_props.sh
- switcher/assets/return_to_oneui.sh
- switcher/native/zygote_launcher.c
- docs/inplace-return.md
- docs/zygote-sockets.md
- docs/app/patch-catalog.md
- app-android/core/model/src/main/kotlin/com/fahadali/oneos/core/model/Profiles.kt

Upstream source: https://github.com/fahadalijaved/OneOS


## 8. Upstream licensing and clean-room boundary (verified 2026-11-07)

The upstream repository has an Apache-2.0 LICENSE, but that file must not be
read as blanket permission for every path in the repository. Its root NOTICE
states that the session scripts under `switcher/assets/` started from
OneLineage by WeWaffles, that OneLineage has no license, and that written
permission was granted to publish OneOS on top of it with credit. The NOTICE
explicitly says that this permission is not a license to reuse those
OneLineage-derived parts elsewhere.

Consequences for AresDroid:

- Do not copy, fork, translate, mechanically rewrite, or adapt the
  OneLineage-derived scripts in `switcher/assets/` into AresDroid unless
  separate permission/license covering that reuse is obtained.
- Do not treat OneOS's Apache-2.0 root LICENSE as overriding the path-specific
  NOTICE or third-party terms.
- Use upstream documentation and observed behavior as architectural research.
  Implement AresDroid's scripts and runtime independently, with its own
  design, tests, and provenance.
- Before reusing any non-script file (including native code, Kotlin, patches,
  overlays, assets or binaries), inspect its copyright headers, NOTICE/CREDITS
  entries and applicable license individually. No blanket reuse approval is
  inferred here.
- Keep attribution for concepts/research references clear, but do not imply
  that attribution alone grants copyright permission.

This is an engineering provenance note, not legal advice. Upstream references:
- OneOS LICENSE: https://github.com/fahadalijaved/OneOS/blob/main/LICENSE
- OneOS NOTICE: https://github.com/fahadalijaved/OneOS/blob/main/NOTICE
- OneOS CREDITS: https://github.com/fahadalijaved/OneOS/blob/main/CREDITS.md

## 9. Additional implementation facts confirmed from upstream source

The source review confirms several details that should shape the independent
Ares implementation:

- The switch script requires the caller to be in PID 1's mount namespace.
  This is deliberate: OneOS creates selected data bridges there because host
  installd/artd use that namespace. AresDroid must measure its own namespace
  topology and must not assume KernelSU's shell is already in init's namespace.
- The guest preparation code performs extensive symlink and mount checks
  before recursively changing staged data. This is a critical safety property:
  a guest-controlled symlink below the stage could redirect host-side
  cleanup/chown/relabel operations into real host data.
- The guest launcher exports classpaths derived from the guest image rather
  than blindly inheriting host BOOTCLASSPATH and SYSTEMSERVERCLASSPATH.
  AresDroid must derive and validate guest runtime paths for the exact guest
  image and Android release.
- The guest zygote sockets live under the shared `/dev/socket` tree in the
  OneOS design. Host init can unlink/recreate those paths if its zygote is
  restarted. AresDroid must explicitly model socket path ownership and init
  onrestart behavior; a separate PID or mount namespace alone does not
  isolate a shared socket filesystem.
- OneOS's documented in-place return is conditional on a detailed baseline
  (boot ID, properties, SELinux mode, APEX devices, service PIDs, mounts,
  linker configuration and kernel-log marker) and multiple gates. It falls
  back to reboot when checks fail. This is not a generic return script that
  can be transplanted to Ares.
- The current MediaTek profile in upstream is Galaxy A07 / MT6789, Android 17,
  vendor API 31 and kernel 6.12. This is materially different from Ares
  MT6893 and its observed Android 16 / vendor API 30 / kernel 4.14.x
  environment. It demonstrates only that OneOS has begun device-specific
  MediaTek adaptation; it does not validate Ares.

## 10. Updated AresDroid implementation gates

The next work is an independent, staged implementation—not a port of the
upstream shell scripts:

1. **M0 closeout:** capture one internally consistent Ares report and resolve
   conflicting host identity properties (including build fingerprint,
   Android release/API and vendor API).
2. **M1 namespace laboratory:** use a disposable directory on writable
   `/data`; test namespace creation, mount propagation and pivot_root with
   no host service changes and no host data mounts exposed writable.
3. **M2 Android interface inventory:** map Binder devices/context managers,
   servicemanager ownership, init service definitions, zygote sockets,
   property service, APEX and VINTF/HALs. Linux IPC namespaces do not by
   themselves create an independent Android Binder context manager.
4. **M3 guest-image compatibility:** inspect the exact guest image offline;
   validate API/vendor compatibility, linker namespaces, APEX requirements,
   classpaths, VINTF and framework service dependencies.
5. **M4 recovery engineering:** design and test an Ares-specific recovery
   controller while the host framework is still running. Prove a forced
   reboot/recovery route independently before considering a handoff.
6. **M5 supervised framework experiment:** only after the preceding gates
   pass, consider a controlled guest bootstrap with explicit service
   ownership and a tested return path.

Until then, all AresDroid runtime actions remain disabled. No host zygote,
system_server, SurfaceFlinger, vold, installd, property service, watchdog or
SELinux state is to be changed by bring-up scripts.
