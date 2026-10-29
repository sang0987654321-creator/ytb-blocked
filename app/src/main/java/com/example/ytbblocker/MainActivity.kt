package com.example.ytbblocker

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences(BlockerService.PREFS, Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        resetDayIfNeeded()
        render()
    }

    override fun onResume() {
        super.onResume()
        resetDayIfNeeded()
        render()
    }

    private fun resetDayIfNeeded() {
        val today = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        if (prefs.getString("LAST_DATE", "") != today) {
            val limit = prefs.getInt("DAILY_LIMIT_MIN", 60).coerceAtLeast(1)
            prefs.edit().putString("LAST_DATE", today)
                .putInt("REMAINING_SEC", limit * 60)
                .putBoolean("IS_LOCKED_NOW", false).apply()
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun label(text: String, size: Float, color: Int = Color.WHITE) = TextView(this).apply {
        this.text = text; textSize = size; setTextColor(color)
    }

    private fun render() {
        val bg = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(24), dp(24), dp(24))
            setBackgroundColor(Color.rgb(15, 20, 43))
        }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(24), dp(22), dp(24))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.rgb(244, 247, 252)); cornerRadius = dp(24).toFloat()
            }
        }
        val title = label("YouTube TimeGlass  v1.13", 21f, Color.rgb(20, 25, 40)).apply {
            typeface = Typeface.DEFAULT_BOLD
        }
        val desc = label("Giới hạn thời gian xem YouTube mỗi ngày.", 14f, Color.DKGRAY)
        desc.setPadding(0, dp(8), 0, dp(18))
        val minutes = EditText(this).apply {
            hint = "Số phút mỗi ngày (ví dụ 60)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(prefs.getInt("DAILY_LIMIT_MIN", 60).toString())
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(0, 90, 200))
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        val remaining = prefs.getInt("REMAINING_SEC", prefs.getInt("DAILY_LIMIT_MIN", 60) * 60).coerceAtLeast(0)
        val status = label("Thời gian còn lại hôm nay: %02d:%02d".format(remaining / 60, remaining % 60), 15f, Color.DKGRAY)
        status.setPadding(0, dp(12), 0, dp(12))
        val permOverlay = button(if (Settings.canDrawOverlays(this)) "✓ Quyền hiển thị lớp khóa: Đã bật" else "Bật quyền hiển thị lớp khóa")
        permOverlay.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            }
        }
        val permAccess = button(if (isAccessibilityEnabled()) "✓ Quyền theo dõi ứng dụng: Đã bật" else "Bật quyền theo dõi ứng dụng")
        permAccess.setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        val save = button("Lưu thời gian")
        save.setOnClickListener {
            val mins = minutes.text.toString().toIntOrNull()
            if (mins == null || mins !in 1..1440) {
                Toast.makeText(this, "Nhập số phút từ 1 đến 1440.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val today = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
            prefs.edit().putString("LAST_DATE", today).putInt("DAILY_LIMIT_MIN", mins)
                .putInt("REMAINING_SEC", mins * 60).putBoolean("IS_LOCKED_NOW", false).apply()
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(minutes.windowToken, 0)
            Toast.makeText(this, "Đã lưu giới hạn $mins phút.", Toast.LENGTH_SHORT).show()
            render()
        }
        card.addView(title); card.addView(desc); card.addView(minutes)
        card.addView(status); card.addView(permOverlay); card.addView(permAccess); card.addView(save)
        bg.addView(card, LinearLayout.LayoutParams(-1, -2))
        setContentView(bg)
    }

    private fun button(text: String) = Button(this).apply {
        this.text = text
        isAllCaps = false
        textSize = 14f
        setPadding(dp(8), dp(6), dp(8), dp(6))
    }

    private fun isAccessibilityEnabled(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return enabled.split(':').any { it.contains(packageName, true) }
    }
}
