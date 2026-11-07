package org.aresdroid.app

import android.content.Context
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.nio.file.Files
import java.util.Date

/**
 * Creates a deterministic, integrity-checked description of the guest payload.
 * It deliberately does not perform a host handoff or change device state.
 */
object GuestSessionManifest {
    private const val SCHEMA = 1

    fun create(context: Context, stagedSystemImage: File): File {
        val stageDir = File(context.filesDir, "gsi-staging").canonicalFile
        val image = stagedSystemImage.canonicalFile
        require(image.parentFile == stageDir && image.isFile && image.length() >= 4096L) {
            "Selected image is not a valid staged system image"
        }
        require(!FilesCompat.isSymlink(stagedSystemImage)) { "Symlinked images are not accepted" }

        val digest = sha256(image)
        val expected = image.name.removeSuffix(".img")
        require(expected.equals(digest, ignoreCase = true)) {
            "Staged image hash does not match its filename; restage the image"
        }

        val rawImage = File(stageDir, "$digest.raw")
        val payload = if (rawImage.isFile) rawImage else image
        val fs = detectFilesystem(payload)
        require(fs == "ext4" || fs == "erofs") {
            "Guest payload filesystem is not a supported raw ext4/EROFS image"
        }

        val root = JSONObject()
            .put("schema", "org.aresdroid.guest-session")
            .put("schema_version", SCHEMA)
            .put("created_at_utc", Date().toInstant().toString())
            .put("state", "staged_not_bootable")
            .put("target", JSONObject()
                .put("device", "ares")
                .put("soc", "MT6893")
                .put("model", Build.MODEL)
                .put("hardware", Build.HARDWARE))
            .put("host", JSONObject()
                .put("fingerprint", Build.FINGERPRINT)
                .put("android_release", Build.VERSION.RELEASE)
                .put("sdk", Build.VERSION.SDK_INT)
                .put("security_patch", Build.VERSION.SECURITY_PATCH)
                .put("kernel", System.getProperty("os.version") ?: JSONObject.NULL)
                .put("abis", JSONArray(Build.SUPPORTED_ABIS.toList())))
            .put("guest", JSONObject()
                .put("system_image", image.name)
                .put("system_image_sha256", digest)
                .put("container_size_bytes", image.length())
                .put("payload_image", payload.name)
                .put("payload_sha256", sha256(payload))
                .put("payload_size_bytes", payload.length())
                .put("filesystem", fs)
                .put("requires_boot_ramdisk", true)
                .put("requires_vendor_compatibility", true))
            .put("handoff", JSONObject()
                .put("enabled", false)
                .put("reason", "Ares-specific init/ramdisk, service ownership, SELinux policy and recovery integration are not part of a system image"))
        val out = File(stageDir, "guest-session.json")
        val temp = File(stageDir, "guest-session.json.part")
        temp.writeText(root.toString(2), Charsets.UTF_8)
        temp.setReadable(false, false)
        temp.setReadable(true, true)
        if (out.exists() && !out.delete()) {
            temp.delete()
            throw IllegalStateException("Could not replace previous guest manifest")
        }
        if (!temp.renameTo(out)) {
            temp.delete()
            throw IllegalStateException("Could not finalize guest manifest")
        }
        return out
    }

    private fun detectFilesystem(file: File): String {
        RandomAccessFile(file, "r").use { raf ->
            if (raf.length() >= 1082L) {
                raf.seek(1080L)
                val a = raf.read()
                val b = raf.read()
                if (a == 0x53 && b == 0xEF) return "ext4"
            }
            if (raf.length() >= 1028L) {
                raf.seek(1024L)
                val magic = ByteArray(4)
                raf.readFully(magic)
                if (magic.contentEquals(byteArrayOf(0xE2.toByte(), 0xE1.toByte(), 0xF5.toByte(), 0xE0.toByte()))) return "erofs"
            }
        }
        return "unknown"
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered(128 * 1024).use { input ->
            val buffer = ByteArray(128 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private object FilesCompat {
        fun isSymlink(file: File): Boolean = Files.isSymbolicLink(file.toPath())
    }
}
