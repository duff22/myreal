package com.example.myrealtv.data.model

data class ResolvedItem(
    val id: String,
    val title: String,
    val poster: String?,
    val url: String,
    val type: String, // "movie" or "series"
    val seriesId: String? = null,
    val episodeNum: Int? = null,
    val progress: Float? = null,
    val lastPosition: Int? = null,
    val totalDuration: Int? = null
)
