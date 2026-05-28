package com.rsilverst.mememeupscotty

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Proactive cleanup of orphaned generated memes bound to activity lifecycle
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                cacheDir?.listFiles()?.forEach { file ->
                    if (file.name.startsWith("generated_meme_")) {
                        file.delete()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
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
