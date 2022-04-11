package com.mars.atlastrack

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat.getSystemService
import com.mars.atlastrack.IntervalReceiver.Companion.INTERVAL_ACTION
import com.mars.atlastrack.SharedPreferenceUtil.isDozing
import java.lang.Error
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*


class WakeUp : BroadcastReceiver() {
    private val TAG = "WakeUp"
    lateinit var alarmManager: AlarmManager
    lateinit var context: Context
    override fun onReceive(context: Context, intent: Intent?) {

        this.context = context
        val currentTime: Date = Calendar.getInstance().getTime()
        val dateFormat: DateFormat = SimpleDateFormat("yyyy-MM-dd hh:mm:ss", Locale.ENGLISH)
        val strDate: String = dateFormat.format(currentTime)
        Log.d(TAG, "WakeUp ${strDate}")
        val isStarted = startService()
        if (!isStarted) {
            alarmManager =
                context.getSystemService(AppCompatActivity.ALARM_SERVICE) as AlarmManager;
            val alarmIntent = Intent(context, WakeUp::class.java)
            alarmIntent.action = WAKE_UP_ACTION
            val pi = PendingIntent.getBroadcast(
                context,
                0,
                alarmIntent,
                PendingIntent.FLAG_CANCEL_CURRENT
            )
            val time = System.currentTimeMillis() + 1000 * 1 * 5
            if (isDozing(context)) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pi)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, time, pi)
            }
        }

        val t = Thread {
            Thread.sleep(10 * 1000)
        }
        t.start()

    }

    private fun startService(): Boolean {
        val serviceIntent = Intent(context, LocationService::class.java)
        var isOk = true
        isOk = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)

            } else {
                context.startService(serviceIntent)
            }
            true
        } catch (e: Error) {
            false
        }
        return isOk
    }

    companion object {
        private const val PACKAGE_NAME = "com.mars.atlastrack"
        internal const val WAKE_UP_ACTION = "$PACKAGE_NAME.action.WAKE_UP_ACTION"
    }

}