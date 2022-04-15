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
import androidx.work.*
import com.mars.atlastrack.IntervalReceiver.Companion.INTERVAL_ACTION
import com.mars.atlastrack.SharedPreferenceUtil.THIRTY_MINUTES
import com.mars.atlastrack.SharedPreferenceUtil.isDozing
import kotlinx.coroutines.delay
import java.lang.Error
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit


class WakeUp : BroadcastReceiver() {
    private val TAG = "WakeUp"
    lateinit var alarmManager: AlarmManager
    lateinit var context: Context
    override fun onReceive(context: Context, intent: Intent?) {

        this.context = context
        val currentTime: Date = Calendar.getInstance().getTime()
        val dateFormat: DateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH)
        val strDate: String = dateFormat.format(currentTime)
        Log.d(TAG, "WakeUp ${strDate}")
        if(intent?.action === "android.intent.action.DREAMING_STOPPED"){
           // start()
            startService()
            //startWorker(context)
           // scheduleNextAlarm()
        }else{
            startService()
           //  startWorker(context)
           // scheduleNextAlarm()
         //   start()
        }
    }

    private fun startWorker(context: Context){
        val workManager = WorkManager.getInstance(context)
        workManager.cancelAllWorkByTag(WORKER_TAG)
        val networkConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val myWorker = PeriodicWorkRequestBuilder<PeriodWorker>(20, TimeUnit.MINUTES)
            .addTag(WORKER_TAG)
            .setConstraints(networkConstraints)
            .build()

        workManager.enqueue(
            myWorker
        )
    }

    private fun scheduleNextAlarm(){
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
                0,
            )
            val time = System.currentTimeMillis() + THIRTY_MINUTES

            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pi)

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

    inner class ImageDownloadWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
        override  fun doWork(): Result {
            startService()
            Thread.sleep(10000)
            return  Result.success()
        }
    }


    companion object {
        private const val PACKAGE_NAME = "com.mars.atlastrack"
        internal const val WAKE_UP_ACTION = "$PACKAGE_NAME.action.WAKE_UP_ACTION"
        internal const val WORKER_TAG = "WIFIJOB1"
    }

}