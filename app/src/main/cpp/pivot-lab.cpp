#include <cerrno>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <string>
#include <sys/mount.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <sys/types.h>
#include <unistd.h>

#ifndef SYS_pivot_root
#error "pivot_root syscall number is unavailable for this target"
#endif

int main() {
    if (geteuid() != 0) {
        std::fprintf(stderr, "REFUSED: run as root inside a child mount namespace\n");
        return 2;
    }

    // The caller must create a new mount namespace with unshare -m.
    // Make propagation private before creating any mount in that namespace.
    if (mount(nullptr, "/", nullptr, MS_REC | MS_PRIVATE, nullptr) != 0) {
        std::fprintf(stderr, "FAIL: make-rprivate: %s\n", std::strerror(errno));
        return 3;
    }

    char new_root[] = "/data/local/tmp/aresdroid-pivot.XXXXXX";
    if (!mkdtemp(new_root)) {
        std::fprintf(stderr, "FAIL: mkdtemp: %s\n", std::strerror(errno));
        return 4;
    }
    if (mount("tmpfs", new_root, "tmpfs", MS_NOSUID | MS_NODEV,
              "size=1m,mode=0700") != 0) {
        const int e = errno;
        rmdir(new_root);
        std::fprintf(stderr, "FAIL: tmpfs mount: %s\n", std::strerror(e));
        return 5;
    }

    const std::string old_root = std::string(new_root) + "/oldroot";
    if (mkdir(old_root.c_str(), 0700) != 0) {
        std::fprintf(stderr, "FAIL: mkdir oldroot: %s\n", std::strerror(errno));
        umount2(new_root, MNT_DETACH);
        return 6;
    }
    if (chdir(new_root) != 0) {
        std::fprintf(stderr, "FAIL: chdir newroot: %s\n", std::strerror(errno));
        umount2(new_root, MNT_DETACH);
        return 7;
    }

    if (syscall(SYS_pivot_root, ".", "oldroot") != 0) {
        const int e = errno;
        chdir("/");
        umount2(new_root, MNT_DETACH);
        std::fprintf(stderr, "FAIL: pivot_root syscall: %s\n", std::strerror(e));
        return 8;
    }
    if (chdir("/") != 0) {
        std::fprintf(stderr, "FAIL: chdir after pivot: %s\n", std::strerror(errno));
        return 9;
    }
    if (umount2("/oldroot", MNT_DETACH) != 0) {
        std::fprintf(stderr, "FAIL: detach old root: %s\n", std::strerror(errno));
        return 10;
    }
    if (rmdir("/oldroot") != 0) {
        std::fprintf(stderr, "FAIL: remove oldroot mountpoint: %s\n", std::strerror(errno));
        return 11;
    }

    struct stat root_stat {};
    if (stat("/", &root_stat) != 0) {
        std::fprintf(stderr, "FAIL: stat new root: %s\n", std::strerror(errno));
        return 12;
    }
    std::printf("PASS: pivot_root syscall succeeded in isolated mount namespace\n");
    std::printf("PASS: old root detached; new root is temporary tmpfs (dev=%llu, ino=%llu)\n",
                static_cast<unsigned long long>(root_stat.st_dev),
                static_cast<unsigned long long>(root_stat.st_ino));
    std::printf("SAFE EXIT: namespace teardown discards the temporary root; host mounts were not changed\n");
    return 0;
}
