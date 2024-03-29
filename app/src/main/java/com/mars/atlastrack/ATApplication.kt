package com.mars.atlastrack

import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.ktx.messaging
import java.util.*

class ATApplication : Application() {


    var deviceId: String
        get() {
            val sharedPreferences = this.getSharedPreferences("DATA", Context.MODE_PRIVATE)
            var mDeviceId = sharedPreferences.getString("DEVICE_ID", null)
            if (mDeviceId == null) {
                // val dateFormat: DateFormat = SimpleDateFormat("00yyyyMMddHH", Locale.ENGLISH)
                // deviceId = dateFormat.format(System.currentTimeMillis())
                mDeviceId = BuildConfig.DEVICE_ID
                sharedPreferences.edit().putString("DEVICE_ID", mDeviceId).apply()
            }

            return mDeviceId
        }
        set(value) {
            val sharedPreferences = this.getSharedPreferences("DATA", Context.MODE_PRIVATE)
            sharedPreferences.edit().putString("DEVICE_ID", value).apply()
        }

    var serviceIsRunning = false
    var batteryReceiver: BatteryReceiver? = null
    var batteryReceiverRegistered = false
    var batLevel: Number = 0


    fun registerReceiver(callback: (level: Number) -> Unit) {
        if (batteryReceiverRegistered) {
            callback(batLevel)
        }
        batteryReceiver = BatteryReceiver {
            batLevel = it
            callback(it)
        }
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        batteryReceiverRegistered = true
    }

    fun unregisterReceiver() {
        if (batteryReceiverRegistered) {
            batteryReceiver?.let {
                unregisterReceiver(it);
            }
        }
        batteryReceiverRegistered = false
    }

    fun registerDreamingStop() {
        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager;
        val alarmIntent = Intent(this, WakeUp::class.java)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent) {
                console.log("received broacast intent: $intent")
                if (intent.action == Intent.ACTION_DREAMING_STOPPED) {
                    val pi = PendingIntent.getBroadcast(
                        this@ATApplication,
                        0,
                        alarmIntent,
                        PendingIntent.FLAG_CANCEL_CURRENT
                    )
                    val time = System.currentTimeMillis();
                    console.log("received dream stopped")
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, time, pi)
                }
            }
        }
        val filter = IntentFilter("android.intent.action.DREAMING_STOPPED");
        super.registerReceiver(receiver, filter);
    }

    inner class BatteryReceiver(val callback: (level: Number) -> Unit) : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val level = intent!!.getIntExtra("level", 0)
            callback(level)
        }
    }

    internal object console {
        private const val TAG = "ATApplication"
        fun log(message: String) {
            Log.d(TAG, message)
        }
    }
}