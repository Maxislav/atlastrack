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
import android.telephony.CellIdentityLte
import android.telephony.CellInfo
import android.telephony.CellInfoLte
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Observer
import androidx.work.*
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*


class LocationService : Service() {
    private val TAG = "LocationService"
    private val localBinder = LocalBinder()
    private var locationManagerNet: LocationManager? = null
    private var locationManagerGps: LocationManager? = null
    lateinit var locationListenerNet: MyLocationListener
    lateinit var atlasRest: AtlasRest
    lateinit var networkListener: NetworkListener
    lateinit var gpsListener: GPSListener
    private var gpsDefined = false
    private var startServicetime: Long = 0
    var notificationId = 0
    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var batLevel: Number
    private lateinit var batteryReceiver: BatteryReceiver
    private lateinit var emergencyHandler: Handler
    var timerForNetHandler: Handler? = null


    override fun onCreate() {
        Log.d(TAG, "onCreate")

    }

    override fun onLowMemory() {
        super.onLowMemory()
        setupNextAlarm()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand")
        super.onStartCommand(intent, flags, startId)
        setupNextAlarm();
        startServicetime = System.currentTimeMillis()


        startEmergencyTimeout()
        defineGsmCell();
        val powerManager = applicationContext.getSystemService(POWER_SERVICE) as PowerManager


        if ((getSystemService(LOCATION_SERVICE) as LocationManager).isProviderEnabled(
                LocationManager.GPS_PROVIDER
            )
        ) {
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK, // PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "atlas.track:wakelock"
            )
            wakeLock?.acquire(2 * 60 * 1000L /*10 minutes*/)
            batteryReceiver = BatteryReceiver(fun(level: Number) {
                batLevel = level
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

        locationManagerGps?.requestLocationUpdates(
            LocationManager.GPS_PROVIDER,
            10000,
            0F,
            gpsListener

        )
    }

    override fun onBind(intent: Intent?): IBinder {
        return localBinder
    }


    fun createIntent(action: String?, extra: String?): Intent {
        /* SampleBootReceiver sm = new SampleBootReceiver();
        sm.setMainActivity(this);*/
        val intent = Intent(this, WakeUp::class.java)
        intent.action = action
        intent.putExtra("extra", extra)
        return intent
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy()")
        emergencyHandler.removeCallbacksAndMessages(null);
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

            if (startServicetime + 60 * 1000 * 2 < t2) {
                // notificationStart("GPS 2min ")
                stopSelf()
            } else {
                sendWithWorker(location, GPS)
            }

        }

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
            Thread.sleep(TWO_MINUTES)
            emergencyHandler.sendEmptyMessage(1)
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
        val dateFormat: DateFormat = SimpleDateFormat("ddMMyy")
        dateFormat.timeZone = TimeZone.getTimeZone("UTC")
        val sdf = SimpleDateFormat("HHmmss")
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        val time: String = sdf.format(currentTime)
        return dateFormat.format(currentTime)
    }

    private fun getTime(currentTime: Date): String {
        val dateFormat: DateFormat = SimpleDateFormat("ddMMyy")
        dateFormat.timeZone = TimeZone.getTimeZone("UTC")
        val sdf = SimpleDateFormat("HHmmss")
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
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val intent1 = Intent(this, MainActivity::class.java)
        intent1.putExtra("were_from", "my_service")
        intent1.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP

        val pendingIntent = PendingIntent.getActivity(this, 0, intent1, PendingIntent.FLAG_ONE_SHOT)
        val notification = Notification.Builder(this).setContentTitle("LocationService")
            .setContentText("network ${isInternetAvailable(this)} | ${messagee}")
            .setContentIntent(pendingIntent)
            .setSmallIcon(R.drawable.ic_launch)
            .build()
        notification.flags = Notification.FLAG_SHOW_LIGHTS or Notification.FLAG_AUTO_CANCEL
        COUNT++
        nm.notify(COUNT, notification)
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

    private fun defineGsmCell() {
        val telephonyManager = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val cellLocation = telephonyManager.allCellInfo as List<CellInfo>
        var mcc: String? = ""
        var mnc: String? = ""
        var lac: String? = ""
        for (item in cellLocation) {
            item.isRegistered
            val cel: CellIdentityLte = (item as CellInfoLte).cellIdentity

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



            cellId = cel.pci.toString()

            map.put("cellId", cellId)

            Log.d(TAG, "->>  ${mcc} ${mnc} ${lac} ${cellId}")

        }
    }

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

