# Proposed architecture

## Runtime model: OneOS-style hot switch

AresDroid follows the runtime model documented by the original OneOS project: the installed Android host remains on the phone, while a staged guest Android userspace is activated as a **session**. This is a hot userspace handoff, not two fully running Android frameworks at once.

The OneOS implementation stops the host zygote and system_server, keeps the host kernel and selected vendor/radio services alive, prepares a guest root filesystem in a mount namespace, then starts the guest framework. AresDroid may reuse this high-level model, but it must not copy Samsung-specific scripts or assume that Ares has the same service topology.

The initial AresDroid implementation remains **research-only and non-invasive**. No host framework service may be stopped until the Ares-specific recovery path, service ownership map, and all safety gates have been tested.

## Explicit architecture choices still open

OneOS shares important Android infrastructure with the host. Its own documentation confirms that the guest shares the host servicemanager and that /dev and zygote sockets are shared. This is not a private Binder context manager or a fully isolated Android VM.

AresDroid must choose and prove one of these designs before guest Android services start:

1. **OneOS-style shared service plane:** host servicemanager and selected host services remain shared. This requires a complete service-name ownership map, guest/host conflict handling, and image-specific guards. No guest vold, installd, keystore or gatekeeper may register or operate until its behavior is proven.
2. **Separate Binder/service-manager plane:** only if the Ares kernel and Android userspace can provide a distinct Binder context and service manager with working framework clients. The existence of binderfs or separate Linux IPC namespaces alone does not prove this.

Do not describe the guest as Binder-isolated until a separate context manager and successful guest-only service lookups are demonstrated.

## Components

1. **Probe layer** — read-only inventory of kernel configuration, mount topology, Binder devices and contexts, service-manager ownership, SELinux, watchdogs, APEX, VINTF/HALs and host build identity.
2. **Image staging** — future component; validates image format, filesystem, free space, hashes, image/host compatibility and rollback metadata. Not implemented.
3. **Namespace and rootfs builder** — future component; creates a private mount view and exposes only reviewed host interfaces. Must prevent guest writes to host data and block-backed mounts. Not implemented.
4. **Host handoff controller** — future component; models the host framework stop/start sequence only after the recovery path is proven. Disabled during bring-up.
5. **Guest bootstrap** — future component; prepares the guest runtime environment, classpaths, APEX view, zygote sockets and selected Android services. Not implemented.
6. **Session supervisor** — future component; tracks host/guest state, watchdog ownership, health checks, timeout and return triggers. Not implemented.
7. **Recovery path** — must be independently testable before any host service takeover. Ares-specific baseline and rollback checks are required. Not implemented.

## Ares-specific constraints

- Target is Xiaomi Ares, MediaTek MT6893 (device-tree compatible: mediatek,MT6893).
- Phase 1 is userspace-only. Do not modify kernel_mt6893, boot, vendor_boot, dtbo, vendor, odm or super.
- Do not import Samsung-specific OneOS logic: Samsung watchdog nodes, composer names, framework patches, IMS handling, property overrides or service names.
- OneOS's first MediaTek profile is based on a Galaxy A07 / MT6789. It is evidence that some MediaTek devices can use this model, not evidence that MT6893 is compatible.
- Do not globally set SELinux permissive as a shortcut.
- Do not mutate the host property area until the Ares property-service design and rollback behavior are proven.

## Critical compatibility questions

- Does the running kernel support the required mount namespace, pivot_root and propagation semantics?
- What is the actual init mount namespace and which host services depend on mounts made there?
- Which Binder devices and contexts exist, and who owns each context manager?
- Is the chosen service-manager model shared or separate, and how are service-name conflicts prevented?
- Which APEX modules are host-mounted, and which are required by the guest framework?
- What Android release/API and vendor API are installed on Ares?
- Which VINTF HALs are HIDL or AIDL, and which versions are available?
- Which SELinux domains and property contexts apply to the required operations?
- Which watchdog nodes are hardware versus software watchdogs, and who holds them?
- Can the host recover if guest bootstrap fails after framework handoff?

A positive result for unshare, pivot_root or binderfs availability is not sufficient to establish Android guest compatibility.
