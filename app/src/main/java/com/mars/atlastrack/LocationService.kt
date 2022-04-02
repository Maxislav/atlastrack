package com.mars.atlastrack

import android.Manifest
import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.*
import android.telephony.*
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Observer
import androidx.work.*
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*


class LocationService : Service() {

    lateinit var networkListener: NetworkListener
    lateinit var gpsListener: GPSListener

    private var gpsDefined = false
    private var startServicetime: Long = 0
    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var batLevel: Number
    private lateinit var batteryReceiver: BatteryReceiver
    private var emergencyHandler: Handler? = null
    private var timerForNetHandler: Handler? = null
    private val localBinder = LocalBinder()
    private var locationManagerNet: LocationManager? = null
    private var locationManagerGps: LocationManager? = null
    private val TAG = "LocationService"

    override fun onLowMemory() {
        super.onLowMemory()
        setupNextAlarm()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand")
        super.onStartCommand(intent, flags, startId)
        notificationStart("Location update")
        startServicetime = System.currentTimeMillis()
        setupNextAlarm();
        startEmergencyTimeout()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                defineGsmCell()
            } catch (e: Exception) {
                Log.d(TAG, e.stackTraceToString())
            }
        };

        val powerManager = applicationContext.getSystemService(POWER_SERVICE) as PowerManager
        val isNetProviderEnabled =
            (getSystemService(LOCATION_SERVICE) as LocationManager).isProviderEnabled(
                LocationManager.NETWORK_PROVIDER
            )
        isNetProviderEnabled
        if ((getSystemService(LOCATION_SERVICE) as LocationManager).isProviderEnabled(
                LocationManager.GPS_PROVIDER
            )
        ) {
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,  // PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "atlas.track:wakelock"
            )
            wakeLock?.acquire(2 * 60 * 1000L /*10 minutes*/)
            batteryReceiver = BatteryReceiver(fun(level: Number) {
                batLevel = level
                Log.d(TAG, "bat level ${batLevel}")
                unregisterReceiver(batteryReceiver);
                startLocationListeners()
            })
            registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }
        return START_STICKY
    }

    private fun startLocationListeners() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            stopSelf()
            return
        }

        locationManagerNet = getSystemService(LOCATION_SERVICE) as LocationManager
        locationManagerGps = getSystemService(LOCATION_SERVICE) as LocationManager

        networkListener = NetworkListener()
        gpsListener = GPSListener()
        timeoutForNetLocation(fun() {
            locationManagerNet?.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER,
                5000,
                0F,
                networkListener
            )
        });

        locationManagerGps?.let {

            Log.d(TAG, "locationManagerGps")

            it.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                10000,
                0F,
                gpsListener

            )
        }
        Log.d(TAG, "locationManagerGps ++")

    }

    override fun onBind(intent: Intent?): IBinder {
        return localBinder
    }


    override fun onDestroy() {
        Log.d(TAG, "onDestroy()")
        emergencyHandler?.removeCallbacksAndMessages(null);
        timerForNetHandler?.removeCallbacksAndMessages(null);

        locationManagerNet?.removeUpdates(networkListener)
        locationManagerGps?.removeUpdates(gpsListener)


        if (wakeLock != null && wakeLock!!.isHeld) {
            wakeLock?.release()
        }
        super.onDestroy()
    }


    inner class LocalBinder : Binder() {
        internal val service: LocationService
            get() = this@LocationService
    }


    private fun isInternetAvailable(context: Context): Boolean {
        var result = false
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val networkCapabilities = connectivityManager.activeNetwork ?: return false
            val actNw =
                connectivityManager.getNetworkCapabilities(networkCapabilities) ?: return false
            result = when {
                actNw.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
                actNw.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
                actNw.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
                else -> false
            }
        } else {
            connectivityManager.run {
                connectivityManager.activeNetworkInfo?.run {
                    result = when (type) {
                        ConnectivityManager.TYPE_WIFI -> true
                        ConnectivityManager.TYPE_MOBILE -> true
                        ConnectivityManager.TYPE_ETHERNET -> true
                        else -> false
                    }

                }
            }
        }

        return result
    }

    inner class GPSListener : LocationListener {
        override fun onLocationChanged(location: Location) {
            val t2 = System.currentTimeMillis()
            if (location.hasAccuracy() && location.accuracy < 200) {
                gpsDefined = true
            }

            if (startServicetime + 30 * 1000 * 2 < t2) {
                Log.d(TAG, "stopSelf from gps")
                stopSelf()
            } else {
                sendWithWorker(location, GPS)
            }

        }

        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}

        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    }

    inner class NetworkListener : LocationListener {

        override fun onLocationChanged(location: Location) {
            val t2 = System.currentTimeMillis()
            if (!gpsDefined) {
                //   notificationStart("net ${location.longitude} ${location.latitude}")
                sendWithWorker(location, NETWORK)
            } else if (startServicetime + 60 * 1000 * 2 < t2) {
                // notificationStart("net timeout")
                stopSelf()
            }
        }

        override fun onProviderEnabled(provider: String) {
            Log.d(TAG, provider)
        }

        override fun onProviderDisabled(provider: String) {
            Log.d(TAG, provider)
        }

        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    }

    private fun timeoutForNetLocation(callback: () -> Unit) {
        timerForNetHandler = object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(msg: Message) {
                callback()
            }
        }
        val t = Thread {
            Thread.sleep(TEN_SECONDS)
            timerForNetHandler?.sendEmptyMessage(1)
        }
        t.start()
    }


    private fun startEmergencyTimeout() {
        emergencyHandler = object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(msg: Message) {
                this@LocationService.stopSelf()
            }
        }
        val t = Thread {
            Thread.sleep(30 * 1000)
            emergencyHandler?.sendEmptyMessage(1)
        }
        t.start()
    }


    fun sendWithWorker(location: Location, byLocation: String) {
        Log.d(TAG, "Location define ${byLocation} ${location.longitude} ${location.latitude}")
        val currentTime = Date()
        val data = Data.Builder()
        data.putDouble("lng", location.longitude)
        data.putDouble("lat", location.latitude)
        data.putString("date", getDate(currentTime))
        data.putString("time", getTime(currentTime))
        data.putInt("batt", batLevel.toInt())
        val workManager = WorkManager.getInstance(this)
        val networkConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val uploadTask = OneTimeWorkRequestBuilder<LocationWorker>()
            .setConstraints(networkConstraints)
            .setInputData(data.build())
            .build()
        workManager.getWorkInfoByIdLiveData(uploadTask.id)
            .observeForever(Observer { workInfo: WorkInfo? ->
                if (workInfo != null && workInfo.state.isFinished) {
                    Log.d(TAG, "response ok ${workInfo.outputData.getString("body")}")
                }
            })
        workManager.enqueue(uploadTask)
    }

    private fun getDate(currentTime: Date): String {
        val dateFormat: DateFormat = SimpleDateFormat("ddMMyy", Locale.ENGLISH)
        dateFormat.timeZone = TimeZone.getTimeZone("UTC")
        val sdf = SimpleDateFormat("HHmmss", Locale.ENGLISH)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        val time: String = sdf.format(currentTime)
        return dateFormat.format(currentTime)
    }

    private fun getTime(currentTime: Date): String {
        val dateFormat: DateFormat = SimpleDateFormat("ddMMyy", Locale.ENGLISH)
        dateFormat.timeZone = TimeZone.getTimeZone("UTC")
        val sdf = SimpleDateFormat("HHmmss", Locale.ENGLISH)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(currentTime)
    }

    inner class BatteryReceiver(val callback: (level: Number) -> Unit) : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val level = intent!!.getIntExtra("level", 0)
            callback(level)
        }
    }


    private fun notificationStart(messagee: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                "CHANNEL_IDDD",
                "Location Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            serviceChannel.description = "no sound";
            serviceChannel.setShowBadge(true)
            serviceChannel.setSound(null, null) //< ----ignore sound
            serviceChannel.enableLights(false)
            manager.createNotificationChannel(serviceChannel)
        }
        val cancelIntent = Intent("CHANNEL_ID")

        val pendingIntent = PendingIntent.getBroadcast(
            this, 0,
            cancelIntent, PendingIntent.FLAG_CANCEL_CURRENT
        )

        val nfc = NotificationCompat.Builder(this, "CHANNEL_IDDD")
            .setContentTitle(getString(R.string.app_name))
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setContentText("Location update...")
            .setOngoing(true)
            .build()
        startForeground(1, nfc)
    }

    private fun setupNextAlarm(): Unit {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager;
        val alarmIntent = Intent(this, IntervalReceiver::class.java)
        val time = System.currentTimeMillis() + TWENTY_MINUTES;
        val pi = PendingIntent.getBroadcast(
            this,
            0,
            alarmIntent,
            PendingIntent.FLAG_CANCEL_CURRENT
        )

        if (isDozing(this)) {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                time,
                pi
            )
        } else {
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                time,
                pi
            )
        }

    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun defineGsmCell() {
        // android.telephony.CellInfoWcdma cannot be cast to android.telephony.CellInfoLte
        val telephonyManager = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val cellLocation: List<CellInfo> = telephonyManager.allCellInfo
        var mcc: String? = ""
        var mnc: String? = ""
        var lac: String? = ""
        for (item in cellLocation) {
            item.isRegistered

            //val dd = item.cellIdentity

            val cel: CellIdentityLte = item.cellIdentity as CellIdentityLte


            var cellId: String?
            val map: MutableMap<String, String?> = mutableMapOf()
            if (item.isRegistered) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    mcc = cel.mccString
                    mnc = cel.mncString
                } else {
                    mcc = cel.mcc.toString()
                    mnc = cel.mnc.toString()
                }
                lac = cel.tac.toString()
                map.put("mcc", mcc)
                map.put("mnc", mnc)
                map.put("lac", lac)
            }
            val gson = Gson()


            cellId = cel.pci.toString()

            map.put("cellId", cellId)

            Log.d(TAG, "->>  ${mcc} ${mnc} ${lac} ${cellId}")

            val f = JsonDataParser(mcc)
            val json = Gson()
            var jsonString = gson.toJson(f)

        }
    }

    data class JsonDataParser(
        @SerializedName("mcc") val mcc: String?,
        /*   @SerializedName("mnc") val mnc: String,
           @SerializedName("lac") val lac: String,
           @SerializedName("cellId") val cellId: String*/
    )

    companion object {
        private var COUNT = 0
        private var TWO_MINUTES = (60 * 1000 * 2).toLong()
        private var TEN_SECONDS = (10 * 1000).toLong()
        private var TWENTY_MINUTES = 60 * 1000 * 20
        private var GPS = "GPS"
        private var NETWORK = "NETWORK"
        fun isDozing(context: Context): Boolean {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val powerManager = context.getSystemService(POWER_SERVICE) as PowerManager
                return powerManager.isDeviceIdleMode &&
                        !powerManager.isIgnoringBatteryOptimizations(context.packageName)
            } else {
                return false
            }
        }
    }
}

