package com.example.livetranslate

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.common.model.DownloadConditions

class OverlayService : Service() {

    private lateinit var wm: WindowManager
    private lateinit var box: LinearLayout
    private lateinit var tvId: TextView
    private lateinit var tvEn: TextView
    private lateinit var params: WindowManager.LayoutParams
    private lateinit var translator: Translator
    private var recognizer: SpeechRecognizer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var modelReady = false
    private var lastPartialAt = 0L

    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        buildOverlay()

        translator = Translation.getClient(
            TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.ENGLISH)
                .setTargetLanguage(TranslateLanguage.INDONESIAN)
                .build()
        )
        tvId.text = "Mengunduh model terjemahan (sekali saja)…"
        translator.downloadModelIfNeeded(DownloadConditions.Builder().build())
            .addOnSuccessListener {
                modelReady = true
                tvId.text = "Siap. Putar videonya…"
            }
            .addOnFailureListener { tvId.text = "Gagal mengunduh model. Cek internet." }

        running = true
        startListening()
    }

    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("lt", "Live Translate", NotificationManager.IMPORTANCE_LOW)
        )
        val n = Notification.Builder(this, "lt")
            .setContentTitle("Live Translate aktif")
            .setContentText("Mendengarkan dan menerjemahkan ke Indonesia")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else startForeground(1, n)
    }

    private fun buildOverlay() {
        val d = resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()

        box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(12))
            background = GradientDrawable().apply {
                setColor(Color.argb(205, 0, 0, 0)); cornerRadius = dp(16).toFloat()
            }
        }
        val close = TextView(this).apply {
            text = "✕  tutup"
            setTextColor(Color.LTGRAY); textSize = 12f
            gravity = Gravity.END
            setOnClickListener { stopSelf() }
        }
        tvId = TextView(this).apply {
            setTextColor(Color.WHITE); textSize = 19f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        tvEn = TextView(this).apply { setTextColor(Color.LTGRAY); textSize = 12f }
        box.addView(close); box.addView(tvId); box.addView(tvEn)

        params = WindowManager.LayoutParams(
            (resources.displayMetrics.widthPixels * 0.92).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (resources.displayMetrics.widthPixels * 0.04).toInt()
            y = (resources.displayMetrics.heightPixels * 0.65).toInt()
        }

        // Drag to move
        var sx = 0; var sy = 0; var tx = 0f; var ty = 0f
        box.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { sx = params.x; sy = params.y; tx = e.rawX; ty = e.rawY }
                MotionEvent.ACTION_MOVE -> {
                    params.x = sx + (e.rawX - tx).toInt()
                    params.y = sy + (e.rawY - ty).toInt()
                    wm.updateViewLayout(box, params)
                }
            }
            true
        }
        wm.addView(box, params)
    }

    // ---------- Speech recognition (English, continuous via auto-restart) ----------

    private fun startListening() {
        if (!running) return
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            tvId.text = "Pengenalan suara tidak tersedia di perangkat ini."
            return
        }
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(listener)
            startListening(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
                }
            )
        }
    }

    private fun restart(delay: Long = 250) {
        handler.postDelayed({ startListening() }, delay)
    }

    private val listener = object : RecognitionListener {
        override fun onPartialResults(b: Bundle?) {
            val t = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: return
            val now = System.currentTimeMillis()
            if (now - lastPartialAt > 700) { lastPartialAt = now; show(t) }
        }
        override fun onResults(b: Bundle?) {
            b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { show(it) }
            restart()
        }
        override fun onError(error: Int) {
            restart(if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 1000 else 300)
        }
        override fun onReadyForSpeech(p: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(v: Float) {}
        override fun onBufferReceived(b: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(t: Int, b: Bundle?) {}
    }

    private fun show(en: String) {
        tvEn.text = en
        if (!modelReady) return
        translator.translate(en).addOnSuccessListener { tvId.text = it }
    }

    override fun onDestroy() {
        running = false
        handler.removeCallbacksAndMessages(null)
        recognizer?.destroy()
        translator.close()
        if (::box.isInitialized) runCatching { wm.removeView(box) }
        super.onDestroy()
    }
}
