package com.mars.atlastrack

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.*
import android.content.pm.PackageManager
import android.location.Location
import android.net.ConnectivityManager
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.work.*
import com.mars.atlastrack.WakeUp.Companion.WAKE_UP_ACTION
import org.w3c.dom.Text
import androidx.lifecycle.Observer


class MainActivity : AppCompatActivity(), SharedPreferences.OnSharedPreferenceChangeListener {

    private var TAG: String = "TAG_MainActivity"
    private lateinit var myReceiver: MyReceiver
    private var foregroundOnlyLocationService: ForegroundOnlyLocationService? = null
    private var bound: Boolean = false

    private lateinit var sharedPreferences:SharedPreferences
    lateinit var lngLatTextView: TextView
    lateinit var accuracyTextView: TextView
    lateinit var buttonStartStop: Button

    var alarmManager: AlarmManager? = null
    private val workManager = WorkManager.getInstance(application)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById(R.id.toolbar))




        // workManager.enqueue(PeriodicWorkRequest)
        val vv = findViewById<TextView>(R.id.tv_device_id)
        vv.text = (BuildConfig.DEVICE_ID)
        lngLatTextView = findViewById(R.id.lng_lat)
        accuracyTextView = findViewById(R.id.accuracy)
        buttonStartStop = findViewById(R.id.start_stop_button)
        this.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

        myReceiver = MyReceiver()
        sharedPreferences =
            getSharedPreferences(getString(R.string.preference_file_key), Context.MODE_PRIVATE)

        alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager;
        val alarmIntent = Intent(this, WakeUp::class.java)
        //startService(alarmIntent)

        alarmIntent.setAction(WAKE_UP_ACTION)
            .putExtra("extra", "extra!")

        val pIntent2 = PendingIntent.getBroadcast(
            this,
            0,
            alarmIntent,
            0 //PendingIntent.FLAG_CANCEL_CURRENT
        )
        alarmManager?.cancel(pIntent2)
        val time = System.currentTimeMillis() + 10;
        // alarmManager?.setRepeating(AlarmManager.RTC_WAKEUP, time, 60000, pIntent2)
        alarmManager?.set(AlarmManager.RTC_WAKEUP, time, pIntent2)
        // alarmManager?.setRepeating(AlarmManager.RTC_WAKEUP, time)

        val serviceIntent = Intent(this, ForegroundOnlyLocationService::class.java)
        buttonStartStop.setOnClickListener{ view ->
            val b  = view as Button
            val text = b.text.toString()
            when(text){
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

        // If the user denied a previous request, but didn't check "Don't ask again", provide
        // additional rationale.
        if (provideRationale) {
           /* Snackbar.make(
                findViewById(R.id.activity_main),
                R.string.permission_rationale,
                Snackbar.LENGTH_LONG
            )
                .setAction(R.string.ok) {
                    // Request permission
                    ActivityCompat.requestPermissions(
                        this@MainActivity,
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                        REQUEST_FOREGROUND_ONLY_PERMISSIONS_REQUEST_CODE
                    )
                }
                .show()*/
        } else {
            Log.d(TAG, "Request foreground only permission")
            /*ActivityCompat.requestPermissions(
                this@MainActivity,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                REQUEST_FOREGROUND_ONLY_PERMISSIONS_REQUEST_CODE
            )*/
        }
    }

    override fun onStart() {
        super.onStart()
      /*  val serviceIntent = Intent(this, ForegroundOnlyLocationService::class.java)
        bindService(serviceIntent, foregroundOnlyServiceConnection, Context.BIND_AUTO_CREATE)*/
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
        // location.accuracy
        // val outputWithPreviousLogs = "$output"
        //  outputTextView.text = outputWithPreviousLogs
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

        /*override fun onReceive(context: Context, intent: Intent) {
            val location = intent.getParcelableExtra<Location>(
                ForegroundOnlyLocationService.EXTRA_LOCATION
            )

            if (location != null) {
                logResultsToScreen("Foreground location: ${location.toText()}")
            }
        }*/

    }


    /*override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_settings -> true
            else -> super.onOptionsItemSelected(item)
        }
    }
*/
}