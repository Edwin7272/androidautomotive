package com.latineo.radio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.media.MediaBrowserServiceCompat
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player

class RadioService : MediaBrowserServiceCompat() {

    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var stateBuilder: PlaybackStateCompat.Builder
    private var player: ExoPlayer? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var wasPlayingBeforeFocusLoss = false
    private var currentStreamUrl: String = ""
    private var currentEmisora: String = ""

    companion object {
        private const val CHANNEL_ID = "latineo_radio_channel"
        private const val NOTIFICATION_ID = 1
        private const val MEDIA_ROOT_ID = "latineo_root"
    }

    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        // Crear MediaSession
        mediaSession = MediaSessionCompat(this, "LatineoRadioService").apply {
            stateBuilder = PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                            PlaybackStateCompat.ACTION_PAUSE or
                            PlaybackStateCompat.ACTION_STOP or
                            PlaybackStateCompat.ACTION_PLAY_PAUSE
                )
            setPlaybackState(stateBuilder.build())

            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    startPlayback()
                }

                override fun onPause() {
                    pausePlayback()
                }

                override fun onStop() {
                    stopPlayback()
                }

                override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
                    super.onPlayFromMediaId(mediaId, extras)
                    when (mediaId) {
                        "sevilla" -> playSevilla()
                        "malaga" -> playMalaga()
                    }
                }
            })

            setSessionToken(sessionToken)
        }

        // Inicializar ExoPlayer
        player = ExoPlayer.Builder(this).build().apply {
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    updatePlaybackState()
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    updatePlaybackState()
                }
            })
        }
    }

    private fun playSevilla() {
        val newUrl = getString(R.string.sevilla_url)
        val newEmisora = getString(R.string.sevilla)

        if (player?.isPlaying == true && currentStreamUrl != newUrl) {
            cambiarStream(newUrl, newEmisora)
        } else {
            currentStreamUrl = newUrl
            currentEmisora = newEmisora
            startPlayback()
        }
    }

    private fun playMalaga() {
        val newUrl = getString(R.string.malaga_url)
        val newEmisora = getString(R.string.malaga)

        if (player?.isPlaying == true && currentStreamUrl != newUrl) {
            cambiarStream(newUrl, newEmisora)
        } else {
            currentStreamUrl = newUrl
            currentEmisora = newEmisora
            startPlayback()
        }
    }

    private fun cambiarStream(newUrl: String, newEmisora: String) {
        player?.let {
            currentStreamUrl = newUrl
            currentEmisora = newEmisora

            it.clearMediaItems()
            val mediaItem = MediaItem.fromUri(Uri.parse(newUrl))
            it.setMediaItem(mediaItem)
            it.prepare()
            it.play()

            updatePlaybackState()
            startForeground(NOTIFICATION_ID, createNotification())
        }
    }

    private fun requestAudioFocus(): Boolean {
        audioManager?.let {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()

            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(audioAttributes)
                .setOnAudioFocusChangeListener { focusChange ->
                    when (focusChange) {
                        AudioManager.AUDIOFOCUS_GAIN -> {
                            if (wasPlayingBeforeFocusLoss) {
                                player?.play()
                                wasPlayingBeforeFocusLoss = false
                            }
                        }
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                            if (player?.isPlaying == true) {
                                wasPlayingBeforeFocusLoss = true
                                player?.pause()
                            }
                        }
                        AudioManager.AUDIOFOCUS_LOSS -> {
                            stopPlayback()
                        }
                    }
                }
                .build()

            return it.requestAudioFocus(audioFocusRequest!!) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
        return false
    }

    private fun abandonAudioFocus() {
        audioManager?.let {
            audioFocusRequest?.let { request ->
                it.abandonAudioFocusRequest(request)
            }
        }
    }

    private fun startPlayback() {
        player?.let {
            if (!requestAudioFocus()) {
                return
            }

            if (it.mediaItemCount == 0 && currentStreamUrl.isNotEmpty()) {
                val mediaItem = MediaItem.fromUri(Uri.parse(currentStreamUrl))
                it.setMediaItem(mediaItem)
                it.prepare()
            }
            it.play()
            mediaSession.isActive = true

            startForeground(NOTIFICATION_ID, createNotification())
            updatePlaybackState()
        }
    }

    private fun pausePlayback() {
        player?.pause()
        updatePlaybackState()
    }

    private fun stopPlayback() {
        player?.let {
            it.stop()
            it.clearMediaItems()
        }
        mediaSession.isActive = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        abandonAudioFocus()
        currentStreamUrl = ""
        currentEmisora = ""
        updatePlaybackState()
    }

    private fun updatePlaybackState() {
        val state = when {
            player == null -> PlaybackStateCompat.STATE_NONE
            player!!.isPlaying -> PlaybackStateCompat.STATE_PLAYING
            player!!.playbackState == Player.STATE_BUFFERING -> PlaybackStateCompat.STATE_BUFFERING
            player!!.playbackState == Player.STATE_READY -> PlaybackStateCompat.STATE_PAUSED
            else -> PlaybackStateCompat.STATE_STOPPED
        }

        val stateBuilder = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_STOP or
                        PlaybackStateCompat.ACTION_PLAY_PAUSE
            )
            .setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1.0f)

        mediaSession.setPlaybackState(stateBuilder.build())

        // Actualizar metadata con la emisora actual
        val titulo = if (currentEmisora.isNotEmpty()) {
            "Latineo Radio - $currentEmisora"
        } else {
            "Latineo Radio"
        }

        val metadata = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, titulo)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "En vivo")
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, "Streaming")
            .build()
        mediaSession.setMetadata(metadata)
    }

    private fun createNotification(): Notification {
        val contentText = if (currentEmisora.isNotEmpty()) {
            "Reproduciendo: $currentEmisora"
        } else {
            "Reproduciendo en vivo"
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Latineo Radio")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)

        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Latineo Radio",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Control de reproducción Latineo Radio"
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot {
        return BrowserRoot(MEDIA_ROOT_ID, null)
    }

    override fun onLoadChildren(
        parentId: String,
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>
    ) {
        val mediaItems = mutableListOf<MediaBrowserCompat.MediaItem>()

        if (parentId == MEDIA_ROOT_ID) {
            val sevillaDescription = MediaDescriptionCompat.Builder()
                .setMediaId("sevilla")
                .setTitle("Latineo Radio Sevilla")
                .setSubtitle("En vivo")
                .build()

            mediaItems.add(
                MediaBrowserCompat.MediaItem(
                    sevillaDescription,
                    MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
                )
            )

            val malagaDescription = MediaDescriptionCompat.Builder()
                .setMediaId("malaga")
                .setTitle("Latineo Radio Málaga")
                .setSubtitle("En vivo")
                .build()

            mediaItems.add(
                MediaBrowserCompat.MediaItem(
                    malagaDescription,
                    MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
                )
            )
        }

        result.sendResult(mediaItems)
    }

    override fun onDestroy() {
        super.onDestroy()
        abandonAudioFocus()
        player?.release()
        player = null
        mediaSession.release()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        stopSelf()
    }
}
