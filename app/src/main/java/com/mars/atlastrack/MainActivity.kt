package com.mars.atlastrack

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.*
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.databinding.ObservableField
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.work.WorkManager
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.iid.FirebaseInstanceId
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.ktx.messaging
import com.mars.atlastrack.WakeUp.Companion.WAKE_UP_ACTION
import com.mars.atlastrack.databinding.ActivityMainBinding


class MainActivity : AppCompatActivity(), SharedPreferences.OnSharedPreferenceChangeListener {
    private lateinit var binding: ActivityMainBinding
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

    public val deviceIdField = ObservableField<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        binding.mainActivity = this;
        setContentView(binding.root)
        // setSupportActionBar(findViewById(R.id.toolbar))
        myReceiver = MyReceiver()

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            console.log("no location permission")
            requestLocationPermission()
            return
        }
        // todo
        // startBackgroundProcess();


        deviceIdField.set(BuildConfig.DEVICE_ID)

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
        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true)
        FirebaseMessaging.getInstance().token.addOnCompleteListener(OnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w(TAG, "Fetching FCM registration token failed", task.exception)
                return@OnCompleteListener
            }
            // token ->  dgIhqpmeTTOrdQuI0eAhEa:APA91bG5-yFL7ZGQtJPxQ32Ds9nkdo8ZoVYWWaFZXc3YsH17ybGUiu1gvR9VwrmyMsyuQvcDodPz8XVQwQfnURt8_1cQ7gbYGwWcy-NCfUFi1I_qiBQHOHewy2krNmBCzBMEUbliRDLJ
            // fNKuo15hRf2qhb8EsLMmqs:APA91bGmQox17E0TBMXNoOOk2fU7XvgUDB76JARDrmz7kAgOzIH99YM8CSedSvVveVb6OqG6m-kjUJ_moYtvL83f6PFlWdl8KHeoaCcRyNqQupnDqUaXtsJr3O9fEYST06qcILuy6wFY
            // fNKuo15hRf2qhb8EsLMmqs:APA91bGmQox17E0TBMXNoOOk2fU7XvgUDB76JARDrmz7kAgOzIH99YM8CSedSvVveVb6OqG6m-kjUJ_moYtvL83f6PFlWdl8KHeoaCcRyNqQupnDqUaXtsJr3O9fEYST06qcILuy6wFY
            val token = task.result
            console.log("Token: ${task.result}")

            // Log and toast
            //  val msg = getString(R.string.msg_token_fmt, token)
            // Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
        })

        Firebase.messaging.subscribeToTopic("weather")
            .addOnCompleteListener { task ->
                // var msg = getString(R.string.msg_subscribed)

                console.log("")
                //  Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
            }
    }

    fun saveDeviceId() {
        deviceIdField.get()?.let { console.log("device id ->", it) }
    }

    private fun startBackgroundProcess() {
        alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager;
        val alarmIntent = Intent(this, WakeUp::class.java)

        // alarmIntent.action = WAKE_UP_ACTION
        alarmIntent.action = "${WAKE_UP_ACTION}-${System.currentTimeMillis()}"
        val pi = PendingIntent.getBroadcast(
            this,
            0,
            alarmIntent,
            0//PendingIntent.FLAG_ONE_SHOT
        )
        // alarmManager.cancel(pi)
        val time = System.currentTimeMillis();
        alarmManager.set(AlarmManager.RTC_WAKEUP, time + 1000, pi)

        val app = applicationContext as ATApplication
        app.registerDreamingStop()

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
            // ?:console.log( "Service Not Bound")
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
            console.log("Request foreground only permission")
        }
    }

    override fun onStart() {
        super.onStart()
    }

    override fun onResume() {
        super.onResume()
        console.log("On resume main activity")

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

    internal object console {
        val TAG = "TAG_MainActivity"
        fun log(vararg message: String) {
            Log.d(TAG, message.joinToString(separator = " ") { it })
        }
    }
}