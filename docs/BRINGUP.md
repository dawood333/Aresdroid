# Bring-up plan

## M0 — source audit and device inventory (current)

- [x] Identify the original OneOS repository and audit its runtime model.
- [x] Record that OneOS is a hot userspace handoff, not two simultaneously running Android frameworks.
- [x] Record OneOS's shared servicemanager and shared /dev/Binder behavior.
- [ ] Capture Ares build fingerprint, Android release, SDK and security patch.
- [ ] Capture Ares kernel release and available kernel config.
- [ ] Inventory Binder nodes, Binder contexts and context-manager ownership.
- [ ] Inventory host servicemanager, hwservicemanager and vndservicemanager.
- [ ] Inventory mount namespaces and mount propagation, including PID 1/init.
- [ ] Record SELinux mode, policy filesystem visibility and property contexts.
- [ ] Identify watchdog devices and holders without writing to them.
- [ ] Record vendor / odm / vendor_dlkm / apex layout, VINTF and vendor API.
- [ ] Record free space and filesystem types.

**Gate:** source audit reviewed and Ares probe report complete. No unknown Binder, service-manager, watchdog or vendor behavior is guessed.

## M1 — non-invasive Linux primitive tests

Only after M0 review, test namespace creation, mount propagation, bind mounts and pivot_root in a throwaway isolated directory. Do not touch Android service processes or production mountpoints.

- [ ] Verify mount namespace creation and cleanup after forced process termination.
- [ ] Verify private propagation does not alter host mount topology.
- [ ] Verify a disposable rootfs cannot traverse into host /data or /storage.
- [ ] Verify recovery cleanup after the test process is killed.

**Gate:** all mounts and loop devices are cleaned up, with host mountinfo unchanged.

## M2 — guest filesystem validation

Validate a generic arm64 Android guest image offline. Confirm Android/API compatibility with the Ares host/vendor, dynamic partitions, filesystem support, APEX format, linker namespaces, framework classpaths and required HAL versions. No boot attempt.

**Gate:** image manifest and compatibility report complete; no framework launch.

## M3 — Binder and service ownership design

- [ ] Choose shared-service-plane or genuinely separate Binder/service-manager architecture.
- [ ] Demonstrate context-manager ownership and service lookup behavior using harmless test services.
- [ ] Inventory service names that would conflict with host vold, installd, keystore, gatekeeper, SurfaceFlinger and vendor services.
- [ ] Define image-specific guards and fail-closed checks.

**Gate:** guest-only service registration and lookup behavior proven without stopping or replacing host services.

## M4 — supervised guest bootstrap and recovery

Only after storage isolation, Binder design, property/SELinux strategy and recovery are proven: implement a controlled bootstrap with health checks, timeout, watchdog ownership monitoring, thermal/power monitoring and a tested return path.

Host zygote/system_server takeover remains disabled until the external recovery route has been demonstrated on this exact Ares build.

**Gate:** repeatable guest start/stop and recovery without host data changes or boot partition modifications.

## M5 — Android framework

Attempt guest zygote and system_server only after M0–M4 pass. Derive classpaths from the guest image, validate APEX activation and add vendor HAL compatibility one service at a time.

## Stop conditions

Stop immediately if the test requires:
- disabling SELinux globally;
- writing to an unidentified watchdog;
- killing host init, servicemanager, zygote, system_server or surfaceflinger without a proven recovery route;
- replacing host vendor files or modifying boot/vendor partitions;
- guessing Binder context-manager ownership, service ownership or HAL compatibility;
- exposing host /data, user storage, keys or block-backed mounts to guest writes.
