package com.remixtv.data.network

import com.remixtv.data.models.ClientStatusBody
import com.remixtv.data.models.SyncResponse
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

interface ApiService {
    @GET("api/sync")
    suspend fun sync(@Query("client_id") clientId: String): SyncResponse

    @Streaming
    @GET("api/download/{video_id}")
    suspend fun download(@Path("video_id") videoId: Int): Response<ResponseBody>

    @POST("api/client/status")
    suspend fun postStatus(
        @Query("client_id") clientId: String,
        @Body body: ClientStatusBody
    )
}
