package com.example.export

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.R

class ExportForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannel()
        val progress = intent?.getIntExtra(EXTRA_PROGRESS, 0) ?: 0
        val stage = intent?.getStringExtra(EXTRA_STAGE) ?: getString(R.string.export_notification_title)
        val notification = buildNotification(progress, stage)
        startForeground(NOTIFICATION_ID, notification)
        return START_NOT_STICKY
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val ch = NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.export_channel_name),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = getString(R.string.export_channel_desc)
                }
                nm.createNotificationChannel(ch)
            }
        }
    }

    private fun buildNotification(progress: Int, stage: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.export_notification_title))
            .setContentText(stage)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress.coerceIn(0, 100), progress <= 0)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "cube_editor_export_channel"
        private const val NOTIFICATION_ID = 4041
        const val EXTRA_PROGRESS = "extra_progress"
        const val EXTRA_STAGE = "extra_stage"

        fun updateProgress(context: Context, progressPct: Int, stage: String) {
            runCatching {
                val intent = Intent(context, ExportForegroundService::class.java).apply {
                    putExtra(EXTRA_PROGRESS, progressPct)
                    putExtra(EXTRA_STAGE, stage)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }

        fun stop(context: Context) {
            runCatching {
                context.stopService(Intent(context, ExportForegroundService::class.java))
            }
        }
    }
}
