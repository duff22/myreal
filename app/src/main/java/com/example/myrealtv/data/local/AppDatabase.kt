package com.example.myrealtv.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Delete
import androidx.room.Room
import androidx.room.RoomDatabase

@Dao
interface PlaybackHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(history: PlaybackHistory)

    @Query("SELECT * FROM playback_history WHERE userId = :userId ORDER BY updatedAt DESC")
    suspend fun getPlaybackHistory(userId: String): List<PlaybackHistory>

    @Query("SELECT * FROM playback_history WHERE userId = :userId AND streamId = :streamId AND isDismissed = 0 LIMIT 1")
    suspend fun getPlaybackHistoryForStream(userId: String, streamId: String): PlaybackHistory?

    @Query("SELECT * FROM playback_history WHERE userId = :userId AND lastPosition * 10 > totalDuration AND lastPosition * 100 < totalDuration * 95 AND isDismissed = 0 ORDER BY updatedAt DESC")
    suspend fun getContinueWatching(userId: String): List<PlaybackHistory>

    @Query("UPDATE playback_history SET isDismissed = 1, updatedAt = :updatedAt WHERE userId = :userId AND streamId = :streamId")
    suspend fun dismissPlaybackHistory(userId: String, streamId: String, updatedAt: Long = System.currentTimeMillis())
}

@Dao
interface WatchedStateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(state: WatchedState)

    @Query("SELECT * FROM watched_states WHERE userId = :userId")
    suspend fun getWatchedStates(userId: String): List<WatchedState>

    @Query("SELECT * FROM watched_states WHERE userId = :userId AND itemId = :itemId LIMIT 1")
    suspend fun getWatchedState(userId: String, itemId: String): WatchedState?
}

@Dao
interface UserProfileDao {
    @Query("SELECT * FROM user_profiles WHERE householdId = :householdId ORDER BY createdAt ASC")
    suspend fun getProfilesForHousehold(householdId: String): List<UserProfile>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: UserProfile)

    @Delete
    suspend fun deleteProfile(profile: UserProfile)
}

@Dao
interface HomeCatalogCacheDao {
    @Query("SELECT * FROM home_catalog_cache WHERE householdId = :householdId LIMIT 1")
    suspend fun getCache(householdId: String): HomeCatalogCache?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCache(cache: HomeCatalogCache)

    @Query("DELETE FROM home_catalog_cache WHERE householdId = :householdId")
    suspend fun clearCache(householdId: String)
}

@Dao
interface FavoriteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(favorite: Favorite)

    @Query("DELETE FROM favorites WHERE userId = :userId AND itemId = :itemId")
    suspend fun delete(userId: String, itemId: String)

    @Query("SELECT * FROM favorites WHERE userId = :userId ORDER BY createdAt DESC")
    suspend fun getFavorites(userId: String): List<Favorite>

    @Query("SELECT * FROM favorites WHERE userId = :userId AND itemId = :itemId LIMIT 1")
    suspend fun getFavorite(userId: String, itemId: String): Favorite?
}

@Database(entities = [PlaybackHistory::class, WatchedState::class, UserProfile::class, HomeCatalogCache::class, Favorite::class], version = 6, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun playbackHistoryDao(): PlaybackHistoryDao
    abstract fun watchedStateDao(): WatchedStateDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun homeCatalogCacheDao(): HomeCatalogCacheDao
    abstract fun favoriteDao(): FavoriteDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "myrealtv_local.db"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
