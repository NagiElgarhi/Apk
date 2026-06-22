package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.service.AlertService

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra("EVENT_ID", -1L)
        val title = intent.getStringExtra("EVENT_TITLE") ?: ""
        val text = intent.getStringExtra("EVENT_TEXT") ?: ""
        val useFlash = intent.getBooleanExtra("EVENT_FLASH", true)
        val useVoice = intent.getBooleanExtra("EVENT_VOICE", true)

        Log.d("AlarmReceiver", "Alarm received! ID: $id, Title: $title, UseFlash: $useFlash")

        if (id != -1L) {
            // Start the Alert Service to handle speech synthesis & flashlight flash in background
            val serviceIntent = Intent(context, AlertService::class.java).apply {
                putExtra("EVENT_ID", id)
                putExtra("EVENT_TITLE", title)
                putExtra("EVENT_TEXT", text)
                putExtra("EVENT_FLASH", useFlash)
                putExtra("EVENT_VOICE", useVoice)
            }
            try {
                // On Android 8.0+ we must start foreground service
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            } catch (e: Exception) {
                Log.e("AlarmReceiver", "Failed to start AlertService: ${e.message}")
            }
        }
    }
}
