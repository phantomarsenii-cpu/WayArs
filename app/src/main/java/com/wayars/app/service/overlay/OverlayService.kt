package com.wayars.app.service.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.wayars.app.MainActivity
import com.wayars.app.R
import com.wayars.app.appContainer
import com.wayars.app.presentation.ui.theme.WayArsTheme
import com.wayars.app.presentation.widget.OverlayContent
import com.wayars.app.presentation.widget.OverlayLifecycleOwner
import com.wayars.app.presentation.widget.OverlayState
import com.wayars.app.service.accessibility.ScanningState
import kotlinx.coroutines.launch

/**
 * Foreground service that hosts the floating "verdict" widget over other
 * apps via WindowManager. It never touches other apps' windows or dispatches
 * input — it only draws its own overlay window and lets the driver tap its
 * own Accept/Reject buttons.
 *
 * The card itself is only attached to the WindowManager while there is an
 * actual order to show — no permanent "Waiting for order…" plaque cluttering
 * the driver's map. The underlying foreground service (and its status-bar
 * icon) keeps running the whole time Active is ON, ready to pop the card up
 * the instant a new order is parsed.
 */
class OverlayService : LifecycleService() {

    private lateinit var windowManager: WindowManager
    private var composeView: ComposeView? = null
    private var isViewAttached = false
    private val overlayLifecycleOwner = OverlayLifecycleOwner()

    private var layoutParams: WindowManager.LayoutParams? = null
    private var initialX = 0
    private var initialY = 0
    private var touchStartX = 0f
    private var touchStartY = 0f

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        overlayLifecycleOwner.performRestore()
        overlayLifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_START)
        overlayLifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        // Show/hide the card purely based on whether there's something to show —
        // and stop everything immediately if Active gets flipped off elsewhere.
        lifecycleScope.launch {
            OverlayState.latestEvaluation.collect { evaluation ->
                if (evaluation != null && ScanningState.isActive.value) attachView() else detachView()
            }
        }
        lifecycleScope.launch {
            ScanningState.isActive.collect { active -> if (!active) stopSelf() }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    private fun buildComposeViewIfNeeded(): ComposeView {
        composeView?.let { return it }

        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(overlayLifecycleOwner)
            setViewTreeViewModelStoreOwner(overlayLifecycleOwner)
            setViewTreeSavedStateRegistryOwner(overlayLifecycleOwner)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)

            setOnTouchListener { _, event -> handleDrag(event, this) }

            setContent {
                val evaluation by OverlayState.latestEvaluation.collectAsStateWithLifecycle()
                WayArsTheme {
                    OverlayContent(
                        evaluation = evaluation,
                        onAccept = { onDecision(accepted = true) },
                        onReject = { onDecision(accepted = false) },
                        onSettings = { openApp() },
                        onClose = { stopSelf() }
                    )
                }
            }
        }
        composeView = view
        return view
    }

    private fun attachView() {
        if (isViewAttached) return
        val view = buildComposeViewIfNeeded()

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val params = layoutParams ?: WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            // FLAG_NOT_TOUCH_MODAL lets touches outside the card fall through to
            // the app underneath, while the card itself still reliably receives
            // its own taps (buttons were previously eating the first tap without it).
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 40
            y = 160
        }
        layoutParams = params

        runCatching { windowManager.addView(view, params) }
        isViewAttached = true
    }

    private fun detachView() {
        if (!isViewAttached) return
        composeView?.let { runCatching { windowManager.removeView(it) } }
        isViewAttached = false
    }

    private fun handleDrag(event: MotionEvent, view: View): Boolean {
        val params = layoutParams ?: return false
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                initialX = params.x
                initialY = params.y
                touchStartX = event.rawX
                touchStartY = event.rawY
                return false // let clicks on children (buttons) pass through
            }
            MotionEvent.ACTION_MOVE -> {
                params.x = initialX + (event.rawX - touchStartX).toInt()
                params.y = initialY + (event.rawY - touchStartY).toInt()
                windowManager.updateViewLayout(view, params)
            }
        }
        return false
    }

    /**
     * A row is only ever written to Room here, on Accept — never during
     * scanning. This is deliberate: it's what stopped every re-scan of the
     * same still-visible order from spamming duplicate rows into history.
     * Reject just dismisses the card with no history entry.
     */
    private fun onDecision(accepted: Boolean) {
        val evaluation = OverlayState.latestEvaluation.value
        OverlayState.clear() // hide instantly, before the DB write even starts
        if (accepted && evaluation != null) {
            lifecycleScope.launch {
                runCatching {
                    val id = appContainer().orderRepository.record(evaluation)
                    appContainer().orderRepository.markDecision(id, true)
                }
            }
        }
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "WayArs", NotificationManager.IMPORTANCE_MIN
            )
            manager.createNotificationChannel(channel)
        }

        val openAppIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.settings_overlay_hint))
            .setSmallIcon(R.drawable.ic_stat_wayars)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        detachView()
        overlayLifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        OverlayState.clear()
        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_ID = 42
        private const val CHANNEL_ID = "wayars_overlay"
    }
}
