package com.mars.atlastrack

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*


class IntervalReceiver : BroadcastReceiver() {
    private val TAG = "IntervalReceiver"
    lateinit var context: Context
    override fun onReceive(context: Context, intent: Intent?) {
         this.context = context
        val currentTime: Date = Calendar.getInstance().getTime()
        val dateFormat: DateFormat = SimpleDateFormat("yyyy-MM-dd hh:mm:ss")
        val strDate: String = dateFormat.format(currentTime)
        Log.d(TAG, "onReceive ${strDate}")
        // Log.d(TAG, "onReceive")





        val serviceIntent = Intent(context, LocationService::class.java)
        ContextCompat.startForegroundService(context, serviceIntent);

       /* val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager;
        val alarmIntent = Intent(context, IntervalReceiver::class.java)
        val time = System.currentTimeMillis() + 60*1000*20;
        val pIntent2 = PendingIntent.getBroadcast(
            context,
            0,
            alarmIntent,
            PendingIntent.FLAG_CANCEL_CURRENT
        )

        alarmManager.set(
            AlarmManager.RTC_WAKEUP,
            time,
            pIntent2
        )*/



    }

    companion object {
        private const val PACKAGE_NAME = "com.mars.atlastrack"
        internal const val INTERVAL_ACTION = "$PACKAGE_NAME.action.INTERVAL_ACTION"
    }

}