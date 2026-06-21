package net.adrianba.portal.sensorexplorer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class WeatherInfo(
    val temperature: Int?,
    val scale: String,
    val condition: String,
    val rawLog: String,
)

@Composable
fun WeatherSection() {
  var weatherInfo by remember { mutableStateOf<WeatherInfo?>(null) }
  var lastUpdated by remember { mutableStateOf("Never") }
  var error by remember { mutableStateOf<String?>(null) }
  val scope = rememberCoroutineScope()

  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(
        "Weather (Aloha Service)",
        style = MaterialTheme.typography.headlineSmall,
    )

    Text(
        "Reads weather data from aloha.WeatherFetcher logcat output",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Button(
        onClick = {
          scope.launch {
            val result = withContext(Dispatchers.IO) { fetchWeatherFromLogcat() }
            if (result != null) {
              weatherInfo = result
              lastUpdated = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
                  .format(java.util.Date())
              error = null
            } else {
              error = "No weather data found in logcat. Weather may not have been fetched yet."
            }
          }
        },
        modifier = Modifier.heightIn(min = 52.dp),
    ) {
      Text("Fetch Weather")
    }

    if (error != null) {
      Surface(
          color = MaterialTheme.colorScheme.errorContainer,
          shape = MaterialTheme.shapes.medium,
          modifier = Modifier.fillMaxWidth(),
      ) {
        Text(
            error!!,
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
      }
    }

    weatherInfo?.let { info ->
      Surface(
          color = MaterialTheme.colorScheme.surfaceVariant,
          shape = MaterialTheme.shapes.medium,
          modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          if (info.temperature != null) {
            Text(
                "${info.temperature}° ${if (info.scale == "FAHRENHEIT") "F" else "C"}",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
          }
          Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text(
                "Condition",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                info.condition.replace("_", " "),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
          }
          Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text(
                "Scale",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                info.scale,
                style = MaterialTheme.typography.bodyMedium,
            )
          }
          Text(
              "Last updated: $lastUpdated",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
  }
}

private fun fetchWeatherFromLogcat(): WeatherInfo? {
  var lastWeather: WeatherInfo? = null
  val pattern = java.util.regex.Pattern.compile(
      "weatherInfo=WeatherInfo\\(temperature=(\\d+),\\s*scaleString=(\\w+),\\s*conditionString=(\\w+)\\)"
  )

  ShellUtils.execLines("logcat", "-d", "-s", "aloha.WeatherFetcher:I") { line ->
    val matcher = pattern.matcher(line)
    if (matcher.find()) {
      lastWeather = WeatherInfo(
          temperature = matcher.group(1)?.toIntOrNull(),
          scale = matcher.group(2) ?: "UNKNOWN",
          condition = matcher.group(3) ?: "unknown",
          rawLog = line,
      )
    }
  }
  return lastWeather
}
