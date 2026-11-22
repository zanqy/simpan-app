package com.za.sidqi.simpan.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.za.sidqi.simpan.R
import com.za.sidqi.simpan.data.AppDatabase
import com.za.sidqi.simpan.data.DownloadRecord
import com.za.sidqi.simpan.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class DownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Unduhan", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            DownloadManager.ACTION_START -> {
                val url = intent.getStringExtra(DownloadManager.EXTRA_URL)
                if (url.isNullOrBlank()) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                startForeground(NOTIFICATION_ID, buildNotification("Menyiapkan unduhan…", 0))
                if (DownloadManager.isBusy()) {
                    DownloadManager.update(DownloadState.Failed(url, "Masih ada unduhan berjalan"))
                    stopSelf()
                    return START_NOT_STICKY
                }
                scope.launch { doWork(url) }
            }

            DownloadManager.ACTION_CANCEL -> {
                YoutubeDL.destroyProcessById(DownloadManager.PROCESS_ID)
                DownloadManager.update(DownloadState.Canceled)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private var stalledRef = AtomicBoolean(false)

    private suspend fun doWork(url: String) {
        stalledRef.set(false)
        var watchdog: Job? = null
        try {
            DownloadManager.update(DownloadState.Fetching(url))
            updateNotification("Mengambil info lagu…", 0)

            val info = YoutubeDL.getInstance().getInfo(url)
            val title = info.title?.takeIf { it.isNotBlank() } ?: url
            val safe = title
                .replace(Regex("[/\\\\:*?\"<>|%\\n\\r]"), "")
                .take(80)
                .trim()
                .ifBlank { "lagu" }

            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "Simpan"
            )
            if (!dir.exists()) dir.mkdirs()
            val outFile = File(dir, "$safe.mp3")

            val attempts = listOf(
                Triple(url, "visionos", "bestaudio"),
                Triple(url, "tv_embedded", "140/bestaudio[ext=m4a]/bestaudio"),
                Triple(url.replace("music.youtube.com", "www.youtube.com"), "", "bestaudio")
            )

            val lastUpdate = AtomicLong(System.currentTimeMillis())
            stalledRef.set(false)
            watchdog = scope.launch {
                while (true) {
                    delay(10_000)
                    if (System.currentTimeMillis() - lastUpdate.get() > 150_000L) {
                        stalledRef.set(true)
                        YoutubeDL.destroyProcessById(DownloadManager.PROCESS_ID)
                        DownloadManager.update(
                            DownloadState.Failed(url, "YouTube tidak merespons. Coba lagi.")
                        )
                        break
                    }
                }
            }

            var succeeded = false
            var lastError: Exception? = null
            for ((attemptUrl, client, format) in attempts) {
                if (outFile.exists()) outFile.delete()
                val request = YoutubeDLRequest(attemptUrl).apply {
                    addOption("-f", format)
                    addOption("-x")
                    addOption("--audio-format", "mp3")
                    addOption("--audio-quality", "0")
                    addOption("--no-playlist")
                    if (client.isNotBlank()) {
                        addOption("--extractor-args", "youtube:player_client=$client")
                    }
                    addOption("--no-update")
                    addOption("-o", outFile.absolutePath)
                }
                lastUpdate.set(System.currentTimeMillis())
                val label = if (client.isNotBlank()) "Mencoba $client…" else "Mencoba ulang…"
                DownloadManager.update(DownloadState.Running(url, title, 0f, 0L, label))
                try {
                    YoutubeDL.getInstance().execute(request, DownloadManager.PROCESS_ID) { progress, eta, line ->
                        lastUpdate.set(System.currentTimeMillis())
                        val clean = if (progress.isFinite() && progress >= 0f) progress.coerceAtMost(100f) else 0f
                        DownloadManager.update(
                            DownloadState.Running(url, title, clean, eta, line.trim().take(120))
                        )
                        updateNotification(title, clean.toInt())
                    }
                    succeeded = true
                    break
                } catch (e: YoutubeDL.CanceledException) {
                    throw e
                } catch (e: Exception) {
                    lastError = e
                    val msg = e.message.orEmpty()
                    if (!msg.contains("403") && !msg.contains("Forbidden")) throw e
                }
            }
            if (!succeeded) throw (lastError ?: Exception("Gagal mengunduh"))
            val size = if (outFile.exists()) outFile.length() else 0L
            if (outFile.exists()) scanMedia(outFile)
            AppDatabase.get(this).dao().upsert(
                DownloadRecord(
                    url = url,
                    title = title,
                    fileName = outFile.name,
                    outputPath = outFile.absolutePath,
                    durationSec = info.duration,
                    sizeBytes = size,
                    status = "DONE",
                    createdAt = System.currentTimeMillis(),
                    finishedAt = System.currentTimeMillis()
                )
            )
            DownloadManager.update(
                DownloadState.Done(url, title, outFile.absolutePath, info.duration, size)
            )
            updateNotification("Tersimpan: $title", 100)
        } catch (e: YoutubeDL.CanceledException) {
            if (!stalledRef.get()) DownloadManager.update(DownloadState.Canceled)
            updateNotification("Dibatalkan", 0)
        } catch (e: Exception) {
            val raw = e.message
                ?.lineSequence()
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                ?.lastOrNull()
                ?: "Gagal mengunduh"
            val message = when {
                raw.contains("403") || raw.contains("Forbidden") -> "YouTube menolak akses (403). Coba lagi nanti."
                raw.contains("Requested format") -> "Format audio tidak tersedia untuk link ini."
                else -> raw.take(160)
            }
            DownloadManager.update(DownloadState.Failed(url, message))
            updateNotification("Gagal: $message", 0)
        }

        watchdog?.cancel()
        withContext(Dispatchers.Main) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
        stopSelf()
    }

    private fun scanMedia(file: File) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, file.name)
                    put(MediaStore.Downloads.MIME_TYPE, "audio/mpeg")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Simpan")
                }
                contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            } else {
                val values = ContentValues().apply {
                    put(MediaStore.Audio.Media.DATA, file.absolutePath)
                    put(MediaStore.Audio.Media.DISPLAY_NAME, file.name)
                    put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg")
                }
                contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
            }
        }
    }

    private fun buildNotification(text: String, progress: Int): android.app.Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val cancel = PendingIntent.getService(
            this,
            1,
            Intent(this, DownloadService::class.java).apply { action = DownloadManager.ACTION_CANCEL },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Simpan")
            .setContentText(text)
            .setContentIntent(open)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Batal", cancel)
            .setProgress(100, progress, progress == 0)
            .setOngoing(progress in 0..99)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun updateNotification(text: String, progress: Int) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(text, progress))
    }

    companion object {
        private const val CHANNEL_ID = "simpan_download"
        private const val NOTIFICATION_ID = 11
    }
}
