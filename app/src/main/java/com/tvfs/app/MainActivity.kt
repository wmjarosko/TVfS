package com.tvfs.app

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Text
import androidx.tv.material3.ExperimentalTvMaterial3Api

data class Channel(val id: Int, val name: String, val streamUrl: String)

@OptIn(ExperimentalTvMaterial3Api::class)
class MainActivity : ComponentActivity() {

    private val testChannels = listOf(
        Channel(1, "News Channel (Test)", "https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.ism/.m3u8"),
        Channel(2, "Nature Channel (Test)", "https://cph-p2p-msl.akamaized.net/hls/live/2000341/test/master.m3u8")
    )
    private var currentChannelIndex = 0

    private var backPressedOnce = false
    private val backHandler = Handler(Looper.getMainLooper())
    private val resetBackPressedRunnable = Runnable { backPressedOnce = false }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Start playing initial channel
        playCurrentChannel()

        setContent {
            var showExitDialog by remember { mutableStateOf(false) }

            // Using this hack for the key handler instead of LocalView because Jetpack Compose for TV
            // has some specific focus mechanics and ComponentActivity manages keys itself.
            LaunchedEffect(Unit) {
                // Initial setup effect, but most key events are captured overriding dispatchKeyEvent
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                // ExoPlayer View
                val context = LocalContext.current
                AndroidView(
                    factory = {
                        PlayerView(context).apply {
                            useController = false
                            player = ExoPlayerManager.getPlayer(context)
                            layoutParams = FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.MATCH_PARENT,
                                FrameLayout.LayoutParams.MATCH_PARENT
                            )
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // High Contrast Channel Overlay Banner
                Text(
                    text = testChannels[currentChannelIndex].name,
                    color = Color.White,
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .background(Color(24, 20, 16, 210))
                )

                if (showExitDialog) {
                    Box(modifier = Modifier.fillMaxSize().background(Color(0, 0, 0, 180)), contentAlignment = Alignment.Center) {
                        Text("Are you sure you want to turn off the TV app?", color = Color.White, fontSize = 38.sp)
                    }
                }
            }
        }
    }

    private fun playCurrentChannel() {
        ExoPlayerManager.playChannel(this, testChannels[currentChannelIndex].streamUrl)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> {
                    // Channel Up
                    currentChannelIndex = (currentChannelIndex + 1) % testChannels.size
                    playCurrentChannel()
                    // Force recompose hack isn't pretty without State hoisting but this is a stub.
                    window.decorView.postInvalidate()
                    return true
                }
                KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                    // Channel Down
                    currentChannelIndex = if (currentChannelIndex - 1 < 0) testChannels.size - 1 else currentChannelIndex - 1
                    playCurrentChannel()
                    window.decorView.postInvalidate()
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (backPressedOnce) {
            backHandler.removeCallbacks(resetBackPressedRunnable)
            ExoPlayerManager.release()
            super.onBackPressed()
        } else {
            backPressedOnce = true
            // Single tap could open bottom drawer eventually. For now we just arm exit protection.
            backHandler.postDelayed(resetBackPressedRunnable, 1500)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ExoPlayerManager.release()
    }
}
