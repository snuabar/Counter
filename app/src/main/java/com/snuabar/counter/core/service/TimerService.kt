package com.snuabar.counter.core.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import com.snuabar.counter.CounterApplication
import com.snuabar.counter.MainActivity
import com.snuabar.counter.R
import kotlinx.coroutines.*
import java.util.Locale

/**
 * Foreground service for timer mode (e.g. plank hold).
 * Keeps counting elapsed time even when the app is in background or screen is locked.
 */
class TimerService : Service(), TextToSpeech.OnInitListener {

    private val binder = TimerBinder()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val timerStateHolder: TimerStateHolder by lazy {
        (applicationContext as CounterApplication).timerStateHolder
    }

    private var timerJob: Job? = null
    private var targetSeconds: Int? = null
    private var onTargetReached: (() -> Unit)? = null

    private var textToSpeech: TextToSpeech? = null
    private var ttsInitialized = false
    private var voiceAnnouncement = false

    inner class TimerBinder : Binder() {
        fun getService(): TimerService = this@TimerService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        textToSpeech = TextToSpeech(this, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = textToSpeech?.setLanguage(Locale.getDefault())
            ttsInitialized = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Always call startForeground to satisfy Android 12+ 5-second requirement
        startForeground(NOTIFICATION_ID, buildNotification(timerStateHolder.elapsedSeconds.value))

        voiceAnnouncement = intent?.getBooleanExtra(EXTRA_VOICE_ANNOUNCEMENT, false) ?: false

        when (intent?.action) {
            ACTION_START -> {
                val target = intent.getIntExtra(EXTRA_TARGET_SECONDS, 0).takeIf { it > 0 }
                targetSeconds = target
                startTimer()
            }
            ACTION_PAUSE -> pauseTimer()
            ACTION_RESUME -> resumeTimer()
            ACTION_STOP -> stopTimer()
        }
        return START_NOT_STICKY
    }

    private fun startTimer() {
        timerStateHolder.updateElapsed(0)
        timerStateHolder.setServiceRunning(true)
        // startForeground already called in onStartCommand

        timerJob?.cancel()
        timerJob = scope.launch {
            while (isActive && timerStateHolder.isServiceRunning.value) {
                delay(1000)
                if (timerStateHolder.isServiceRunning.value) {
                    val newElapsed = timerStateHolder.elapsedSeconds.value + 1
                    timerStateHolder.updateElapsed(newElapsed)
                    updateNotification(newElapsed)

                    // Voice announcement every 30 seconds
                    if (voiceAnnouncement && newElapsed % 30 == 0 && newElapsed > 0) {
                        speakElapsedTime(newElapsed)
                    }

                    // Check target
                    targetSeconds?.let { target ->
                        if (newElapsed >= target) {
                            onTargetReached?.invoke()
                            notifyTargetReached()
                            stopTimer()
                        }
                    }
                }
            }
        }
    }

    private fun pauseTimer() {
        timerStateHolder.setServiceRunning(false)
        timerJob?.cancel()
        updateNotification(timerStateHolder.elapsedSeconds.value)
    }

    private fun resumeTimer() {
        timerStateHolder.setServiceRunning(true)
        timerJob?.cancel()
        timerJob = scope.launch {
            while (isActive && timerStateHolder.isServiceRunning.value) {
                delay(1000)
                if (timerStateHolder.isServiceRunning.value) {
                    val newElapsed = timerStateHolder.elapsedSeconds.value + 1
                    timerStateHolder.updateElapsed(newElapsed)
                    updateNotification(newElapsed)

                    targetSeconds?.let { target ->
                        if (newElapsed >= target) {
                            onTargetReached?.invoke()
                            stopTimer()
                        }
                    }
                }
            }
        }
    }

    fun stopTimer() {
        timerStateHolder.setServiceRunning(false)
        timerJob?.cancel()
        timerJob = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    fun setOnTargetReached(listener: () -> Unit) {
        onTargetReached = listener
    }

    private fun updateNotification(elapsed: Int) {
        val notification = buildNotification(elapsed)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(elapsed: Int): Notification {
        val minutes = elapsed / 60
        val seconds = elapsed % 60
        val timeText = "%02d:%02d".format(minutes, seconds)

        val contentIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseResumeIntent = Intent(this, TimerService::class.java).apply {
            action = if (timerStateHolder.isServiceRunning.value) ACTION_PAUSE else ACTION_RESUME
        }
        val pauseResumePending = PendingIntent.getService(
            this, 1, pauseResumeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, TimerService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPending = PendingIntent.getService(
            this, 2, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val targetText = targetSeconds?.let {
            val tm = it / 60
            val ts = it % 60
            " | 目标: %02d:%02d".format(tm, ts)
        } ?: ""

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("计时中")
            .setContentText("$timeText$targetText")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .addAction(
                android.R.drawable.ic_media_pause,
                if (timerStateHolder.isServiceRunning.value) "暂停" else "继续",
                pauseResumePending
            )
            .addAction(android.R.drawable.ic_delete, "停止", stopPending)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "计时服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "平板支撑等计时模式的后台计时通知"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun speakElapsedTime(seconds: Int) {
        if (!ttsInitialized) return
        val minutes = seconds / 60
        val secs = seconds % 60
        val text = if (minutes > 0) {
            "$minutes 分 $secs 秒"
        } else {
            "$secs 秒"
        }
        speak(text)
    }

    private fun speak(text: String) {
        if (!ttsInitialized || textToSpeech == null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
        } else {
            @Suppress("DEPRECATION")
            textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null)
        }
    }

    private fun notifyTargetReached() {
        // Send high-priority notification with sound
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("目标时间到达！")
            .setContentText("计时已完成")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .build()

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID + 1, notification)

        // Vibrate
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 200, 500), -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(longArrayOf(0, 500, 200, 500), -1)
        }

        // Speak target reached
        if (voiceAnnouncement) {
            speak("目标时间到达")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
    }

    companion object {
        const val ACTION_START = "com.snuabar.counter.action.START_TIMER"
        const val ACTION_PAUSE = "com.snuabar.counter.action.PAUSE_TIMER"
        const val ACTION_RESUME = "com.snuabar.counter.action.RESUME_TIMER"
        const val ACTION_STOP = "com.snuabar.counter.action.STOP_TIMER"
        const val EXTRA_TARGET_SECONDS = "target_seconds"
        const val EXTRA_VOICE_ANNOUNCEMENT = "voice_announcement"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "timer_service_channel"

        fun startTimer(context: Context, targetSeconds: Int? = null, voiceAnnouncement: Boolean = false) {
            val intent = Intent(context, TimerService::class.java).apply {
                action = ACTION_START
                targetSeconds?.let { putExtra(EXTRA_TARGET_SECONDS, it) }
                putExtra(EXTRA_VOICE_ANNOUNCEMENT, voiceAnnouncement)
            }
            context.startForegroundService(intent)
        }

        fun pauseTimer(context: Context) {
            val intent = Intent(context, TimerService::class.java).apply {
                action = ACTION_PAUSE
            }
            context.startForegroundService(intent)
        }

        fun resumeTimer(context: Context) {
            val intent = Intent(context, TimerService::class.java).apply {
                action = ACTION_RESUME
            }
            context.startForegroundService(intent)
        }

        fun stopTimer(context: Context) {
            val intent = Intent(context, TimerService::class.java).apply {
                action = ACTION_STOP
            }
            context.startForegroundService(intent)
        }
    }
}
