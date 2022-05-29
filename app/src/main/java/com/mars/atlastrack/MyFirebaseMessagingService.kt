package com.mars.atlastrack

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.mars.atlastrack.service.HttpService
import com.mars.atlastrack.service.HttpService.Companion.TOKEN
import com.mars.atlastrack.service.LocationService
import java.lang.Error

class MyFirebaseMessagingService : FirebaseMessagingService() {
    var app: ATApplication? = null
    override fun onNewToken(token: String) {
        console.log("token: ${token}")
        saveToken(token);
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        // ...

        // TODO(developer): Handle FCM messages here.
        // Not getting messages here? See why this may be: https://goo.gl/39bRNJ
        //  Log.d(TAG, "From: ${remoteMessage.from}")
        console.log("Message action: ${remoteMessage.data["action"]} ${remoteMessage.data["date"]}")
        when (remoteMessage.data["action"]) {
            "location" -> {
                startService(this)
            }
        }
    }

    private fun saveToken(token: String) {
        val serviceIntent = Intent(this, HttpService::class.java)
        serviceIntent.putExtra(TOKEN, token)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            this.startForegroundService(serviceIntent)
        } else {
            this.startService(serviceIntent)
        }
    }


    private fun startService(context: Context): Boolean {
        app = context.applicationContext as ATApplication
        if (app?.serviceIsRunning == true) {
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