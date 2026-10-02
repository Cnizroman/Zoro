package ru.zoro.assistant

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ZoroService : Service(), TextToSpeech.OnInitListener {

    private var recognizer: SpeechRecognizer? = null
    private lateinit var tts: TextToSpeech
    private lateinit var mediaController: ZoroMediaController

    private val handler = Handler(Looper.getMainLooper())
    private val ruLocale = Locale("ru", "RU")

    private var isListening = false
    private var waitingForCommand = false
    private var restarting = false

    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()

        startForeground(
            NOTIFICATION_ID,
            createNotification("Зоро активен — скажи «Зоро»")
        )

        tts = TextToSpeech(this, this)
        mediaController = ZoroMediaController(this)

        startContinuousListening()
    }

    override fun onInit(result: Int) {
        if (result == TextToSpeech.SUCCESS) {
            tts.language = ruLocale
            tts.setSpeechRate(1.0f)
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Зоро",
            NotificationManager.IMPORTANCE_LOW
        )

        channel.description =
            "Фоновая работа голосового ассистента Зоро"

        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    private fun createNotification(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java)

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or
                    PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Зоро")
            .setContentText(text)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java)
            .notify(
                NOTIFICATION_ID,
                createNotification(text)
            )
    }

    private fun startContinuousListening() {
        if (isListening || restarting) return

        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            updateNotification("Нет доступа к микрофону")
            return
        }

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            updateNotification("Распознавание речи недоступно")
            return
        }

        recognizer?.destroy()

        recognizer =
            SpeechRecognizer.createSpeechRecognizer(this)

        recognizer?.setRecognitionListener(
            object : RecognitionListener {

                override fun onReadyForSpeech(
                    params: Bundle?
                ) {
                    isListening = true
                    updateNotification("Слушаю…")
                }

                override fun onBeginningOfSpeech() {}

                override fun onRmsChanged(
                    rmsdB: Float
                ) {}

                override fun onBufferReceived(
                    buffer: ByteArray?
                ) {}

                override fun onEndOfSpeech() {
                    isListening = false
                    updateNotification("Обрабатываю…")
                }

                override fun onError(error: Int) {
                    isListening = false
                    scheduleRestart()
                }

                override fun onResults(
                    results: Bundle?
                ) {
                    isListening = false

                    val text =
                        results
                            ?.getStringArrayList(
                                SpeechRecognizer.RESULTS_RECOGNITION
                            )
                            ?.firstOrNull()
                            ?.lowercase(ruLocale)
                            ?.replace("ё", "е")
                            ?.trim()

                    if (!text.isNullOrEmpty()) {
                        handleSpeech(text)
                    }

                    scheduleRestart()
                }

                override fun onPartialResults(
                    partialResults: Bundle?
                ) {}

                override fun onEvent(
                    eventType: Int,
                    params: Bundle?
                ) {}
            }
        )

        val intent =
            Intent(
                RecognizerIntent.ACTION_RECOGNIZE_SPEECH
            ).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )

                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE,
                    "ru-RU"
                )

                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,
                    "ru-RU"
                )

                putExtra(
                    RecognizerIntent.EXTRA_MAX_RESULTS,
                    5
                )

                putExtra(
                    RecognizerIntent.EXTRA_PARTIAL_RESULTS,
                    false
                )
            }

        recognizer?.startListening(intent)
    }

    private fun scheduleRestart() {
        if (restarting) return

        restarting = true

        handler.postDelayed(
            {
                restarting = false
                startContinuousListening()
            },
            350L
        )
    }

    private fun handleSpeech(text: String) {
        if (!waitingForCommand) {

            if (
                text.contains("зоро") ||
                text.contains("зорро")
            ) {
                val command =
                    text
                        .replace("зоро", "")
                        .replace("зорро", "")
                        .trim()

                if (command.isEmpty()) {
                    waitingForCommand = true
                    speak("Да?")
                } else {
                    handleCommand(command)
                }
            }

            return
        }

        waitingForCommand = false
        handleCommand(text)
    }

    private fun handleCommand(text: String) {
        when {

            containsAny(
                text,
                "пауза",
                "поставь на паузу",
                "поставь видео на паузу",
                "останови"
            ) -> {
                speak(
                    if (mediaController.pause())
                        "Пауза"
                    else
                        "Я не нашёл активный медиаплеер."
                )
            }

            containsAny(
                text,
                "продолжи",
                "возобнови",
                "воспроизведи",
                "включи",
                "играй"
            ) -> {
                speak(
                    if (mediaController.play())
                        "Продолжаю."
                    else
                        "Я не нашёл активный медиаплеер."
                )
            }

            containsAny(
                text,
                "следующий трек",
                "следующая песня",
                "следующее видео",
                "следующий ролик",
                "дальше"
            ) -> {
                speak(
                    if (mediaController.next())
                        "Следующий."
                    else
                        "Не удалось переключить."
                )
            }

            containsAny(
                text,
                "предыдущий трек",
                "предыдущая песня",
                "предыдущее видео",
                "предыдущий ролик",
                "назад"
            ) -> {
                speak(
                    if (mediaController.previous())
                        "Предыдущий."
                    else
                        "Не удалось переключить."
                )
            }

            containsAny(
                text,
                "перемотай вперед",
                "перемотай вперёд",
                "вперед на 10 секунд",
                "вперёд на 10 секунд"
            ) -> {
                if (mediaController.seekForward()) {
                    speak("Перематываю вперёд.")
                }
            }

            containsAny(
                text,
                "перемотай назад",
                "назад на 10 секунд"
            ) -> {
                if (mediaController.seekBackward()) {
                    speak("Перематываю назад.")
                }
            }

            containsAny(
                text,
                "громче",
                "увеличь громкость",
                "сделай громче"
            ) -> {
                mediaController.volumeUp()
                speak("Громче.")
            }

            containsAny(
                text,
                "тише",
                "уменьши громкость",
                "сделай тише"
            ) -> {
                mediaController.volumeDown()
                speak("Тише.")
            }

            containsAny(
                text,
                "что играет",
                "что сейчас играет",
                "что включено",
                "какая песня играет"
            ) -> {
                val info =
                    mediaController.getCurrentMediaInfo()

                speak(
                    if (info != null)
                        "Сейчас играет $info"
                    else
                        "Я не смог определить, что сейчас играет."
                )
            }

            containsAny(
                text,
                "который час",
                "сколько времени",
                "сколько сейчас времени",
                "скажи время"
            ) -> tellTime()

            containsAny(
                text,
                "открой яндекс музыку",
                "открой яндекс музыка",
                "запусти яндекс музыку",
                "включи яндекс музыку"
            ) -> openApplication(
                "ru.yandex.music",
                "Яндекс Музыка"
            )

            containsAny(
                text,
                "открой дискорд",
                "запусти дискорд",
                "открой discord"
            ) -> openApplication(
                "com.discord",
                "Discord"
            )

            containsAny(
                text,
                "открой телеграм",
                "запусти телеграм",
                "открой telegram"
            ) -> openApplication(
                "org.telegram.messenger",
                "Telegram"
            )

            containsAny(
                text,
                "открой стим",
                "запусти стим",
                "открой steam"
            ) -> openApplication(
                "com.valvesoftware.android.steam.community",
                "Steam"
            )

            containsAny(
                text,
                "открой ютуб",
                "запусти ютуб",
                "открой youtube"
            ) -> openApplication(
                "com.google.android.youtube",
                "YouTube"
            )

            containsAny(
                text,
                "открой вк",
                "запусти вк",
                "открой вконтакте"
            ) -> openApplication(
                "com.vkontakte.android",
                "ВКонтакте"
            )

            text.startsWith("найди ") -> {
                val query =
                    text.removePrefix("найди ").trim()

                if (query.isNotBlank()) {
                    searchGoogle(query)
                }
            }

            text.startsWith("поищи ") -> {
                val query =
                    text.removePrefix("поищи ").trim()

                if (query.isNotBlank()) {
                    searchGoogle(query)
                }
            }

            else -> speak("Я не знаю такую команду.")
        }
    }

    private fun tellTime() {
        val time =
            SimpleDateFormat(
                "HH:mm",
                Locale.getDefault()
            ).format(Date())

        speak("Сейчас $time")
    }

    private fun openApplication(
        packageName: String,
        name: String
    ) {
        val intent =
            packageManager.getLaunchIntentForPackage(
                packageName
            )

        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            speak("Открываю $name")
        } else {
            speak("$name не установлен.")
        }
    }

    private fun searchGoogle(query: String) {
        val url =
            "https://www.google.com/search?q=" +
                    android.net.Uri.encode(query)

        try {
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    android.net.Uri.parse(url)
                ).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK
                )
            )

            speak("Ищу $query")
        } catch (_: Exception) {
            speak("Не удалось открыть браузер.")
        }
    }

    private fun containsAny(
        text: String,
        vararg variants: String
    ): Boolean =
        variants.any {
            text.contains(it)
        }

    private fun speak(text: String) {
        if (!::tts.isInitialized) return

        recognizer?.cancel()
        isListening = false

        updateNotification(text)

        tts.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "zoro"
        )
    }

    override fun onDestroy() {
        recognizer?.destroy()

        if (::tts.isInitialized) {
            tts.shutdown()
        }

        handler.removeCallbacksAndMessages(null)

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "zoro_voice_service"
        private const val NOTIFICATION_ID = 1001
    }
}
