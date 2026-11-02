package com.tvfs.app

import android.app.Application
import com.tvfs.app.data.TvfsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TvfsApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CoroutineScope(Dispatchers.IO).launch {
            TvfsRepository.ensureInitialized(this@TvfsApplication)
        }
    }
}
