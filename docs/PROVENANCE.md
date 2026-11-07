# Provenance and upstream references

This repository is intended to be a clean-room implementation.

Research references:
- OneOS: https://github.com/fahadalijaved/OneOS
- OneLineage: https://github.com/WeWaffles/OneLineage

Do not copy switcher scripts, patches, application assets, or binaries unless
the exact file's license and upstream notice explicitly permit the intended
reuse. OneOS contains a special notice for switcher assets; review the current
`NOTICE` in that repository before considering any reuse.

General concepts such as Linux mount namespaces and `pivot_root` can be
implemented independently, but their use does not imply compatibility with
Android Binder, init, APEX, SELinux, or vendor HALs.
