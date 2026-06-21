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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PlatformState(
    val alohaState: Int?,
    val privacyMode: Int?,
    val shutterState: Int?,
    val callState: Int?,
    val callIncomingId: String?,
    val callConnectedId: String?,
    val localeLanguage: String?,
    val accessMode: String?,
    val retailIdleState: Int?,
    val userSetupComplete: Int?,
)

@Composable
fun PlatformStateSection() {
  var platformState by remember { mutableStateOf<PlatformState?>(null) }
  var lastUpdated by remember { mutableStateOf("Never") }
  var error by remember { mutableStateOf<String?>(null) }
  var rawBundle by remember { mutableStateOf<String?>(null) }
  val scope = rememberCoroutineScope()

  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(
        "Platform State (Aloha)",
        style = MaterialTheme.typography.headlineSmall,
    )

    Text(
        "Reads device state bundle from platform_state_service via dumpsys",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Button(
        onClick = {
          scope.launch {
            val result = withContext(Dispatchers.IO) { fetchPlatformState() }
            if (result != null) {
              platformState = result.first
              rawBundle = result.second
              lastUpdated = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
                  .format(java.util.Date())
              error = null
            } else {
              error = "Failed to read platform_state_service. May require shell access."
            }
          }
        },
        modifier = Modifier.heightIn(min = 52.dp),
    ) {
      Text("Refresh State")
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

    platformState?.let { state ->
      Surface(
          color = MaterialTheme.colorScheme.surfaceVariant,
          shape = MaterialTheme.shapes.medium,
          modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          val alohaLabel = when (state.alohaState) {
            0 -> "STANDBY"
            1 -> "AMBIENT"
            2 -> "ACTIVE"
            else -> "UNKNOWN (${state.alohaState})"
          }
          StateRow("Aloha State", alohaLabel)
          StateRow("Privacy Mode", if (state.privacyMode == 0) "Off" else "On (${state.privacyMode})")
          StateRow("Shutter State", when (state.shutterState) {
            -1 -> "Open"
            1 -> "Closed"
            else -> "${state.shutterState}"
          })
          StateRow("Call State", when (state.callState) {
            0 -> "Idle"
            1 -> "Ringing"
            2 -> "In Call"
            else -> "${state.callState}"
          })
          if (!state.callIncomingId.isNullOrBlank()) {
            StateRow("Incoming Call", state.callIncomingId)
          }
          if (!state.callConnectedId.isNullOrBlank()) {
            StateRow("Connected Call", state.callConnectedId)
          }
          StateRow("Locale", state.localeLanguage ?: "Unknown")
          StateRow("Access Mode", state.accessMode ?: "Unknown")
          StateRow("Retail Idle", "${state.retailIdleState ?: "N/A"}")
          StateRow("User Setup", if (state.userSetupComplete == 1) "Complete" else "Incomplete")

          Text(
              "Last updated: $lastUpdated",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(top = 4.dp),
          )
        }
      }

      // Raw bundle display
      rawBundle?.let { raw ->
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        ) {
          Text(
              raw,
              modifier = Modifier.padding(8.dp),
              style = MaterialTheme.typography.bodySmall,
              fontFamily = FontFamily.Monospace,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
  }
}

@Composable
private fun StateRow(label: String, value: String) {
  Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Text(
        label,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        value,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
    )
  }
}

private fun fetchPlatformState(): Pair<PlatformState, String>? {
  val output = ShellUtils.exec("dumpsys", "platform_state_service") ?: return null

  // Parse the mBundle line
  val bundlePattern = java.util.regex.Pattern.compile(
      "mBundle:PersistableBundle\\[\\{(.+?)\\}\\]"
  )
  val matcher = bundlePattern.matcher(output)
  if (!matcher.find()) return null

  val bundleStr = matcher.group(1) ?: return null
  val fields = bundleStr.split(", ").associate { field ->
    val parts = field.split("=", limit = 2)
    if (parts.size == 2) parts[0] to parts[1] else parts[0] to ""
  }

  val state = PlatformState(
      alohaState = fields["aloha_state"]?.toIntOrNull(),
      privacyMode = fields["privacy_mode"]?.toIntOrNull(),
      shutterState = fields["shutter_state"]?.toIntOrNull(),
      callState = fields["call_state"]?.toIntOrNull(),
      callIncomingId = fields["call_incoming_call_id"],
      callConnectedId = fields["call_connected_call_id"],
      localeLanguage = fields["locale_language"],
      accessMode = fields["access_mode"],
      retailIdleState = fields["retail_idle_state"]?.toIntOrNull(),
      userSetupComplete = fields["user_setup_complete"]?.toIntOrNull(),
  )

  return Pair(state, bundleStr)
}
