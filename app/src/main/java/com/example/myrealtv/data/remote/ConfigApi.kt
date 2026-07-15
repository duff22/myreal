package com.example.myrealtv.data.remote

import retrofit2.http.GET
import retrofit2.http.Url

// VPS Row Configuration
data class ConfigRow(
    val id: String,
    val title: String,
    val type: String, // "movie" or "series"
    val listUrl: String
)

data class ConfigResponse(
    val rows: List<ConfigRow>
)

interface ConfigApi {
    @GET("config.json")
    suspend fun getConfig(): ConfigResponse
}

// MDBList Item Schema
data class MdbItem(
    val id: Int? = null,
    val title: String,
    val type: String? = null, // "movie" or "show"
    val mediatype: String? = null,
    val year: Int? = null,
    val release_year: Int? = null,
    val imdb_id: String? = null
) {
    val mediaType: String
        get() = type ?: mediatype ?: "movie"
        
    val itemYear: Int?
        get() = year ?: release_year
}

interface MdbListApi {
    @GET
    suspend fun getListItems(@Url url: String): List<MdbItem>
}
