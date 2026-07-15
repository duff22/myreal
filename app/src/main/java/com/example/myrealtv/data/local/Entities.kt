package com.example.myrealtv.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playback_history", primaryKeys = ["userId", "streamId"])
data class PlaybackHistory(
    val userId: String, // Format: [Household_ID]_[Profile_Name]
    val streamId: String,
    val lastPosition: Int,
    val totalDuration: Int,
    val isDismissed: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
    val isSeries: Boolean = false,
    val seriesId: String? = null,
    val episodeNum: Int? = null
)

@Entity(tableName = "watched_states", primaryKeys = ["userId", "itemId"])
data class WatchedState(
    val userId: String, // Format: [Household_ID]_[Profile_Name]
    val itemId: String,
    val status: Boolean
)

@Entity(tableName = "user_profiles", primaryKeys = ["householdId", "profileName"])
data class UserProfile(
    val householdId: String,
    val profileName: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "home_catalog_cache")
data class HomeCatalogCache(
    @PrimaryKey val householdId: String,
    val serializedRowsJson: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "favorites", primaryKeys = ["userId", "itemId"])
data class Favorite(
    val userId: String, // Format: [Household_ID]_[Profile_Name]
    val itemId: String,
    val title: String,
    val poster: String?,
    val type: String, // "movie" or "series"
    val url: String,
    val createdAt: Long = System.currentTimeMillis()
)
