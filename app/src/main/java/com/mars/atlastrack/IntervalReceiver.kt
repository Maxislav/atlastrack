package com.mars.atlastrack

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Context.ALARM_SERVICE
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat.getSystemService
import com.mars.atlastrack.SharedPreferenceUtil.TWENTY_MINUTES
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
        val serviceIntent = Intent(context, LocationService::class.java)

        val alarmManager = context.getSystemService(ALARM_SERVICE) as AlarmManager;
        val alarmIntent = Intent(context, LocationService::class.java)
        alarmIntent.action = INTERVAL_ACTION
        val pIntent2 = PendingIntent.getService(
            context,
            0,
            alarmIntent,
            PendingIntent.FLAG_CANCEL_CURRENT
        )
        val nextAlarmTime = System.currentTimeMillis()//  + TWENTY_MINUTES


        alarmManager.setRepeating(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis(),
            TWENTY_MINUTES,
            pIntent2
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        }else{
            context.startService(serviceIntent)
        }
    }

    companion object {
        private const val PACKAGE_NAME = "com.mars.atlastrack"
        internal const val INTERVAL_ACTION = "$PACKAGE_NAME.action.INTERVAL_ACTION"
    }

}