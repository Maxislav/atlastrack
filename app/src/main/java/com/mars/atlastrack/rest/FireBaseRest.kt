package com.mars.atlastrack.rest

import android.util.Log
import com.mars.atlastrack.BuildConfig
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Query

class FireBaseRest {

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.SERVER_BASE_PATH) // .addConverterFactory(GsonConverterFactory.create())
        .build()

    val service: WebService = retrofit.create(WebService::class.java)


    interface WebService {
        @GET("/firebase")
        fun request(
            @Query("id") id: String,
            @Query("token") token: String,
            @Query("sum") sum: String,
        ): Call<ResponseBody>
    }

    fun execute(deviceId: String, token: String, sum: String): Response<ResponseBody>{
        return service.request(deviceId, token, sum).execute()
    }

    internal object console {
        val TAG = "TAG_FireBaseRest"
        fun log(message: String) {
            Log.d(TAG, message)
        }
    }
}