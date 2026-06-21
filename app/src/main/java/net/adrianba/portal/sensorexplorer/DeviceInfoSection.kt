package net.adrianba.portal.sensorexplorer

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun DeviceInfoSection() {
  val context = LocalContext.current
  val deviceInfo = produceState<List<Pair<String, String>>>(initialValue = emptyList()) {
    value = withContext(Dispatchers.IO) { gatherDeviceInfo(context) }
  }

  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(
        stringResource(R.string.section_device_info),
        style = MaterialTheme.typography.headlineSmall,
    )

    if (deviceInfo.value.isEmpty()) {
      Text("Loading…", style = MaterialTheme.typography.bodyMedium)
    } else {
      deviceInfo.value.forEach { (label, value) ->
        InfoRow(label = label, value = value)
      }
    }
  }
}

@Composable
fun InfoRow(label: String, value: String) {
  Surface(
      color = MaterialTheme.colorScheme.surfaceVariant,
      shape = MaterialTheme.shapes.small,
      modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Text(
          text = label,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.weight(1f),
      )
      Text(
          text = value,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.weight(1.5f),
      )
    }
  }
}

private fun gatherDeviceInfo(context: Context): List<Pair<String, String>> {
  val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
  val metrics = DisplayMetrics()
  @Suppress("DEPRECATION")
  wm.defaultDisplay.getRealMetrics(metrics)

  val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
  val memInfo = ActivityManager.MemoryInfo()
  activityManager.getMemoryInfo(memInfo)
  val totalMemMb = memInfo.totalMem / (1024 * 1024)
  val availMemMb = memInfo.availMem / (1024 * 1024)

  // CPU & SoC details from system properties and /proc, /sys
  val cpuDetails = mutableListOf<Pair<String, String>>()
  fun sysProp(key: String): String = runCatching {
    val clazz = Class.forName("android.os.SystemProperties")
    val method = clazz.getMethod("get", String::class.java, String::class.java)
    method.invoke(null, key, "") as String
  }.getOrDefault("")
  fun readFile(path: String): String = runCatching {
    java.io.File(path).readText().trim()
  }.getOrDefault("")
  fun shellCmd(cmd: String): String = ShellUtils.shellCmd(cmd)

  val platform = sysProp("ro.board.platform")
  val socFamily = readFile("/sys/devices/soc0/family")
  val socMachine = readFile("/sys/devices/soc0/machine")
  val socId = readFile("/sys/devices/soc0/soc_id")
  val cpuFeatures = shellCmd("head -20 /proc/cpuinfo | grep -i features | head -1 | cut -d: -f2")

  if (platform.isNotEmpty()) cpuDetails.add("Platform" to platform)
  if (socFamily.isNotEmpty() || socMachine.isNotEmpty()) {
    cpuDetails.add("SoC" to "$socFamily $socMachine".trim())
  }
  if (socId.isNotEmpty()) cpuDetails.add("SoC ID" to socId)
  if (cpuFeatures.isNotEmpty()) cpuDetails.add("CPU Features" to cpuFeatures.trim())

  // Light sensor details from SensorManager
  val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as android.hardware.SensorManager
  val lightSensor = sensorManager.getDefaultSensor(android.hardware.Sensor.TYPE_LIGHT)
  val sensorDetails = mutableListOf<Pair<String, String>>()
  if (lightSensor != null) {
    sensorDetails.add("Light Sensor" to lightSensor.name)
    sensorDetails.add("Vendor" to lightSensor.vendor.ifBlank { "(unknown)" })
    sensorDetails.add("Max Range" to "%.0f lux".format(lightSensor.maximumRange))
    sensorDetails.add("Resolution" to "%.4f lux".format(lightSensor.resolution))
    sensorDetails.add("Power" to "%.3f mA".format(lightSensor.power))
    sensorDetails.add("Min Delay" to "${lightSensor.minDelay} µs")
    if (Build.VERSION.SDK_INT >= 21) {
      sensorDetails.add("Max Delay" to "${lightSensor.maxDelay} µs")
      sensorDetails.add("Reporting Mode" to when (lightSensor.reportingMode) {
        android.hardware.Sensor.REPORTING_MODE_CONTINUOUS -> "Continuous"
        android.hardware.Sensor.REPORTING_MODE_ON_CHANGE -> "On-change"
        android.hardware.Sensor.REPORTING_MODE_ONE_SHOT -> "One-shot"
        android.hardware.Sensor.REPORTING_MODE_SPECIAL_TRIGGER -> "Special trigger"
        else -> "${lightSensor.reportingMode}"
      })
      sensorDetails.add("Wake-up" to if (lightSensor.isWakeUpSensor) "Yes" else "No")
    }
  }

  return listOf(
      "Model" to Build.MODEL,
      "Manufacturer" to Build.MANUFACTURER,
      "Brand" to Build.BRAND,
      "Product" to Build.PRODUCT,
      "Device" to Build.DEVICE,
      "Board" to Build.BOARD,
      "Hardware" to Build.HARDWARE,
      "Android Version" to "${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})",
      "Build Fingerprint" to Build.FINGERPRINT,
      "Display" to "${metrics.widthPixels} × ${metrics.heightPixels}",
      "Density" to "${metrics.densityDpi} dpi (${metrics.density}x)",
      "CPU Cores" to "${Runtime.getRuntime().availableProcessors()}",
      "Memory" to "${availMemMb} MB free / ${totalMemMb} MB total",
      "Supported ABIs" to Build.SUPPORTED_ABIS.joinToString(", "),
  ) + cpuDetails + sensorDetails
}
