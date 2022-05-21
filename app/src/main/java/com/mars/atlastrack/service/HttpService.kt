package com.mars.atlastrack.service

import android.content.Context
import androidx.work.*
import com.mars.atlastrack.ATApplication
import com.mars.atlastrack.LocationService
import com.mars.atlastrack.worker.FirebaseWorker

class HttpService(val context: Context) {
    private var app: ATApplication = context.applicationContext as ATApplication
    fun saveToken(token: String){
        val data = Data.Builder()
        data.putString(FirebaseWorker.TOKEN, token)
        data.putString(FirebaseWorker.DEVICE_ID, app.deviceId)
        val workManager = WorkManager.getInstance(context)
        val networkConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val uploadTask = OneTimeWorkRequestBuilder<FirebaseWorker>()
            .setConstraints(networkConstraints)
            .setInputData(data.build())
            .build()
        workManager.getWorkInfoByIdLiveData(uploadTask.id)
            .observeForever { workInfo: WorkInfo? ->
                if (workInfo != null && workInfo.state.isFinished) {
                    LocationService.console.log("response firebase sum ${workInfo.outputData.getString("body")}")
                }
            }
        workManager.enqueue(uploadTask)
    }

}