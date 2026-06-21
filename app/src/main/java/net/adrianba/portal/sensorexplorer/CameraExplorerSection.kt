package net.adrianba.portal.sensorexplorer

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Size
import android.util.SizeF
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner

@Composable
fun CameraExplorerSection() {
  val context = LocalContext.current
  val cameraDetails = remember { enumerateCameraDetails(context) }

  var showPreview by remember { mutableStateOf(false) }
  var permissionDenied by remember { mutableStateOf(false) }
  var cameraError by remember { mutableStateOf<String?>(null) }
  var availableCameras by remember {
    mutableStateOf<List<Pair<String, CameraSelector>>>(emptyList())
  }
  var selectedCamera by remember { mutableStateOf<Pair<String, CameraSelector>?>(null) }

  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(
        stringResource(R.string.section_camera_explorer),
        style = MaterialTheme.typography.headlineSmall,
    )

    Text(
        stringResource(R.string.cameras_found_count, cameraDetails.size),
        style = MaterialTheme.typography.bodyLarge,
    )

    // Camera details cards
    cameraDetails.forEach { camera ->
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
              text = camera.title,
              style = MaterialTheme.typography.bodyLarge,
              fontWeight = FontWeight.Bold,
          )
          camera.properties.forEach { (label, value) ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Text(
                  text = label,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.weight(1f),
              )
              Text(
                  text = value,
                  style = MaterialTheme.typography.bodySmall,
                  modifier = Modifier.weight(1.5f),
              )
            }
          }
        }
      }
    }

    // Live preview
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Button(
          onClick = {
            if (
                ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
            ) {
              showPreview = !showPreview
              if (!showPreview) {
                permissionDenied = false
                cameraError = null
                availableCameras = emptyList()
                selectedCamera = null
              }
            } else {
              permissionDenied = true
            }
          },
          modifier = Modifier.heightIn(min = 52.dp),
      ) {
        Text(
            stringResource(
                if (showPreview) R.string.btn_close_camera else R.string.btn_open_camera
            )
        )
      }

      availableCameras.forEach { option ->
        FilterChip(
            selected = selectedCamera?.first == option.first,
            onClick = { selectedCamera = option },
            label = { Text(option.first) },
            modifier = Modifier.heightIn(min = 52.dp),
        )
      }
    }

    if (permissionDenied) {
      Text(
          stringResource(R.string.error_camera_permission),
          color = MaterialTheme.colorScheme.error,
          style = MaterialTheme.typography.bodySmall,
      )
    }

    if (cameraError != null) {
      Text(
          cameraError!!,
          color = MaterialTheme.colorScheme.error,
          style = MaterialTheme.typography.bodySmall,
      )
    }

    if (showPreview) {
      CameraPreviewView(
          modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(MaterialTheme.shapes.medium),
          selectedSelector = selectedCamera?.second,
          onCamerasEnumerated = { cameras ->
            availableCameras = cameras
            if (selectedCamera == null) selectedCamera = cameras.firstOrNull()
          },
          onError = {
            cameraError = it
            showPreview = false
          },
      )
    }
  }
}

@Composable
private fun CameraPreviewView(
    modifier: Modifier = Modifier,
    selectedSelector: CameraSelector? = null,
    onCamerasEnumerated: (List<Pair<String, CameraSelector>>) -> Unit = {},
    onError: (String) -> Unit = {},
) {
  val context = LocalContext.current
  val lifecycleOwner = context as LifecycleOwner
  val providerRef = remember { arrayOfNulls<ProcessCameraProvider>(1) }
  val pvRef = remember { arrayOfNulls<PreviewView>(1) }

  DisposableEffect(Unit) { onDispose { providerRef[0]?.unbindAll() } }

  LaunchedEffect(selectedSelector) {
    val provider = providerRef[0] ?: return@LaunchedEffect
    val pv = pvRef[0] ?: return@LaunchedEffect
    val selector = selectedSelector ?: return@LaunchedEffect
    val preview = Preview.Builder().build().also { it.setSurfaceProvider(pv.surfaceProvider) }
    try {
      provider.unbindAll()
      provider.bindToLifecycle(lifecycleOwner, selector, preview)
    } catch (e: Exception) {
      onError(e.localizedMessage ?: context.getString(R.string.error_camera_generic))
    }
  }

  AndroidView(
      modifier = modifier,
      factory = { ctx ->
        PreviewView(ctx)
            .apply {
              implementationMode = PreviewView.ImplementationMode.COMPATIBLE
              scaleType = PreviewView.ScaleType.FILL_CENTER
            }
            .also { pv ->
              pvRef[0] = pv
              val future = ProcessCameraProvider.getInstance(ctx)
              future.addListener(
                  {
                    val provider = future.get()
                    providerRef[0] = provider
                    val cameras =
                        provider.availableCameraInfos
                            .mapIndexedNotNull { i, info ->
                              runCatching {
                                    val label =
                                        when (info.lensFacing) {
                                          CameraSelector.LENS_FACING_BACK ->
                                              ctx.getString(R.string.camera_back)
                                          CameraSelector.LENS_FACING_FRONT ->
                                              ctx.getString(R.string.camera_front)
                                          else -> ctx.getString(R.string.camera_other, i + 1)
                                        }
                                    label to
                                        CameraSelector.Builder()
                                            .requireLensFacing(info.lensFacing)
                                            .build()
                                  }
                                  .getOrNull()
                            }
                            .distinctBy { it.first }
                    if (cameras.isEmpty()) {
                      onError(ctx.getString(R.string.error_no_camera))
                      return@addListener
                    }
                    onCamerasEnumerated(cameras)
                  },
                  ContextCompat.getMainExecutor(ctx),
              )
            }
      },
  )
}

private data class CameraInfo(
    val title: String,
    val properties: List<Pair<String, String>>,
)

private fun enumerateCameraDetails(context: Context): List<CameraInfo> {
  val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
  return cameraManager.cameraIdList.map { id ->
    val chars = cameraManager.getCameraCharacteristics(id)
    val facing = when (chars.get(CameraCharacteristics.LENS_FACING)) {
      CameraCharacteristics.LENS_FACING_FRONT -> "Front"
      CameraCharacteristics.LENS_FACING_BACK -> "Back"
      CameraCharacteristics.LENS_FACING_EXTERNAL -> "External"
      else -> "Unknown"
    }

    val configs = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
    val outputSizes = configs?.getOutputSizes(android.graphics.ImageFormat.JPEG)
        ?.sortedByDescending { it.width * it.height }
        ?.take(5)
        ?: emptyList<Size>()

    val sensorSize = chars.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
    val focalLengths = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
    val maxZoom = chars.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM)
    val afModes = chars.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES) ?: intArrayOf()
    val aeModes = chars.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES) ?: intArrayOf()
    val hwLevel = chars.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)

    val props = mutableListOf<Pair<String, String>>()
    props.add("Camera ID" to id)
    props.add("Facing" to facing)
    props.add("Hardware Level" to hwLevelName(hwLevel))
    if (sensorSize != null) {
      props.add("Sensor Size" to "%.1f × %.1f mm".format(sensorSize.width, sensorSize.height))
    }
    if (focalLengths != null && focalLengths.isNotEmpty()) {
      props.add("Focal Lengths" to focalLengths.joinToString(", ") { "%.1f mm".format(it) })
    }
    if (maxZoom != null) {
      props.add("Max Digital Zoom" to "%.1fx".format(maxZoom))
    }
    props.add("AF Modes" to afModes.joinToString(", ") { afModeName(it) })
    props.add("AE Modes" to aeModes.joinToString(", ") { aeModeName(it) })
    if (outputSizes.isNotEmpty()) {
      props.add("JPEG Resolutions" to outputSizes.joinToString(", ") { "${it.width}×${it.height}" })
    }

    CameraInfo(title = "$facing Camera (ID: $id)", properties = props)
  }
}

private fun hwLevelName(level: Int?): String = when (level) {
  CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "Limited"
  CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "Full"
  CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "Legacy"
  CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "Level 3"
  CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL -> "External"
  else -> "Unknown ($level)"
}

private fun afModeName(mode: Int): String = when (mode) {
  0 -> "OFF"
  1 -> "AUTO"
  2 -> "MACRO"
  3 -> "CONTINUOUS_VIDEO"
  4 -> "CONTINUOUS_PICTURE"
  5 -> "EDOF"
  else -> "$mode"
}

private fun aeModeName(mode: Int): String = when (mode) {
  0 -> "OFF"
  1 -> "ON"
  2 -> "ON_AUTO_FLASH"
  3 -> "ON_ALWAYS_FLASH"
  4 -> "ON_AUTO_FLASH_REDEYE"
  5 -> "ON_EXTERNAL_FLASH"
  else -> "$mode"
}
