package com.mars.atlastrack

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.lang.Error

class PeriodWorker(private val ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): Result {
        // startService()
        val serviceIntent = Intent(ctx, LocationService::class.java)
        var isOk = true
        isOk = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ctx.startForegroundService(serviceIntent)

            } else {
                ctx.startService(serviceIntent)
            }
            true
        } catch (e: Error) {
            false
        }
        return  Result.success()
    }
}