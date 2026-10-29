package com.example.ytbblocker

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import android.text.InputType
import android.widget.*
import android.view.View
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BlockerService : AccessibilityService() {
    companion object { const val PREFS = "YTB_PREFS" }

    private val handler = Handler(Looper.getMainLooper())
    private var wm: WindowManager? = null
    private var overlay: View? = null
    private var youtubeForeground = false
    private var lastTickMs = 0L
    private var countdownRunning = false

    private val ticker = object : Runnable {
        override fun run() {
            if (!countdownRunning || !youtubeForeground || overlay != null) return
            val now = System.currentTimeMillis()
            if (lastTickMs == 0L) lastTickMs = now
            val elapsed = ((now - lastTickMs) / 1000L).toInt().coerceAtLeast(0)
            if (elapsed > 0) {
                lastTickMs += elapsed * 1000L
                val p = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                val remaining = (p.getInt("REMAINING_SEC", p.getInt("DAILY_LIMIT_MIN", 60) * 60) - elapsed).coerceAtLeast(0)
                p.edit().putInt("REMAINING_SEC", remaining).putBoolean("IS_LOCKED_NOW", remaining <= 0).apply()
                if (remaining <= 0) {
                    countdownRunning = false
                    youtubeForeground = false
                    pauseMedia()
                    showLockOverlay()
                    return
                }
            }
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        resetDailyIfNeeded()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || overlay != null) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return
        val isYoutube = isYoutubePackage(pkg)
        if (isYoutube) {
            resetDailyIfNeeded()
            val p = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val rem = p.getInt("REMAINING_SEC", p.getInt("DAILY_LIMIT_MIN", 60) * 60)
            if (rem <= 0 || p.getBoolean("IS_LOCKED_NOW", false)) {
                youtubeForeground = false
                countdownRunning = false
                pauseMedia()
                showLockOverlay()
                return
            }
        }
        if (youtubeForeground != isYoutube) {
            youtubeForeground = isYoutube
            lastTickMs = System.currentTimeMillis()
            if (youtubeForeground) startTicker() else stopTicker()
        }
    }

    private fun startTicker() {
        if (countdownRunning) return
        countdownRunning = true
        lastTickMs = System.currentTimeMillis()
        handler.removeCallbacks(ticker)
        handler.postDelayed(ticker, 1000L)
    }

    private fun stopTicker() {
        countdownRunning = false
        lastTickMs = 0L
        handler.removeCallbacks(ticker)
    }

    private fun isYoutubePackage(pkg: String): Boolean {
        val p = pkg.lowercase(Locale.ROOT)
        return p == "com.google.android.youtube" ||
            p.contains("smarttube") || p.contains("vanced") ||
            p.contains("revanced") || p.contains("newpipe") || p.contains("tubular")
    }

    private fun resetDailyIfNeeded() {
        val p = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val today = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        if (p.getString("LAST_DATE", "") != today) {
            val mins = p.getInt("DAILY_LIMIT_MIN", 60).coerceAtLeast(1)
            p.edit().putString("LAST_DATE", today).putInt("REMAINING_SEC", mins * 60)
                .putBoolean("IS_LOCKED_NOW", false).apply()
        }
    }

    private fun pauseMedia() {
        try {
            val am = getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
            am.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_MEDIA_PAUSE))
            am.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_MEDIA_PAUSE))
        } catch (_: Exception) {}
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun showLockOverlay() {
        if (overlay != null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) return
        val manager = wm ?: return
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(24), dp(24), dp(24))
            setBackgroundColor(0xCC080B18.toInt())
            isFocusable = true
            isFocusableInTouchMode = true
        }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(22), dp(24), dp(22), dp(24))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.rgb(245, 247, 252)); cornerRadius = dp(22).toFloat()
            }
        }
        val title = TextView(this).apply {
            text = "Đã hết thời gian xem YouTube"
            textSize = 20f; setTextColor(Color.rgb(20, 25, 40))
            typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
        }
        val hint = TextView(this).apply {
            text = "Nhập mã quản trị để mở thêm thời gian."
            textSize = 14f; setTextColor(Color.DKGRAY); gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, dp(16))
        }
        val input = EditText(this).apply {
            hint = "Mã quản trị"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            textSize = 18f; gravity = Gravity.CENTER
            setSingleLine(true)
        }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        val continueBtn = Button(this).apply {
            text = "Mở thêm giờ"; isAllCaps = false
            setOnClickListener {
                val secret = SimpleDateFormat("HHmmddMM", Locale.US).format(Date())
                if (input.text.toString() == secret) {
                    val p = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    val mins = p.getInt("DAILY_LIMIT_MIN", 60).coerceAtLeast(1)
                    p.edit().putInt("REMAINING_SEC", mins * 60).putBoolean("IS_LOCKED_NOW", false).apply()
                    removeOverlay()
                    Toast.makeText(this@BlockerService, "Đã mở khóa.", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@BlockerService, "Mã không đúng.", Toast.LENGTH_SHORT).show()
                    input.text.clear()
                }
            }
        }
        val stopBtn = Button(this).apply {
            text = "Thoát YouTube"; isAllCaps = false
            setOnClickListener {
                performGlobalAction(GLOBAL_ACTION_HOME)
                handler.postDelayed({ removeOverlay() }, 250L)
            }
        }
        row.addView(continueBtn, LinearLayout.LayoutParams(0, dp(48), 1f))
        row.addView(stopBtn, LinearLayout.LayoutParams(0, dp(48), 1f))
        card.addView(title); card.addView(hint); card.addView(input); card.addView(row)
        root.addView(card, LinearLayout.LayoutParams(-1, -2))
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_DIM_BEHIND,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            dimAmount = 0.25f
        }
        try {
            manager.addView(root, params)
            overlay = root
            stopTicker()
        } catch (_: Exception) {
            overlay = null
            performGlobalAction(GLOBAL_ACTION_HOME)
        }
    }

    private fun removeOverlay() {
        val v = overlay ?: return
        try { wm?.removeView(v) } catch (_: Exception) {}
        overlay = null
        youtubeForeground = false
        stopTicker()
    }

    override fun onInterrupt() { stopTicker(); removeOverlay() }
    override fun onDestroy() { stopTicker(); removeOverlay(); super.onDestroy() }
}
