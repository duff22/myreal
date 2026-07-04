package com.example.myrealtv

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object Login : NavKey
@Serializable data object Profiles : NavKey
@Serializable data object MainTabs : NavKey

@Serializable data class Player(
  val streamId: String,
  val streamUrl: String,
  val title: String,
  val isSeries: Boolean,
  val seriesId: String? = null,
  val episodeNum: Int? = null
) : NavKey

@Serializable data class ContentDetails(
  val itemId: String,
  val type: String, // "movie" or "series"
  val nextEpisodeId: String? = null
) : NavKey
