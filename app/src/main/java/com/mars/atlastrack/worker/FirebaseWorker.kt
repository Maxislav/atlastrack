package com.mars.atlastrack.worker

import android.content.Context
import androidx.work.Data
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.google.firebase.ktx.Firebase
import com.mars.atlastrack.Callback
import com.mars.atlastrack.rest.FireBaseRest

class FirebaseWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params), Callback {
    var end = false
    override fun doWork(): Result {

        var deviceId = ""
        inputData.getString(DEVICE_ID)?.run {
            deviceId = this
        }
        var token = ""
        inputData.getString(TOKEN)?.run {
            token = this
        }

        val sum = "${(0..9).random()}${(0..9).random()}" //control sum
        val rest = FireBaseRest()
        val response = rest.execute(deviceId, token, sum)
        if(response.isSuccessful){
            val responseBody  = response.body()?.string()
            val responseData = Data.Builder()
            responseData.putString(BODY, responseBody)
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

    companion object {
        val DEVICE_ID = "device_id"
        val TOKEN = "token"
        val BODY = "body"
    }
}