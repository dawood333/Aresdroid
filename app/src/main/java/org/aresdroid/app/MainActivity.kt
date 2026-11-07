package org.aresdroid.app

import androidx.activity.ComponentActivity
import android.content.Intent
import android.graphics.Typeface
import android.app.AlertDialog
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    private lateinit var gsiStatus: TextView
    private var nativeStatusText = "Native core not checked"
    private lateinit var auditStatus: TextView
    private var auditRunning = false

    private val openGsi = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: SecurityException) {
                // The selected provider may not support persistable grants.
            }
            validateAndStageImage(uri)
        }
    }

    private external fun nativeRuntimeInfo(): String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            System.loadLibrary("aresdroid")
            nativeStatusText = nativeRuntimeInfo()
        } catch (error: UnsatisfiedLinkError) {
            nativeStatusText = "Native core unavailable: ${error.message ?: "load failed"}"
        }
        buildDashboard()
    }

    private fun buildDashboard() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF101522.toInt())
        }
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(22), dp(20), dp(28))
        }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        content.addView(TextView(this).apply {
            text = "ARESDROID"
            textSize = 12f
            letterSpacing = 0.22f
            setTextColor(0xFF9AAAC8.toInt())
        })
        content.addView(TextView(this).apply {
            text = "Android, on your terms."
            textSize = 27f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(0xFFF3F6FF.toInt())
            setPadding(0, dp(8), 0, dp(5))
        })
        content.addView(label("A userspace GSI session manager for Xiaomi Ares."))

        content.addView(sectionTitle("DEVICE STATUS"))
        val device = card(content)
        device.addView(valueRow("Device", "${Build.DEVICE} / ${Build.MODEL}"))
        device.addView(valueRow("Hardware", Build.HARDWARE))
        device.addView(valueRow("Android", "${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})"))
        device.addView(valueRow("Kernel", System.getProperty("os.version") ?: "Unknown"))
        device.addView(valueRow("ABI", Build.SUPPORTED_ABIS.joinToString()))
        device.addView(valueRow("SELinux", readSelinuxState()))
        device.addView(valueRow("Target check", aresTargetStatus()))

        content.addView(sectionTitle("GUEST IMAGE"))
        val image = card(content)
        gsiStatus = label("No image selected. Choose a system image to copy, hash and inspect it offline.")
        image.addView(gsiStatus)
        image.addView(actionButton("Choose GSI image") { openGsi.launch(arrayOf("*/*")) })
        image.addView(actionButton("View staged images") { showStagedImages() })
        image.addView(actionButton("Remove staged images") { confirmClearStagedImages() })

        content.addView(sectionTitle("RUNTIME"))
        val runtime = card(content)
        runtime.addView(label(nativeStatusText))
        runtime.addView(label("Root-backed, read-only Ares audit. It does not change mounts, services, properties or SELinux."))
        runtime.addView(actionButton("Run privileged Ares audit") { runAresAudit() })
        auditStatus = label("Audit not run yet. Tap the button and approve the KernelSU prompt.")
        runtime.addView(auditStatus)
        runtime.addView(Button(this).apply {
            text = "Switch Android session — locked"
            isEnabled = false
            alpha = 0.55f
            isAllCaps = false
        })

        content.addView(sectionTitle("NEXT MILESTONES"))
        val milestones = card(content)
        milestones.addView(valueRow("M0", "Device inventory and compatibility"))
        milestones.addView(valueRow("M1", "Isolated namespace and pivot_root lab"))
        milestones.addView(valueRow("M2", "Guest image validation"))
        milestones.addView(valueRow("M3", "Binder and service ownership"))
        milestones.addView(valueRow("M4", "Independent recovery controller"))
        milestones.addView(valueRow("M5", "Supervised Android framework bootstrap"))

        content.addView(label("AresDroid 0.1.0 • Experimental • No host services modified").apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(22), 0, 0)
            textSize = 11f
        })
        setContentView(root)
    }

    private fun runAresAudit() {
        if (auditRunning) return
        auditRunning = true
        auditStatus.text = "Requesting root and collecting read-only diagnostics…"
        Thread {
            val script = """
                echo '=== AresDroid M0 / M1 preflight ==='
                echo '[identity]'
                id
                echo "device=$(getprop ro.product.device)"
                echo "model=$(getprop ro.product.model)"
                echo "hardware=$(getprop ro.hardware)"
                echo "soc=$(getprop ro.soc.model)"
                echo "android=$(getprop ro.build.version.release) sdk=$(getprop ro.build.version.sdk)"
                echo "fingerprint=$(getprop ro.build.fingerprint)"
                echo "vendor_api=$(getprop ro.vndk.version)"
                echo "kernel=$(uname -a)"
                echo '[security]'
                printf 'selinux_enforce='
                cat /sys/fs/selinux/enforce 2>/dev/null || echo unavailable
                echo '[namespaces]'
                for ns in mnt pid ipc net uts user cgroup; do
                    printf '%s self=' "${'$'}ns"; readlink "/proc/self/ns/${'$'}ns" 2>/dev/null || echo unavailable
                    printf '%s init=' "${'$'}ns"; readlink "/proc/1/ns/${'$'}ns" 2>/dev/null || echo unavailable
                done
                echo '[namespace-tools]'
                command -v unshare 2>/dev/null || echo 'unshare: unavailable'
                command -v nsenter 2>/dev/null || echo 'nsenter: unavailable'
                command -v pivot_root 2>/dev/null || echo 'pivot_root: unavailable'
                echo '[binder]'
                ls -l /dev/binder /dev/hwbinder /dev/vndbinder /dev/binderfs 2>&1
                grep -E 'binder|binderfs' /proc/1/mountinfo 2>/dev/null | head -n 20
                echo '[mount-propagation]'
                grep -E ' /data | / | /dev ' /proc/1/mountinfo 2>/dev/null | head -n 20
                echo '[storage]'
                df -h /data 2>&1
                echo '[isolated-mount-namespace-test]'
                if command -v unshare >/dev/null 2>&1; then
                    unshare -m sh -c 'echo "unshare_mount_namespace=created"; printf "child="; readlink /proc/self/ns/mnt; printf "init="; readlink /proc/1/ns/mnt' 2>&1
                    echo "unshare_exit=$?"
                else
                    echo 'unshare_test=SKIPPED (no unshare executable)'
                fi
                echo '=== End of read-only audit ==='
            """.trimIndent()
            var report: String
            try {
                val process = ProcessBuilder("su", "-c", script).redirectErrorStream(true).start()
                val completed = process.waitFor(25, TimeUnit.SECONDS)
                if (!completed) {
                    process.destroyForcibly()
                    report = "Root audit timed out after 25 seconds."
                } else {
                    report = process.inputStream.bufferedReader().use { it.readText() }
                    if (report.length > 14000) report = report.take(14000) + "\n[output truncated]"
                    if (process.exitValue() != 0) report += "\n[su exit=${process.exitValue()}]"
                }
            } catch (error: Exception) {
                report = "Could not start root audit: ${error.javaClass.simpleName}: ${error.message}"
            }
            runOnUiThread {
                auditStatus.text = report
                auditRunning = false
            }
        }.start()
    }

    private fun validateAndStageImage(uri: Uri) {
        gsiStatus.text = "Copying selected image into private staging and calculating SHA-256…"
        Thread {
            var result: String
            try {
                val stageDir = File(filesDir, "gsi-staging")
                if (!stageDir.exists() && !stageDir.mkdirs()) {
                    throw IllegalStateException("Cannot create private staging directory")
                }
                val temp = File(stageDir, "incoming-${System.currentTimeMillis()}.part")
                val digest = MessageDigest.getInstance("SHA-256")
                var total = 0L
                contentResolver.openInputStream(uri)?.use { input ->
                    temp.outputStream().buffered(128 * 1024).use { output ->
                        val buffer = ByteArray(128 * 1024)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            digest.update(buffer, 0, count)
                            total += count
                        }
                    }
                } ?: throw IllegalStateException("The selected document provider returned no stream")

                if (total < 4096L) throw IllegalArgumentException("Image is too small to be a system image")
                val sha256 = digest.digest().joinToString("") { "%02x".format(it) }
                val format = inspectSystemImage(temp)
                if (!format.valid) {
                    temp.delete()
                    throw IllegalArgumentException(format.message)
                }

                val staged = File(stageDir, "$sha256.img")
                if (staged.exists()) temp.delete()
                else if (!temp.renameTo(staged)) throw IllegalStateException("Could not finalize staged image")
                result = buildString {
                    appendLine("GSI image staged successfully")
                    appendLine("Format: ${format.name}")
                    appendLine("File size: ${formatSize(total)}")
                    appendLine("Expanded size: ${format.expandedSize?.let { formatSize(it) } ?: "Not applicable"}")
                    appendLine("SHA-256: $sha256")
                    appendLine("Private path: ${staged.absolutePath}")
                    append("Status: offline structure check passed; image is not mounted or boot-tested.")
                }
            } catch (error: Exception) {
                result = "GSI validation failed: ${error.javaClass.simpleName}: ${error.message ?: "unknown error"}"
            }
            runOnUiThread { gsiStatus.text = result }
        }.start()
    }

    private fun showStagedImages() {
        val dir = File(filesDir, "gsi-staging")
        val images = dir.listFiles { file -> file.isFile && file.extension == "img" }
            ?.sortedByDescending { it.lastModified() }.orEmpty()
        if (images.isEmpty()) {
            gsiStatus.text = "No validated images are currently staged."
            return
        }
        gsiStatus.text = buildString {
            appendLine("STAGED GSI IMAGES (${images.size})")
            images.forEachIndexed { index, file ->
                appendLine()
                appendLine("${index + 1}. ${file.name}")
                appendLine("Size: ${formatSize(file.length())}")
                appendLine("Modified: ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(file.lastModified()))}")
            }
            append("Stored in app-private storage. These images have not been mounted or boot-tested.")
        }
    }

    private fun confirmClearStagedImages() {
        val dir = File(filesDir, "gsi-staging")
        val count = dir.listFiles { file -> file.isFile && (file.extension == "img" || file.extension == "part") }
            ?.size ?: 0
        if (count == 0) {
            gsiStatus.text = "Staging is already empty."
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Remove staged images?")
            .setMessage("Delete $count staged image file(s) from AresDroid private storage? Original files will not be affected.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Remove") { _, _ ->
                val removed = dir.listFiles { file ->
                    file.isFile && (file.extension == "img" || file.extension == "part")
                }?.count { it.delete() } ?: 0
                gsiStatus.text = "Removed $removed staged image file(s). Original source files were not changed."
            }
            .show()
    }
    private data class ImageCheck(
        val valid: Boolean,
        val name: String,
        val message: String,
        val expandedSize: Long? = null
    )

    private fun inspectSystemImage(file: File): ImageCheck {
        RandomAccessFile(file, "r").use { image ->
            if (image.length() < 4L) return ImageCheck(false, "Unknown", "Truncated image")
            val magic = readLeInt(image, 0)
            if (magic == 0xED26FF3A.toInt()) return inspectAndroidSparse(image)
            if (image.length() >= 1082L && readLeShort(image, 1080) == 0xEF53) {
                return ImageCheck(true, "Raw ext4", "ext4 superblock magic found")
            }
            if (image.length() >= 1028L && readLeInt(image, 1024) == 0xE0F5E1E2.toInt()) {
                return ImageCheck(true, "Raw EROFS", "EROFS superblock magic found")
            }
            return ImageCheck(false, "Unknown", "Unsupported format: expected Android sparse, raw ext4 or raw EROFS system image")
        }
    }

    private fun inspectAndroidSparse(image: RandomAccessFile): ImageCheck {
        if (image.length() < 28L) return ImageCheck(false, "Android sparse", "Truncated sparse header")
        val major = readLeShort(image, 4)
        val fileHeaderSize = readLeShort(image, 8)
        val chunkHeaderSize = readLeShort(image, 10)
        val blockSize = readLeInt(image, 12).toLong() and 0xFFFFFFFFL
        val totalBlocks = readLeInt(image, 16).toLong() and 0xFFFFFFFFL
        val totalChunks = readLeInt(image, 20).toLong() and 0xFFFFFFFFL
        if (major != 1 || fileHeaderSize < 28 || chunkHeaderSize < 12 ||
            blockSize == 0L || totalBlocks == 0L || totalChunks > 1_000_000L
        ) return ImageCheck(false, "Android sparse", "Invalid or unsupported sparse header")

        var offset = fileHeaderSize.toLong()
        var blocksSeen = 0L
        repeat(totalChunks.toInt()) {
            if (offset + chunkHeaderSize > image.length()) {
                return ImageCheck(false, "Android sparse", "Truncated chunk header")
            }
            val type = readLeShort(image, offset)
            val chunkBlocks = readLeInt(image, offset + 4).toLong() and 0xFFFFFFFFL
            val totalSize = readLeInt(image, offset + 8).toLong() and 0xFFFFFFFFL
            val payload = when (type) {
                0xCAC1 -> chunkBlocks * blockSize
                0xCAC2 -> 4L
                0xCAC3 -> 0L
                0xCAC4 -> 4L
                else -> return ImageCheck(false, "Android sparse", "Unknown sparse chunk type 0x${type.toString(16)}")
            }
            if (totalSize != chunkHeaderSize + payload || offset + totalSize > image.length()) {
                return ImageCheck(false, "Android sparse", "Invalid or truncated sparse chunk")
            }
            if (type != 0xCAC4) blocksSeen += chunkBlocks
            offset += totalSize
        }
        if (blocksSeen != totalBlocks || offset != image.length()) {
            return ImageCheck(false, "Android sparse", "Sparse chunk table does not match header or file length")
        }
        val expanded = totalBlocks * blockSize
        return ImageCheck(true, "Android sparse image", "Sparse header and chunk table validated", expanded)
    }

    private fun readLeShort(file: RandomAccessFile, offset: Long): Int {
        file.seek(offset)
        val a = file.read()
        val b = file.read()
        if (a < 0 || b < 0) throw IllegalArgumentException("Unexpected end of image")
        return a or (b shl 8)
    }

    private fun readLeShort(file: RandomAccessFile, offset: Int): Int = readLeShort(file, offset.toLong())

    private fun readLeInt(file: RandomAccessFile, offset: Long): Int {
        file.seek(offset)
        val a = file.read()
        val b = file.read()
        val c = file.read()
        val d = file.read()
        if (a < 0 || b < 0 || c < 0 || d < 0) throw IllegalArgumentException("Unexpected end of image")
        return a or (b shl 8) or (c shl 16) or (d shl 24)
    }

    private fun readLeInt(file: RandomAccessFile, offset: Int): Int = readLeInt(file, offset.toLong())

    private fun formatSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format(Locale.ROOT, "%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format(Locale.ROOT, "%.2f MB", mb)
        return String.format(Locale.ROOT, "%.2f GB", mb / 1024.0)
    }

    private fun aresTargetStatus(): String {
        val device = Build.DEVICE.lowercase(Locale.ROOT)
        val hardware = Build.HARDWARE.lowercase(Locale.ROOT)
        return if (device == "ares" || hardware.contains("mt6893")) {
            "Ares / MT6893 candidate"
        } else "Unverified device (expected ares / MT6893)"
    }

    private fun readSelinuxState(): String = try {
        when (File("/sys/fs/selinux/enforce").readText().trim()) {
            "1" -> "Enforcing"
            "0" -> "Permissive"
            else -> "Unknown"
        }
    } catch (_: Exception) { "Unavailable" }

    private fun card(parent: LinearLayout) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(14), dp(16), dp(14))
        background = GradientDrawable().apply {
            setColor(0xFF1A2334.toInt())
            cornerRadius = dp(16).toFloat()
        }
        parent.addView(this, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(4)
        })
    }

    private fun sectionTitle(value: String) = TextView(this).apply {
        text = value
        textSize = 11f
        letterSpacing = 0.14f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(0xFF91A6CC.toInt())
        setPadding(2, dp(22), 0, dp(10))
    }

    private fun label(value: String) = TextView(this).apply {
        text = value
        textSize = 13f
        setTextColor(0xFFB8C4D9.toInt())
        setPadding(0, dp(5), 0, dp(7))
    }

    private fun valueRow(name: String, value: String) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, dp(5), 0, dp(7))
        addView(TextView(this@MainActivity).apply {
            text = name
            textSize = 11f
            setTextColor(0xFF8192B1.toInt())
        })
        addView(TextView(this@MainActivity).apply {
            text = value.ifBlank { "Unknown" }
            textSize = 14f
            setTextColor(0xFFF0F3FA.toInt())
        })
    }

    private fun actionButton(value: String, action: () -> Unit) = Button(this).apply {
        text = value
        isAllCaps = false
        setOnClickListener { action() }
    }

    private fun formatSize(bytes: Long?): String {
        if (bytes == null || bytes < 0) return "Unknown"
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format(Locale.ROOT, "%.1f KB", kb)
        return String.format(Locale.ROOT, "%.2f MB", kb / 1024.0)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
