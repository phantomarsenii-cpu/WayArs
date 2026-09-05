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
import com.wayars.app.data.prefs.LanguagePrefs
import com.wayars.app.presentation.ui.theme.WayArsTheme
import com.wayars.app.presentation.widget.OverlayContent
import com.wayars.app.presentation.widget.OverlayLifecycleOwner
import com.wayars.app.presentation.widget.OverlayState
import com.wayars.app.service.accessibility.ScanningState
import com.wayars.app.util.LocaleManager
import kotlinx.coroutines.launch

/**
 * Foreground service that hosts the floating "verdict" widget over other
 * apps via WindowManager.
 *
 * Dragging is implemented with Compose's OWN pointer-input system on a
 * dedicated header handle (see [OverlayContent]'s onDragBy), NOT a raw
 * View.OnTouchListener on the whole card. The earlier raw-listener approach
 * intercepted every touch before Compose's click detection ever saw it,
 * which was silently swallowing a lot of Accept/Reject taps — a plain
 * Modifier.pointerInput drag on just the header avoids that entirely and
 * leaves the buttons completely untouched by drag logic.
 */
class OverlayService : LifecycleService() {

    private lateinit var windowManager: WindowManager
    private var composeView: ComposeView? = null
    private var isViewAttached = false
    private val overlayLifecycleOwner = OverlayLifecycleOwner()
    private var layoutParams: WindowManager.LayoutParams? = null

    // The Application's locale is only re-applied at process cold start, so a
    // language change made while the app was already running never reached a
    // freshly-started Service before — the overlay kept showing the OLD
    // language ("Язык приложения не соответствует на оверлее"). Re-wrapping
    // here, every time the service is (re)created, fixes that.
    override fun attachBaseContext(newBase: Context) {
        val languageCode = LanguagePrefs.read(newBase) ?: LocaleManager.resolveInitialLanguage()
        super.attachBaseContext(LocaleManager.wrap(newBase, languageCode))
    }

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

            setContent {
                val evaluation by OverlayState.latestEvaluation.collectAsStateWithLifecycle()
                WayArsTheme {
                    OverlayContent(
                        evaluation = evaluation,
                        onAccept = { onDecision(accepted = true) },
                        onReject = { onDecision(accepted = false) },
                        onSettings = { openApp() },
                        onClose = { onDecision(accepted = false) },
                        onDragBy = ::moveWindowBy
                    )
                }
            }
        }
        composeView = view
        return view
    }

    private fun moveWindowBy(dx: Float, dy: Float) {
        val params = layoutParams ?: return
        val view = composeView ?: return
        params.x += dx.toInt()
        params.y += dy.toInt()
        runCatching { windowManager.updateViewLayout(view, params) }
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

    /**
     * A row is only ever written to Room here, on Accept — Reject dismisses
     * with no history entry. Either way we (a) hide instantly and (b) start a
     * short scanning cooldown, since the order screen underneath often keeps
     * updating its own live text for a few seconds after the driver already
     * made a decision, which used to make the card pop right back up.
     */
    private fun onDecision(accepted: Boolean) {
        val evaluation = OverlayState.latestEvaluation.value
        OverlayState.clear()
        ScanningState.suppressScanningBriefly()
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
