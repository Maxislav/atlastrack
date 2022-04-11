package com.mars.atlastrack

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.*
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.IBinder
import android.telephony.CellIdentityLte
import android.telephony.CellInfo
import android.telephony.CellInfoLte
import android.telephony.TelephonyManager
import android.util.Log
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.work.WorkManager
import com.mars.atlastrack.WakeUp.Companion.WAKE_UP_ACTION


class MainActivity : AppCompatActivity(), SharedPreferences.OnSharedPreferenceChangeListener {

    private var TAG: String = "TAG_MainActivity"
    private lateinit var myReceiver: MyReceiver
    private var foregroundOnlyLocationService: ForegroundOnlyLocationService? = null
    private var bound: Boolean = false

    private lateinit var sharedPreferences: SharedPreferences
    lateinit var lngLatTextView: TextView
    lateinit var accuracyTextView: TextView
    lateinit var buttonStartStop: Button

    lateinit var alarmManager: AlarmManager
    private val workManager = WorkManager.getInstance(application)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById(R.id.toolbar))
        myReceiver = MyReceiver()
       // startActivity(Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS))

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.d(TAG, "no location permission")
            requestLocationPermission()
            return
        }

        startBackgroundProcess();

        val vv = findViewById<TextView>(R.id.tv_device_id)
        vv.text = (BuildConfig.DEVICE_ID)
        lngLatTextView = findViewById(R.id.lng_lat)
        accuracyTextView = findViewById(R.id.accuracy)
        buttonStartStop = findViewById(R.id.start_stop_button)
        this.getWindow()
            .setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        sharedPreferences =
            getSharedPreferences(getString(R.string.preference_file_key), Context.MODE_PRIVATE)

        val serviceIntent = Intent(this, ForegroundOnlyLocationService::class.java)
        buttonStartStop.setOnClickListener { view ->
            val b = view as Button
            val text = b.text.toString()
            when (text) {
                getString(R.string.start) -> {

                    bindService(
                        serviceIntent,
                        foregroundOnlyServiceConnection,
                        Context.BIND_AUTO_CREATE
                    )
                }
                getString(R.string.stop) -> {
                    if (bound) {
                        foregroundOnlyLocationService?.unsubscribeToLocationUpdates()

                        unbindService(foregroundOnlyServiceConnection);
                        stopService(serviceIntent)
                        buttonStartStop.text = getString(R.string.start)
                        bound = false
                    }
                }
            }
        }
    }

    private fun startBackgroundProcess() {
        alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager;
        val alarmIntent = Intent(this, WakeUp::class.java)
        alarmIntent.action = WAKE_UP_ACTION

        val pi = PendingIntent.getBroadcast(
            this,
            0,
            alarmIntent,
            PendingIntent.FLAG_CANCEL_CURRENT
        )
        // alarmManager.cancel(pi)
        val time = System.currentTimeMillis() + 1000*1*5;
        alarmManager.setExact(AlarmManager.RTC_WAKEUP, time, pi)
    }

    private fun requestLocationPermission() {
        ActivityCompat.requestPermissions(
            this, arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ),
            1
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        if (grantResults[0] == PackageManager.PERMISSION_GRANTED && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
            startBackgroundProcess()
        }
    }


    // Monitors connection to the while-in-use service.
    private val foregroundOnlyServiceConnection = object : ServiceConnection {

        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            val binder = service as ForegroundOnlyLocationService.LocalBinder
            foregroundOnlyLocationService = binder.service
            buttonStartStop.text = getString(R.string.stop)
            // foregroundOnlyLocationService?.unsubscribeToLocationUpdates()
            foregroundOnlyLocationService?.subscribeToLocationUpdates()
            bound = true
            // ?: Log.d(TAG, "Service Not Bound")
        }

        override fun onServiceDisconnected(name: ComponentName) {
            foregroundOnlyLocationService = null
            bound = false
            buttonStartStop.text = getString(R.string.start)
        }
    }

    private fun foregroundPermissionApproved(): Boolean {
        return PackageManager.PERMISSION_GRANTED == ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }

    // TODO: Step 1.0, Review Permissions: Method requests permissions.
    private fun requestForegroundPermissions() {
        val provideRationale = foregroundPermissionApproved()


        if (provideRationale) {

        } else {
            Log.d(TAG, "Request foreground only permission")
        }
    }

    override fun onStart() {
        super.onStart()
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "On resume main activity")

        LocalBroadcastManager.getInstance(this)
            .registerReceiver(
                myReceiver,
                IntentFilter(ForegroundOnlyLocationService.ACTION_FOREGROUND_ONLY_LOCATION_BROADCAST)
            )

    }

    override fun onPause() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(
            myReceiver
        )
        super.onPause()
    }


    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        TODO("Not yet implemented")
    }

    private fun logResultsToScreen(location: Location) {
        lngLatTextView.text = "${location.longitude} : ${location.latitude}"
        accuracyTextView.text = "${location.accuracy}"
    }

    private inner class MyReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val location = intent.getParcelableExtra<Location>(
                ForegroundOnlyLocationService.EXTRA_LOCATION
            )

            if (location != null) {
                // ogResultsToScreen("Foreground location: ${location.toText()}")
                logResultsToScreen(location)
            }
        }
    }
}