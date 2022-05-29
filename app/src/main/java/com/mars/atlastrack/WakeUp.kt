package com.mars.atlastrack

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import com.mars.atlastrack.SharedPreferenceUtil.FIVE_MINUTES
import com.mars.atlastrack.service.LocationService
import java.lang.Error
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*


class WakeUp : BroadcastReceiver() {
    private val TAG = "WakeUp"
    var app: ATApplication? = null
    lateinit var alarmManager: AlarmManager
    //lateinit var context: Context

    // var latch: CountDownLatch? = null
    override fun onReceive(context: Context, intent: Intent?) {

       // this.context = context
        val currentTime: Date = Calendar.getInstance().getTime()
        val dateFormat: DateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH)
        val strDate: String = dateFormat.format(currentTime)
        Log.d(TAG, "WakeUp ${strDate}")
        // latch = CountDownLatch(1)
        app = context.applicationContext as ATApplication
        // notificationCreate()
        startService(context)

    }

    private fun notificationCreate(context: Context){
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                "NOTIFICATION_CHANNEL_ID",
                "Location Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )

            serviceChannel.description = "no sound";
            serviceChannel.setShowBadge(true)
            serviceChannel.setSound(null, null) //< ----ignore sound
            serviceChannel.enableLights(false)
            manager.createNotificationChannel(serviceChannel)
        }
        val cancelIntent = Intent("CANCEL_ID")

        val pendingIntent = PendingIntent.getBroadcast(
            context, 0,
            cancelIntent, PendingIntent.FLAG_CANCEL_CURRENT
        )

        val nfc = NotificationCompat.Builder(context, "NOTIFICATION_CHANNEL_ID")
            .setContentTitle(context.getString(R.string.app_name))
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setContentText("Location update...")
            .setOngoing(true)
            .build()
        manager.notify(2, nfc);
        // startForeground(1, nfc)
    }

    fun scheduleNextAlarm(context: Context) {
        alarmManager =
            context.getSystemService(AppCompatActivity.ALARM_SERVICE) as AlarmManager;
        val alarmIntent = Intent(context, WakeUp::class.java)
        alarmIntent.action = "${WAKE_UP_ACTION}-${System.currentTimeMillis()}"
        val pi = PendingIntent.getBroadcast(
            context,
            0,
            alarmIntent,
            PendingIntent.FLAG_ONE_SHOT
        )
        val time = System.currentTimeMillis() + FIVE_MINUTES

        alarmManager.set(AlarmManager.RTC_WAKEUP, time, pi)

        val dateFormat: DateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH)
        val strDate: String = dateFormat.format(Date(time))
        console.log("setup next alarm: ${strDate}")
        Thread.sleep(5000)
    }

    private fun startService(context: Context): Boolean {
        if(app?.serviceIsRunning == true){
           return false
        }

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

        // Thread.sleep(10000)

        return isOk
    }



    companion object {
        private const val PACKAGE_NAME = "com.mars.atlastrack"
        internal const val WAKE_UP_ACTION = "$PACKAGE_NAME.action.WAKE_UP_ACTION"
        internal const val WORKER_TAG = "WIFIJOB1"
    }

    internal object console {
        val TAG = "WakeUp"
        fun log(message: String) {
            Log.d(TAG, message)
        }
    }

}