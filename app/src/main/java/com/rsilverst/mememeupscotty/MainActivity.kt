package com.rsilverst.mememeupscotty

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.rsilverst.mememeupscotty.data.network.NetworkModule
import com.rsilverst.mememeupscotty.data.repository.ImageRepository
import com.rsilverst.mememeupscotty.data.repository.ReplicateImageRepository
import com.rsilverst.mememeupscotty.ui.MemeScreen
import com.rsilverst.mememeupscotty.ui.cleanCacheDirectory
import com.rsilverst.mememeupscotty.ui.theme.MemeMeUpScottyTheme
import com.rsilverst.mememeupscotty.ui.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

private const val TAG = "MainActivity"

private val Context.historyDataStore by preferencesDataStore(name = "history_prefs")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Must run before super.onCreate(): swaps the .Starting theme out for the
        // post-splash app theme and keeps the system splash visible until the
        // first Compose frame.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Proactive cleanup of orphaned cache files bound to activity lifecycle:
        // - generated_meme_* : AI generations (downloaded by ImageRepository)
        // - gallery_meme_*   : copies of user-picked photos
        // - shared_meme_*    : FileProvider-shared bitmaps from the share sheet (recursively cleaned)
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                cacheDir?.let { cleanCacheDirectory(it) }
            } catch (e: Exception) {
                Log.w(TAG, "Cache cleanup failed", e)
            }
        }

        val imageRepository: ImageRepository = ReplicateImageRepository(NetworkModule.replicateApi)
        val historyDir = File(filesDir, "history").apply { mkdirs() }
        val factory = viewModelFactory {
            initializer { MainViewModel(imageRepository, historyDir, historyDataStore) }
        }

        setContent {
            MemeMeUpScottyTheme {
                val viewModel: MainViewModel = viewModel(factory = factory)

                MemeScreen(
                    viewModel = viewModel
                )
            }
        }
    }
}
