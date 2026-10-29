package app.claudering

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.TextView
import kotlin.math.abs

/**
 * Hält das Rädchen als Overlay am Leben. Läuft als Vordergrund-Dienst,
 * damit Android es nicht nach ein paar Minuten beendet.
 */
class OverlayService : Service() {

    companion object {
        const val ACTION_REFRESH = "app.claudering.REFRESH"
        const val ACTION_STOP = "app.claudering.STOP"
        private const val CHANNEL_ID = "ring"
        private const val NOTIFICATION_ID = 1
        private const val RING_DP = 46
        private const val BUBBLE_MS = 5_000L
        private const val TICK_MS = 60_000L
    }

    private lateinit var windowManager: WindowManager
    private val handler = Handler(Looper.getMainLooper())

    private var ringView: RingView? = null
    private lateinit var ringParams: WindowManager.LayoutParams
    private var bubbleView: TextView? = null

    private val hideBubble = Runnable { removeBubble() }
    private val tick = object : Runnable {
        override fun run() {
            refreshRing()
            handler.postDelayed(this, TICK_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        startInForeground()
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        addRing()
        handler.post(tick)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        removeBubble()
        refreshRing()
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        removeBubble()
        ringView?.let { windowManager.removeView(it) }
        ringView = null
        super.onDestroy()
    }

    // ---------- Rädchen ----------

    @SuppressLint("ClickableViewAccessibility")
    private fun addRing() {
        val size = dp(RING_DP)
        val screenWidth = resources.displayMetrics.widthPixels
        val (startX, startY) = UsageStore.loadPosition(this)
            ?: ((screenWidth - size - dp(12)) to dp(180))

        ringParams = WindowManager.LayoutParams(
            size,
            size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = startX
            y = startY
        }

        val view = RingView(this)
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        var downX = 0f
        var downY = 0f
        var startParamX = 0
        var startParamY = 0
        var moved = false

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startParamX = ringParams.x
                    startParamY = ringParams.y
                    moved = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (!moved && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                        moved = true
                        removeBubble()
                    }
                    if (moved) {
                        ringParams.x = startParamX + dx.toInt()
                        ringParams.y = startParamY + dy.toInt()
                        windowManager.updateViewLayout(view, ringParams)
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (moved) {
                        keepRingOnScreen(view)
                        UsageStore.savePosition(this, ringParams.x, ringParams.y)
                    } else {
                        toggleBubble()
                    }
                }
            }
            true
        }

        windowManager.addView(view, ringParams)
        ringView = view
        refreshRing()
    }

    private fun keepRingOnScreen(view: View) {
        val metrics = resources.displayMetrics
        val size = ringParams.width
        ringParams.x = ringParams.x.coerceIn(0, metrics.widthPixels - size)
        ringParams.y = ringParams.y.coerceIn(0, metrics.heightPixels - size)
        windowManager.updateViewLayout(view, ringParams)
    }

    private fun refreshRing() {
        val usage = UsageStore.load(this)
        ringView?.percentLeft = usage?.displayPercent(System.currentTimeMillis())
    }

    // ---------- Sprechblase ----------

    private fun toggleBubble() {
        if (bubbleView != null) removeBubble() else showBubble()
    }

    private fun showBubble() {
        val bubble = TextView(this).apply {
            text = BubbleText.build(UsageStore.load(this@OverlayService))
            setTextColor(getColor(R.color.ink))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setLineSpacing(0f, 1.15f)
            setBackgroundResource(R.drawable.bubble_bg)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            maxWidth = dp(250)
            alpha = 0f
            setOnClickListener { removeBubble() }
        }

        // Größe vorab messen, damit die Blase neben das Rädchen passt.
        val screenWidth = resources.displayMetrics.widthPixels
        bubble.measure(
            View.MeasureSpec.makeMeasureSpec(dp(250), View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        val ringSize = ringParams.width
        val gap = dp(8)
        val ringOnRight = ringParams.x + ringSize / 2 > screenWidth / 2
        val bubbleX = if (ringOnRight) {
            ringParams.x - bubble.measuredWidth - gap
        } else {
            ringParams.x + ringSize + gap
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = bubbleX.coerceIn(0, (screenWidth - bubble.measuredWidth).coerceAtLeast(0))
            y = ringParams.y.coerceAtLeast(0)
        }

        windowManager.addView(bubble, params)
        bubbleView = bubble
        bubble.animate().alpha(1f).setDuration(180).start()
        handler.postDelayed(hideBubble, BUBBLE_MS)
    }

    private fun removeBubble() {
        handler.removeCallbacks(hideBubble)
        bubbleView?.let { windowManager.removeView(it) }
        bubbleView = null
    }

    // ---------- Benachrichtigung ----------

    private fun startInForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Claude-Rädchen", NotificationManager.IMPORTANCE_MIN),
        )

        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, OverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_ring)
            .setContentTitle("Claude-Rädchen ist aktiv")
            .setContentText("Tippen, um neue Werte einzutragen")
            .setContentIntent(openApp)
            .setOngoing(true)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, R.drawable.ic_ring),
                    "Ausblenden",
                    stop,
                ).build(),
            )
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
