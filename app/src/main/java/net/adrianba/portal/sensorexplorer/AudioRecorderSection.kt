package net.adrianba.portal.sensorexplorer

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private enum class AudioState {
  IDLE,
  RECORDING,
  RECORDED,
  PLAYING,
}

private const val TAG = "AudioRecorder"

@Composable
fun AudioRecorderSection() {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var audioState by remember { mutableStateOf(AudioState.IDLE) }
  val outputFile = remember { File(context.cacheDir, "audio_recording.m4a") }
  var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
  var player by remember { mutableStateOf<MediaPlayer?>(null) }
  var permissionDenied by remember { mutableStateOf(false) }
  var fileInfo by remember { mutableStateOf("") }

  DisposableEffect(Unit) {
    onDispose {
      runCatching { recorder?.stop() }
      recorder?.release()
      player?.release()
    }
  }

  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(
        stringResource(R.string.section_audio_recorder),
        style = MaterialTheme.typography.headlineSmall,
    )

    Text(
        "Note: Hey Portal (com.millennium) holds the mic. Run 'adb shell am force-stop com.millennium' first if recording fails.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
    ) {
      Text(
          text =
              stringResource(
                  when (audioState) {
                    AudioState.IDLE -> R.string.audio_status_idle
                    AudioState.RECORDING -> R.string.audio_status_recording
                    AudioState.RECORDED -> R.string.audio_status_recorded
                    AudioState.PLAYING -> R.string.audio_status_playing
                  }
              ),
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
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

    if (fileInfo.isNotBlank()) {
      Text(
          fileInfo,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
      Button(
          onClick = {
            when (audioState) {
              AudioState.IDLE,
              AudioState.RECORDED -> {
                if (
                    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
                        PackageManager.PERMISSION_GRANTED
                ) {
                  permissionDenied = true
                  return@Button
                }
                permissionDenied = false
                outputFile.delete()
                // Request exclusive audio focus to preempt Hey Portal
                val audioManager = context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(
                    null,
                    android.media.AudioManager.STREAM_MUSIC,
                    android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
                )
                // Stop Hey Portal on IO thread, then start recording on main
                audioState = AudioState.RECORDING
                scope.launch {
                  withContext(Dispatchers.IO) {
                    ShellUtils.exec("am", "force-stop", "com.millennium")
                    android.util.Log.i(TAG, "Attempted to stop com.millennium")
                    Thread.sleep(300)
                  }
                  // Back on main thread — start the recorder
                  try {
                    recorder =
                        (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context)
                            else @Suppress("DEPRECATION") MediaRecorder())
                            .apply {
                              setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
                              setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                              setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                              setAudioEncodingBitRate(96000)
                              setAudioSamplingRate(16000)
                              setAudioChannels(1)
                              setOutputFile(outputFile.absolutePath)
                              prepare()
                              start()
                            }
                    android.util.Log.i(TAG, "Recording started to ${outputFile.absolutePath}")
                  } catch (e: Exception) {
                    android.util.Log.e(TAG, "Failed to start recording: ${e.message}", e)
                    recorder?.release()
                    recorder = null
                    fileInfo = "Error: ${e.message}"
                    audioState = AudioState.IDLE
                  }
                }
              }
              AudioState.RECORDING -> {
                try {
                  recorder?.stop()
                  android.util.Log.i(TAG, "Recording stopped, file size: ${outputFile.length()} bytes")
                  fileInfo = "Recorded: ${outputFile.length()} bytes"
                  audioState = AudioState.RECORDED
                } catch (e: RuntimeException) {
                  android.util.Log.e(TAG, "Stop failed: ${e.message}", e)
                  fileInfo = "Stop error: ${e.message}"
                  audioState = AudioState.IDLE
                } finally {
                  recorder?.release()
                  recorder = null
                }
              }
              AudioState.PLAYING -> {}
            }
          },
          enabled = audioState != AudioState.PLAYING,
          modifier = Modifier.heightIn(min = 52.dp),
      ) {
        Text(
            stringResource(
                if (audioState == AudioState.RECORDING) R.string.btn_stop else R.string.btn_record
            )
        )
      }

      Button(
          onClick = {
            when (audioState) {
              AudioState.RECORDED -> {
                try {
                  android.util.Log.i(TAG, "Playing file: ${outputFile.absolutePath}, size: ${outputFile.length()}")
                  player =
                      MediaPlayer().apply {
                        setDataSource(outputFile.absolutePath)
                        prepare()
                        android.util.Log.i(TAG, "MediaPlayer duration: ${duration}ms")
                        fileInfo = "File: ${outputFile.length()} bytes, Duration: ${duration}ms"
                        start()
                        setOnCompletionListener {
                          android.util.Log.i(TAG, "Playback complete")
                          release()
                          player = null
                          audioState = AudioState.RECORDED
                        }
                      }
                  audioState = AudioState.PLAYING
                } catch (e: Exception) {
                  android.util.Log.e(TAG, "Playback failed: ${e.message}", e)
                  fileInfo = "Play error: ${e.message}"
                  player?.release()
                  player = null
                }
              }
              AudioState.PLAYING -> {
                player?.stop()
                player?.release()
                player = null
                audioState = AudioState.RECORDED
              }
              else -> {}
            }
          },
          enabled = audioState == AudioState.RECORDED || audioState == AudioState.PLAYING,
          modifier = Modifier.heightIn(min = 52.dp),
      ) {
        Text(
            stringResource(
                if (audioState == AudioState.PLAYING) R.string.btn_stop else R.string.btn_play
            )
        )
      }

      OutlinedButton(
          onClick = {
            runCatching { recorder?.stop() }
            recorder?.release()
            recorder = null
            player?.stop()
            player?.release()
            player = null
            outputFile.delete()
            audioState = AudioState.IDLE
            permissionDenied = false
          },
          enabled = audioState != AudioState.IDLE,
          modifier = Modifier.heightIn(min = 52.dp),
      ) {
        Text(stringResource(R.string.btn_reset))
      }
    }
  }
}
