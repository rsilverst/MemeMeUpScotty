package com.rsilverst.mememeupscotty.data.repository

import com.rsilverst.mememeupscotty.data.network.ReplicateApi
import com.rsilverst.mememeupscotty.data.network.ReplicateModel
import com.rsilverst.mememeupscotty.data.network.ReplicateModelVersion
import com.rsilverst.mememeupscotty.data.network.ReplicatePrediction
import com.rsilverst.mememeupscotty.data.network.ReplicatePredictionRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import java.io.File
import kotlin.test.assertFailsWith

@OptIn(ExperimentalCoroutinesApi::class)
class ReplicateImageRepositoryTest {

    private val cacheDir = File(System.getProperty("java.io.tmpdir"), "repo-test")
    private val validModelId = "test-owner/test-model"

    @Test
    fun invalidModelId_returnsUnexpected() = runTest {
        val repo = ReplicateImageRepository(FakeReplicateApi())
        val outcome = repo.generateImage("not-a-slash-pair", "prompt", cacheDir)
        val error = (outcome as GenerationOutcome.Failure).error as GenerationError.Unexpected
        assertTrue(error.detail.contains("Invalid model id"))
    }

    @Test
    fun getModel_401_returnsAuthRejected() = runTest {
        val api = FakeReplicateApi(getModelResponse = errorResponse(401))
        val outcome = ReplicateImageRepository(api).generateImage(validModelId, "p", cacheDir)
        assertEquals(GenerationError.AuthRejected, (outcome as GenerationOutcome.Failure).error)
    }

    @Test
    fun getModel_402_returnsOutOfCredit() = runTest {
        val api = FakeReplicateApi(getModelResponse = errorResponse(402))
        val outcome = ReplicateImageRepository(api).generateImage(validModelId, "p", cacheDir)
        assertEquals(GenerationError.OutOfCredit, (outcome as GenerationOutcome.Failure).error)
    }

    @Test
    fun getModel_404_returnsModelUnavailable() = runTest {
        val api = FakeReplicateApi(getModelResponse = errorResponse(404))
        val outcome = ReplicateImageRepository(api).generateImage(validModelId, "p", cacheDir)
        assertEquals(GenerationError.ModelUnavailable, (outcome as GenerationOutcome.Failure).error)
    }

    @Test
    fun getModel_429WithRetryAfter_parsesSeconds() = runTest {
        val body = """{"detail":"slow down","retry_after":42}"""
        val api = FakeReplicateApi(getModelResponse = errorResponse(429, body))
        val outcome = ReplicateImageRepository(api).generateImage(validModelId, "p", cacheDir)
        val error = (outcome as GenerationOutcome.Failure).error as GenerationError.RateLimited
        assertEquals(42, error.retryAfterSec)
    }

    @Test
    fun getModel_429WithoutRetryAfter_returnsRateLimitedNull() = runTest {
        val api = FakeReplicateApi(getModelResponse = errorResponse(429))
        val outcome = ReplicateImageRepository(api).generateImage(validModelId, "p", cacheDir)
        val error = (outcome as GenerationOutcome.Failure).error as GenerationError.RateLimited
        assertNull(error.retryAfterSec)
    }

    @Test
    fun getModel_500_returnsServerWithCode() = runTest {
        val api = FakeReplicateApi(getModelResponse = errorResponse(503))
        val outcome = ReplicateImageRepository(api).generateImage(validModelId, "p", cacheDir)
        val error = (outcome as GenerationOutcome.Failure).error as GenerationError.Server
        assertEquals(503, error.httpCode)
    }

    @Test
    fun modelHasNoLatestVersion_returnsUnexpected() = runTest {
        val api = FakeReplicateApi(
            getModelResponse = Response.success(
                ReplicateModel(owner = "test-owner", name = "test-model", latestVersion = null)
            )
        )
        val outcome = ReplicateImageRepository(api).generateImage(validModelId, "p", cacheDir)
        val error = (outcome as GenerationOutcome.Failure).error as GenerationError.Unexpected
        assertTrue(error.detail.contains("no published version"))
    }

    @Test
    fun createPrediction_HTTPError_mapsToTypedVariant() = runTest {
        val api = FakeReplicateApi(
            getModelResponse = okModelResponse(),
            createPredictionResponse = errorResponse(402)
        )
        val outcome = ReplicateImageRepository(api).generateImage(validModelId, "p", cacheDir)
        assertEquals(GenerationError.OutOfCredit, (outcome as GenerationOutcome.Failure).error)
    }

    @Test
    fun predictionFailedWithErrorMessage_returnsUnexpected() = runTest {
        val api = FakeReplicateApi(
            getModelResponse = okModelResponse(),
            createPredictionResponse = okPredictionResponse(status = "starting"),
            getPredictionResponse = okPredictionResponse(
                status = "failed",
                error = "model exploded"
            )
        )
        val outcome = ReplicateImageRepository(api).generateImage(validModelId, "p", cacheDir)
        val error = (outcome as GenerationOutcome.Failure).error as GenerationError.Unexpected
        assertEquals("model exploded", error.detail)
    }

    @Test
    fun predictionCanceled_returnsUnexpected() = runTest {
        val api = FakeReplicateApi(
            getModelResponse = okModelResponse(),
            createPredictionResponse = okPredictionResponse(status = "starting"),
            getPredictionResponse = okPredictionResponse(status = "canceled")
        )
        val outcome = ReplicateImageRepository(api).generateImage(validModelId, "p", cacheDir)
        val error = (outcome as GenerationOutcome.Failure).error as GenerationError.Unexpected
        assertTrue(error.detail.contains("canceled"))
    }

    @Test
    fun predictionSucceededButEmptyOutput_returnsUnexpected() = runTest {
        val api = FakeReplicateApi(
            getModelResponse = okModelResponse(),
            createPredictionResponse = okPredictionResponse(status = "starting"),
            getPredictionResponse = okPredictionResponse(status = "succeeded", output = null)
        )
        val outcome = ReplicateImageRepository(api).generateImage(validModelId, "p", cacheDir)
        val error = (outcome as GenerationOutcome.Failure).error as GenerationError.Unexpected
        assertTrue(error.detail.contains("no image"))
    }

    // Per current behavior, an HTTP failure on getPrediction makes poll() return
    // null, which the caller surfaces as Timeout. Pinning that so the contract
    // is explicit; if we ever differentiate "poll error" from "actual timeout",
    // this test should change deliberately.
    @Test
    fun getPrediction_HTTPFailureMidPoll_returnsTimeout() = runTest {
        val api = FakeReplicateApi(
            getModelResponse = okModelResponse(),
            createPredictionResponse = okPredictionResponse(status = "starting"),
            getPredictionResponse = errorResponse(500)
        )
        val outcome = ReplicateImageRepository(api).generateImage(validModelId, "p", cacheDir)
        assertEquals(GenerationError.Timeout, (outcome as GenerationOutcome.Failure).error)
    }

    @Test
    fun networkException_returnsUnexpectedWithMessage() = runTest {
        val api = FakeReplicateApi(getModelThrow = RuntimeException("network down"))
        val outcome = ReplicateImageRepository(api).generateImage(validModelId, "p", cacheDir)
        val error = (outcome as GenerationOutcome.Failure).error as GenerationError.Unexpected
        assertEquals("network down", error.detail)
    }

    @Test
    fun generateImage_cancellation_propagatesCorrectly() = runTest {
        val api = FakeReplicateApi(getModelThrow = CancellationException("Job was cancelled"))
        cacheDir.mkdirs()
        
        val exception = assertFailsWith<CancellationException> {
            ReplicateImageRepository(api).generateImage(validModelId, "p", cacheDir)
        }
        assertEquals("Job was cancelled", exception.message)
    }

    // ---------- Test fixtures ----------

    private fun okModelResponse(): Response<ReplicateModel> = Response.success(
        ReplicateModel(
            owner = "test-owner",
            name = "test-model",
            latestVersion = ReplicateModelVersion(id = "v-123")
        )
    )

    private fun okPredictionResponse(
        status: String,
        output: List<String>? = null,
        error: String? = null
    ): Response<ReplicatePrediction> = Response.success(
        ReplicatePrediction(id = "pred-1", status = status, output = output, error = error)
    )

    private fun <T> errorResponse(code: Int, json: String = ""): Response<T> {
        val body = json.toResponseBody("application/json".toMediaTypeOrNull())
        return Response.error(code, body)
    }
}

private class FakeReplicateApi(
    private val getModelResponse: Response<ReplicateModel>? = null,
    private val createPredictionResponse: Response<ReplicatePrediction>? = null,
    private val getPredictionResponse: Response<ReplicatePrediction>? = null,
    private val getModelThrow: Throwable? = null
) : ReplicateApi {

    override suspend fun getModel(owner: String, name: String): Response<ReplicateModel> {
        getModelThrow?.let { throw it }
        return getModelResponse ?: error("getModelResponse not stubbed in this test")
    }

    override suspend fun createPrediction(
        request: ReplicatePredictionRequest
    ): Response<ReplicatePrediction> =
        createPredictionResponse ?: error("createPredictionResponse not stubbed in this test")

    override suspend fun getPrediction(id: String): Response<ReplicatePrediction> =
        getPredictionResponse ?: error("getPredictionResponse not stubbed in this test")
}
