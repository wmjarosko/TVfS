package com.tvfs.app

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer

object ExoPlayerManager {
    private var player: ExoPlayer? = null

    fun getPlayer(context: Context): ExoPlayer {
        if (player == null) {
            player = ExoPlayer.Builder(context.applicationContext).build()
        }
        return player!!
    }

    fun playChannel(context: Context, url: String) {
        val exoPlayer = getPlayer(context)
        val mediaItem = MediaItem.fromUri(url)
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.play()
    }

    fun release() {
        player?.release()
        player = null
    }
}
