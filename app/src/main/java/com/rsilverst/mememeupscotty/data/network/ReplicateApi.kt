package com.rsilverst.mememeupscotty.data.network

import com.squareup.moshi.JsonClass
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

@JsonClass(generateAdapter = true)
data class ReplicatePredictionRequest(
    val version: String,
    val input: ReplicatePredictionInput
)

@JsonClass(generateAdapter = true)
data class ReplicateModel(
    val owner: String,
    val name: String,
    val latest_version: ReplicateModelVersion?
)

@JsonClass(generateAdapter = true)
data class ReplicateModelVersion(
    val id: String
)

@JsonClass(generateAdapter = true)
data class ReplicatePredictionInput(
    val prompt: String,
    val negative_prompt: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val seed: Int? = null
)

@JsonClass(generateAdapter = true)
data class ReplicatePrediction(
    val id: String,
    val status: String,
    val output: List<String>? = null,
    val error: String? = null
)

interface ReplicateApi {
    @GET("v1/models/{owner}/{name}")
    suspend fun getModel(
        @Path("owner") owner: String,
        @Path("name") name: String
    ): Response<ReplicateModel>

    @POST("v1/predictions")
    suspend fun createPrediction(
        @Body request: ReplicatePredictionRequest
    ): Response<ReplicatePrediction>

    @GET("v1/predictions/{id}")
    suspend fun getPrediction(
        @Path("id") id: String
    ): Response<ReplicatePrediction>
}
