package com.rsilverst.mememeupscotty

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.preferencesDataStore

val Context.historyDataStore by preferencesDataStore(name = "history_prefs")

class MemeApplication : Application()

