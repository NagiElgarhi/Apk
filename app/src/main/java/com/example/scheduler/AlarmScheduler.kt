package com.example.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.model.ScheduledEvent
import com.example.receiver.AlarmReceiver

object AlarmScheduler {
    fun schedule(context: Context, event: ScheduledEvent) {
        if (!event.enabled || event.timeMillis <= System.currentTimeMillis()) return

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("EVENT_ID", event.id)
            putExtra("EVENT_TITLE", event.title)
            putExtra("EVENT_TEXT", event.announcementText.ifEmpty { event.title })
            putExtra("EVENT_FLASH", event.useFlash)
            putExtra("EVENT_VOICE", event.useVoice)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            event.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        event.timeMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        event.timeMillis,
                        pendingIntent
                    )
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    event.timeMillis,
                    pendingIntent
                )
            }
            Log.d("AlarmScheduler", "Alarm scheduled for ${event.title} at ${event.timeMillis}")
        } catch (e: Exception) {
            Log.e("AlarmScheduler", "Error scheduling alarm: ${e.message}")
        }
    }

    fun cancel(context: Context, event: ScheduledEvent) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            event.id.toInt(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            Log.d("AlarmScheduler", "Alarm cancelled for ${event.title}")
        }
    }
}
