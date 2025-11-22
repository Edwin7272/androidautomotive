package com.latineo.radio

import android.content.ComponentName
import android.os.Bundle
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
    private lateinit var btnSevilla: Button
    private lateinit var btnMalaga: Button
    private lateinit var tvStatus: TextView
    private lateinit var tvEmisora: TextView
    private lateinit var ivLogo: ImageView
    private var mediaController: MediaControllerCompat? = null
    private var sevillaActiva = false
    private var malagaActiva = false

    private val connectionCallbacks = object : MediaBrowserCompat.ConnectionCallback() {
        override fun onConnected() {
            mediaBrowser.sessionToken.also { token ->
                mediaController = MediaControllerCompat(
                    this@MainActivity,
                    token
                )
                MediaControllerCompat.setMediaController(this@MainActivity, mediaController!!)
                buildTransportControls()
            }
        }

        override fun onConnectionSuspended() {
            tvStatus.text = getString(R.string.disconnected)
            btnPlay.isEnabled = false
            btnStop.isEnabled = false
        }

        override fun onConnectionFailed() {
            tvStatus.text = getString(R.string.connection_error)
            btnPlay.isEnabled = false
            btnStop.isEnabled = false
        }
    }

    private val controllerCallback = object : MediaControllerCompat.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
            when (state?.state) {
                PlaybackStateCompat.STATE_PLAYING -> {
                    tvStatus.text = getString(R.string.playing)
                    btnPlay.text = getString(R.string.pause)
                    btnPlay.isEnabled = true
                    btnStop.isEnabled = true
                }
                PlaybackStateCompat.STATE_PAUSED -> {
                    tvStatus.text = getString(R.string.paused)
                    btnPlay.text = getString(R.string.play)
                    btnPlay.isEnabled = true
                    btnStop.isEnabled = true
                }
                PlaybackStateCompat.STATE_STOPPED -> {
                    tvStatus.text = getString(R.string.stopped)
                    btnPlay.text = getString(R.string.play)
                    btnStop.isEnabled = false
                    sevillaActiva = false
                    malagaActiva = false
                    btnSevilla.text = getString(R.string.sevilla)
                    btnMalaga.text = getString(R.string.malaga)
                    btnSevilla.backgroundTintList = null
                    btnMalaga.backgroundTintList = null
                }
                PlaybackStateCompat.STATE_BUFFERING -> {
                    tvStatus.text = getString(R.string.buffering)
                    btnPlay.isEnabled = false
                    btnStop.isEnabled = true
                }
                PlaybackStateCompat.STATE_ERROR -> {
                    tvStatus.text = getString(R.string.error)
                    btnPlay.text = getString(R.string.play)
                    btnStop.isEnabled = false
                }
                else -> {
                    tvStatus.text = getString(R.string.ready)
                    btnStop.isEnabled = false
                }
            }
        }

        override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
            // Aquí podrías actualizar información adicional
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnPlay = findViewById(R.id.btnPlay)
        btnStop = findViewById(R.id.btnStop)
        btnSevilla = findViewById(R.id.btnSevilla)
        btnMalaga = findViewById(R.id.btnMalaga)
        tvStatus = findViewById(R.id.tvStatus)
        tvEmisora = findViewById(R.id.tvEmisora)
        ivLogo = findViewById(R.id.ivLogo)

        mediaBrowser = MediaBrowserCompat(
            this,
            ComponentName(this, RadioService::class.java),
            connectionCallbacks,
            null
        )

        // Botón Sevilla
        btnSevilla.setOnClickListener {
            if (sevillaActiva) {
                // Si está activa, detener
                mediaController?.transportControls?.stop()
                sevillaActiva = false
                btnSevilla.text = getString(R.string.sevilla)
                btnSevilla.backgroundTintList = null
            } else {
                // Si no está activa, reproducir
                sevillaActiva = true
                malagaActiva = false
                btnSevilla.text = "⏹"
                btnSevilla.setBackgroundColor(getColor(android.R.color.holo_red_dark))
                btnMalaga.text = getString(R.string.malaga)
                btnMalaga.backgroundTintList = null
                tvEmisora.text = "Seleccionado: ${getString(R.string.sevilla)}"
                mediaController?.transportControls?.playFromMediaId("sevilla", null)
            }
        }

        // Botón Málaga
        btnMalaga.setOnClickListener {
            if (malagaActiva) {
                // Si está activa, detener
                mediaController?.transportControls?.stop()
                malagaActiva = false
                btnMalaga.text = getString(R.string.malaga)
                btnMalaga.backgroundTintList = null
            } else {
                // Si no está activa, reproducir
                malagaActiva = true
                sevillaActiva = false
                btnMalaga.text = "⏹"
                btnMalaga.setBackgroundColor(getColor(android.R.color.holo_red_dark))
                btnSevilla.text = getString(R.string.sevilla)
                btnSevilla.backgroundTintList = null
                tvEmisora.text = "Seleccionado: ${getString(R.string.malaga)}"
                mediaController?.transportControls?.playFromMediaId("malaga", null)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (!mediaBrowser.isConnected) {
            mediaBrowser.connect()
        }
    }

    override fun onResume() {
        super.onResume()
        MediaControllerCompat.getMediaController(this)?.let {
            it.registerCallback(controllerCallback)
            controllerCallback.onPlaybackStateChanged(it.playbackState)
        }
    }

    override fun onPause() {
        super.onPause()
        MediaControllerCompat.getMediaController(this)?.unregisterCallback(controllerCallback)
    }

    override fun onStop() {
        super.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        MediaControllerCompat.getMediaController(this)?.unregisterCallback(controllerCallback)
        if (mediaBrowser.isConnected) {
            mediaBrowser.disconnect()
        }
    }

    private fun buildTransportControls() {
        mediaController?.let { controller ->
            controller.registerCallback(controllerCallback)
            controllerCallback.onPlaybackStateChanged(controller.playbackState)

            btnPlay.setOnClickListener {
                val state = controller.playbackState?.state
                if (state == PlaybackStateCompat.STATE_PLAYING) {
                    controller.transportControls.pause()
                } else if (sevillaActiva || malagaActiva) {
                    val mediaId = if (sevillaActiva) "sevilla" else "malaga"
                    controller.transportControls.playFromMediaId(mediaId, null)
                }
            }

            btnStop.setOnClickListener {
                controller.transportControls.stop()
            }
        }
    }
}
