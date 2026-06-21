package net.adrianba.portal.sensorexplorer

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.adrianba.portal.sensorexplorer.ui.theme.SampleAppTheme

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    dumpSensorInfo()
    enableEdgeToEdge()
    setContent {
      SampleAppTheme(darkTheme = true) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
          Spacer(modifier = Modifier.height(64.dp))
          Scaffold(
              modifier = Modifier.weight(1f),
              topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.title_ui_showcase)) },
                    colors =
                        TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                )
              }
          ) { paddingValues ->
            ShowcaseScreen(modifier = Modifier.padding(paddingValues))
          }
        }
      }
    }
  }
  private fun fmtF(v: Float): String = if (v.isInfinite() || v.isNaN() || v <= -Float.MAX_VALUE / 2) "N/A" else "%.2f".format(v)

  private fun dumpSensorInfo() {
    val tag = "SensorExplorer"

    // Device info
    Log.i(tag, "=== DEVICE INFO ===")
    Log.i(tag, "Model: ${android.os.Build.MODEL}")
    Log.i(tag, "Manufacturer: ${android.os.Build.MANUFACTURER}")
    Log.i(tag, "Brand: ${android.os.Build.BRAND}")
    Log.i(tag, "Product: ${android.os.Build.PRODUCT}")
    Log.i(tag, "Device: ${android.os.Build.DEVICE}")
    Log.i(tag, "Board: ${android.os.Build.BOARD}")
    Log.i(tag, "Hardware: ${android.os.Build.HARDWARE}")
    Log.i(tag, "Android: ${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT})")
    Log.i(tag, "Fingerprint: ${android.os.Build.FINGERPRINT}")
    Log.i(tag, "ABIs: ${android.os.Build.SUPPORTED_ABIS.joinToString(", ")}")
    Log.i(tag, "CPU Cores: ${Runtime.getRuntime().availableProcessors()}")
    fun sysProp(key: String): String = runCatching {
      val clazz = Class.forName("android.os.SystemProperties")
      val method = clazz.getMethod("get", String::class.java, String::class.java)
      method.invoke(null, key, "") as String
    }.getOrDefault("")
    fun readSys(path: String): String = runCatching { java.io.File(path).readText().trim() }.getOrDefault("")
    Log.i(tag, "Platform: ${sysProp("ro.board.platform")}")
    Log.i(tag, "SoC: ${readSys("/sys/devices/soc0/family")} ${readSys("/sys/devices/soc0/machine")} (ID: ${readSys("/sys/devices/soc0/soc_id")})")

    val wm = getSystemService(android.content.Context.WINDOW_SERVICE) as android.view.WindowManager
    val metrics = android.util.DisplayMetrics()
    @Suppress("DEPRECATION") wm.defaultDisplay.getRealMetrics(metrics)
    Log.i(tag, "Display: ${metrics.widthPixels}x${metrics.heightPixels} @ ${metrics.densityDpi}dpi (${metrics.density}x)")

    val am = getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
    val memInfo = android.app.ActivityManager.MemoryInfo()
    am.getMemoryInfo(memInfo)
    Log.i(tag, "Memory: ${memInfo.availMem / 1048576}MB free / ${memInfo.totalMem / 1048576}MB total")

    // Hardware sensors
    Log.i(tag, "=== HARDWARE SENSORS ===")
    val sensorManager = getSystemService(android.content.Context.SENSOR_SERVICE) as android.hardware.SensorManager
    val sensors = sensorManager.getSensorList(android.hardware.Sensor.TYPE_ALL)
    Log.i(tag, "Found ${sensors.size} sensor(s)")
    sensors.forEach { s ->
      Log.i(tag, "  ${s.name} | type=${s.type} | vendor=${s.vendor} | v${s.version} | range=${s.maximumRange} | res=${s.resolution} | power=${s.power}mA")
      Log.i(tag, "    minDelay=${s.minDelay}us | maxDelay=${s.maxDelay}us | reportingMode=${s.reportingMode} | wakeUp=${s.isWakeUpSensor}")
    }

    // Cameras
    Log.i(tag, "=== CAMERAS ===")
    val cameraManager = getSystemService(android.content.Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager
    cameraManager.cameraIdList.forEach { id ->
      val chars = cameraManager.getCameraCharacteristics(id)
      val facing = when (chars.get(android.hardware.camera2.CameraCharacteristics.LENS_FACING)) {
        android.hardware.camera2.CameraCharacteristics.LENS_FACING_FRONT -> "Front"
        android.hardware.camera2.CameraCharacteristics.LENS_FACING_BACK -> "Back"
        android.hardware.camera2.CameraCharacteristics.LENS_FACING_EXTERNAL -> "External"
        else -> "Unknown"
      }
      Log.i(tag, "Camera $id ($facing):")
      val sensorSize = chars.get(android.hardware.camera2.CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
      if (sensorSize != null) Log.i(tag, "  Sensor: %.1fx%.1f mm".format(sensorSize.width, sensorSize.height))
      val focalLengths = chars.get(android.hardware.camera2.CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
      if (focalLengths != null) Log.i(tag, "  Focal lengths: ${focalLengths.joinToString(", ") { "%.1fmm".format(it) }}")
      val maxZoom = chars.get(android.hardware.camera2.CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM)
      if (maxZoom != null) Log.i(tag, "  Max zoom: %.1fx".format(maxZoom))
      val hwLevel = chars.get(android.hardware.camera2.CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
      Log.i(tag, "  HW level: $hwLevel")
      val configs = chars.get(android.hardware.camera2.CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
      val jpegSizes = configs?.getOutputSizes(android.graphics.ImageFormat.JPEG)
      if (jpegSizes != null) Log.i(tag, "  JPEG sizes: ${jpegSizes.joinToString(", ") { "${it.width}x${it.height}" }}")
    }

    // Audio devices
    Log.i(tag, "=== AUDIO DEVICES ===")
    val audioManager = getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
    val inputs = audioManager.getDevices(android.media.AudioManager.GET_DEVICES_INPUTS)
    Log.i(tag, "Audio inputs: ${inputs.size}")
    inputs.forEach { d ->
      Log.i(tag, "  Input ID=${d.id} type=${d.type} product='${d.productName}' channels=${d.channelCounts.joinToString(",")} rates=${d.sampleRates.joinToString(",")}")
      Log.i(tag, "    channelMasks=${d.channelMasks.joinToString(",") { "0x%X".format(it) }} indexMasks=${d.channelIndexMasks.joinToString(",") { "0x%X".format(it) }}")
      Log.i(tag, "    encodings=${d.encodings.joinToString(",")}")
    }
    val outputs = audioManager.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS)
    Log.i(tag, "Audio outputs: ${outputs.size}")
    outputs.forEach { d ->
      Log.i(tag, "  Output ID=${d.id} type=${d.type} product='${d.productName}' channels=${d.channelCounts.joinToString(",")} rates=${d.sampleRates.joinToString(",")}")
    }

    // Microphone details
    Log.i(tag, "=== MICROPHONE DETAILS ===")
    runCatching {
      val mics = audioManager.microphones
      Log.i(tag, "Microphones: ${mics.size}")
      mics.forEach { mic ->
        Log.i(tag, "  Mic ID=${mic.id} desc='${mic.description}' addr='${mic.address}'")
        Log.i(tag, "    type=${mic.type} location=${mic.location} group=${mic.group} index=${mic.indexInTheGroup}")
        Log.i(tag, "    position=(${fmtF(mic.position.x)},${fmtF(mic.position.y)},${fmtF(mic.position.z)}) orientation=(${fmtF(mic.orientation.x)},${fmtF(mic.orientation.y)},${fmtF(mic.orientation.z)})")
        Log.i(tag, "    directionality=${mic.directionality} sensitivity=${fmtF(mic.sensitivity)} maxSpl=${fmtF(mic.maxSpl)} minSpl=${fmtF(mic.minSpl)}")
        val freq = mic.frequencyResponse
        if (freq.isNotEmpty()) Log.i(tag, "    freqResponse: ${freq.size} points, %.0f-%.0f Hz".format(freq.first().first, freq.last().first))
        val chMap = mic.channelMapping
        if (chMap.isNotEmpty()) Log.i(tag, "    channelMapping: ${chMap.joinToString(", ") { "ch${it.first}->mapping${it.second}" }}")
      }
    }.onFailure { Log.w(tag, "getMicrophones() failed: ${it.message}") }

    // Bluetooth
    Log.i(tag, "=== BLUETOOTH ===")
    val btManager = getSystemService(android.content.Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager
    val btAdapter = btManager?.adapter
    if (btAdapter != null) {
      Log.i(tag, "BT state: ${btAdapter.state} scanMode: ${btAdapter.scanMode}")
      Log.i(tag, "BT LE multi-adv: ${btAdapter.isMultipleAdvertisementSupported}")
      Log.i(tag, "BT LE offloaded filtering: ${btAdapter.isOffloadedFilteringSupported}")
      Log.i(tag, "BT LE offloaded scan batching: ${btAdapter.isOffloadedScanBatchingSupported}")
      val realAddr = runCatching {
        android.provider.Settings.Secure.getString(contentResolver, "bluetooth_address")
      }.getOrNull()
      runCatching {
        @Suppress("MissingPermission")
        val apiAddr = btAdapter.address
        Log.i(tag, "BT name: ${btAdapter.name}")
        Log.i(tag, "BT API addr: $apiAddr")
        Log.i(tag, "BT Settings.Secure addr: $realAddr")
        @Suppress("MissingPermission")
        val bonded = btAdapter.bondedDevices
        Log.i(tag, "Bonded: ${bonded.size}")
        bonded.forEach { d ->
          @Suppress("MissingPermission")
          Log.i(tag, "  ${d.name} ${d.address} type=${d.type} bond=${d.bondState}")
          val btClass = d.bluetoothClass
          if (btClass != null) Log.i(tag, "    class=0x${btClass.deviceClass.toString(16)}")
          @Suppress("MissingPermission")
          val uuids = d.uuids
          if (uuids != null && uuids.isNotEmpty()) Log.i(tag, "    uuids=${uuids.joinToString(",") { it.uuid.toString() }}")
        }
      }.onFailure { Log.w(tag, "BT info failed: ${it.message}") }
    } else {
      Log.i(tag, "Bluetooth not available")
    }

    // Network
    Log.i(tag, "=== NETWORK ===")
    @Suppress("DEPRECATION")
    val wifiManager = applicationContext.getSystemService(android.content.Context.WIFI_SERVICE) as android.net.wifi.WifiManager
    Log.i(tag, "WiFi enabled: ${wifiManager.isWifiEnabled}")
    if (wifiManager.isWifiEnabled) {
      @Suppress("DEPRECATION")
      val wi = wifiManager.connectionInfo
      Log.i(tag, "SSID: ${wi.ssid} BSSID: ${wi.bssid} RSSI: ${wi.rssi} speed: ${wi.linkSpeed}Mbps freq: ${wi.frequency}MHz")
    }

    Log.i(tag, "=== DUMP COMPLETE ===")
  }
}

@Composable
fun ShowcaseScreen(modifier: Modifier = Modifier) {
  Column(
      modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    DeviceInfoSection()
    HorizontalDivider()
    SensorListSection()
    HorizontalDivider()
    PermissionsSection()
    HorizontalDivider()
    CameraExplorerSection()
    HorizontalDivider()
    MicrophoneExplorerSection()
    HorizontalDivider()
    AudioRecorderSection()
    HorizontalDivider()
    BluetoothSection()
    HorizontalDivider()
    NetworkSection()
    HorizontalDivider()
    WeatherSection()
    HorizontalDivider()
    PlatformStateSection()
    HorizontalDivider()
    PresenceMonitorSection()
  }
}
