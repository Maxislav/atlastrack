package com.mars.atlastrack

import android.content.Context
import android.content.Context.POWER_SERVICE
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.mars.atlastrack.SharedPreferenceUtil.FIVE_MINUTES
import com.mars.atlastrack.SharedPreferenceUtil.getSharedDate
import com.mars.atlastrack.SharedPreferenceUtil.saveSharedDate
import java.util.concurrent.CountDownLatch


class PeriodWorker(private val ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    var latch: CountDownLatch? = null
    override fun doWork(): Result {
        val serviceIntent = Intent(ctx, LocationService::class.java)
        latch = CountDownLatch(1)
        val d: Long? = getSharedDate(ctx)?.time
        saveSharedDate(ctx, System.currentTimeMillis())



        if (d == null || d + FIVE_MINUTES < System.currentTimeMillis()) {
            saveSharedDate(ctx, System.currentTimeMillis())
            sleep {
                val pm = ctx.getSystemService(POWER_SERVICE) as PowerManager?
                val powersaving: Boolean = pm!!.isPowerSaveMode
                if (!powersaving) {
                    val isOk = try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            ctx.startForegroundService(serviceIntent)

                        } else {
                            ctx.startService(serviceIntent)
                        }
                        true
                    } catch (e: Error) {
                        false
                    }
                }
            }
        } else {
            return Result.success()
        }

        latch?.await()
        return Result.success()
    }

    private fun sleep(callback: () -> Unit) {

        callback()
        Thread.sleep(5000)
        latch?.countDown()
    }
}