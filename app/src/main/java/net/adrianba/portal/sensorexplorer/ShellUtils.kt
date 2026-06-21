package net.adrianba.portal.sensorexplorer

import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/** Safely execute shell commands with proper resource cleanup and timeouts. */
object ShellUtils {

  private const val DEFAULT_TIMEOUT_SECONDS = 5L

  /**
   * Execute a command and return its stdout as a trimmed string.
   * Returns null if the command fails, times out, or throws.
   * All resources (process, streams) are guaranteed to be cleaned up.
   * Stderr is merged via ProcessBuilder to avoid pipe-buffer deadlocks.
   */
  fun exec(vararg command: String, timeoutSeconds: Long = DEFAULT_TIMEOUT_SECONDS): String? {
    var process: Process? = null
    return try {
      process = ProcessBuilder(*command)
          .redirectErrorStream(true)
          .start()
      val reader = BufferedReader(InputStreamReader(process.inputStream))
      val output = reader.readText()
      val completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
      if (!completed) {
        process.destroyForcibly()
        null
      } else {
        output.trim().ifEmpty { null }
      }
    } catch (_: Exception) {
      null
    } finally {
      process?.destroyForcibly()
    }
  }

  /**
   * Execute a shell command (via sh -c) and return stdout as a trimmed string.
   * Returns empty string on failure (convenient for display values).
   */
  fun shellCmd(cmd: String, timeoutSeconds: Long = DEFAULT_TIMEOUT_SECONDS): String {
    return exec("sh", "-c", cmd, timeoutSeconds = timeoutSeconds) ?: ""
  }

  /**
   * Execute a command and process each line of stdout via the callback.
   * Returns true if the command completed successfully.
   * Stderr is merged via ProcessBuilder to avoid pipe-buffer deadlocks.
   */
  fun execLines(
      vararg command: String,
      timeoutSeconds: Long = DEFAULT_TIMEOUT_SECONDS,
      onLine: (String) -> Unit,
  ): Boolean {
    var process: Process? = null
    return try {
      process = ProcessBuilder(*command)
          .redirectErrorStream(true)
          .start()
      val reader = BufferedReader(InputStreamReader(process.inputStream))
      reader.forEachLine(onLine)
      val completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
      if (!completed) {
        process.destroyForcibly()
        false
      } else {
        process.exitValue() == 0
      }
    } catch (_: Exception) {
      false
    } finally {
      process?.destroyForcibly()
    }
  }
}
