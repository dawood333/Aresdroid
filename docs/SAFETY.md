# Safety rules

This project is experimental and can cause a bootloop or loss of access if
future takeover code is used incorrectly.

- The probe script is read-only and must remain read-only.
- Never assume `/dev/watchdog` is a software watchdog. Identify every node
  through sysfs and kernel documentation; never test by writing to it.
- Do not set SELinux permissive as a shortcut.
- Do not stop host Android framework services until a recovery path has been
  proven on the exact device and build.
- Do not alter `/vendor`, `/odm`, `boot`, `vendor_boot`, `dtbo`, or `super`.
- Keep a known-good boot image and a tested recovery method available.
- Back up important data before any future experimental session.
- Treat all device-specific output as untrusted input; never execute commands
  copied from a report without review.
