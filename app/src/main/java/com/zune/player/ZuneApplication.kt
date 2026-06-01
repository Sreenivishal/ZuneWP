package com.zune.player

import android.app.Application
import android.util.Log
import com.maxrave.data.di.loader.loadAllModules
import com.maxrave.simpmusic.di.viewModelModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.loadKoinModules
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

/**
 * ZuneApplication orchestrates single-instance initializations
 * for the streaming core extractors and global configurations using Koin.
 */
class ZuneApplication : Application() {
    private val TAG = "ZuneApplication"

    override fun onCreate() {
        super.onCreate()
        
        try {
            Log.d(TAG, "Initializing Koin Container...")
            startKoin {
                androidLogger(level = Level.DEBUG)
                androidContext(this@ZuneApplication)
                loadAllModules()
                loadKoinModules(viewModelModule)
            }
            Log.d(TAG, "Koin successfully initialized with Metrolist modules.")
        } catch (e: Exception) {
            Log.e(TAG, "Koin failed to initialize", e)
        }
    }
}
