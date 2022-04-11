package com.mars.atlastrack

import android.content.Context
import android.location.Location
import androidx.work.Data
import androidx.work.Worker
import androidx.work.WorkerParameters


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

        val atlasRest = AtlasRest(location, batt, date, time)
        val response =  atlasRest.execute()
        if(response.isSuccessful){
            val responseBody  = response.body()?.string()
            val responseData = Data.Builder()
            responseData.putString("body", responseBody);
            return Result.success(responseData.build())
        }



        /*val location = Location("A")
        location.longitude = inputData.getDouble("lng", 0.0)
        location.latitude = inputData.getDouble("lat", 0.0)
        val atlasRest = AtlasRest(location)
        atlasRest.request(this)*/
        //val completableFuture: CompletableFuture<String> = CompletableFuture<String>()
        // Data outputData
        // atlasRest.
        // return Result.success()
        //while ()
        // val completableFuture: CompletableFuture<String> = CompletableFuture<String>()

        return Result.failure()
    }

    override fun onSuccess() {
       end = true
    }

    override fun onFailure() {
        end = true
    }


}