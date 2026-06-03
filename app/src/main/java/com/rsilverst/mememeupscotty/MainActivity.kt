package com.rsilverst.mememeupscotty

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rsilverst.mememeupscotty.data.network.NetworkModule
import com.rsilverst.mememeupscotty.data.repository.ImageRepository
import com.rsilverst.mememeupscotty.data.repository.ReplicateImageRepository
import com.rsilverst.mememeupscotty.ui.MemeScreen
import com.rsilverst.mememeupscotty.ui.theme.MemeMeUpScottyTheme
import com.rsilverst.mememeupscotty.ui.viewmodel.MainViewModel
import com.rsilverst.mememeupscotty.ui.viewmodel.MainViewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val TAG = "MainActivity"

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
        // - shared_meme_*    : FileProvider-shared bitmaps from the share sheet
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                cacheDir?.listFiles()?.forEach { file ->
                    val name = file.name
                    if (name.startsWith("generated_meme_") ||
                        name.startsWith("gallery_meme_") ||
                        name.startsWith("shared_meme_")
                    ) {
                        file.delete()
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Cache cleanup failed", e)
            }
        }

        val imageRepository: ImageRepository = ReplicateImageRepository(NetworkModule.replicateApi)
        val factory = MainViewModelFactory(imageRepository)

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
