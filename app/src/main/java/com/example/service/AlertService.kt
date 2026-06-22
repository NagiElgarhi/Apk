package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Locale

class AlertService : Service() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main + job)

    private var tts: TextToSpeech? = null
    private var isBlinking = false
    private var isTtsInitialized = false

    private var eventId: Long = -1L
    private var eventTitle: String = ""
    private var eventText: String = ""
    private var useFlash: Boolean = true
    private var useVoice: Boolean = true

    companion object {
        const val CHANNEL_ID = "ALERT_SERVICE_CHANNEL"
        const val NOTIFICATION_ID = 9999

        private val _activeAlertEventId = MutableStateFlow<Long?>(null)
        val activeAlertEventId: StateFlow<Long?> = _activeAlertEventId

        private val _activeAlertTitle = MutableStateFlow<String?>(null)
        val activeAlertTitle: StateFlow<String?> = _activeAlertTitle

        fun stopActiveAlert(context: Context) {
            val stopIntent = Intent(context, AlertService::class.java).apply {
                action = "STOP_ALERT"
            }
            context.startService(stopIntent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP_ALERT") {
            Log.d("AlertService", "STOP_ALERT action triggered.")
            stopSelfAndClear()
            return START_NOT_STICKY
        }

        eventId = intent?.getLongExtra("EVENT_ID", -1L) ?: -1L
        eventTitle = intent?.getStringExtra("EVENT_TITLE") ?: "موعد مجدول"
        eventText = intent?.getStringExtra("EVENT_TEXT") ?: "حان الآن موعد التنبيه المجدول"
        useFlash = intent?.getBooleanExtra("EVENT_FLASH", true) ?: true
        useVoice = intent?.getBooleanExtra("EVENT_VOICE", true) ?: true

        if (eventId == -1L) {
            stopSelf()
            return START_NOT_STICKY
        }

        _activeAlertEventId.value = eventId
        _activeAlertTitle.value = eventTitle

        // Show foreground notification
        startForegroundServiceNotification()

        // 1. Voice alert via Text-To-Speech (TTS)
        if (useVoice) {
            initTtsAndAnnounce()
        }

        // 2. Flashlight alert (blink camera flash)
        if (useFlash) {
            startFlashBlinking()
        }

        // 3. Auto stop safety timer (stop after 45 seconds to prevent battery drain & overheating)
        scope.launch {
            delay(45000)
            Log.d("AlertService", "Auto-stopping alert after safety timeout")
            stopSelfAndClear()
        }

        return START_STICKY
    }

    private fun startForegroundServiceNotification() {
        val notificationIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, AlertService::class.java).apply {
            action = "STOP_ALERT"
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("تنبيه موعد مجدول جارٍ الآن")
            .setContentText(eventTitle)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setFullScreenIntent(pendingIntent, true)
            .setContentIntent(pendingIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "إيقاف التنبيه",
                stopPendingIntent
            )
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun initTtsAndAnnounce() {
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isTtsInitialized = true
                val arabicLocale = Locale("ar")
                val result = tts?.setLanguage(arabicLocale)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e("AlertService", "Arabic language is not supported or missing content. Falling back to default locale.")
                    tts?.language = Locale.getDefault()
                }
                
                // Let's loop the TTS announcement every 6 seconds inside are coroutine
                scope.launch {
                    val announcement = "تنبيه. حان اللّص مَوعد: $eventText"
                    while (isTtsInitialized) {
                        Log.d("AlertService", "TTS speaking: $announcement")
                        tts?.speak(announcement, TextToSpeech.QUEUE_FLUSH, null, "EVENT_ALERT_TTS")
                        delay(6000)
                    }
                }
            } else {
                Log.e("AlertService", "TTS Initialization failed!")
            }
        }
    }

    private fun startFlashBlinking() {
        isBlinking = true
        scope.launch(Dispatchers.IO) {
            val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
            var cameraId: String? = null
            try {
                cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                    val characteristics = cameraManager.getCameraCharacteristics(id)
                    characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                }
            } catch (e: Exception) {
                Log.e("AlertService", "Error finding camera with flash: ${e.message}")
            }

            if (cameraId != null) {
                var flashState = false
                while (isBlinking) {
                    try {
                        cameraManager.setTorchMode(cameraId, flashState)
                        flashState = !flashState
                    } catch (e: Exception) {
                        Log.e("AlertService", "Error setting torch mode: ${e.message}")
                    }
                    delay(400) // Blink every 400ms
                }
                // Ensure flashlight is finally switched off
                try {
                    cameraManager.setTorchMode(cameraId, false)
                } catch (e: Exception) {}
            } else {
                Log.w("AlertService", "No camera with physical flash found on this device!")
            }
        }
    }

    private fun stopSelfAndClear() {
        isBlinking = false
        isTtsInitialized = false
        _activeAlertEventId.value = null
        _activeAlertTitle.value = null

        scope.launch(Dispatchers.IO) {
            try {
                tts?.stop()
                tts?.shutdown()
            } catch (e: Exception) {
                Log.e("AlertService", "Error stopping TTS: ${e.message}")
            }
        }

        stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() {
        isBlinking = false
        isTtsInitialized = false
        _activeAlertEventId.value = null
        _activeAlertTitle.value = null
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {}
        job.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "تنبيه المواعيد المجدولة",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "قناة لتقديم تنبيهات ذكية ومستمرة للمواعيد الهامة ناطقة ومصحوبة بفلاش"
                enableLights(true)
                enableVibration(true)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }
}
