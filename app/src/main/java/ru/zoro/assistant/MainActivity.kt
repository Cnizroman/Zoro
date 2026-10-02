package ru.zoro.assistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        status = findViewById(R.id.status)

        findViewById<Button>(R.id.listenButton).setOnClickListener {
            startZoro()
        }

        findViewById<Button>(R.id.stopButton).setOnClickListener {
            stopZoro()
        }

        findViewById<Button>(R.id.mediaAccessButton).setOnClickListener {
            openNotificationAccessSettings()
        }

        requestMicrophonePermission()
    }

    private fun requestMicrophonePermission() {
        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                REQUEST_MICROPHONE
            )
        }
    }

    private fun startZoro() {
        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestMicrophonePermission()
            status.text = "Сначала разреши доступ к микрофону."
            return
        }

        ContextCompat.startForegroundService(
            this,
            Intent(this, ZoroService::class.java)
        )

        status.text = "Зоро активен. Скажи: «Зоро...»"
    }

    private fun stopZoro() {
        stopService(Intent(this, ZoroService::class.java))
        status.text = "Зоро выключен."
    }

    private fun openNotificationAccessSettings() {
        try {
            startActivity(
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            )
        } catch (_: Exception) {
            status.text = "Не удалось открыть настройки."
        }
    }

    companion object {
        private const val REQUEST_MICROPHONE = 100
    }
}
