package com.tvfs.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.tvfs.app.data.TvfsRepository

/**
 * Legacy MainActivity router that delegates to HubActivity or LivePlayerActivity
 * based on the SDS 7.1 Remote Bootstrap Configuration (`behavior.boot_mode`).
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val config = TvfsRepository.getBootstrapConfig(this)
        val destination = if (config.behavior.bootMode.equals("live", ignoreCase = true)) {
            LivePlayerActivity::class.java
        } else {
            HubActivity::class.java
        }
        startActivity(Intent(this, destination))
        finish()
    }
}
