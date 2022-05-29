package com.mars.atlastrack.service

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log

class AliveService : Service() {
    private val localBinder = LocalBinder()
    override fun onBind(intent: Intent?): IBinder {
        return localBinder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        console.log("Alive start")
        return START_STICKY
    }

    override fun onDestroy() {
        console.log("Alive destroyed")
        super.onDestroy()
    }

    inner class LocalBinder : Binder() {
        internal val service: AliveService
            get() = this@AliveService
    }

    internal object console {
        val TAG = "TAG_AliveService"
        fun log(vararg message: String) {
            Log.d(TAG, message.joinToString(separator = " ") { it })
        }
    }
}