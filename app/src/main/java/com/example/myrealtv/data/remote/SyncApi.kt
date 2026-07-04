package com.example.myrealtv.data.remote

import com.example.myrealtv.data.local.PlaybackHistory
import com.example.myrealtv.data.local.WatchedState
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface SyncApi {
    @POST("api/sync/playback_history")
    suspend fun syncPlaybackHistory(@Body history: PlaybackHistory): SyncResponse

    @GET("api/sync/playback_history")
    suspend fun getPlaybackHistory(@Query("userId") userId: String): List<PlaybackHistory>

    @POST("api/sync/watched_states")
    suspend fun syncWatchedState(@Body state: WatchedState): SyncResponse

    @GET("api/sync/watched_states")
    suspend fun getWatchedStates(@Query("userId") userId: String): List<WatchedState>
}

data class SyncResponse(
    val success: Boolean,
    val message: String?
)
