package com.mars.atlastrack

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.work.*
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import java.lang.Error
import java.util.concurrent.TimeUnit

class MyFirebaseMessagingService:  FirebaseMessagingService() {
    var app: ATApplication? = null
    override fun onNewToken(token: String) {
        console.log("token: ${token}")
    }
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        // ...

        // TODO(developer): Handle FCM messages here.
        // Not getting messages here? See why this may be: https://goo.gl/39bRNJ
       //  Log.d(TAG, "From: ${remoteMessage.from}")
        console.log("Message action: ${remoteMessage.data["action"]} ${remoteMessage.data["date"]}")
        when(remoteMessage.data["action"]){
            "location" -> {
                startService(this)
            }
        }
    }


    private fun startService(context: Context): Boolean {
        app = context.applicationContext as ATApplication
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
        return isOk
    }


    internal object console {
        val TAG = "MyFirebaseMessagingService"
        fun log(message: String) {
            Log.d(TAG, message)
        }
    }


}