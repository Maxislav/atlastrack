package com.mars.atlastrack

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Context.NOTIFICATION_SERVICE
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.getSystemService
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*


class IntervalReceiver : BroadcastReceiver() {
    private val TAG = "IntervalReceiver"
    lateinit var context: Context
    override fun onReceive(context: Context, intent: Intent?) {
         this.context = context
        val currentTime: Date = Calendar.getInstance().getTime()
        val dateFormat: DateFormat = SimpleDateFormat("yyyy-MM-dd hh:mm:ss", Locale.ENGLISH)
        val strDate: String = dateFormat.format(currentTime)
        Log.d(TAG, "onReceive ${strDate}")
        // Log.d(TAG, "onReceive")
        val serviceIntent = Intent(context, LocationService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService( serviceIntent)
        }else{
            context.startService(serviceIntent)
        }

    }

    companion object {
        private const val PACKAGE_NAME = "com.mars.atlastrack"
        internal const val INTERVAL_ACTION = "$PACKAGE_NAME.action.INTERVAL_ACTION"
    }

}