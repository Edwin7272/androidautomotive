package com.latineo.radio

import android.content.ComponentName
import android.os.Bundle
// USA estas en su lugar
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.appcompat.app.AppCompatActivity
import android.widget.Button
import android.widget.TextView
import android.widget.ImageView

class MainActivity : AppCompatActivity() {

    private lateinit var mediaBrowser: MediaBrowserCompat
    private lateinit var btnPlay: Button
    private lateinit var btnStop: Button
    private lateinit var tvStatus: TextView
    private lateinit var ivLogo: ImageView

    private val connectionCallbacks = object : MediaBrowserCompat.ConnectionCallback() {
        override fun onConnected() {
            mediaBrowser.sessionToken.also { token ->
                val mediaController = MediaControllerCompat(
                    this@MainActivity,
                    token
                )
                MediaControllerCompat.setMediaController(this@MainActivity, mediaController)
                buildTransportControls()
            }
        }

        override fun onConnectionSuspended() {
            tvStatus.text = "Desconectado"
        }

        override fun onConnectionFailed() {
            tvStatus.text = "Error de conexión"
        }
    }

    private val controllerCallback = object : MediaControllerCompat.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
            when (state?.state) {
                PlaybackStateCompat.STATE_PLAYING -> {
                    tvStatus.text = "▶ Reproduciendo: Latineo Radio"
                    btnPlay.text = "⏸ PAUSAR"
                    btnPlay.isEnabled = true
                    btnStop.isEnabled = true
                }
                PlaybackStateCompat.STATE_PAUSED -> {
                    tvStatus.text = "⏸ Pausado"
                    btnPlay.text = "▶ REPRODUCIR"
                    btnPlay.isEnabled = true
                    btnStop.isEnabled = true
                }
                PlaybackStateCompat.STATE_STOPPED -> {
                    tvStatus.text = "⏹ Detenido"
                    btnPlay.text = "▶ REPRODUCIR"
                    btnPlay.isEnabled = true
                    btnStop.isEnabled = false
                }
                PlaybackStateCompat.STATE_BUFFERING -> {
                    tvStatus.text = "⏳ Cargando..."
                    btnPlay.isEnabled = false
                    btnStop.isEnabled = true
                }
                PlaybackStateCompat.STATE_ERROR -> {
                    tvStatus.text = "❌ Error de reproducción"
                    btnPlay.text = "▶ REPRODUCIR"
                    btnPlay.isEnabled = true
                    btnStop.isEnabled = false
                }
                else -> {
                    tvStatus.text = "Listo"
                    btnPlay.isEnabled = true
                }
            }
        }

        override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
            // Aquí podrías actualizar información adicional si tu stream envía metadata
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnPlay = findViewById(R.id.btnPlay)
        btnStop = findViewById(R.id.btnStop)
        tvStatus = findViewById(R.id.tvStatus)
        ivLogo = findViewById(R.id.ivLogo)

        mediaBrowser = MediaBrowserCompat(
            this,
            ComponentName(this, RadioService::class.java),
            connectionCallbacks,
            null
        )
    }

    override fun onStart() {
        super.onStart()
        mediaBrowser.connect()
    }

    override fun onStop() {
        super.onStop()
        MediaControllerCompat.getMediaController(this)?.unregisterCallback(controllerCallback)
        mediaBrowser.disconnect()
    }

    private fun buildTransportControls() {
        val mediaController = MediaControllerCompat.getMediaController(this@MainActivity)
        mediaController.registerCallback(controllerCallback)

        // Actualizar UI según el estado actual
        controllerCallback.onPlaybackStateChanged(mediaController.playbackState)

        btnPlay.setOnClickListener {
            val state = mediaController.playbackState?.state
            if (state == PlaybackStateCompat.STATE_PLAYING) {
                mediaController.transportControls.pause()
            } else {
                mediaController.transportControls.play()
            }
        }

        btnStop.setOnClickListener {
            mediaController.transportControls.stop()
        }
    }
}
