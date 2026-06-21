package net.adrianba.portal.sensorexplorer

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

data class DiscoveredDevice(
    val device: BluetoothDevice,
    val rssi: Int? = null,
    val source: String = "classic", // "classic" or "ble"
)

@Composable
fun BluetoothSection() {
  val context = LocalContext.current
  val btManager = remember {
    context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
  }
  val btAdapter = remember { btManager?.adapter }
  var isScanning by remember { mutableStateOf(false) }
  var scanPhase by remember { mutableStateOf("") } // "classic", "ble", ""
  var bleScanError by remember { mutableStateOf<Int?>(null) }
  val discoveredDevices = remember { mutableStateListOf<DiscoveredDevice>() }
  var bleScanner by remember { mutableStateOf<BluetoothLeScanner?>(null) }
  // Store callback ref for use in receiver
  var bleScanCallbackRef by remember { mutableStateOf<ScanCallback?>(null) }

  // BLE scan callback — declared before receiver so receiver can reference it
  // ScanCallback is invoked on a Binder thread, so state mutations must be posted to main
  val mainHandler = remember { android.os.Handler(android.os.Looper.getMainLooper()) }
  val bleScanCallback = remember {
    object : ScanCallback() {
      override fun onScanResult(callbackType: Int, result: ScanResult) {
        val device = result.device
        val rssi = result.rssi
        android.util.Log.i("SensorExplorer", "BLE found: ${device.address} rssi=$rssi")
        mainHandler.post {
          val idx = discoveredDevices.indexOfFirst { it.device.address == device.address }
          val entry = DiscoveredDevice(device, rssi, "ble")
          if (idx >= 0) discoveredDevices[idx] = entry else discoveredDevices.add(entry)
        }
      }
      override fun onScanFailed(errorCode: Int) {
        android.util.Log.e("SensorExplorer", "BLE scan failed: errorCode=$errorCode")
        mainHandler.post {
          bleScanError = errorCode
          scanPhase = ""
          isScanning = false
        }
      }
    }
  }

  // Classic discovery receiver — starts BLE scan when classic finishes
  val receiver = remember {
    object : BroadcastReceiver() {
      override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.action) {
          BluetoothDevice.ACTION_FOUND -> {
            @Suppress("DEPRECATION")
            val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
            val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE).toInt()
            if (device != null) {
              val idx = discoveredDevices.indexOfFirst { it.device.address == device.address }
              val entry = DiscoveredDevice(device, if (rssi > -200) rssi else null, "classic")
              if (idx >= 0) discoveredDevices[idx] = entry else discoveredDevices.add(entry)
            }
          }
          BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
            android.util.Log.i("SensorExplorer", "Classic discovery finished, starting BLE scan")
            scanPhase = "ble"
            // Now start BLE scan — classic is done so no conflict
            try {
              val scanner = btAdapter?.bluetoothLeScanner
              bleScanner = scanner
              if (scanner != null) {
                val cb = bleScanCallbackRef
                if (cb != null) {
                  val settings = android.bluetooth.le.ScanSettings.Builder()
                      .setScanMode(android.bluetooth.le.ScanSettings.SCAN_MODE_LOW_LATENCY)
                      .build()
                  @Suppress("MissingPermission")
                  scanner.startScan(null, settings, cb)
                  android.util.Log.i("SensorExplorer", "BLE scan started after classic")
                  // Auto-stop BLE after 15 seconds
                  android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    try {
                      @Suppress("MissingPermission")
                      scanner.stopScan(cb)
                    } catch (_: Exception) { }
                    scanPhase = ""
                    isScanning = false
                    android.util.Log.i("SensorExplorer", "BLE scan stopped (timeout)")
                  }, 15000)
                }
              } else {
                android.util.Log.w("SensorExplorer", "BLE scanner is null")
                isScanning = false
                scanPhase = ""
              }
            } catch (e: Exception) {
              android.util.Log.e("SensorExplorer", "BLE scan start failed: ${e.message}")
              isScanning = false
              scanPhase = ""
            }
          }
        }
      }
    }
  }

  // Store ref so receiver can access it
  bleScanCallbackRef = bleScanCallback

  DisposableEffect(Unit) {
    val filter = IntentFilter().apply {
      addAction(BluetoothDevice.ACTION_FOUND)
      addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
    }
    @Suppress("DEPRECATION")
    context.registerReceiver(receiver, filter)
    onDispose {
      try {
        @Suppress("MissingPermission")
        btAdapter?.cancelDiscovery()
      } catch (_: SecurityException) { }
      try {
        @Suppress("MissingPermission")
        bleScanner?.stopScan(bleScanCallback)
      } catch (_: Exception) { }
      context.unregisterReceiver(receiver)
    }
  }

  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(
        stringResource(R.string.section_bluetooth),
        style = MaterialTheme.typography.headlineSmall,
    )

    if (btAdapter == null) {
      Text(
          stringResource(R.string.bt_not_available),
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.error,
      )
    } else {
      // Adapter info
      Surface(
          color = MaterialTheme.colorScheme.surfaceVariant,
          shape = MaterialTheme.shapes.medium,
          modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Text(
              text = stringResource(R.string.bt_adapter_info),
              style = MaterialTheme.typography.bodyLarge,
              fontWeight = FontWeight.Bold,
          )
          val btName = runCatching {
            @Suppress("MissingPermission") btAdapter.name
          }.getOrDefault("(permission required)")
          Text("Name: $btName", style = MaterialTheme.typography.bodySmall)

          // Real MAC address — btAdapter.address returns 02:00:00:00:00:00 on API 26+
          val apiAddress = btAdapter.address
          val realAddress = runCatching {
            Settings.Secure.getString(context.contentResolver, "bluetooth_address")
          }.getOrNull() ?: run {
            ShellUtils.exec("settings", "get", "secure", "bluetooth_address")
                ?.let { if (it == "null" || it.isBlank()) null else it }
          }
          val isMasked = apiAddress == "02:00:00:00:00:00"
          if (realAddress != null && isMasked) {
            Text("Address: $realAddress", style = MaterialTheme.typography.bodySmall)
            Text(
                "Note: API returns $apiAddress (privacy-masked since Android 8)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
          } else if (isMasked) {
            Text(
                "Address: $apiAddress (privacy-masked — real address requires system privileges)",
                style = MaterialTheme.typography.bodySmall,
            )
          } else {
            Text("Address: $apiAddress", style = MaterialTheme.typography.bodySmall)
          }

          Text(
              "State: ${btStateName(btAdapter.state)}",
              style = MaterialTheme.typography.bodySmall,
          )
          Text(
              "Scan Mode: ${btScanModeName(btAdapter.scanMode)}",
              style = MaterialTheme.typography.bodySmall,
          )
          Text(
              "BLE Multi-Advertisement: ${btAdapter.isMultipleAdvertisementSupported}",
              style = MaterialTheme.typography.bodySmall,
          )
          Text(
              "BLE Offloaded Filtering: ${btAdapter.isOffloadedFilteringSupported}",
              style = MaterialTheme.typography.bodySmall,
          )
          Text(
              "BLE Offloaded Scan Batching: ${btAdapter.isOffloadedScanBatchingSupported}",
              style = MaterialTheme.typography.bodySmall,
          )

          // Supported profiles
          val profiles = mutableListOf<String>()
          // Check connected profiles via BluetoothManager
          val profileIds = mapOf(
              1 to "Headset", 2 to "A2DP", 3 to "Health",
              4 to "HID", 5 to "PAN", 6 to "PBAP",
              7 to "GATT", 8 to "GATT Server",
              10 to "A2DP Sink", 11 to "AVRCP Controller",
              16 to "HID Device",
          )
          profileIds.forEach { (id, name) ->
            val devices = runCatching {
              @Suppress("MissingPermission")
              btManager?.getConnectedDevices(id)
            }.getOrNull()
            if (devices != null) profiles.add(name)
          }
          if (profiles.isNotEmpty()) {
            Text(
                "Available Profiles: ${profiles.joinToString(", ")}",
                style = MaterialTheme.typography.bodySmall,
            )
          }

          // Bonded devices
          val bondedDevices = runCatching {
            @Suppress("MissingPermission") btAdapter.bondedDevices
          }.getOrNull()
          if (bondedDevices != null && bondedDevices.isNotEmpty()) {
            Text(
                "\nBonded Devices (${bondedDevices.size}):",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
            )
            bondedDevices.forEach { device ->
              val deviceName = runCatching {
                @Suppress("MissingPermission") device.name
              }.getOrDefault("Unknown")
              val uuids = runCatching {
                @Suppress("MissingPermission") device.uuids
              }.getOrNull()
              Text(
                  "  $deviceName — ${device.address} (${btDeviceTypeName(device.type)})",
                  style = MaterialTheme.typography.bodySmall,
              )
              if (uuids != null && uuids.isNotEmpty()) {
                Text(
                    "    UUIDs: ${uuids.joinToString(", ") { it.uuid.toString().take(8) + "…" }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
              }
              val btClass = device.bluetoothClass
              if (btClass != null) {
                Text(
                    "    Class: ${btMajorClassName(btClass.majorDeviceClass)} (0x${btClass.deviceClass.toString(16)})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
              }
            }
          } else if (bondedDevices != null) {
            Text("Bonded devices: none", style = MaterialTheme.typography.bodySmall)
          } else {
            Text(
                "Bonded devices: (permission required)",
                style = MaterialTheme.typography.bodySmall,
            )
          }
        }
      }

      // Scan controls — Classic + BLE simultaneously
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (isScanning) {
          OutlinedButton(
              onClick = {
                try {
                  @Suppress("MissingPermission")
                  btAdapter.cancelDiscovery()
                } catch (_: SecurityException) { }
                try {
                  @Suppress("MissingPermission")
                  bleScanner?.stopScan(bleScanCallback)
                } catch (_: Exception) { }
                isScanning = false
              },
              modifier = Modifier.heightIn(min = 52.dp),
          ) {
            Text(stringResource(R.string.btn_stop_scan))
          }
        } else {
          Button(
              onClick = {
                discoveredDevices.clear()
                bleScanError = null
                scanPhase = "classic"
                // Start classic discovery — BLE starts automatically when this finishes
                try {
                  @Suppress("MissingPermission")
                  val started = btAdapter.startDiscovery()
                  android.util.Log.i("SensorExplorer", "Classic discovery started: $started")
                } catch (e: SecurityException) {
                  android.util.Log.e("SensorExplorer", "Classic discovery failed: ${e.message}")
                }
                isScanning = true
              },
              modifier = Modifier.heightIn(min = 52.dp),
          ) {
            Text(if (scanPhase == "classic") "Scanning Classic…" else if (scanPhase == "ble") "Scanning BLE…" else "Scan (Classic → BLE)")
          }
        }
      }

      // BLE scan status
      if (bleScanError != null) {
        val errName = when (bleScanError) {
          1 -> "SCAN_FAILED_ALREADY_STARTED"
          2 -> "SCAN_FAILED_APPLICATION_REGISTRATION_FAILED"
          3 -> "SCAN_FAILED_INTERNAL_ERROR"
          4 -> "SCAN_FAILED_FEATURE_UNSUPPORTED"
          else -> "Unknown ($bleScanError)"
        }
        Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "BLE Scan Not Available",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                "Error: $errName (max scan filters: 0). BLE scanning is not supported on this device's firmware. Only classic Bluetooth discovery is available.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
          }
        }
      }

      // Discovered devices
      if (discoveredDevices.isNotEmpty()) {
        Text(
            stringResource(R.string.bt_discovered_count, discoveredDevices.size),
            style = MaterialTheme.typography.bodyLarge,
        )
        discoveredDevices.sortedByDescending { it.rssi ?: -999 }.forEach { entry ->
          Surface(
              color = MaterialTheme.colorScheme.surfaceVariant,
              shape = MaterialTheme.shapes.small,
              modifier = Modifier.fillMaxWidth(),
          ) {
            Column(modifier = Modifier.padding(12.dp)) {
              val deviceName = runCatching {
                @Suppress("MissingPermission") entry.device.name
              }.getOrDefault(null)
              Text(
                  text = deviceName ?: "(unnamed)",
                  style = MaterialTheme.typography.bodyMedium,
                  fontWeight = FontWeight.Bold,
              )
              Text(entry.device.address, style = MaterialTheme.typography.bodySmall)
              val details = mutableListOf<String>()
              details.add("Type: ${btDeviceTypeName(entry.device.type)}")
              details.add("Via: ${entry.source.uppercase()}")
              if (entry.rssi != null) details.add("RSSI: ${entry.rssi} dBm")
              details.add("Bond: ${btBondStateName(entry.device.bondState)}")
              Text(details.joinToString(" | "), style = MaterialTheme.typography.bodySmall)

              val btClass = entry.device.bluetoothClass
              if (btClass != null) {
                Text(
                    "Class: ${btMajorClassName(btClass.majorDeviceClass)} (0x${btClass.deviceClass.toString(16)})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
              }
              val uuids = runCatching {
                @Suppress("MissingPermission") entry.device.uuids
              }.getOrNull()
              if (uuids != null && uuids.isNotEmpty()) {
                Text(
                    "UUIDs: ${uuids.joinToString(", ") { it.uuid.toString().take(8) + "…" }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
              }
            }
          }
        }
      }
    }
  }
}

private fun btStateName(state: Int): String = when (state) {
  BluetoothAdapter.STATE_OFF -> "OFF"
  BluetoothAdapter.STATE_ON -> "ON"
  BluetoothAdapter.STATE_TURNING_ON -> "Turning ON"
  BluetoothAdapter.STATE_TURNING_OFF -> "Turning OFF"
  else -> "Unknown ($state)"
}

private fun btScanModeName(mode: Int): String = when (mode) {
  BluetoothAdapter.SCAN_MODE_NONE -> "None"
  BluetoothAdapter.SCAN_MODE_CONNECTABLE -> "Connectable"
  BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE -> "Connectable & Discoverable"
  else -> "Unknown ($mode)"
}

private fun btDeviceTypeName(type: Int): String = when (type) {
  BluetoothDevice.DEVICE_TYPE_CLASSIC -> "Classic"
  BluetoothDevice.DEVICE_TYPE_LE -> "BLE"
  BluetoothDevice.DEVICE_TYPE_DUAL -> "Dual"
  BluetoothDevice.DEVICE_TYPE_UNKNOWN -> "Unknown"
  else -> "Unknown ($type)"
}

private fun btBondStateName(state: Int): String = when (state) {
  BluetoothDevice.BOND_NONE -> "None"
  BluetoothDevice.BOND_BONDING -> "Bonding"
  BluetoothDevice.BOND_BONDED -> "Bonded"
  else -> "Unknown ($state)"
}

private fun btMajorClassName(majorClass: Int): String = when (majorClass) {
  0x0000 -> "Misc"
  0x0100 -> "Computer"
  0x0200 -> "Phone"
  0x0300 -> "Networking"
  0x0400 -> "Audio/Video"
  0x0500 -> "Peripheral"
  0x0600 -> "Imaging"
  0x0700 -> "Wearable"
  0x0800 -> "Toy"
  0x0900 -> "Health"
  0x1F00 -> "Uncategorized"
  else -> "0x${majorClass.toString(16)}"
}
