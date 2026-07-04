package com.example.myrealtv.data.remote

import retrofit2.http.GET
import retrofit2.http.Query

// Response Models for Xtream Codes API
data class XcUserInfo(
    val username: String,
    val status: String?,
    val auth: Int?
)

data class XcLoginResponse(
    val user_info: XcUserInfo?
)

data class XcCategory(
    val category_id: String,
    val category_name: String
)

data class XcVodStream(
    val stream_id: Int,
    val name: String?,
    val stream_icon: String?,
    val category_id: String?,
    val container_extension: String?
)

data class XcSeries(
    val series_id: Int,
    val name: String?,
    val cover: String?,
    val category_id: String?
)

// VOD Detailed Info Response Models
data class XcVodInfoResponse(
    val info: XcVodInfo?,
    val movie_data: XcMovieData?
)

data class XcVodInfo(
    val name: String?,
    val description: String?,
    val genre: String?,
    val director: String?,
    val cast: String?,
    val rating: String?,
    val releasedate: String?,
    val movie_image: String?,
    val backdrop_path: List<String>?
)

data class XcMovieData(
    val stream_id: Int?,
    val container_extension: String?
)

// Series Detailed Info Response Models
data class XcSeriesInfoResponse(
    val info: XcSeriesInfo?,
    val episodes: Map<String, List<XcEpisode>>?
)

data class XcSeriesInfo(
    val name: String?,
    val cover: String?,
    val plot: String?,
    val genre: String?,
    val cast: String?,
    val director: String?,
    val rating: String?,
    val releaseDate: String?,
    val backdrop_path: List<String>?
)

data class XcEpisode(
    val id: com.google.gson.JsonElement?, // can be number or string
    val episode_num: com.google.gson.JsonElement?,
    val title: String?,
    val container_extension: String?,
    val info: XcEpisodeInfo?
) {
    val streamId: String
        get() = id?.asString ?: ""
    val episodeNum: Int
        get() = episode_num?.asInt ?: 0
}

data class XcEpisodeInfo(
    val plot: String?,
    val movie_image: String?,
    val duration: String?,
    val duration_secs: Int?
)

interface XtreamApi {
    @GET("player_api.php")
    suspend fun login(
        @Query("username") username: String,
        @Query("password") String: String
    ): XcLoginResponse

    @GET("player_api.php")
    suspend fun getVodCategories(
        @Query("username") username: String,
        @Query("password") String: String,
        @Query("action") action: String = "get_vod_categories"
    ): List<XcCategory>

    @GET("player_api.php")
    suspend fun getVodStreams(
        @Query("username") username: String,
        @Query("password") String: String,
        @Query("action") action: String = "get_vod_streams",
        @Query("category_id") categoryId: String? = null
    ): List<XcVodStream>

    @GET("player_api.php")
    suspend fun getSeriesCategories(
        @Query("username") username: String,
        @Query("password") String: String,
        @Query("action") action: String = "get_series_categories"
    ): List<XcCategory>

    @GET("player_api.php")
    suspend fun getSeries(
        @Query("username") username: String,
        @Query("password") String: String,
        @Query("action") action: String = "get_series",
        @Query("category_id") categoryId: String? = null
    ): List<XcSeries>

    @GET("player_api.php")
    suspend fun getVodInfo(
        @Query("username") username: String,
        @Query("password") pass: String,
        @Query("action") action: String = "get_vod_info",
        @Query("vod_id") vodId: Int
    ): XcVodInfoResponse

    @GET("player_api.php")
    suspend fun getSeriesInfo(
        @Query("username") username: String,
        @Query("password") pass: String,
        @Query("action") action: String = "get_series_info",
        @Query("series_id") seriesId: Int
    ): XcSeriesInfoResponse
}
