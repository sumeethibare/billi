package com.example.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.R
import com.example.ui.overlay.QuickAddBubbleActivity
import kotlin.math.abs

/**
 * Opt-in Floating "Trip Bubble" Overlay & Persistent Notification Shortcut Service.
 * - Shows a persistent active-trip notification shortcut (tap to log an expense in 3 seconds).
 * - When SYSTEM_ALERT_WINDOW is granted by the user, also attaches a draggable floating ₹+ bubble
 *   over any app (Google Maps, WhatsApp, etc.) that opens QuickAddBubbleActivity cleanly.
 */
class TripBubbleService : Service() {

    private var windowManager: WindowManager? = null
    private var bubbleView: View? = null
    private var activeTripId: String = ""
    private var activeTripTitle: String = "Active Trip"

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                removeFloatingBubble()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                activeTripId = intent?.getStringExtra(EXTRA_TRIP_ID).orEmpty()
                activeTripTitle = intent?.getStringExtra(EXTRA_TRIP_TITLE)?.takeIf { it.isNotBlank() } ?: "Active Trip"
                startForegroundWithNotification()
                if (Settings.canDrawOverlays(this)) {
                    attachFloatingBubbleIfNeeded()
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        removeFloatingBubble()
        super.onDestroy()
    }

    private fun startForegroundWithNotification() {
        createChannelIfNeeded()

        val quickAddIntent = Intent(this, QuickAddBubbleActivity::class.java).apply {
            putExtra(QuickAddBubbleActivity.EXTRA_TRIP_ID, activeTripId)
            putExtra(QuickAddBubbleActivity.EXTRA_TRIP_TITLE, activeTripTitle)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val quickAddPending = PendingIntent.getActivity(
            this,
            101,
            quickAddIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, TripBubbleService::class.java).apply { action = ACTION_STOP }
        val stopPending = PendingIntent.getService(
            this,
            102,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Billi • $activeTripTitle")
            .setContentText("Tap to quick-log ₹60 auto, ₹120 chai, or any trip bill in 3 seconds")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(quickAddPending)
            .addAction(0, "+ Quick Add", quickAddPending)
            .addAction(0, "Hide Bubble", stopPending)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val existing = nm.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val ch = NotificationChannel(
                    CHANNEL_ID,
                    "Trip Quick-Add Bubble",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Persistent shortcut for 3-second expense logging during active trips."
                    setShowBadge(false)
                }
                nm.createNotificationChannel(ch)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attachFloatingBubbleIfNeeded() {
        if (bubbleView != null) return
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager = wm

        val density = resources.displayMetrics.density
        val sizePx = (56 * density).toInt()
        val container = FrameLayout(this).apply {
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#0F6A4F"))
                setStroke((2 * density).toInt(), Color.parseColor("#E6F4EE"))
            }
            background = bg
            elevation = 10 * density
            contentDescription = "Billi Trip Bubble Quick Add"
        }
        val label = TextView(this).apply {
            text = "₹+"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        container.addView(
            label,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            sizePx,
            sizePx,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = resources.displayMetrics.widthPixels - sizePx - (14 * density).toInt()
            y = (resources.displayMetrics.heightPixels * 0.42f).toInt()
        }

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var moved = false

        container.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (abs(dx) > 10 || abs(dy) > 10) moved = true
                    params.x = initialX + dx
                    params.y = initialY + dy
                    try {
                        wm.updateViewLayout(container, params)
                    } catch (_: Exception) {
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        openQuickAddOverlay()
                    } else {
                        // Snap smoothly to nearest left or right screen edge
                        val screenW = resources.displayMetrics.widthPixels
                        val margin = (12 * density).toInt()
                        params.x = if (params.x + sizePx / 2 < screenW / 2) margin else screenW - sizePx - margin
                        try {
                            wm.updateViewLayout(container, params)
                        } catch (_: Exception) {
                        }
                    }
                    true
                }
                else -> false
            }
        }

        try {
            wm.addView(container, params)
            bubbleView = container
        } catch (_: Exception) {
            bubbleView = null
        }
    }

    private fun openQuickAddOverlay() {
        val intent = Intent(this, QuickAddBubbleActivity::class.java).apply {
            putExtra(QuickAddBubbleActivity.EXTRA_TRIP_ID, activeTripId)
            putExtra(QuickAddBubbleActivity.EXTRA_TRIP_TITLE, activeTripTitle)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    private fun removeFloatingBubble() {
        val v = bubbleView
        if (v != null) {
            try {
                windowManager?.removeView(v)
            } catch (_: Exception) {
            }
            bubbleView = null
        }
    }

    companion object {
        const val CHANNEL_ID = "billi_trip_bubble_channel"
        const val NOTIFICATION_ID = 4108
        const val ACTION_START = "com.isuh.billi.action.START_BUBBLE"
        const val ACTION_STOP = "com.isuh.billi.action.STOP_BUBBLE"
        const val EXTRA_TRIP_ID = "extra_trip_id"
        const val EXTRA_TRIP_TITLE = "extra_trip_title"

        fun start(context: Context, tripId: String, tripTitle: String) {
            val intent = Intent(context, TripBubbleService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_TRIP_ID, tripId)
                putExtra(EXTRA_TRIP_TITLE, tripTitle)
            }
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (_: Exception) {
            }
        }

        fun canDrawOverlay(context: Context): Boolean = Settings.canDrawOverlays(context)

        fun overlaySettingsIntent(context: Context): Intent =
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                android.net.Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        fun stop(context: Context) {
            val intent = Intent(context, TripBubbleService::class.java).apply {
                action = ACTION_STOP
            }
            try {
                context.startService(intent)
            } catch (_: Exception) {
            }
        }
    }
}
