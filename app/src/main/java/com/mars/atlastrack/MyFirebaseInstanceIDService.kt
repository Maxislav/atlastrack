package com.mars.atlastrack

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseInstanceIDService: FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        console.log("From: ${message.from}")
        super.onMessageReceived(message)
    }
    override fun onNewToken(token: String) {
        console.log("Refreshed token: $token")

        // If you want to send messages to this application instance or
        // manage this apps subscriptions on the server side, send the
        // FCM registration token to your app server.
       // sendRegistrationToServer(token)
    }
    internal object console {
        val TAG = "MyFirebaseInstanceIDService"
        fun log(message: String) {
            Log.d(TAG, message)
        }
    }
}