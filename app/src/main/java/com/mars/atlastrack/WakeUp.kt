package com.mars.atlastrack

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.mars.atlastrack.IntervalReceiver.Companion.INTERVAL_ACTION
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*


class WakeUp : BroadcastReceiver() {
    private val TAG = "WakeUp"
    lateinit var alarmManager: AlarmManager
    override fun onReceive(context: Context, intent: Intent?) {
       //  Log.d(TAG, "onReceive")
        val currentTime: Date = Calendar.getInstance().getTime()
        val dateFormat: DateFormat = SimpleDateFormat("yyyy-MM-dd hh:mm:ss")
        val strDate: String = dateFormat.format(currentTime)
        Log.d(TAG, "onReceive ${strDate}")
        alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager;
        val alarmIntent = Intent(context, IntervalReceiver::class.java)
        //context.startService(alarmIntent)

        alarmIntent.setAction(INTERVAL_ACTION)
            .putExtra("extra", "extra!")
      /*  val pIntent2 = PendingIntent.getBroadcast(
            context,
            0,
            alarmIntent,
            0 // PendingIntent.FLAG_CANCEL_CURRENT
        ) */
        val pIntent2 = PendingIntent.getBroadcast(
            context,
            0,
            alarmIntent,
            PendingIntent.FLAG_CANCEL_CURRENT
        )
        // alarmManager?.cancel(pIntent2)
        val time = System.currentTimeMillis();
        alarmManager?.set(
            AlarmManager.RTC_WAKEUP,
            time,
            pIntent2
        )
      /*  alarmManager.setRepeating(
            AlarmManager.RTC_WAKEUP,
            time,
            60000*15 ,
            pIntent2
        )*/


    }
    companion object {
        private const val PACKAGE_NAME = "com.mars.atlastrack"
        internal const val WAKE_UP_ACTION = "$PACKAGE_NAME.action.WAKE_UP_ACTION"
    }

}