# Proposed architecture

## Runtime model

The proposed design is a guest Android userspace rooted in a separate root
filesystem while sharing the host kernel. A Linux mount namespace alone does
not make Android guests compatible: Binder context management, service
manager ownership, init/property behavior, APEX activation, cgroups, SELinux,
and vendor HAL expectations all need explicit validation.

## Components

1. **Probe layer** — gathers kernel config exposure, mount topology, Binder
   devices, SELinux state, watchdog identities, properties, and vendor layout.
2. **Image staging** — future component; validates image format, filesystem,
   free space, hashes, and rollback metadata. Not implemented.
3. **Namespace builder** — future component; creates a private mount view and
   binds only reviewed host interfaces. Not implemented.
4. **Guest bootstrap** — future component; guest init / servicemanager /
   zygote sequence. Not implemented.
5. **Session supervisor** — future component; tracks host and guest state and
   guarantees a return path. Not implemented.
6. **Recovery path** — must be independently testable before any host service
   takeover. Not implemented.

## Critical compatibility questions

- Does the running kernel support unprivileged/privileged mount namespaces,
  `pivot_root`, and required mount propagation semantics?
- Which Binder devices and Binder contexts are present? Is binderfs used?
- Which process owns the relevant service-manager context?
- Can a guest servicemanager coexist without replacing the host's Binder
  service namespace?
- Which APEX modules are activated by host init, and which are required by the
  guest framework?
- What vendor interface/API level does the installed vendor image expose?
- Which HALs are HIDL or AIDL, and which Android release do they support?
- What SELinux policy constraints apply to the required operations?
- Which watchdog devices are hardware watchdogs versus software watchdogs?
- Can the host recover if the guest bootstrap fails after host framework
  services have stopped?

A positive answer to `unshare` or `pivot_root` availability is not sufficient
to establish Android guest compatibility.
