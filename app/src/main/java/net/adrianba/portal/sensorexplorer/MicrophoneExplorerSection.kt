package net.adrianba.portal.sensorexplorer

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MicrophoneInfo
import android.media.MediaRecorder
import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.io.File
import kotlinx.coroutines.delay

@Composable
fun MicrophoneExplorerSection() {
  val context = LocalContext.current
  val audioManager = remember {
    context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
  }
  val inputDevices = remember {
    audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS).toList()
  }
  val outputDevices = remember {
    audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList()
  }

  var isMonitoring by remember { mutableStateOf(false) }
  var audioLevel by remember { mutableFloatStateOf(0f) }
  var permissionDenied by remember { mutableStateOf(false) }
  var recorder by remember { mutableStateOf<MediaRecorder?>(null) }

  val animatedLevel by animateFloatAsState(targetValue = audioLevel, label = "audioLevel")

  DisposableEffect(Unit) {
    onDispose {
      runCatching { recorder?.stop() }
      recorder?.release()
    }
  }

  // Poll audio level while monitoring
  LaunchedEffect(isMonitoring) {
    while (isMonitoring) {
      val maxAmplitude = recorder?.maxAmplitude ?: 0
      // Normalize: max amplitude for MediaRecorder is ~32767
      audioLevel = (maxAmplitude / 32767f).coerceIn(0f, 1f)
      delay(100)
    }
  }

  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(
        stringResource(R.string.section_microphone),
        style = MaterialTheme.typography.headlineSmall,
    )

    // Audio input devices
    Text(
        stringResource(R.string.audio_inputs_count, inputDevices.size),
        style = MaterialTheme.typography.bodyLarge,
    )

    inputDevices.forEach { device ->
      AudioDeviceCard(device, isInput = true)
    }

    // Detailed microphone info (API 28+)
    val microphones = remember {
      runCatching { audioManager.microphones }.getOrDefault(emptyList())
    }
    if (microphones.isNotEmpty()) {
      Text(
          stringResource(R.string.microphone_details),
          style = MaterialTheme.typography.bodyLarge,
          fontWeight = FontWeight.Bold,
      )
      microphones.forEach { mic ->
        MicrophoneInfoCard(mic)
      }
    }

    // Audio output devices
    Text(
        stringResource(R.string.audio_outputs_count, outputDevices.size),
        style = MaterialTheme.typography.bodyLarge,
    )

    outputDevices.forEach { device ->
      AudioDeviceCard(device, isInput = false)
    }

    // Audio level meter
    Text(
        stringResource(R.string.audio_level_meter),
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.Bold,
    )

    if (isMonitoring) {
      LinearProgressIndicator(
          progress = { animatedLevel },
          modifier = Modifier.fillMaxWidth().height(24.dp),
          trackColor = MaterialTheme.colorScheme.surfaceVariant,
      )
      Text(
          stringResource(R.string.audio_level_value, (animatedLevel * 100).toInt()),
          style = MaterialTheme.typography.bodyMedium,
      )
    }

    if (permissionDenied) {
      Text(
          stringResource(R.string.error_audio_permission),
          color = MaterialTheme.colorScheme.error,
          style = MaterialTheme.typography.bodySmall,
      )
    }

    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
      if (isMonitoring) {
        OutlinedButton(
            onClick = {
              runCatching { recorder?.stop() }
              recorder?.release()
              recorder = null
              isMonitoring = false
              audioLevel = 0f
            },
            modifier = Modifier.heightIn(min = 52.dp),
        ) {
          Text(stringResource(R.string.btn_stop_monitoring))
        }
      } else {
        Button(
            onClick = {
              if (
                  ContextCompat.checkSelfPermission(
                      context,
                      Manifest.permission.RECORD_AUDIO,
                  ) != PackageManager.PERMISSION_GRANTED
              ) {
                permissionDenied = true
                return@Button
              }
              permissionDenied = false
              val outputFile = File(context.cacheDir, "mic_monitor.3gp")
              recorder =
                  (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context)
                  else @Suppress("DEPRECATION") MediaRecorder())
                      .apply {
                        setAudioSource(MediaRecorder.AudioSource.MIC)
                        setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                        setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                        setOutputFile(outputFile.absolutePath)
                        prepare()
                        start()
                      }
              isMonitoring = true
            },
            modifier = Modifier.heightIn(min = 52.dp),
        ) {
          Text(stringResource(R.string.btn_start_level_meter))
        }
      }
    }
  }
}

@Composable
private fun MicrophoneInfoCard(mic: MicrophoneInfo) {
  Surface(
      color = MaterialTheme.colorScheme.primaryContainer,
      shape = MaterialTheme.shapes.medium,
      modifier = Modifier.fillMaxWidth(),
  ) {
    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text(
          text = mic.description.ifBlank { "Microphone (ID: ${mic.id})" },
          style = MaterialTheme.typography.bodyLarge,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onPrimaryContainer,
      )
      val details = mutableListOf<Pair<String, String>>()
      details.add("ID" to "${mic.id}")
      details.add("Type" to micTypeName(mic.type))
      details.add("Address" to mic.address)
      details.add("Location" to micLocationName(mic.location))
      details.add("Group" to "${mic.group}")
      details.add("Index in Group" to "${mic.indexInTheGroup}")
      val pos = mic.position
      details.add("Position" to formatCoord(pos.x, pos.y, pos.z))
      val orient = mic.orientation
      details.add("Orientation" to formatCoord(orient.x, orient.y, orient.z))
      details.add("Directionality" to micDirectionalityName(mic.directionality))
      val freqResponse = mic.frequencyResponse
      if (freqResponse.isNotEmpty()) {
        details.add("Freq Range" to "%.0f – %.0f Hz".format(freqResponse.first().first, freqResponse.last().first))
      }
      details.add("Sensitivity" to formatFloat(mic.sensitivity, "dBFS"))
      details.add("Max SPL" to formatFloat(mic.maxSpl, "dB"))
      details.add("Min SPL" to formatFloat(mic.minSpl, "dB"))
      val channelMapping = mic.channelMapping
      if (channelMapping.isNotEmpty()) {
        details.add("Channel Mapping" to channelMapping.joinToString(", ") { "ch${it.first}→${channelMappingName(it.second)}" })
      }

      details.forEach { (label, value) ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
          Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
      }
    }
  }
}

@Composable
private fun AudioDeviceCard(device: AudioDeviceInfo, isInput: Boolean) {
  Surface(
      color = MaterialTheme.colorScheme.surfaceVariant,
      shape = MaterialTheme.shapes.medium,
      modifier = Modifier.fillMaxWidth(),
  ) {
    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text(
          text = device.productName.toString().ifBlank { audioDeviceTypeName(device.type) },
          style = MaterialTheme.typography.bodyLarge,
          fontWeight = FontWeight.Bold,
      )
      Text(
          text = "Type: ${audioDeviceTypeName(device.type)}",
          style = MaterialTheme.typography.bodySmall,
      )
      Text(
          text = "ID: ${device.id}",
          style = MaterialTheme.typography.bodySmall,
      )
      if (device.isSource) {
        Text(text = "Role: Source (input)", style = MaterialTheme.typography.bodySmall)
      } else {
        Text(text = "Role: Sink (output)", style = MaterialTheme.typography.bodySmall)
      }
      val sampleRates = device.sampleRates
      if (sampleRates.isNotEmpty()) {
        Text(
            text = "Sample Rates: ${sampleRates.joinToString(", ")} Hz",
            style = MaterialTheme.typography.bodySmall,
        )
      } else {
        Text(text = "Sample Rates: (any)", style = MaterialTheme.typography.bodySmall)
      }
      val channelCounts = device.channelCounts
      if (channelCounts.isNotEmpty()) {
        Text(
            text = "Channel Counts: ${channelCounts.joinToString(", ")}",
            style = MaterialTheme.typography.bodySmall,
        )
      } else {
        Text(text = "Channel Counts: (any)", style = MaterialTheme.typography.bodySmall)
      }
      val channelMasks = device.channelMasks
      if (channelMasks.isNotEmpty()) {
        Text(
            text = "Channel Masks: ${channelMasks.joinToString(", ") { "0x%X".format(it) }}",
            style = MaterialTheme.typography.bodySmall,
        )
      }
      val channelIndexMasks = device.channelIndexMasks
      if (channelIndexMasks.isNotEmpty()) {
        Text(
            text = "Channel Index Masks: ${channelIndexMasks.joinToString(", ") { "0x%X".format(it) }} (${channelIndexMasks.map { Integer.bitCount(it) }.joinToString(", ")} ch)",
            style = MaterialTheme.typography.bodySmall,
        )
      }
      val encodings = device.encodings
      if (encodings.isNotEmpty()) {
        Text(
            text = "Encodings: ${encodings.joinToString(", ") { audioEncodingName(it) }}",
            style = MaterialTheme.typography.bodySmall,
        )
      }
    }
  }
}

private fun formatFloat(value: Float, unit: String): String =
    if (value.isInfinite() || value.isNaN() || value <= -Float.MAX_VALUE / 2) "N/A" else "%.1f %s".format(value, unit)

private fun formatCoord(x: Float, y: Float, z: Float): String {
  if (x.isInfinite() || x.isNaN() || x <= -Float.MAX_VALUE / 2) return "N/A"
  return "(%.2f, %.2f, %.2f)".format(x, y, z)
}

private fun audioDeviceTypeName(type: Int): String = when (type) {
  AudioDeviceInfo.TYPE_BUILTIN_MIC -> "Built-in Mic"
  AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Built-in Speaker"
  AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "Built-in Earpiece"
  AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth SCO"
  AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth A2DP"
  AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired Headset"
  AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Wired Headphones"
  AudioDeviceInfo.TYPE_USB_DEVICE -> "USB Device"
  AudioDeviceInfo.TYPE_USB_ACCESSORY -> "USB Accessory"
  AudioDeviceInfo.TYPE_USB_HEADSET -> "USB Headset"
  AudioDeviceInfo.TYPE_HDMI -> "HDMI"
  AudioDeviceInfo.TYPE_HDMI_ARC -> "HDMI ARC"
  AudioDeviceInfo.TYPE_LINE_ANALOG -> "Line Analog"
  AudioDeviceInfo.TYPE_LINE_DIGITAL -> "Line Digital"
  AudioDeviceInfo.TYPE_TELEPHONY -> "Telephony"
  AudioDeviceInfo.TYPE_AUX_LINE -> "Aux Line"
  AudioDeviceInfo.TYPE_IP -> "IP"
  AudioDeviceInfo.TYPE_BUS -> "Bus"
  else -> "Unknown ($type)"
}

private fun audioEncodingName(encoding: Int): String = when (encoding) {
  android.media.AudioFormat.ENCODING_PCM_8BIT -> "PCM 8-bit"
  android.media.AudioFormat.ENCODING_PCM_16BIT -> "PCM 16-bit"
  android.media.AudioFormat.ENCODING_PCM_FLOAT -> "PCM Float"
  android.media.AudioFormat.ENCODING_AC3 -> "AC3"
  android.media.AudioFormat.ENCODING_E_AC3 -> "E-AC3"
  android.media.AudioFormat.ENCODING_DTS -> "DTS"
  android.media.AudioFormat.ENCODING_DTS_HD -> "DTS-HD"
  android.media.AudioFormat.ENCODING_AAC_LC -> "AAC-LC"
  android.media.AudioFormat.ENCODING_AAC_HE_V1 -> "AAC-HE v1"
  android.media.AudioFormat.ENCODING_AAC_HE_V2 -> "AAC-HE v2"
  android.media.AudioFormat.ENCODING_MP3 -> "MP3"
  else -> "Encoding($encoding)"
}

private fun micTypeName(type: Int): String = when (type) {
  0 -> "Unknown"
  1 -> "Built-in"
  2 -> "Peripheral"
  else -> "Type($type)"
}

private fun micLocationName(location: Int): String = when (location) {
  0 -> "Unknown"
  1 -> "Main Body"
  2 -> "Main Body (Movable)"
  3 -> "Peripheral"
  else -> "Location($location)"
}

private fun micDirectionalityName(dir: Int): String = when (dir) {
  0 -> "Unknown"
  1 -> "Omnidirectional"
  2 -> "Bi-directional"
  3 -> "Cardioid"
  4 -> "Hyper-cardioid"
  5 -> "Super-cardioid"
  6 -> "Unidirectional"
  else -> "Dir($dir)"
}

private fun channelMappingName(mapping: Int): String = when (mapping) {
  1 -> "Direct"
  2 -> "Processed"
  else -> "Mapping($mapping)"
}
