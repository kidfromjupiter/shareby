package com.kidfromjupiter.shareby.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.kidfromjupiter.shareby.R
import com.kidfromjupiter.shareby.model.TransferDirection

class TransferNotificationManager(private val context: Context) {

    fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.transfer_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.transfer_channel_description)
        }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    fun showProgress(
        payloadId: Long,
        fileName: String,
        direction: TransferDirection,
        transferredBytes: Long,
        totalBytes: Long,
    ) {
        if (!canNotify()) {
            return
        }

        val notificationId = notificationId(payloadId)
        val title = when (direction) {
            TransferDirection.OUTGOING -> context.getString(R.string.notification_sending_title)
            TransferDirection.INCOMING -> context.getString(R.string.notification_receiving_title)
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(
                if (direction == TransferDirection.INCOMING) android.R.drawable.stat_sys_download
                else android.R.drawable.stat_sys_upload
            )
            .setContentTitle(title)
            .setContentText(fileName)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        val hasProgress = totalBytes > 0
        if (hasProgress) {
            val progress = ((transferredBytes.coerceAtLeast(0) * 100L) / totalBytes).toInt().coerceIn(0, 100)
            builder.setProgress(100, progress, false)
                .setSubText(context.getString(R.string.notification_progress_percent, progress))
        } else {
            builder.setProgress(0, 0, true)
        }

        NotificationManagerCompat.from(context).notify(notificationId, builder.build())
    }

    fun showCompleted(payloadId: Long, fileName: String, direction: TransferDirection) {
        if (!canNotify()) {
            return
        }

        val title = when (direction) {
            TransferDirection.OUTGOING -> context.getString(R.string.notification_send_complete)
            TransferDirection.INCOMING -> context.getString(R.string.notification_receive_complete)
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(
                if (direction == TransferDirection.INCOMING) android.R.drawable.stat_sys_download_done
                else android.R.drawable.stat_sys_upload_done
            )
            .setContentTitle(title)
            .setContentText(fileName)
            .setAutoCancel(true)
            .setOngoing(false)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        NotificationManagerCompat.from(context).notify(notificationId(payloadId), builder.build())
        Handler(Looper.getMainLooper()).postDelayed({
            NotificationManagerCompat.from(context).cancel(notificationId(payloadId))
        }, AUTO_CANCEL_DELAY_MS)
    }

    fun showFailed(payloadId: Long, fileName: String, direction: TransferDirection) {
        if (!canNotify()) {
            return
        }

        val title = when (direction) {
            TransferDirection.OUTGOING -> context.getString(R.string.notification_send_failed)
            TransferDirection.INCOMING -> context.getString(R.string.notification_receive_failed)
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(title)
            .setContentText(fileName)
            .setAutoCancel(true)
            .setOngoing(false)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        NotificationManagerCompat.from(context).notify(notificationId(payloadId), builder.build())
    }

    private fun notificationId(payloadId: Long): Int {
        val mixed = payloadId xor (payloadId ushr 32)
        return mixed.toInt().let { if (it == Int.MIN_VALUE) 0 else kotlin.math.abs(it) }
    }

    private fun canNotify(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true
        }
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        const val CHANNEL_ID = "transfer_progress"
        private const val AUTO_CANCEL_DELAY_MS = 3_000L
    }
}
