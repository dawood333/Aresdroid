# Bring-up plan

## M0 — inventory (current)

- [ ] Capture build fingerprint, Android release, SDK and security patch.
- [ ] Capture kernel release and available kernel config.
- [ ] Inventory Binder nodes and binderfs mounts.
- [ ] Inventory mount namespaces and mount propagation.
- [ ] Record SELinux mode and policy filesystem visibility.
- [ ] Identify watchdog devices without writing to them.
- [ ] Record vendor / odm / vendor_dlkm / apex layout and vendor API.
- [ ] Record free space and filesystem types.

**Gate:** report reviewed; no unknown watchdog or Binder behavior is guessed.

## M1 — non-invasive Linux primitive tests

Only after M0 review, test namespace creation and mount operations in a
throwaway, isolated directory. Do not touch Android service processes or
production mountpoints.

**Gate:** demonstrate cleanup after forced process termination.

## M2 — guest filesystem validation

Validate a generic arm64 Android GSI offline. Confirm dynamic partitions,
filesystem support, APEX format, linker namespaces, and required framework
version. No boot attempt.

## M3 — minimal guest services

Design a separate Binder/service-manager strategy. Do not stop host zygote or
system_server. A guest service manager must be demonstrably isolated.

## M4 — supervised guest session

Only after a recovery mechanism is proven: controlled guest startup, health
checks, watchdog ownership analysis, thermal and power monitoring, and a
tested return path.

## M5 — Android framework

Attempt guest zygote and system_server only after M0–M4 pass. Add HAL
compatibility one service at a time.

## Stop conditions

Stop immediately if the test requires:
- disabling SELinux globally;
- writing to an unidentified watchdog;
- killing host init, servicemanager, zygote, system_server or surfaceflinger
  without a proven recovery route;
- replacing host vendor files or modifying boot/vendor partitions;
- guessing Binder device ownership or HAL compatibility.
