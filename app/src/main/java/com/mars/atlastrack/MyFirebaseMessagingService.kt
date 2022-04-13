package com.mars.atlastrack

import android.content.Context
import android.util.Log
import androidx.work.*
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import java.util.concurrent.TimeUnit

class MyFirebaseMessagingService:  FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        // Log.d(TAG, "Refreshed token: $token")
        console.log("token: ${token}")
        // If you want to send messages to this application instance or
        // manage this apps subscriptions on the server side, send the
        // FCM registration token to your app server.
       //  sendRegistrationToServer(token)
    }
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        // ...

        // TODO(developer): Handle FCM messages here.
        // Not getting messages here? See why this may be: https://goo.gl/39bRNJ
       //  Log.d(TAG, "From: ${remoteMessage.from}")
        console.log("Message body: ${remoteMessage.notification?.body}")
       // startWorker(this)
        // Check if message contains a data payload.
        /*if (remoteMessage.data.isNotEmpty()) {
            Log.d(TAG, "Message data payload: ${remoteMessage.data}")

            if (*//* Check if data needs to be processed by long running job *//* true) {
                // For long-running tasks (10 seconds or more) use WorkManager.
                scheduleJob()
            } else {
                // Handle message within 10 seconds
                handleNow()
            }
        }

        // Check if message contains a notification payload.
        remoteMessage.notification?.let {
            Log.d(TAG, "Message Notification Body: ${it.body}")
        }
*/
        // Also if you intend on generating your own notifications as a result of a received FCM
        // message, here is where that should be initiated. See sendNotification method below.
    }
    private fun startWorker(context: Context){
        val workManager = WorkManager.getInstance(context)
        val networkConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val myWorker = OneTimeWorkRequestBuilder<PeriodWorker>()
            .setConstraints(networkConstraints)
            .build()

        workManager.enqueue(
            myWorker
        )
    }
    internal object console {
        val TAG = "MyFirebaseMessagingService"
        fun log(message: String) {
            Log.d(TAG, message)
        }
    }

}