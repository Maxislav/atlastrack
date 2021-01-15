package com.mars.atlastrack

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.*
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Observer
import androidx.work.*


class LocationService : Service(), Callback {
    private val TAG = "LocationService"
    private val localBinder = LocalBinder()
    lateinit var locationManagerNet: LocationManager
    lateinit var locationManagerGps: LocationManager
    lateinit var locationListenerNet: MyLocationListener
    lateinit var atlasRest: AtlasRest
    lateinit var networkListener: NetworkListener
    lateinit var gpsListener: GPSListener
    var gpsDefined = false
    var time: Long = 0
    var notificationId = 0
    lateinit var wakeLock: PowerManager.WakeLock

    @SuppressLint("InvalidWakeLockTag")
    override fun onCreate() {
        Log.d(TAG, "onCreate()")
        time = System.currentTimeMillis()
        // notificationStart("service created")
        onTimeout()
        timeoutForNetLocation();

        val c = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val connMgr = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager


        val powerManager = applicationContext.getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK, // PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "GCMOnMessage"
        )
        // PARTIAL_WAKE_LOCK
        wakeLock?.acquire(10*60*1000L /*10 minutes*/)


        /*val intent1 = createIntent("action 1", "extra 1")
        val am = getSystemService(ALARM_SERVICE) as AlarmManager
        val pendingIntent =   PendingIntent.getService(applicationContext, 1, intent1, PendingIntent.FLAG_CANCEL_CURRENT)
        am.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 60000*2, pendingIntent)
*/
        locationManagerNet = getSystemService(LOCATION_SERVICE) as LocationManager
        locationManagerGps = getSystemService(LOCATION_SERVICE) as LocationManager
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            stopSelf()
            return
        }
        networkListener = NetworkListener()
        gpsListener = GPSListener()

        locationManagerGps.requestLocationUpdates(
            LocationManager.GPS_PROVIDER,
            10000,
            0F,
            gpsListener
            /* LocationListener { location ->
                 atlasRest = AtlasRest(location)

                 atlasRest.request(this);
             }*/
        )


    }

    override fun onBind(intent: Intent?): IBinder? {
        // return localBinder
        return null
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
        locationManagerNet.removeUpdates(networkListener)
        locationManagerGps.removeUpdates(gpsListener)
        if(wakeLock?.isHeld){
            wakeLock?.release()
        }

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
            gpsDefined = true
            if (time + 60 * 1000 * 2 < t2) {
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
            } else if (time + 60 * 1000 * 2 < t2) {
                // notificationStart("net timeout")
                stopSelf()
            }
        }

        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
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

    fun startNetListener(){

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
       // notificationStart("startNetListener")
        locationManagerNet.requestLocationUpdates(
            LocationManager.NETWORK_PROVIDER,
            5000,
            0F,
            networkListener
        )
    }

    private fun timeoutForNetLocation(){
        val h = object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(msg: Message) {
                startNetListener()
            }
        }
        val t = Thread {
            Thread.sleep(TEN_SECONDS)
            h.sendEmptyMessage(1)
        }
        t.start()
    }


    private fun onTimeout() {
        val h = object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(msg: Message) {
                stopSelf()
            }
        }
        val t = Thread {

            Thread.sleep(TWO_MINUTES)
            h.sendEmptyMessage(1)
        }
        t.start()
    }


    fun sendWithWorker(location: Location, byLocation: String) {
        val data = Data.Builder()
        // val m = mapOf<String, Double>("lng" to location.longitude, "lat" to location.latitude)
        data.putDouble("lng", location.longitude)
        data.putDouble("lat", location.latitude)
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
                    val atlasRest = AtlasRest(location)
                    atlasRest.request(this@LocationService)
                }
            })
        workManager.enqueue(uploadTask)
    }

    companion object {
        private var COUNT = 0
        private var TWO_MINUTES = (60 * 1000 * 2).toLong()
        private var TEN_SECONDS = (10 * 1000).toLong()
        private var GPS = "GPS"
        private var NETWORK = "NETWORK"
    }

    override fun onSuccess() {
        val t2 = System.currentTimeMillis()
        if (time + 30 * 1000 < t2) {
            stopSelf()
        }
    }

    override fun onFailure() {
        val t2 = System.currentTimeMillis()
        if (time + 30 * 1000 < t2) {
            stopSelf()
        }
    }


}

