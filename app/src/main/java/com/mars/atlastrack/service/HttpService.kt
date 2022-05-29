package com.mars.atlastrack.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.mars.atlastrack.ATApplication
import com.mars.atlastrack.R
import com.mars.atlastrack.worker.FirebaseWorker

class HttpService: Service() {
    private val localBinder = LocalBinder()

   //  private var app: ATApplication =  applicationContext.applicationContext as ATApplication

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        console.log("ololo")
        val token = intent?.getStringExtra(TOKEN)
        if (token != null) {
            notificationStart()
            saveToken(token)
        } else {
            stopSelf()
        }
        return START_STICKY
    }

    private fun notificationStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Location Service Channel",
                NotificationManager.IMPORTANCE_HIGH
            )
            val manager = getSystemService(NotificationManager::class.java)
            serviceChannel.description = "no sound";
            serviceChannel.setShowBadge(true)
            serviceChannel.setSound(null, null) //< ----ignore sound
            serviceChannel.enableLights(false)
            // serviceChannel.lightColor = 0xffff01
            // serviceChannel.shouldShowLights()
            manager.createNotificationChannel(serviceChannel)
        }
        val cancelIntent = Intent("CANCEL_ID")

        val pendingIntent = PendingIntent.getBroadcast(
            this, 0,
            cancelIntent, PendingIntent.FLAG_CANCEL_CURRENT
        )

        val nfc = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
           // .setLights(Color.BLUE, 200, 1000)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setContentText("Token update...")
            .setOngoing(true)

            .build()

        startForeground(2, nfc)
    }


    private fun saveToken(token: String) {
        var app: ATApplication =  applicationContext.applicationContext as ATApplication
        val data = Data.Builder()
        data.putString(FirebaseWorker.TOKEN, token)
        data.putString(FirebaseWorker.DEVICE_ID, app.deviceId)
        val workManager = WorkManager.getInstance(this)
        val networkConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val uploadTask = OneTimeWorkRequestBuilder<FirebaseWorker>()
            .setConstraints(networkConstraints)
            .setInputData(data.build())
            .build()
        workManager.getWorkInfoByIdLiveData(uploadTask.id)
            .observeForever { workInfo: WorkInfo? ->
                if (workInfo != null && workInfo.state.isFinished) {
                    LocationService.console.log(
                        "response firebase sum ${
                            workInfo.outputData.getString(
                                "body"
                            )
                        }"
                    )
                    stopSelf()
                }
            }
        workManager.enqueue(uploadTask)
    }

    override fun onDestroy() {
        super.onDestroy()
        console.log("Destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? {
        return localBinder
    }

    inner class LocalBinder : Binder() {
        internal val service: HttpService
            get() = this@HttpService
    }

    companion object {
        val TOKEN = "token"
        val NOTIFICATION_CHANNEL_ID = "http_service_id"
    }

    internal object console {
        val TAG = "TAG_HttpService"
        fun log(vararg message: String) {
            Log.d(TAG, message.joinToString(separator = " ") { it })
        }
    }

}