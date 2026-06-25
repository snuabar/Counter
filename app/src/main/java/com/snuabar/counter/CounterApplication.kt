package com.snuabar.counter

import android.app.Application
import android.util.Log
import com.snuabar.counter.core.service.TimerStateHolder
import dagger.hilt.android.HiltAndroidApp
import org.opencv.android.OpenCVLoader
import javax.inject.Inject

@HiltAndroidApp
class CounterApplication : Application() {
    @Inject
    lateinit var timerStateHolder: TimerStateHolder

    override fun onCreate() {
        super.onCreate()
        // Initialize OpenCV
        if (!OpenCVLoader.initDebug()) {
            Log.e(TAG, "OpenCV initialization failed!")
        } else {
            Log.d(TAG, "OpenCV initialized successfully")
        }
    }

    companion object {
        private const val TAG = "CounterApplication"
    }
}
