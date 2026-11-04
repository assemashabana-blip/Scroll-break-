package com.assem.scrollbreak.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.assem.scrollbreak.AppLog

/** Full-screen second-stage block: shown when BACK did not leave the player. */
class BlockOverlay(
    private val service: AccessibilityService,
    private val onGoBack: () -> Unit,
) {
    private val windowManager = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var view: View? = null

    val isShowing: Boolean get() = view != null

    fun show(message: String) {
        if (view != null) return
        try {
            val pad = (24 * service.resources.displayMetrics.density).toInt()
            val root = LinearLayout(service).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setBackgroundColor(Color.parseColor("#F2101216"))
                setPadding(pad, pad, pad, pad)
                isClickable = true // swallow touches so the feed underneath can't be scrolled
                addView(TextView(service).apply {
                    text = message
                    setTextColor(Color.WHITE)
                    textSize = 22f
                    gravity = Gravity.CENTER
                })
                addView(Button(service).apply {
                    text = "Go back"
                    setOnClickListener {
                        dismiss()
                        onGoBack()
                    }
                }, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = pad })
            }
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT,
            )
            windowManager.addView(root, params)
            view = root
        } catch (t: Throwable) {
            AppLog.error("overlay show failed", t)
        }
    }

    fun dismiss() {
        val v = view ?: return
        view = null
        try {
            windowManager.removeView(v)
        } catch (t: Throwable) {
            AppLog.error("overlay dismiss failed", t)
        }
    }
}
