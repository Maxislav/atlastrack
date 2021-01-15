package com.mars.atlastrack

import android.content.Context
import android.location.Location
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.CompletableFuture


class LocationWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params), Callback {
    val context = ctx
    var end = false
    override fun doWork(): Result {

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

        return Result.success()
    }

    override fun onSuccess() {
       end = true
    }

    override fun onFailure() {
        end = true
    }


}