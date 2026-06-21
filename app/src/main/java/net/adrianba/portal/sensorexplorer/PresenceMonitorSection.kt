package net.adrianba.portal.sensorexplorer

import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.adrianba.portal.sensorexplorer.ui.theme.OnMetaBlue
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

data class PresenceEvent(
    val timestamp: String,
    val state: String,
    val rawMessage: String,
)

@Composable
fun PresenceMonitorSection() {
  var isMonitoring by remember { mutableStateOf(false) }
  var currentState by remember { mutableStateOf("Unknown") }
  var hasReadLogsPermission by remember { mutableStateOf<Boolean?>(null) }
  val events = remember { mutableStateListOf<PresenceEvent>() }
  var monitorThread by remember { mutableStateOf<Thread?>(null) }
  var logcatProcess by remember { mutableStateOf<Process?>(null) }
  val listState = rememberLazyListState()

  // Check READ_LOGS on first composition
  if (hasReadLogsPermission == null) {
    hasReadLogsPermission = checkReadLogsPermission()
  }

  DisposableEffect(Unit) {
    onDispose {
      logcatProcess?.destroyForcibly()
      monitorThread?.interrupt()
    }
  }

  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(
        "User Presence Monitor",
        style = MaterialTheme.typography.headlineSmall,
    )

    if (hasReadLogsPermission == false) {
      Surface(
          color = MaterialTheme.colorScheme.errorContainer,
          shape = MaterialTheme.shapes.medium,
          modifier = Modifier.fillMaxWidth(),
      ) {
        Column(modifier = Modifier.padding(12.dp)) {
          Text(
              "READ_LOGS Permission Required",
              style = MaterialTheme.typography.bodyMedium,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onErrorContainer,
          )
          Text(
              "Grant via ADB:\nadb shell pm grant net.adrianba.portal.sensorexplorer android.permission.READ_LOGS",
              style = MaterialTheme.typography.bodySmall,
              fontFamily = FontFamily.Monospace,
              color = MaterialTheme.colorScheme.onErrorContainer,
          )
          OutlinedButton(
              onClick = { hasReadLogsPermission = checkReadLogsPermission() },
              modifier = Modifier.padding(top = 8.dp).heightIn(min = 52.dp),
          ) {
            Text("Re-check Permission")
          }
        }
      }
    } else {
      // Current state display
      Surface(
          color = when (currentState) {
            "ACTIVE" -> Color(0xFF1B5E20)   // dark green
            "AMBIENT" -> Color(0xFF33691E)  // olive green
            "STANDBY" -> Color(0xFF424242)  // grey
            else -> MaterialTheme.colorScheme.surfaceVariant
          },
          shape = MaterialTheme.shapes.medium,
          modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Text(
              "Current Presence",
              style = MaterialTheme.typography.bodySmall,
              color = OnMetaBlue.copy(alpha = 0.7f),
          )
          Text(
              currentState,
              style = MaterialTheme.typography.headlineMedium,
              fontWeight = FontWeight.Bold,
              color = OnMetaBlue,
          )
          Text(
              when (currentState) {
                "ACTIVE" -> "User detected in front of Portal"
                "AMBIENT" -> "Motion/presence detected nearby"
                "STANDBY" -> "No user detected — device idle"
                else -> "Monitoring not started"
              },
              style = MaterialTheme.typography.bodySmall,
              color = OnMetaBlue.copy(alpha = 0.7f),
          )
        }
      }

      // Controls
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (isMonitoring) {
          OutlinedButton(
              onClick = {
                logcatProcess?.destroy()
                monitorThread?.interrupt()
                isMonitoring = false
              },
              modifier = Modifier.heightIn(min = 52.dp),
          ) {
            Text("Stop Monitoring")
          }
        } else {
          Button(
              onClick = {
                isMonitoring = true
                val mainHandler = Handler(Looper.getMainLooper())
                // Run seed + logcat stream setup on a background thread
                val thread = Thread {
                  try {
                    // Seed current state from logcat history (blocking I/O)
                    val seedEvents = mutableListOf<PresenceEvent>()
                    var lastState: String? = null
                    ShellUtils.execLines("logcat", "-d", "-s", "aloha.UserPresenceManager:I") { line ->
                      val event = parsePresenceLine(line)
                      if (event != null) {
                        lastState = event.state
                        seedEvents.add(0, event)
                      }
                    }
                    val trimmedEvents = seedEvents.take(200)
                    mainHandler.post {
                      events.addAll(trimmedEvents)
                      while (events.size > 200) events.removeAt(events.size - 1)
                      if (lastState != null) currentState = lastState!!
                    }

                    // Start live monitoring
                    val proc = startLogcatStream()
                    mainHandler.post { logcatProcess = proc }
                    if (proc != null) {
                      val reader = BufferedReader(InputStreamReader(proc.inputStream))
                      var line: String?
                      while (Thread.currentThread().isInterrupted.not()) {
                        line = reader.readLine() ?: break
                        val event = parsePresenceLine(line)
                        if (event != null) {
                          mainHandler.post {
                            events.add(0, event)
                            while (events.size > 200) events.removeAt(events.size - 1)
                            currentState = event.state
                          }
                          android.util.Log.i("SensorExplorer", "Presence: ${event.state} at ${event.timestamp}")
                        }
                      }
                    }
                  } catch (_: InterruptedException) {
                    // stopped
                  } catch (_: Exception) {
                    // stream closed
                  }
                }
                thread.isDaemon = true
                thread.start()
                monitorThread = thread
              },
              modifier = Modifier.heightIn(min = 52.dp),
          ) {
            Text("Start Monitoring")
          }
        }
      }

      // Event log
      if (events.isNotEmpty()) {
        Text(
            "Presence Events (${events.size})",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
        )
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp),
        ) {
          LazyColumn(
              state = listState,
              modifier = Modifier.padding(8.dp),
              verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            items(events) { event ->
              Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                  verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(
                    event.timestamp,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
                Surface(
                    color = when (event.state) {
                      "ACTIVE" -> Color(0xFF4CAF50)
                      "AMBIENT" -> Color(0xFF8BC34A)
                      "STANDBY" -> Color(0xFF757575)
                      else -> Color(0xFF9E9E9E)
                    },
                    shape = MaterialTheme.shapes.small,
                ) {
                  Text(
                      event.state,
                      modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                      style = MaterialTheme.typography.bodySmall,
                      fontWeight = FontWeight.Bold,
                      color = OnMetaBlue,
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}

private fun checkReadLogsPermission(): Boolean {
  return ShellUtils.exec("logcat", "-d", "-s", "aloha.UserPresenceManager:I", "-t", "1") != null
}

private fun startLogcatStream(): Process? {
  return try {
    ProcessBuilder("logcat", "-s", "aloha.UserPresenceManager:I")
        .redirectErrorStream(true)
        .start()
  } catch (_: Exception) {
    null
  }
}

// Match: "COMMAND_DEVICE_PRESENCE_MODE_CHANGE [STATE]"
private val MODE_CHANGE_PATTERN: Pattern =
    Pattern.compile("""COMMAND_DEVICE_PRESENCE_MODE_CHANGE \[(\w+)]""")

// Match timestamp at start of logcat line: "06-19 22:03:56.706"
private val TIMESTAMP_PATTERN: Pattern =
    Pattern.compile("""^(\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3})""")

private fun parsePresenceLine(line: String): PresenceEvent? {
  val modeMatcher = MODE_CHANGE_PATTERN.matcher(line)
  if (!modeMatcher.find()) return null
  val state = modeMatcher.group(1) ?: return null

  val tsMatcher = TIMESTAMP_PATTERN.matcher(line)
  val timestamp = if (tsMatcher.find()) tsMatcher.group(1) ?: nowTimestamp() else nowTimestamp()

  return PresenceEvent(
      timestamp = timestamp,
      state = state,
      rawMessage = line,
  )
}

private fun nowTimestamp(): String =
    SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
