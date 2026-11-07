package com.dawood.aresdroid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AresDroidApp() }
    }
}

private data class DeviceReport(
    val model: String, val device: String, val platform: String,
    val android: String, val sdk: String, val kernel: String,
    val selinux: String, val root: String, val binder: String,
    val storage: String, val raw: String
)

private fun readCommand(command: String): String = try {
    val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
    val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
    process.waitFor()
    output.ifBlank { "غير متاح" }
} catch (error: Exception) {
    "غير متاح: ${error.javaClass.simpleName}"
}

private fun collectReport(): DeviceReport {
    val model = readCommand("getprop ro.product.model")
    val device = readCommand("getprop ro.product.device")
    val platform = readCommand("getprop ro.board.platform")
    val android = readCommand("getprop ro.build.version.release")
    val sdk = readCommand("getprop ro.build.version.sdk")
    val kernel = readCommand("uname -r")
    val selinux = readCommand("getenforce")
    val binder = readCommand("ls -1 /dev/binderfs 2>/dev/null | tr '\\n' ' '")
    val storage = readCommand("df -h /data 2>/dev/null | tail -1")
    val rootCheck = try {
        val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
        val out = p.inputStream.bufferedReader().use { it.readText() }.trim()
        p.waitFor()
        if (p.exitValue() == 0 && out.contains("uid=0")) "متاح" else "غير متاح"
    } catch (_: Exception) { "غير متاح" }
    val raw = listOf(
        "Model: $model", "Device: $device", "Platform: $platform",
        "Android: $android (SDK $sdk)", "Kernel: $kernel",
        "SELinux: $selinux", "Root: $rootCheck",
        "BinderFS: $binder", "Data: $storage"
    ).joinToString("\n")
    return DeviceReport(model, device, platform, android, sdk, kernel, selinux, rootCheck, binder, storage, raw)
}

@Composable
private fun AresDroidApp() {
    val scope = rememberCoroutineScope()
    var report by remember { mutableStateOf<DeviceReport?>(null) }
    var scanning by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("لم يتم فحص الجهاز بعد") }

    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("AresDroid", style = MaterialTheme.typography.headlineLarge)
                Text("Android userspace switcher • Xiaomi Ares / MT6893",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("حالة المشروع", style = MaterialTheme.typography.titleMedium)
                        Text("نسخة تأسيسية — وضع الفحص فقط")
                        Text("التبديل إلى Guest معطّل حتى اكتمال العزل والاستعادة.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text("فحص الجهاز", style = MaterialTheme.typography.titleLarge)
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        InfoRow("الجهاز", report?.model ?: "—")
                        InfoRow("Codename", report?.device ?: "—")
                        InfoRow("SoC", report?.platform ?: "—")
                        InfoRow("Android / SDK", report?.let { "${it.android} / ${it.sdk}" } ?: "—")
                        InfoRow("Kernel", report?.kernel ?: "—")
                        InfoRow("SELinux", report?.selinux ?: "—")
                        InfoRow("Root", report?.root ?: "—")
                        InfoRow("BinderFS", report?.binder ?: "—")
                        InfoRow("/data", report?.storage ?: "—")
                    }
                }
                Button(onClick = {
                    scanning = true
                    status = "جارٍ جمع معلومات القراءة فقط…"
                    scope.launch {
                        report = withContext(Dispatchers.IO) { collectReport() }
                        scanning = false
                        status = "اكتمل الفحص المحلي"
                    }
                }, enabled = !scanning, modifier = Modifier.fillMaxWidth()) {
                    Text(if (scanning) "جارٍ الفحص…" else "فحص الجهاز")
                }
                OutlinedButton(onClick = {
                    status = report?.raw ?: "نفّذ فحص الجهاز أولاً"
                }, modifier = Modifier.fillMaxWidth()) { Text("عرض ملخص التقرير") }
                Text(status, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Text("لا يوقف التطبيق خدمات Android، ولا يغيّر SELinux أو الأقسام أو إعدادات watchdog.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
