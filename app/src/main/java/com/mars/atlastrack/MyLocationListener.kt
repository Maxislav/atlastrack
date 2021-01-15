package com.mars.atlastrack

import android.location.Location
import android.location.LocationListener
import android.os.Bundle
import android.util.Log


class MyLocationListener : LocationListener {
    var TAG = "LocationListener"
    override fun onLocationChanged(location: Location) {
        Log.d(TAG, "${location.longitude} ${location.latitude}")
    }

    override fun onProviderEnabled(provider: String) {}

    override fun onProviderDisabled(provider: String) {}

    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

}