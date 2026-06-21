package net.adrianba.portal.sensorexplorer

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.net.NetworkInterface

@Composable
fun NetworkSection() {
  val context = LocalContext.current
  val networkInfo = remember { gatherNetworkInfo(context) }

  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(
        stringResource(R.string.section_network),
        style = MaterialTheme.typography.headlineSmall,
    )

    // WiFi info
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
            text = stringResource(R.string.wifi_info),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
        )
        networkInfo.wifiDetails.forEach { (label, value) ->
          Text("$label: $value", style = MaterialTheme.typography.bodySmall)
        }
      }
    }

    // Connectivity
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
            text = stringResource(R.string.connectivity_info),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
        )
        networkInfo.connectivityDetails.forEach { (label, value) ->
          Text("$label: $value", style = MaterialTheme.typography.bodySmall)
        }
      }
    }

    // Network interfaces
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
            text = stringResource(R.string.network_interfaces),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
        )
        networkInfo.interfaces.forEach { iface ->
          Text(iface, style = MaterialTheme.typography.bodySmall)
        }
      }
    }
  }
}

private data class NetworkInfo(
    val wifiDetails: List<Pair<String, String>>,
    val connectivityDetails: List<Pair<String, String>>,
    val interfaces: List<String>,
)

@Suppress("DEPRECATION")
private fun gatherNetworkInfo(context: Context): NetworkInfo {
  val wifiManager =
      context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
  val connManager =
      context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

  // WiFi details
  val wifiInfo = wifiManager.connectionInfo
  val wifiDetails = mutableListOf<Pair<String, String>>()
  wifiDetails.add("WiFi Enabled" to "${wifiManager.isWifiEnabled}")
  if (wifiManager.isWifiEnabled && wifiInfo != null) {
    wifiDetails.add("SSID" to wifiInfo.ssid)
    wifiDetails.add("BSSID" to (wifiInfo.bssid ?: "N/A"))
    wifiDetails.add("Link Speed" to "${wifiInfo.linkSpeed} Mbps")
    wifiDetails.add("Frequency" to "${wifiInfo.frequency} MHz")
    wifiDetails.add("RSSI" to "${wifiInfo.rssi} dBm")
    val ip = wifiInfo.ipAddress
    val ipStr = "%d.%d.%d.%d".format(
        ip and 0xff, (ip shr 8) and 0xff,
        (ip shr 16) and 0xff, (ip shr 24) and 0xff,
    )
    wifiDetails.add("IP Address" to ipStr)
    wifiDetails.add("MAC" to wifiInfo.macAddress)
  }

  // Connectivity
  val connectivityDetails = mutableListOf<Pair<String, String>>()
  val activeNetwork = connManager.activeNetwork
  if (activeNetwork != null) {
    val caps = connManager.getNetworkCapabilities(activeNetwork)
    connectivityDetails.add("Active Network" to "Yes")
    if (caps != null) {
      connectivityDetails.add("WiFi" to "${caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)}")
      connectivityDetails.add(
          "Cellular" to "${caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)}"
      )
      connectivityDetails.add(
          "Ethernet" to "${caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)}"
      )
      connectivityDetails.add(
          "Internet" to "${caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)}"
      )
      connectivityDetails.add(
          "Validated" to "${caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)}"
      )
    }
  } else {
    connectivityDetails.add("Active Network" to "None")
  }

  // Network interfaces — Java API may return null on older AOSP, fall back to /proc
  val interfaces = mutableListOf<String>()
  try {
    val netInterfaces = NetworkInterface.getNetworkInterfaces()
    if (netInterfaces != null) {
      while (netInterfaces.hasMoreElements()) {
        val ni = netInterfaces.nextElement()
        if (!ni.isUp) continue
        val addrs = ni.inetAddresses.toList()
            .map { addr ->
              val host = addr.hostAddress ?: "?"
              val flags = mutableListOf<String>()
              if (addr.isLoopbackAddress) flags.add("loopback")
              if (addr.isLinkLocalAddress) flags.add("link-local")
              if (flags.isEmpty()) host else "$host (${flags.joinToString(", ")})"
            }
        val hwAddr = ni.hardwareAddress?.joinToString(":") { "%02X".format(it) }
        val label = buildString {
          append(ni.displayName)
          if (hwAddr != null) append(" [$hwAddr]")
        }
        interfaces.add("$label: ${addrs.joinToString(", ")}")
      }
    }
  } catch (_: Exception) { }

  // Fallback: read from ip command if Java API returned nothing
  if (interfaces.isEmpty()) {
    try {
      val output = ShellUtils.exec("ip", "addr") ?: ""
      val ifaceRegex = Regex("""^\d+: (\S+):.*state (\S+)""", RegexOption.MULTILINE)
      val inetRegex = Regex("""inet6?\s+(\S+)""")
      val sections = output.split(Regex("""(?=^\d+: )""", RegexOption.MULTILINE))
      for (section in sections) {
        val headerMatch = ifaceRegex.find(section) ?: continue
        val name = headerMatch.groupValues[1]
        val state = headerMatch.groupValues[2]
        val addrs = inetRegex.findAll(section).map { it.groupValues[1] }.toList()
        if (addrs.isNotEmpty()) {
          interfaces.add("$name ($state): ${addrs.joinToString(", ")}")
        }
      }
    } catch (_: Exception) { }
  }

  if (interfaces.isEmpty()) {
    interfaces.add("(no active interfaces)")
  }

  return NetworkInfo(wifiDetails, connectivityDetails, interfaces)
}
