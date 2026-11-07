package org.aresdroid.app

import androidx.activity.ComponentActivity
import android.content.Intent
import android.graphics.Typeface
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
import java.util.Locale

class MainActivity : ComponentActivity() {
    private lateinit var gsiStatus: TextView
    private var nativeStatusText = "Native core not checked"

    private val openGsi = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: SecurityException) {
                // The selected provider may not support persistable grants.
            }
            showSelectedImage(uri)
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
        gsiStatus = label("No image selected. Selection does not install or validate a GSI.")
        image.addView(gsiStatus)
        image.addView(actionButton("Choose GSI image") { openGsi.launch(arrayOf("*/*")) })

        content.addView(sectionTitle("RUNTIME"))
        val runtime = card(content)
        runtime.addView(label(nativeStatusText))
        runtime.addView(label("Session switching is locked during safety and recovery bring-up."))
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

    private fun showSelectedImage(uri: Uri) {
        var name = uri.lastPathSegment ?: "Selected document"
        var size: Long? = null
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIndex >= 0) name = cursor.getString(nameIndex) ?: name
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
                }
            }
        gsiStatus.text = "Selected: $name\nSize: ${formatSize(size)}\nNot copied, inspected or validated yet."
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
