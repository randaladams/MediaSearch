package com.example.mediasearch.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.SystemClock
import android.text.format.Formatter
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.mediasearch.MediaSearchApp
import com.example.mediasearch.data.Movie
import com.example.mediasearch.ssh.DownloadCancelledException
import com.example.mediasearch.ssh.SshClient
import kotlinx.coroutines.flow.first

/**
 * Downloads the chosen files of one movie in the background, with a progress notification and a
 * Cancel button. Each movie is its own job, so cancelling one doesn't affect the others.
 */
class DownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private val nm = context.getSystemService(NotificationManager::class.java)
    private val progressId = id.hashCode()

    override suspend fun doWork(): Result {
        val title = inputData.getString(KEY_TITLE) ?: return Result.failure()
        val folder = inputData.getString(KEY_FOLDER) ?: return Result.failure()
        val names = inputData.getStringArray(KEY_FILES)?.toList() ?: return Result.failure()
        val sizes = inputData.getLongArray(KEY_SIZES) ?: LongArray(names.size)
        val totalBytes = sizes.sum()

        ensureChannel(applicationContext)
        try {
            setForeground(foregroundInfo(progressNotification(title, 0, totalBytes)))
        } catch (e: Exception) {
            // Android can refuse a foreground service if this starts while the app is in the background.
            // Carry on; the download still runs, just with less protection from being stopped.
        }

        val app = applicationContext as MediaSearchApp
        val settings = app.settingsRepository.settings.first()
        val store = DownloadStore(applicationContext)
        var doneBefore = 0L
        var lastUpdate = 0L

        for ((i, name) in names.withIndex()) {
            val target = try {
                store.create(folder, name)
            } catch (e: Exception) {
                notifyResult(title, "Failed: ${e.message}", success = false)
                return Result.failure()
            }
            try {
                target.stream.use { out ->
                    app.sshClient.downloadMovieFile(settings, folder, name, out) { bytes ->
                        val now = SystemClock.elapsedRealtime()
                        if (now - lastUpdate > 750) {
                            lastUpdate = now
                            nm.notify(progressId, progressNotification(title, doneBefore + bytes, totalBytes))
                        }
                        !isStopped
                    }
                }
                store.complete(target)
            } catch (e: Exception) {
                store.discard(target)
                if (isStopped || e is DownloadCancelledException) return Result.failure()
                notifyResult(title, "Failed: ${e.message}", success = false)
                return Result.failure()
            }
            doneBefore += sizes.getOrElse(i) { 0L }
        }

        notifyResult(title, "Saved to Download/${DownloadStore.LOCATION_NAME}/$folder", success = true)
        return Result.success()
    }

    private fun foregroundInfo(n: Notification): ForegroundInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(progressId, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(progressId, n)
        }

    private fun progressNotification(title: String, done: Long, total: Long): Notification {
        val pct = if (total > 0) ((done * 100) / total).toInt().coerceIn(0, 100) else 0
        val ctx = applicationContext
        return NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloading $title")
            .setContentText(
                if (total > 0) "${Formatter.formatShortFileSize(ctx, done)} of ${Formatter.formatShortFileSize(ctx, total)}"
                else Formatter.formatShortFileSize(ctx, done),
            )
            .setProgress(100, pct, total <= 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Cancel",
                WorkManager.getInstance(ctx).createCancelPendingIntent(id),
            )
            .build()
    }

    private fun notifyResult(title: String, text: String, success: Boolean) {
        val n = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(if (success) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_notify_error)
            .setContentTitle(if (success) "Downloaded $title" else "Download failed: $title")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .build()
        nm.notify(progressId + 1, n)
    }

    companion object {
        private const val CHANNEL_ID = "downloads"
        private const val KEY_TITLE = "title"
        private const val KEY_FOLDER = "folder"
        private const val KEY_FILES = "files"
        private const val KEY_SIZES = "sizes"

        fun ensureChannel(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "Downloads", NotificationManager.IMPORTANCE_LOW).apply {
                        description = "Progress of movie downloads from your Pi"
                    },
                )
            }
        }

        /** Starts a movie download in the background. */
        fun enqueue(context: Context, movie: Movie, files: List<SshClient.RemoteFile>) {
            val request = OneTimeWorkRequestBuilder<DownloadWorker>()
                .setInputData(
                    workDataOf(
                        KEY_TITLE to listOfNotNull(movie.title, movie.year?.let { "($it)" }).joinToString(" "),
                        KEY_FOLDER to movie.folderName,
                        KEY_FILES to files.map { it.name }.toTypedArray(),
                        KEY_SIZES to files.map { it.size }.toLongArray(),
                    ),
                )
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .addTag("download")
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }
}
