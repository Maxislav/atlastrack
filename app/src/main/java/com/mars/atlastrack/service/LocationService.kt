package com.mars.atlastrack.service

import android.Manifest
import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
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
import com.mars.atlastrack.ATApplication
import com.mars.atlastrack.R
import com.mars.atlastrack.SharedPreferenceUtil.isIdle
import com.mars.atlastrack.WakeUp
import com.mars.atlastrack.WakeUp.Companion.WAKE_UP_ACTION
import com.mars.atlastrack.worker.SendWorker
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*


class LocationService : Service() {

    var networkListener: NetworkListener? = null
    var gpsListener: GPSListener? = null

    private var gpsDefined = false
    private var startServicetime: Long = 0
    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var batLevel: Number
    private var batteryReceiver: BatteryReceiver? = null
    private var emergencyHandler: Handler? = null
    private var timerForNetHandler: Handler? = null
    private val localBinder = LocalBinder()
    private var locationManagerNet: LocationManager? = null
    private var locationManagerGps: LocationManager? = null

    private var serviceLooper: Looper? = null
    private var serviceHandler: ServiceHandler? = null

    val wakeUp: WakeUp = WakeUp()

    /* private var handler  = object : Handler(Looper.getMainLooper()) {
         override fun handleMessage(msg: Message) {
             when (msg.what) {
                 0 -> {
                     onInit()
                 }
             }
         }
     }*/

    // Handler that receives messages from the thread
    private inner class ServiceHandler(looper: Looper) : Handler(looper) {

        override fun handleMessage(msg: Message) {
            // Normally we would do some work here, like download a file.
            // For our sample, we just sleep for 5 seconds.
            when (msg.what) {
                1 -> {
                    console.log("ServiceHandler handleMessage")
                    setupNextAlarm()
                }
            }


            try {
                Thread.sleep(5000)
            } catch (e: InterruptedException) {
                // Restore interrupt status.
                Thread.currentThread().interrupt()
            }
        }
    }

    override fun onCreate() {
        HandlerThread("ServiceStartArguments", Process.THREAD_PRIORITY_BACKGROUND).also {
            it.start()

            // Get the HandlerThread's Looper and use it for our Handler
            serviceLooper = it.looper
            serviceHandler = ServiceHandler(it.looper)
        }
        super.onCreate()
    }


    override fun onLowMemory() {
        super.onLowMemory()
        console.log("low memory")
        setupNextAlarm()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        console.log("onStartCommand")
        val isIdling = isIdle(applicationContext)
        console.log("is idle -> ${isIdle(applicationContext)}")
        // wakeUp.scheduleNextAlarm(this)
        super.onStartCommand(intent, flags, startId)
        val app: ATApplication = applicationContext as ATApplication

        if (app.serviceIsRunning) {
            return START_STICKY
        }
        app.serviceIsRunning = true
        notificationStart("Location update")
        startServicetime = System.currentTimeMillis()
        startEmergencyTimeout(isIdling)
        serviceHandler?.obtainMessage()?.also { msg ->
            //  serviceHandler?.sendEmptyMessage(1)
        }
        // setupNextAlarm()


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                defineGsmCell()
            } catch (e: Exception) {
                console.log(e.stackTraceToString())
            }
        };


        val isNetProviderEnabled =
            (getSystemService(LOCATION_SERVICE) as LocationManager).isProviderEnabled(
                LocationManager.NETWORK_PROVIDER
            )
        if ((getSystemService(LOCATION_SERVICE) as LocationManager).isProviderEnabled(
                LocationManager.GPS_PROVIDER
            )
        ) {
            serviceHandler?.obtainMessage()?.also { msg ->
                msg.arg1 = startId
                serviceHandler?.sendMessage(msg)
            }
            Thread.sleep(5000)
            onInit()
        }
        return START_STICKY
    }

    private fun wakeScreen() {
        val powerManager = applicationContext.getSystemService(POWER_SERVICE) as PowerManager
        powerManager.newWakeLock(
            //PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            PowerManager.PARTIAL_WAKE_LOCK,  // PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "atlas.track:wakelock"
        ).also { wakeLock = it }
        wakeLock?.acquire(2 * 60 * 1000L)
        wakeLock?.setReferenceCounted(true)
        console.log("wakeScreen")
    }


    private fun onInit() {
        wakeScreen()
        /* val powerManager = applicationContext.getSystemService(POWER_SERVICE) as PowerManager
         powerManager.newWakeLock(
             PowerManager.PARTIAL_WAKE_LOCK,  // PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
             "atlas.track:wakelock"
         ).also { wakeLock = it }
         wakeLock?.acquire(2 * 60 * 1000L)*/


        val app: ATApplication = applicationContext as ATApplication


        app.registerReceiver { level ->
            batLevel = level
            app.unregisterReceiver()
            startLocationListeners()
        }
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

        timeoutForNetLocation {
            if (locationManagerNet !== null && networkListener !== null) {

                console.log("start location by network")
                locationManagerNet?.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    5000,
                    0F,
                    networkListener!!
                )
            }
        };

        locationManagerGps?.let {

            console.log("locationManagerGps")
            if (gpsListener != null) {
                it.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    10000,
                    0F,
                    gpsListener!!

                )
            }


        }
        console.log("locationManagerGps ++")

    }

    override fun onBind(intent: Intent?): IBinder {
        return localBinder
    }


    override fun onDestroy() {
        console.log("onDestroyed")
        val app: ATApplication = applicationContext as ATApplication
        app.serviceIsRunning = false
        emergencyHandler?.removeCallbacksAndMessages(null);
        timerForNetHandler?.removeCallbacksAndMessages(null);

        networkListener?.let {
            locationManagerNet?.removeUpdates(it)
        }
        gpsListener?.let {
            locationManagerGps?.removeUpdates(it)
        }

        if (wakeLock != null && wakeLock!!.isHeld) {
            wakeLock?.release()
        }
        // handler.removeCallbacksAndMessages(null)
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
                console.log("stopSelf from gps")
                sendWithWorker(location, GPS) //
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
            console.log(provider)
        }

        override fun onProviderDisabled(provider: String) {
            console.log(provider)
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


    private fun startEmergencyTimeout(isIdling: Boolean) {
        emergencyHandler = object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(msg: Message) {
                console.log("stop self by timeout")
                this@LocationService.stopSelf()
            }
        }
        val t = Thread {
            if (isIdling) {
                Thread.sleep(30 * 60 * 1000)
            } else {
                Thread.sleep(40 * 1000)
            }

            emergencyHandler?.sendEmptyMessage(1)
        }
        t.start()
    }


    fun sendWithWorker(location: Location, byLocation: String) {
        console.log("Location define ${byLocation} ${location.longitude} ${location.latitude}")
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
        val uploadTask = OneTimeWorkRequestBuilder<SendWorker>()
            .setConstraints(networkConstraints)
            .setInputData(data.build())
            .build()
        workManager.getWorkInfoByIdLiveData(uploadTask.id)
            .observeForever(Observer { workInfo: WorkInfo? ->
                if (workInfo != null && workInfo.state.isFinished) {
                    console.log("response ok ${workInfo.outputData.getString("body")}")
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
                "NOTIFICATION_CHANNEL_ID",
                "Location Service Channel",
                NotificationManager.IMPORTANCE_HIGH
            )
            val manager = getSystemService(NotificationManager::class.java)
            serviceChannel.description = "no sound";
            serviceChannel.setShowBadge(true)
            serviceChannel.setSound(null, null) //< ----ignore sound
            serviceChannel.enableLights(true)
            serviceChannel.lightColor = 0xffff01
            // serviceChannel.shouldShowLights()
            manager.createNotificationChannel(serviceChannel)
        }
        val cancelIntent = Intent("CANCEL_ID")

        val pendingIntent = PendingIntent.getBroadcast(
            this, 0,
            cancelIntent, PendingIntent.FLAG_CANCEL_CURRENT
        )

        val nfc = NotificationCompat.Builder(this, "NOTIFICATION_CHANNEL_ID")
            .setContentTitle(getString(R.string.app_name))
            .setLights(Color.BLUE, 200, 1000)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setContentText("Location update...")
            .setOngoing(true)

            .build()
        // nfc.ledARGB = unchecked(-0xffff01)

        startForeground(1, nfc)
    }

    private fun setupNextAlarm(): Unit {


        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager;
        val alarmIntent = Intent(this, WakeUp::class.java)
        alarmIntent.action = WAKE_UP_ACTION
        val time = System.currentTimeMillis() + TWENTY_MINUTES;

        console.log("setup next alarm")
        val dateFormat: DateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH)
        val strDate: String = dateFormat.format(Date(time))
        console.log("setup next alarm: ${strDate}")
        val pi = PendingIntent.getBroadcast(
            this,
            0,
            alarmIntent,
            0, //PendingIntent.FLAG_CANCEL_CURRENT
        )
        alarmManager.set(
            AlarmManager.RTC_WAKEUP,
            time,
            pi
        )
        Thread.sleep(2000)
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

            console.log("->>  ${mcc} ${mnc} ${lac} ${cellId}")

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

    internal object console {
        val TAG = "TAG_LocationService"
        fun log(message: String) {
            Log.d(TAG, message)
        }
    }

    companion object {
        private var COUNT = 0
        private var TWO_MINUTES = (60 * 1000 * 2).toLong()
        private var TEN_SECONDS = (10 * 1000).toLong()
        private var TWENTY_MINUTES = 60 * 1000 * 20
        private var GPS = "GPS"
        private var NETWORK = "NETWORK"


    }
}

