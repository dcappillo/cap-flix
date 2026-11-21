package com.derek.capflix.player

import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.core.view.WindowCompat
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.derek.capflix.ui.theme.CapFlixTheme

/**
 * Full-screen player. Its own activity so it can go landscape, immersive,
 * and picture-in-picture without disturbing the browsing screens.
 */
class PlayerActivity : ComponentActivity() {
    private lateinit var controller: PlayerController
    private val inPip = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemBars()

        controller = PlayerController(applicationContext)
        controller.open(intent.getStringExtra(EXTRA_ID) ?: "", intent.getStringExtra(EXTRA_SOURCE))

        // Keep Android's PiP settings current so swiping home goes straight
        // into picture-in-picture (Android 12+) instead of stopping first.
        lifecycleScope.launch {
            snapshotFlow { controller.playing to controller.source?.id }.collect { updatePipParams() }
        }

        setContent {
            CapFlixTheme {
                PlayerScreen(controller, inPip.value, onClose = { finish() })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Tapping play on something else while the player is in PiP: switch titles.
        controller.stop()
        controller.open(intent.getStringExtra(EXTRA_ID) ?: "", intent.getStringExtra(EXTRA_SOURCE))
    }

    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    // Home button / swipe up while a video plays -> keep watching in a small window.
    // Android 12+ does this itself (setAutoEnterEnabled); older versions need the hint.
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (controller.playing && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) enterPip()
    }

    private fun pipParams(): PictureInPictureParams {
        val v = controller.source?.streams?.firstOrNull { it.type == "Video" }
        val w = v?.width ?: 16
        val h = v?.height ?: 9
        val ratio = Rational(w, h).let { r ->
            // Android only allows ratios between 1:2.39 and 2.39:1
            val f = r.toFloat()
            when { f > 2.39f -> Rational(239, 100); f < 0.42f -> Rational(100, 239); else -> r }
        }
        val b = PictureInPictureParams.Builder().setAspectRatio(ratio)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            b.setAutoEnterEnabled(controller.playing)
            b.setSeamlessResizeEnabled(false)
        }
        return b.build()
    }

    private fun updatePipParams() {
        runCatching { setPictureInPictureParams(pipParams()) }
    }

    fun enterPip() {
        runCatching { enterPictureInPictureMode(pipParams()) }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip.value = isInPictureInPictureMode
        // The window just changed size: hand VLC the surface again so the
        // small window shows the picture instead of an empty frame.
        window.decorView.post { controller.reattach() }
        // Closing the PiP window with its X leaves us stopped in the background: wrap up.
        if (!isInPictureInPictureMode && lifecycle.currentState == Lifecycle.State.CREATED) finish()
    }

    override fun onStop() {
        super.onStop()
        // Screen off or app left without PiP: pause and save the spot.
        if (!isInPictureInPictureMode && !isChangingConfigurations) controller.pause()
    }

    override fun onStart() {
        super.onStart()
        if (::controller.isInitialized) window.decorView.post { controller.reattach() }
    }

    override fun onDestroy() {
        controller.stop()
        controller.release()
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_ID = "id"
        private const val EXTRA_SOURCE = "source"

        fun start(context: Context, itemId: String, mediaSourceId: String? = null) {
            context.startActivity(Intent(context, PlayerActivity::class.java).apply {
                putExtra(EXTRA_ID, itemId)
                putExtra(EXTRA_SOURCE, mediaSourceId)
                if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }
}
