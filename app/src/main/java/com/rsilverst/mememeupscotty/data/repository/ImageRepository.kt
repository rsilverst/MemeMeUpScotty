package com.rsilverst.mememeupscotty.data.repository

import com.rsilverst.mememeupscotty.data.network.NetworkModule
import com.rsilverst.mememeupscotty.data.network.ReplicateApi
import com.rsilverst.mememeupscotty.data.network.ReplicatePrediction
import com.rsilverst.mememeupscotty.data.network.ReplicatePredictionInput
import com.rsilverst.mememeupscotty.data.network.ReplicatePredictionRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import retrofit2.Response
import java.io.File
import kotlin.random.Random

interface ImageRepository {
    suspend fun generateImage(modelId: String, prompt: String, cacheDir: File): Result<File>
}

class ReplicateImageRepository(private val api: ReplicateApi) : ImageRepository {

    override suspend fun generateImage(modelId: String, prompt: String, cacheDir: File): Result<File> = withContext(Dispatchers.IO) {
        var tempFile: File? = null
        try {
            val (owner, name) = parseModelId(modelId)
                ?: return@withContext Result.failure(Exception("Invalid REPLICATE_MODEL_ID: '$modelId' (expected 'owner/name')"))

            val modelResponse = api.getModel(owner, name)
            val model = unwrap(modelResponse)
                ?: return@withContext Result.failure(errorFor(modelResponse, "fetch model $owner/$name"))
            val versionId = model.latest_version?.id
                ?: return@withContext Result.failure(Exception("Model $owner/$name has no published version"))

            val request = ReplicatePredictionRequest(
                version = versionId,
                input = ReplicatePredictionInput(
                    prompt = composePrompt(prompt),
                    negative_prompt = NEGATIVE_PROMPT,
                    seed = Random.nextInt(0, Int.MAX_VALUE)
                )
            )

            val createResponse = api.createPrediction(request)
            val created = unwrap(createResponse)
                ?: return@withContext Result.failure(errorFor(createResponse, "create prediction"))

            val finished = poll(created.id)
                ?: return@withContext Result.failure(Exception("Prediction timed out"))

            when (finished.status) {
                "succeeded" -> {
                    val url = finished.output?.firstOrNull()
                        ?: return@withContext Result.failure(Exception("Prediction succeeded but returned no image"))
                    val file = File.createTempFile("generated_meme_", ".png", cacheDir)
                    tempFile = file
                    downloadTo(url, file)
                    Result.success(file)
                }
                "failed", "canceled" -> {
                    Result.failure(Exception(finished.error ?: "Prediction ${finished.status}"))
                }
                else -> Result.failure(Exception("Unexpected status: ${finished.status}"))
            }
        } catch (e: Exception) {
            tempFile?.takeIf { it.exists() }?.delete()
            Result.failure(e)
        }
    }

    private suspend fun poll(id: String): ReplicatePrediction? {
        val deadline = System.currentTimeMillis() + POLL_TIMEOUT_MS
        var interval = INITIAL_POLL_INTERVAL_MS
        while (System.currentTimeMillis() < deadline) {
            delay(interval)
            val response = api.getPrediction(id)
            val prediction = unwrap(response) ?: return null
            if (prediction.status in TERMINAL_STATUSES) return prediction
            interval = (interval + 500).coerceAtMost(MAX_POLL_INTERVAL_MS)
        }
        return null
    }

    private fun downloadTo(url: String, target: File) {
        val request = Request.Builder().url(url).build()
        NetworkModule.imageDownloadClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Failed to download image: HTTP ${response.code}")
            }
            val body = response.body ?: throw Exception("Image response had no body")
            body.byteStream().use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        }
    }

    private fun <T> unwrap(response: Response<T>): T? =
        if (response.isSuccessful) response.body() else null

    private fun errorFor(response: Response<*>, op: String): Exception {
        val body = response.errorBody()?.string().orEmpty()
        return Exception(friendlyMessage(response.code(), body, op))
    }

    private fun friendlyMessage(code: Int, body: String, op: String): String {
        val detail = parseJsonField(body, "detail")
        val retryAfter = parseJsonInt(body, "retry_after")
        return when (code) {
            401 -> "Your Replicate API token isn't accepted. Check REPLICATE_API_TOKEN in local.properties."
            402 -> "You're out of Replicate credit. Add some at replicate.com/account/billing."
            404 -> "This model isn't available on Replicate right now. Try a different one from the dropdown."
            429 -> buildString {
                append("Hit Replicate's rate limit.")
                if (retryAfter != null) {
                    append(" Try again in ${retryAfter}s.")
                } else {
                    append(" Try again in a few seconds.")
                }
            }
            in 500..599 -> "Replicate is having a problem (HTTP $code). Try again in a moment."
            else -> detail?.let { "Replicate: $it" } ?: "Replicate $op failed (HTTP $code)."
        }
    }

    private fun parseJsonField(body: String, field: String): String? = try {
        if (body.isBlank()) null else JSONObject(body).optString(field).takeIf { it.isNotBlank() }
    } catch (_: Exception) {
        null
    }

    private fun parseJsonInt(body: String, field: String): Int? = try {
        if (body.isBlank()) null else JSONObject(body).optInt(field, -1).takeIf { it > 0 }
    } catch (_: Exception) {
        null
    }

    private fun parseModelId(modelId: String): Pair<String, String>? {
        val parts = modelId.split("/")
        if (parts.size != 2 || parts.any { it.isBlank() }) return null
        return parts[0] to parts[1]
    }

    private fun composePrompt(userPrompt: String): String =
        "$userPrompt, photorealistic, sharp focus, natural lighting, high detail, cinematic photograph"

    companion object {
        private const val NEGATIVE_PROMPT =
            "cartoon, anime, painting, illustration, drawing, 3d render, cgi, low quality, blurry, distorted face, deformed, watermark, text"
        private const val INITIAL_POLL_INTERVAL_MS = 1_500L
        private const val MAX_POLL_INTERVAL_MS = 3_000L
        private const val POLL_TIMEOUT_MS = 120_000L
        private val TERMINAL_STATUSES = setOf("succeeded", "failed", "canceled")
    }
}
