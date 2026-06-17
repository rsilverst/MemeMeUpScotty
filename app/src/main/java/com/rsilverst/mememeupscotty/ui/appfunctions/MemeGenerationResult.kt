package com.rsilverst.mememeupscotty.ui.appfunctions

import androidx.appfunctions.AppFunctionSerializable

/**
 * Represents the outcome of generating a meme background image.
 */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class MemeGenerationResult(
    /** The absolute file path of the downloaded image in the app's local cache directory. */
    val filePath: String,
    /** The ID of the generative model that was used for the generation. */
    val modelId: String,
    /** The prompt that was used to generate the image. */
    val prompt: String
)
