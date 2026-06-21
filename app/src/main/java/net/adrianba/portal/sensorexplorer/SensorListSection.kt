package net.adrianba.portal.sensorexplorer

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun SensorListSection() {
  val context = LocalContext.current
  val sensorManager = remember {
    context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
  }
  val sensors = remember { sensorManager.getSensorList(Sensor.TYPE_ALL) }
  var monitoredSensor by remember { mutableStateOf<Sensor?>(null) }
  var sensorValues by remember { mutableStateOf("") }

  DisposableEffect(monitoredSensor) {
    val sensor = monitoredSensor
    if (sensor == null) {
      onDispose { }
    } else {
      val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
          sensorValues = event.values.take(4).joinToString(", ") { "%.3f".format(it) }
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
      }
      sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
      onDispose { sensorManager.unregisterListener(listener) }
    }
  }

  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(
        stringResource(R.string.section_sensors),
        style = MaterialTheme.typography.headlineSmall,
    )

    Text(
        stringResource(R.string.sensors_found_count, sensors.size),
        style = MaterialTheme.typography.bodyLarge,
    )

    sensors.forEach { sensor ->
      SensorCard(
          sensor = sensor,
          isMonitored = monitoredSensor == sensor,
          currentValues = if (monitoredSensor == sensor) sensorValues else null,
          onMonitor = {
            monitoredSensor = if (monitoredSensor == sensor) null else sensor
            sensorValues = ""
          },
      )
    }
  }
}

@Composable
private fun SensorCard(
    sensor: Sensor,
    isMonitored: Boolean,
    currentValues: String?,
    onMonitor: () -> Unit,
) {
  Surface(
      color = if (isMonitored) MaterialTheme.colorScheme.primaryContainer
              else MaterialTheme.colorScheme.surfaceVariant,
      shape = MaterialTheme.shapes.medium,
      modifier = Modifier.fillMaxWidth(),
  ) {
    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
              text = sensor.name,
              style = MaterialTheme.typography.bodyLarge,
              fontWeight = FontWeight.Bold,
          )
          Text(
              text = sensorTypeName(sensor.type),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        if (isMonitored) {
          OutlinedButton(onClick = onMonitor, modifier = Modifier.heightIn(min = 52.dp)) {
            Text(stringResource(R.string.btn_stop_monitoring))
          }
        } else {
          Button(onClick = onMonitor, modifier = Modifier.heightIn(min = 52.dp)) {
            Text(stringResource(R.string.btn_monitor))
          }
        }
      }

      Text(
          text = "Vendor: ${sensor.vendor} | v${sensor.version}",
          style = MaterialTheme.typography.bodySmall,
      )
      Text(
          text = "Range: ${sensor.maximumRange} | Resolution: ${sensor.resolution} | Power: ${sensor.power} mA",
          style = MaterialTheme.typography.bodySmall,
      )

      if (currentValues != null) {
        Surface(
            color = MaterialTheme.colorScheme.tertiaryContainer,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
              text = "Values: $currentValues",
              modifier = Modifier.padding(8.dp),
              style = MaterialTheme.typography.bodyMedium,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onTertiaryContainer,
          )
        }
      }
    }
  }
}

private fun sensorTypeName(type: Int): String = when (type) {
  Sensor.TYPE_ACCELEROMETER -> "Accelerometer"
  Sensor.TYPE_MAGNETIC_FIELD -> "Magnetic Field"
  Sensor.TYPE_GYROSCOPE -> "Gyroscope"
  Sensor.TYPE_LIGHT -> "Light"
  Sensor.TYPE_PRESSURE -> "Pressure"
  Sensor.TYPE_PROXIMITY -> "Proximity"
  Sensor.TYPE_GRAVITY -> "Gravity"
  Sensor.TYPE_LINEAR_ACCELERATION -> "Linear Acceleration"
  Sensor.TYPE_ROTATION_VECTOR -> "Rotation Vector"
  Sensor.TYPE_RELATIVE_HUMIDITY -> "Relative Humidity"
  Sensor.TYPE_AMBIENT_TEMPERATURE -> "Ambient Temperature"
  Sensor.TYPE_GAME_ROTATION_VECTOR -> "Game Rotation Vector"
  Sensor.TYPE_SIGNIFICANT_MOTION -> "Significant Motion"
  Sensor.TYPE_STEP_DETECTOR -> "Step Detector"
  Sensor.TYPE_STEP_COUNTER -> "Step Counter"
  Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR -> "Geomagnetic Rotation"
  else -> "Type $type"
}
