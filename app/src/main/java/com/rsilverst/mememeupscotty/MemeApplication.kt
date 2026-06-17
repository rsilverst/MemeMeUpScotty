package com.rsilverst.mememeupscotty

import android.app.Application
import android.content.Context
import androidx.appfunctions.service.AppFunctionConfiguration
import androidx.datastore.preferences.preferencesDataStore
import com.rsilverst.mememeupscotty.data.network.NetworkModule
import com.rsilverst.mememeupscotty.data.repository.ReplicateImageRepository
import com.rsilverst.mememeupscotty.ui.appfunctions.MemeAppFunctions

val Context.historyDataStore by preferencesDataStore(name = "history_prefs")

class MemeApplication : Application(), AppFunctionConfiguration.Provider {

    override val appFunctionConfiguration: AppFunctionConfiguration by lazy {
        val imageRepository = ReplicateImageRepository(NetworkModule.replicateApi)
        val appFunctions = MemeAppFunctions(imageRepository)

        AppFunctionConfiguration.Builder()
            .addEnclosingClassFactory(MemeAppFunctions::class.java) { appFunctions }
            .build()
    }
}
