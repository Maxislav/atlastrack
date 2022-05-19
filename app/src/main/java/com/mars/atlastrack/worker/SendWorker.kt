package com.mars.atlastrack.worker

import android.content.Context
import android.location.Location
import androidx.work.Data
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.mars.atlastrack.ATApplication
import com.mars.atlastrack.Callback
import com.mars.atlastrack.rest.AtlasLocationRest


class SendWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params), Callback {
    val context = ctx
    var end = false
    override fun doWork(): Result {
        val location = Location("A")
        location.longitude = inputData.getDouble("lng", 0.0)
        location.latitude = inputData.getDouble("lat", 0.0)
        var date = inputData.getString("date")
        var time = inputData.getString("time")
        val batt = inputData.getInt("batt", 0)
        if(date == null){
            date = ""
        }
        if(time === null){
            time = ""
        }
        val app = context.applicationContext as ATApplication

        val atlasRest = AtlasLocationRest(location, batt, date, time, app.deviceId)
        val response =  atlasRest.execute()
        if(response.isSuccessful){
            val responseBody  = response.body()?.string()
            val responseData = Data.Builder()
            responseData.putString("body", responseBody);
            return Result.success(responseData.build())
        }

        return Result.failure()
    }

    override fun onSuccess() {
       end = true
    }

    override fun onFailure() {
        end = true
    }


}