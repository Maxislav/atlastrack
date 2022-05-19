package com.mars.atlastrack

import android.app.Service
import android.content.Context
import android.location.Location
import android.os.Build
import android.os.PowerManager
import androidx.core.content.edit
import java.util.*

fun Location?.toText(): String {
    return if (this != null) {
        "($latitude, $longitude)"
    } else {
        "Unknown location"
    }
}

/**
 * Provides access to SharedPreferences for location to Activities and Services.
 */
internal object SharedPreferenceUtil {

    const val KEY_FOREGROUND_ENABLED = "tracking_foreground_location"

    const val TWO_MINUTES = (60 * 1000 * 2).toLong()
    const val TEN_SECONDS = (10 * 1000).toLong()
    const val TWENTY_MINUTES = (60 * 1000 * 20).toLong()
    const val THIRTY_MINUTES = (60 * 1000 * 30).toLong()
    const val FIVE_MINUTES = (60 * 1000 * 5).toLong()

    const val date = ""

    /**
     * Returns true if requesting location updates, otherwise returns false.
     *
     * @param context The [Context].
     */
    fun getLocationTrackingPref(context: Context): Boolean =
        context.getSharedPreferences(
            context.getString(R.string.preference_file_key), Context.MODE_PRIVATE
        )
            .getBoolean(KEY_FOREGROUND_ENABLED, false)

    /**
     * Stores the location updates state in SharedPreferences.
     * @param requestingLocationUpdates The location updates state.
     */
    fun saveLocationTrackingPref(context: Context, requestingLocationUpdates: Boolean) =
        context.getSharedPreferences(
            context.getString(R.string.preference_file_key),
            Context.MODE_PRIVATE
        ).edit {
            putBoolean(KEY_FOREGROUND_ENABLED, requestingLocationUpdates)
        }



    fun isIdle(context: Context): Boolean{
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isDeviceIdleMode

    }
    fun isDozing(context: Context): Boolean {
        val powerManager = context.getSystemService(Service.POWER_SERVICE) as PowerManager
        return powerManager.isDeviceIdleMode &&
                !powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }
    @Synchronized
    fun saveSharedDate(context: Context, date: Long): Unit{
        val sharedPreferences = context.getSharedPreferences("DATA", Context.MODE_PRIVATE)
        date.toString()
        sharedPreferences.edit().putString("READ_PATH", date.toString()).apply()
    }

    @Synchronized
    fun getSharedDate(context: Context): Date? {
        val sharedPreferences = context.getSharedPreferences("DATA", Context.MODE_PRIVATE)
        val dateString =  sharedPreferences.getString("READ_PATH", null)
        if(dateString!==null){
            return Date(dateString.toLong())
        }
        return null
    }


}