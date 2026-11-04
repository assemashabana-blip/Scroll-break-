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

/**
 * Covers the "turn ScrollBreak off" page with a countdown. It never traps the user: it has its
 * own "Go back" button and the system back and home gestures keep working underneath it.
 */
class FrictionOverlay(
    private val service: AccessibilityService,
    private val onGoBack: () -> Unit,
) {
    private val windowManager = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    @Volatile private var view: View? = null
    private var countdown: TextView? = null

    val isShowing: Boolean get() = view != null

    /** Shows the overlay, or just updates the number if it is already up. Call on the main thread. */
    fun show(secondsLeft: Long) {
        countdown?.text = secondsText(secondsLeft)
        if (view != null) return
        try {
            val density = service.resources.displayMetrics.density
            val pad = (24 * density).toInt()
            val number = TextView(service).apply {
                text = secondsText(secondsLeft)
                setTextColor(Color.WHITE)
                textSize = 56f
                gravity = Gravity.CENTER
            }
            val root = LinearLayout(service).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setBackgroundColor(Color.parseColor("#F5101216"))
                setPadding(pad, pad, pad, pad)
                isClickable = true // swallow touches so the switch underneath cannot be reached
                addView(TextView(service).apply {
                    text = "Turning ScrollBreak off is delayed"
                    setTextColor(Color.WHITE)
                    textSize = 22f
                    gravity = Gravity.CENTER
                })
                addView(number)
                addView(TextView(service).apply {
                    text = "Wait it out, or go back and keep scrolling under control."
                    setTextColor(Color.parseColor("#B0B7C3"))
                    textSize = 16f
                    gravity = Gravity.CENTER
                })
                addView(Button(service).apply {
                    text = "Go back"
                    setOnClickListener { onGoBack() }
                }, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = pad })
            }
            windowManager.addView(
                root,
                WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    // Not focusable: Back and the gesture must reach the page underneath, not this view.
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT,
                ),
            )
            countdown = number
            view = root
        } catch (t: Throwable) {
            AppLog.error("friction overlay show failed", t)
        }
    }

    fun dismiss() {
        val v = view ?: return
        view = null
        countdown = null
        try {
            windowManager.removeView(v)
        } catch (t: Throwable) {
            AppLog.error("friction overlay dismiss failed", t)
        }
    }

    private fun secondsText(s: Long) = "$s s"
}
