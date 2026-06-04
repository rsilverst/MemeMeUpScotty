package com.rsilverst.mememeupscotty.data.repository

import com.rsilverst.mememeupscotty.data.network.NetworkModule
import com.rsilverst.mememeupscotty.data.network.ReplicateApi
import com.rsilverst.mememeupscotty.data.network.ReplicateErrorBody
import com.rsilverst.mememeupscotty.data.network.ReplicatePrediction
import com.rsilverst.mememeupscotty.data.network.ReplicatePredictionInput
import com.rsilverst.mememeupscotty.data.network.ReplicatePredictionRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.Request
import retrofit2.Response
import java.io.File
import kotlin.random.Random

// Result of a generation attempt. UI layers should pattern-match on this
// (and on GenerationError below) rather than reading any human-readable
// strings — string copy lives entirely in the resource layer now.
sealed class GenerationOutcome {
    data class Success(val file: File) : GenerationOutcome()
    data class Failure(val error: GenerationError) : GenerationOutcome()
}

// Typed taxonomy of generation failures. Each variant is what the UI
// switches on to pick a title + detail string and decide whether retry
// is reasonable. Add a new variant rather than overloading Unexpected
// when a new condition is worth distinguishing in the UI.
sealed class GenerationError {
    data object AuthRejected : GenerationError()
    data object OutOfCredit : GenerationError()
    data object ModelUnavailable : GenerationError()
    data class RateLimited(val retryAfterSec: Int?) : GenerationError()
    data class Server(val httpCode: Int) : GenerationError()
    data object Timeout : GenerationError()
    // Catch-all. `detail` is raw (English from Replicate or our own
    // fallback); the UI shows it verbatim in the small body text.
    data class Unexpected(val detail: String) : GenerationError()
}

interface ImageRepository {
    suspend fun generateImage(modelId: String, prompt: String, cacheDir: File): GenerationOutcome
}

private data class ModelPromptConfig(
    val positiveSuffix: String,
    val negativePrompt: String?
)

class ReplicateImageRepository(private val api: ReplicateApi) : ImageRepository {

    private val errorBodyAdapter = NetworkModule.moshi.adapter(ReplicateErrorBody::class.java)

    override suspend fun generateImage(modelId: String, prompt: String, cacheDir: File): GenerationOutcome = withContext(Dispatchers.IO) {
        var tempFile: File? = null
        try {
            val (owner, name) = parseModelId(modelId)
                ?: return@withContext failure(GenerationError.Unexpected("Invalid model id: '$modelId' (expected 'owner/name')"))

            val modelResponse = api.getModel(owner, name)
            val model = unwrap(modelResponse)
                ?: return@withContext failure(errorFor(modelResponse))
            val versionId = model.latestVersion?.id
                ?: return@withContext failure(GenerationError.Unexpected("Model $owner/$name has no published version"))

            val config = MODEL_PROMPT_CONFIGS[modelId] ?: DEFAULT_PROMPT_CONFIG
            val request = ReplicatePredictionRequest(
                version = versionId,
                input = ReplicatePredictionInput(
                    prompt = composePrompt(prompt, config),
                    negativePrompt = config.negativePrompt,
                    seed = Random.nextInt(0, Int.MAX_VALUE),
                    // Personal/single-user app — brief explicitly says no content moderation.
                    // Models that don't accept this field ignore it.
                    disableSafetyChecker = true
                )
            )

            val createResponse = api.createPrediction(request)
            val created = unwrap(createResponse)
                ?: return@withContext failure(errorFor(createResponse))

            val finished = poll(created.id)
                ?: return@withContext failure(GenerationError.Timeout)

            when (finished.status) {
                "succeeded" -> {
                    val url = finished.output?.firstOrNull()
                        ?: return@withContext failure(GenerationError.Unexpected("Prediction succeeded but returned no image"))
                    val file = File.createTempFile("generated_meme_", ".png", cacheDir)
                    tempFile = file
                    downloadTo(url, file)
                    GenerationOutcome.Success(file)
                }
                "failed", "canceled" -> {
                    failure(GenerationError.Unexpected(finished.error ?: "Prediction ${finished.status}"))
                }
                else -> failure(GenerationError.Unexpected("Unexpected status: ${finished.status}"))
            }
        } catch (e: Exception) {
            tempFile?.takeIf { it.exists() }?.delete()
            // Rethrow CancellationException to support cooperative cancellation in structured concurrency
            if (e is CancellationException) throw e
            failure(GenerationError.Unexpected(e.message ?: "Unknown error"))
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
            val body = response.body
            body.byteStream().use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        }
    }

    private fun <T> unwrap(response: Response<T>): T? =
        if (response.isSuccessful) response.body() else null

    // Maps an HTTP failure response to a typed GenerationError. Replicate's
    // error bodies are JSON like {"detail": "...", "retry_after": 30}; we
    // parse them with Moshi and only fall back to a raw body for codes we
    // don't have a typed variant for.
    private fun errorFor(response: Response<*>): GenerationError {
        val body = response.errorBody()?.string().orEmpty()
        val parsed = parseErrorBody(body)
        return when (val code = response.code()) {
            401 -> GenerationError.AuthRejected
            402 -> GenerationError.OutOfCredit
            404 -> GenerationError.ModelUnavailable
            429 -> GenerationError.RateLimited(parsed?.retryAfter)
            in 500..599 -> GenerationError.Server(code)
            else -> GenerationError.Unexpected(parsed?.detail ?: "Replicate request failed (HTTP $code)")
        }
    }

    private fun parseErrorBody(body: String): ReplicateErrorBody? = try {
        if (body.isBlank()) null else errorBodyAdapter.fromJson(body)
    } catch (_: Exception) {
        null
    }

    private fun parseModelId(modelId: String): Pair<String, String>? {
        val parts = modelId.split("/")
        if (parts.size != 2 || parts.any { it.isBlank() }) return null
        return parts[0] to parts[1]
    }

    private fun composePrompt(userPrompt: String, config: ModelPromptConfig): String =
        if (config.positiveSuffix.isBlank()) userPrompt
        else "$userPrompt, ${config.positiveSuffix}"

    private fun failure(error: GenerationError): GenerationOutcome.Failure =
        GenerationOutcome.Failure(error)

    companion object {
        // Anti-anatomy + anti-duplication terms. These are the canonical SDXL-era
        // negatives that mitigate the artifacts users actually see (extra/fused
        // fingers, mangled limbs, accidental twins). Kept compact so the full
        // composed negative stays under SDXL's 77-token CLIP limit.
        private const val ANATOMY_AND_DUPLICATION_NEG =
            "mutated hands, extra fingers, missing fingers, fused fingers, poorly drawn hands, " +
                "extra limbs, missing limbs, disconnected limbs, bad anatomy, deformed, disfigured, " +
                "bad proportions, multiple subjects, duplicate, twin, cloned"

        // Style negatives that push photoreal models away from cartoon/anime
        // territory. Deliberately omitted for Blue Pencil (anime) and Proteus
        // (painterly) — they would fight the model's intent.
        private const val PHOTOREAL_STYLE_NEG =
            "cartoon, anime, painting, illustration, 3d render"

        // Quality + boilerplate. Includes "text" because we overlay our own
        // captions and don't want random gibberish text baked into the image.
        private const val QUALITY_NEG =
            "low quality, blurry, jpeg artifacts, watermark, signature, text"

        private val FULL_PHOTOREAL_NEG =
            "$PHOTOREAL_STYLE_NEG, $QUALITY_NEG, $ANATOMY_AND_DUPLICATION_NEG"
        private val STYLIZED_NEG =
            "$QUALITY_NEG, $ANATOMY_AND_DUPLICATION_NEG"

        // Keys match ImageModel.id values in ui/viewmodel/MainViewModel.kt.
        // Intentionally string-keyed (not enum) so this data layer stays
        // independent of the UI layer. An unrecognised id falls back to
        // DEFAULT_PROMPT_CONFIG below.
        private val MODEL_PROMPT_CONFIGS = mapOf(
            "sdxl-based/juggernaut-xl-lightning" to ModelPromptConfig(
                positiveSuffix = "photorealistic, sharp focus, cinematic lighting, high detail",
                negativePrompt = FULL_PHOTOREAL_NEG
            ),
            "stability-ai/sdxl" to ModelPromptConfig(
                positiveSuffix = "professional photograph, sharp focus, natural lighting, high detail",
                negativePrompt = FULL_PHOTOREAL_NEG
            ),
            "adirik/realvisxl-v3.0-turbo" to ModelPromptConfig(
                positiveSuffix = "photorealistic portrait, sharp focus, natural skin texture, cinematic lighting, high detail",
                negativePrompt = FULL_PHOTOREAL_NEG
            ),
            "black-forest-labs/flux-schnell" to ModelPromptConfig(
                // Flux is a rectified-flow model: it doesn't use CFG / negative
                // prompts and responds best to natural-language descriptions.
                // Keep the suffix minimal and skip the negative entirely.
                positiveSuffix = "high detail, sharp focus",
                negativePrompt = null
            ),
            "lucataco/dreamshaper-xl-lightning" to ModelPromptConfig(
                positiveSuffix = "realistic, soft lighting, warm tones, sharp focus, high detail",
                negativePrompt = FULL_PHOTOREAL_NEG
            ),
            "asiryan/blue-pencil-xl-v2" to ModelPromptConfig(
                positiveSuffix = "anime illustration, vibrant colors, detailed line art, masterpiece, best quality",
                negativePrompt = STYLIZED_NEG
            ),
            "datacte/proteus-v0.5" to ModelPromptConfig(
                positiveSuffix = "oil painting, painterly brush strokes, fine art, classical composition, detailed",
                negativePrompt = STYLIZED_NEG
            )
        )

        private val DEFAULT_PROMPT_CONFIG = ModelPromptConfig(
            positiveSuffix = "high detail, sharp focus",
            negativePrompt = STYLIZED_NEG
        )

        private const val INITIAL_POLL_INTERVAL_MS = 1_500L
        private const val MAX_POLL_INTERVAL_MS = 3_000L
        private const val POLL_TIMEOUT_MS = 120_000L
        private val TERMINAL_STATUSES = setOf("succeeded", "failed", "canceled")
    }
}
