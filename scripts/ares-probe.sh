#!/system/bin/sh
# AresDroid M0 inventory. Read-only: does not mount, write sysfs/dev nodes,
# stop services, create devices, or change SELinux.
set +e

OUT="${1:-/data/local/tmp/aresdroid-probe-$(date +%Y%m%d-%H%M%S).txt}"
exec >"$OUT" 2>&1

section() { printf '\n===== %s =====\n' "$1"; }
show_file() { [ -r "$1" ] && { echo "--- $1"; cat "$1"; } || echo "--- $1 (not readable)"; }

echo "AresDroid M0 read-only probe"
echo "Date: $(date 2>/dev/null)"
echo "Output: $OUT"

section "IDENTITY"
for p in ro.product.device ro.product.model ro.product.name ro.board.platform \
         ro.hardware ro.boot.hardware ro.build.fingerprint ro.build.version.release \
         ro.build.version.sdk ro.vendor.build.version.sdk \
         ro.build.version.security_patch; do
    printf '%s=' "$p"
    getprop "$p"
done
echo "uname: $(uname -a 2>/dev/null)"
echo "device-tree model: $(cat /proc/device-tree/model 2>/dev/null)"
echo "device-tree compatible:"
tr '\000' '\n' </proc/device-tree/compatible 2>/dev/null

section "ROOT AND SECURITY"
id
command -v su
getenforce 2>/dev/null
show_file /sys/fs/selinux/enforce
show_file /proc/cmdline

section "KERNEL CONFIG"
if [ -r /proc/config.gz ]; then
    zcat /proc/config.gz 2>/dev/null | grep -E \
      'CONFIG_(NAMESPACES|UTS_NS|IPC_NS|PID_NS|NET_NS|USER_NS|CGROUPS|BINDER|ANDROID_BINDERFS|SECURITY_SELINUX|WATCHDOG|DEVTMPFS)='
else
    echo "/proc/config.gz unavailable"
fi

section "NAMESPACE AND MOUNT TOOLS"
for c in unshare nsenter mount umount pivot_root readlink; do
    printf '%s: ' "$c"
    command -v "$c" 2>/dev/null || true
done
grep -E ' - (binder|binderfs|selinuxfs|cgroup2?|erofs|ext4) ' /proc/self/mountinfo 2>/dev/null

section "BINDER"
ls -l /dev/binder* /dev/vndbinder* /dev/hwbinder* 2>/dev/null
find /dev -maxdepth 3 -type c 2>/dev/null | grep -E 'binder|hwbinder' | head -80
grep -i binder /proc/filesystems 2>/dev/null
grep -i binder /proc/self/mountinfo 2>/dev/null

section "WATCHDOG INVENTORY (NO WRITES)"
ls -l /dev/watchdog* /dev/softdog 2>/dev/null
for d in /sys/class/watchdog/watchdog*; do
    [ -d "$d" ] || continue
    echo "--- $d"
    for f in identity state status timeout timeleft nowayout bootstatus; do
        [ -r "$d/$f" ] && { printf '%s=' "$f"; cat "$d/$f"; }
    done
    [ -r "$d/dev" ] && { printf 'dev='; cat "$d/dev"; }
done

section "FILESYSTEMS AND SPACE"
df -h /data /system /vendor 2>/dev/null
grep -E ' /( |system |vendor |odm |product |system_ext |vendor_dlkm )' /proc/mounts 2>/dev/null
for p in /vendor/etc/vintf/manifest.xml /vendor/manifest.xml \
         /odm/etc/vintf/manifest.xml /system/etc/vintf/manifest.xml; do
    [ -r "$p" ] && echo "present: $p"
done

section "APEX"
ls -ld /apex /system/apex /system_ext/apex 2>/dev/null
ls /apex 2>/dev/null | head -100

section "PROCESS SNAPSHOT"
ps -A 2>/dev/null | grep -E '(^| )(init|servicemanager|hwservicemanager|vndservicemanager|zygote|system_server|surfaceflinger|watchdogd)( |$)'

section "END"
echo "Probe complete. No device state was intentionally changed."
echo "Report path: $OUT"
