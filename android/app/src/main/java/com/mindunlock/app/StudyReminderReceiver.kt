package com.mindunlock.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

class StudyReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "mindunlock-study-reminders"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(channelId, "یادآورهای مطالعه", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val title = intent.getStringExtra("title")?.takeIf { it.isNotBlank() } ?: "زمان مطالعه است"
        val open = Intent(context, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            context, 0, open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("MindUnlock · یادآور مطالعه")
            .setContentText(title)
            .setStyle(NotificationCompat.BigTextStyle().bigText(title))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        manager.notify((intent.getLongExtra("task_id", System.currentTimeMillis())).toInt(), notification)
    }
}
