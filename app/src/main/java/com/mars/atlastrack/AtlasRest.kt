package com.mars.atlastrack

import android.location.Location
import android.util.Log
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Query
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*



class AtlasRest : Callback<ResponseBody> {
    private val TAG = "AtlasRest"
    var onLog: Call<ResponseBody>
    var location: Location
    lateinit var callbackLocation: com.mars.atlastrack.Callback

    constructor(location: Location) {
        this.location = location
        val retrofit = Retrofit.Builder()
            .baseUrl(BuildConfig.SERVER_BASE_PATH) // .addConverterFactory(GsonConverterFactory.create())
            .build()
        val service = retrofit.create(WebService::class.java)
        var gprmc = createGprmc()
        Log.d(TAG, gprmc)
        onLog = service.log(BuildConfig.DEVICE_ID, gprmc)
    }

    interface WebService {
        @GET("/log")
        open fun log(@Query("id") id: String, @Query("gprmc") gprmc: String): Call<ResponseBody>
    }

    private fun createGprmc(): String {
        val currentTime = Date()
        val dateFormat: DateFormat = SimpleDateFormat("ddMMyy")
        dateFormat.timeZone = TimeZone.getTimeZone("UTC")
        val sdf = SimpleDateFormat("HHmmss")
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        val time: String = sdf.format(currentTime)
        val date = dateFormat.format(currentTime)
        var latitude = locationToMinute(location.latitude)
        var longitude = locationToMinute(location.longitude)
        return "\$GPRMC,${time},A,${latitude},${NS},${longitude},${WE},00,00,${date},,*74"
    }

    val NS: String get() = if(0<location.latitude) NORTH else SOUTH

    val WE: String get() = if (0 < location.longitude) EAST else WEST

    fun locationToMinute(ltd: Double): String {
        val prefix = ltd.toInt()
        var suffix = (ltd - prefix) * 60
        return "${prefix}${suffix}"

    }


    companion object {
        internal const val NORTH = "N"
        internal const val SOUTH = "S"
        internal const val WEST = "W"
        internal const val EAST = "E"
    }

    fun request(callback: com.mars.atlastrack.Callback) {
        this.callbackLocation = callback
        onLog.enqueue(this)
       // onLog.execute()
    }

    override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
        Log.d(TAG, response.body().toString())
        try {
            this.callbackLocation.onSuccess()
        }catch (e : Throwable){
            Log.d(TAG, e.stackTraceToString())
        }

    }

    override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
        Log.d(TAG, t.toString())

        try {
            this.callbackLocation.onFailure()
        }catch (e : Throwable){
            Log.d(TAG, e.stackTraceToString())
        }
    }

    /*override fun onResponse(call: Call<ResponseBody?>, response: Response<String?>) {
        Log.i("Response", response.body().toString())
        //Toast.makeText()
        if (response.isSuccessful) {
            if (response.body() != null) {
                Log.d("onSuccess", response.body().toString())
            } else {
                Log.d(
                    "onEmptyResponse",
                    "Returned empty response"
                ) //Toast.makeText(getContext(),"Nothing returned",Toast.LENGTH_LONG).show();
            }
        }
    }

    override fun onFailure(call: Call<ResponseBody?>, t: Throwable) {
        Log.d(TAG, t.toString())
    }*/
}