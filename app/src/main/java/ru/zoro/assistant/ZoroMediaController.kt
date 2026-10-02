package ru.zoro.assistant

import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager

class ZoroMediaController(
    private val context: Context
) {
    private val mediaSessionManager =
        context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager

    private fun getController(): MediaController? {
        return try {
            val componentName = ComponentName(
                context,
                ZoroNotificationListener::class.java
            )

            mediaSessionManager
                .getActiveSessions(componentName)
                .firstOrNull()
        } catch (_: Exception) {
            null
        }
    }

    fun play(): Boolean {
        val controller = getController() ?: return false
        controller.transportControls.play()
        return true
    }

    fun pause(): Boolean {
        val controller = getController() ?: return false
        controller.transportControls.pause()
        return true
    }

    fun next(): Boolean {
        val controller = getController() ?: return false
        controller.transportControls.skipToNext()
        return true
    }

    fun previous(): Boolean {
        val controller = getController() ?: return false
        controller.transportControls.skipToPrevious()
        return true
    }

    fun seekForward(milliseconds: Long = 10_000): Boolean {
        val controller = getController() ?: return false
        val currentPosition =
            controller.playbackState?.position ?: 0L

        controller.transportControls.seekTo(
            currentPosition + milliseconds
        )

        return true
    }

    fun seekBackward(milliseconds: Long = 10_000): Boolean {
        val controller = getController() ?: return false
        val currentPosition =
            controller.playbackState?.position ?: 0L

        controller.transportControls.seekTo(
            maxOf(0L, currentPosition - milliseconds)
        )

        return true
    }

    fun volumeUp(): Boolean {
        val audioManager =
            context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        audioManager.adjustVolume(
            AudioManager.ADJUST_RAISE,
            AudioManager.FLAG_SHOW_UI
        )

        return true
    }

    fun volumeDown(): Boolean {
        val audioManager =
            context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        audioManager.adjustVolume(
            AudioManager.ADJUST_LOWER,
            AudioManager.FLAG_SHOW_UI
        )

        return true
    }

    fun getCurrentMediaInfo(): String? {
        val metadata = getController()?.metadata ?: return null

        val title =
            metadata.getString(MediaMetadata.METADATA_KEY_TITLE)

        val artist =
            metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)

        return when {
            !title.isNullOrBlank() && !artist.isNullOrBlank() ->
                "$title — $artist"

            !title.isNullOrBlank() ->
                title

            else ->
                null
        }
    }
}
