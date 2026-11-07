#!/system/bin/sh
# AresDroid read-only service/Binder/watchdog inventory.
# No service is stopped, no sysfs node is written, and SELinux is untouched.
set +e

section() { printf '\n===== %s =====\n' "$1"; }

section "IDENTITY"
echo "device=$(getprop ro.product.device)"
echo "model=$(getprop ro.product.model)"
echo "soc=$(getprop ro.soc.model)"
echo "board=$(getprop ro.board.platform)"
echo "android=$(getprop ro.build.version.release) sdk=$(getprop ro.build.version.sdk)"
echo "vendor_api=$(getprop ro.vendor.api_level)"
echo "kernel=$(uname -r)"
echo "selinux=$(getenforce 2>/dev/null)"

section "BINDER DEVICES AND FILESYSTEM"
ls -l /dev/binder /dev/hwbinder /dev/vndbinder /dev/binderfs /dev/binderfs/* 2>&1
grep -i binder /proc/filesystems 2>/dev/null
grep -i binder /proc/1/mountinfo 2>/dev/null | head -n 40

section "SERVICE MANAGER REGISTRATIONS"
if command -v service >/dev/null 2>&1; then
    service list 2>&1 | head -n 260
else
    echo "service command unavailable"
fi
if command -v cmd >/dev/null 2>&1; then
    echo "--- cmd -l"
    cmd -l 2>&1 | head -n 160
fi
if command -v lshal >/dev/null 2>&1; then
    echo "--- lshal (read-only listing)"
    if command -v timeout >/dev/null 2>&1; then
        timeout 12 lshal 2>&1 | head -n 220
    else
        echo "timeout command unavailable; lshal not run"
    fi
else
    echo "lshal unavailable"
fi

section "INIT SERVICE STATES"
getprop 2>/dev/null | grep -E '^\[init\.svc(_debug_pid)?\.' | head -n 260

section "ANDROID CORE PROCESS OWNERSHIP"
ps -AZ 2>/dev/null | grep -E '(^|[[:space:]])(init|servicemanager|hwservicemanager|vndservicemanager|zygote|system_server|surfaceflinger|vold|installd|keystore2|gatekeeperd|netd|watchdogd|audioserver|cameraserver)([[:space:]]|$)' | head -n 160
for name in servicemanager hwservicemanager vndservicemanager zygote system_server surfaceflinger vold installd keystore2 gatekeeperd netd watchdogd; do
    p="$(pidof "$name" 2>/dev/null | awk '{print $1}')"
    [ -n "$p" ] || continue
    printf '%s pid=%s mount_ns=' "$name" "$p"
    readlink "/proc/$p/ns/mnt" 2>/dev/null
    printf 'selinux='
    cat "/proc/$p/attr/current" 2>/dev/null
done

section "WATCHDOG DEVICES (READ ONLY)"
ls -l /dev/watchdog* /dev/softdog 2>/dev/null
for d in /sys/class/watchdog/watchdog*; do
    [ -d "$d" ] || continue
    echo "--- $d"
    for f in identity state status timeout timeleft nowayout bootstatus dev; do
        [ -r "$d/$f" ] && { printf '%s=' "$f"; cat "$d/$f"; }
    done
done

section "VINTF FILE INVENTORY"
for d in /vendor/etc/vintf /odm/etc/vintf /system/etc/vintf /system_ext/etc/vintf; do
    [ -d "$d" ] || continue
    echo "--- $d"
    find "$d" -maxdepth 2 -type f 2>/dev/null | head -n 100
done

section "MOUNT NAMESPACE SNAPSHOT"
printf 'self='
readlink /proc/self/ns/mnt
printf 'init='
readlink /proc/1/ns/mnt
grep -E ' /( |system |vendor |odm |product |system_ext |apex )' /proc/1/mountinfo 2>/dev/null | head -n 100

section "END"
echo "Read-only inventory complete. No host service or system setting was changed."
