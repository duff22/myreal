package com.myrealtv.app.api

import com.myrealtv.app.data.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
data class PocketBaseAuthResponse(
    val token: String,
    val record: PocketBaseUser
)

@Serializable
data class PocketBaseUser(
    val id: String,
    val email: String = ""
)

@Serializable
data class PocketBaseListResponse<T>(
    val page: Int,
    val perPage: Int,
    val totalItems: Int,
    val totalPages: Int,
    val items: List<T>
)

class PocketBaseClient(private val client: HttpClient) {

    var baseUrl: String = ""
    var token: String = "local_token"
    var userId: String = "local_user"
    var userEmail: String = ""
    
    // In-memory Database for testing/offline mode - Enabled by default
    var isOfflineMode: Boolean = true
    private val offlineCredentials = mutableListOf<ProviderCredentials>()
    private val offlineFavorites = mutableListOf<FavoriteRecord>()
    private val offlineHistory = mutableListOf<WatchHistoryRecord>()
 
    init {
        try {
            val jsonStr = com.myrealtv.app.getLocalString("offline_providers", "")
            if (jsonStr.isNotEmpty()) {
                val list = kotlinx.serialization.json.Json.decodeFromString<List<ProviderCredentials>>(jsonStr)
                offlineCredentials.addAll(list)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            val jsonStr = com.myrealtv.app.readCacheFile("offline_favorites.json")
            if (jsonStr.isNotEmpty()) {
                val list = kotlinx.serialization.json.Json.decodeFromString<List<FavoriteRecord>>(jsonStr)
                offlineFavorites.addAll(list)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            val jsonStr = com.myrealtv.app.readCacheFile("offline_history.json")
            if (jsonStr.isNotEmpty()) {
                val list = kotlinx.serialization.json.Json.decodeFromString<List<WatchHistoryRecord>>(jsonStr)
                offlineHistory.addAll(list)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
 
    private fun saveOfflineProviders() {
        try {
            val jsonStr = kotlinx.serialization.json.Json.encodeToString(
                kotlinx.serialization.builtins.ListSerializer(ProviderCredentials.serializer()),
                offlineCredentials
            )
            com.myrealtv.app.saveLocalString("offline_providers", jsonStr)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun saveOfflineFavorites() {
        try {
            val jsonStr = kotlinx.serialization.json.Json.encodeToString(
                kotlinx.serialization.builtins.ListSerializer(FavoriteRecord.serializer()),
                offlineFavorites
            )
            com.myrealtv.app.saveCacheFile("offline_favorites.json", jsonStr)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun saveOfflineHistory() {
        try {
            val jsonStr = kotlinx.serialization.json.Json.encodeToString(
                kotlinx.serialization.builtins.ListSerializer(WatchHistoryRecord.serializer()),
                offlineHistory
            )
            com.myrealtv.app.saveCacheFile("offline_history.json", jsonStr)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun enableOfflineMode() {
        isOfflineMode = true
        userId = "local_user"
        token = "local_token"
        userEmail = ""
    }

    fun isAuthorized(): Boolean {
        return isOfflineMode || (token.isNotEmpty() && userId.isNotEmpty())
    }

    suspend fun authenticate(serverUrl: String, email: String, password: String): Boolean {
        isOfflineMode = false
        baseUrl = if (serverUrl.endsWith("/")) serverUrl.substring(0, serverUrl.length - 1) else serverUrl
        val url = "$baseUrl/api/collections/users/auth-with-password"
        
        return try {
            val response = client.post(url) {
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject {
                    put("identity", email)
                    put("password", password)
                })
            }
            if (response.status == HttpStatusCode.OK) {
                val authRes: PocketBaseAuthResponse = response.body()
                this.token = authRes.token
                this.userId = authRes.record.id
                this.userEmail = authRes.record.email
                this.isOfflineMode = false
                true
            } else {
                this.isOfflineMode = true
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            this.isOfflineMode = true
            false
        }
    }

    fun logout() {
        token = "local_token"
        userId = "local_user"
        userEmail = ""
        isOfflineMode = true
        com.myrealtv.app.saveLocalString("last_active_provider", "")
    }

    // Provider Credentials

    suspend fun getProviderCredentials(): List<ProviderCredentials> {
        if (!isAuthorized()) return emptyList()
        if (isOfflineMode) return offlineCredentials
        
        val url = "$baseUrl/api/collections/provider_credentials/records?filter=(user='$userId')&perPage=50"
        return try {
            val res: PocketBaseListResponse<ProviderCredentials> = client.get(url) {
                headers {
                    append(HttpHeaders.Authorization, "Bearer $token")
                }
            }.body()
            res.items
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun addProviderCredentials(name: String, providerUrl: String, username: String, password: String): ProviderCredentials? {
        if (!isAuthorized()) return null
        if (isOfflineMode) {
            val cred = ProviderCredentials(
                id = "offline_prov_${offlineCredentials.size + 1}",
                name = name,
                url = providerUrl,
                username = username,
                password = password
            )
            offlineCredentials.add(cred)
            saveOfflineProviders()
            return cred
        }

        val url = "$baseUrl/api/collections/provider_credentials/records"
        return try {
            val response = client.post(url) {
                contentType(ContentType.Application.Json)
                headers {
                    append(HttpHeaders.Authorization, "Bearer $token")
                }
                setBody(buildJsonObject {
                    put("user", userId)
                    put("name", name)
                    put("url", providerUrl)
                    put("username", username)
                    put("password", password)
                })
            }
            if (response.status == HttpStatusCode.OK || response.status == HttpStatusCode.Created) {
                response.body()
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // Favorites

    suspend fun getFavorites(providerId: String): List<FavoriteRecord> {
        if (!isAuthorized()) return emptyList()
        if (isOfflineMode) return offlineFavorites.filter { it.providerId == providerId }

        val url = "$baseUrl/api/collections/favorites/records?filter=(user='$userId'&&providerId='$providerId')&perPage=500"
        return try {
            val res: PocketBaseListResponse<FavoriteRecord> = client.get(url) {
                headers {
                    append(HttpHeaders.Authorization, "Bearer $token")
                }
            }.body()
            res.items
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun addFavorite(providerId: String, type: String, itemId: String): FavoriteRecord? {
        if (!isAuthorized()) return null
        if (isOfflineMode) {
            val fav = FavoriteRecord(
                id = "offline_fav_${offlineFavorites.size + 1}",
                user = userId,
                providerId = providerId,
                type = type,
                itemId = itemId
            )
            offlineFavorites.add(fav)
            saveOfflineFavorites()
            return fav
        }

        val url = "$baseUrl/api/collections/favorites/records"
        return try {
            val response = client.post(url) {
                contentType(ContentType.Application.Json)
                headers {
                    append(HttpHeaders.Authorization, "Bearer $token")
                }
                setBody(buildJsonObject {
                    put("user", userId)
                    put("providerId", providerId)
                    put("type", type)
                    put("itemId", itemId)
                })
            }
            if (response.status == HttpStatusCode.OK || response.status == HttpStatusCode.Created) {
                response.body()
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun deleteFavorite(favoriteId: String): Boolean {
        if (!isAuthorized()) return false
        if (isOfflineMode) {
            val removed = offlineFavorites.removeAll { it.id == favoriteId }
            if (removed) saveOfflineFavorites()
            return removed
        }

        val url = "$baseUrl/api/collections/favorites/records/$favoriteId"
        return try {
            val response = client.delete(url) {
                headers {
                    append(HttpHeaders.Authorization, "Bearer $token")
                }
            }
            response.status == HttpStatusCode.NoContent || response.status == HttpStatusCode.OK
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // Watch History

    suspend fun getWatchHistory(providerId: String): List<WatchHistoryRecord> {
        if (!isAuthorized()) return emptyList()
        if (isOfflineMode) return offlineHistory.filter { it.providerId == providerId }.sortedByDescending { it.updatedAt }

        val url = "$baseUrl/api/collections/watch_history/records?filter=(user='$userId'&&providerId='$providerId')&sort=-updated&perPage=500"
        return try {
            val res: PocketBaseListResponse<WatchHistoryRecord> = client.get(url) {
                headers {
                    append(HttpHeaders.Authorization, "Bearer $token")
                }
            }.body()
            res.items
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun upsertWatchHistory(
        recordId: String?,
        providerId: String,
        type: String,
        itemId: String,
        seriesId: String?,
        season: Int?,
        episode: Int?,
        progressMs: Long,
        durationMs: Long,
        completed: Boolean
    ): WatchHistoryRecord? {
        if (!isAuthorized()) return null
        if (isOfflineMode) {
            val existing = offlineHistory.find { it.id == recordId || (it.type == type && it.itemId == itemId) }
            val nowTime = com.myrealtv.app.getCurrentTimeMillis().toString()
            if (existing != null) {
                offlineHistory.remove(existing)
                val updated = existing.copy(
                    progressMs = progressMs,
                    durationMs = durationMs,
                    completed = completed,
                    updatedAt = nowTime
                )
                offlineHistory.add(updated)
                saveOfflineHistory()
                return updated
            } else {
                val newRecord = WatchHistoryRecord(
                    id = "offline_hist_${offlineHistory.size + 1}",
                    user = userId,
                    providerId = providerId,
                    type = type,
                    itemId = itemId,
                    seriesId = seriesId,
                    season = season,
                    episode = episode,
                    progressMs = progressMs,
                    durationMs = durationMs,
                    completed = completed,
                    updatedAt = nowTime
                )
                offlineHistory.add(newRecord)
                saveOfflineHistory()
                return newRecord
            }
        }

        val url = if (recordId != null) {
            "$baseUrl/api/collections/watch_history/records/$recordId"
        } else {
            "$baseUrl/api/collections/watch_history/records"
        }

        return try {
            val response = if (recordId != null) {
                client.patch(url) {
                    contentType(ContentType.Application.Json)
                    headers {
                        append(HttpHeaders.Authorization, "Bearer $token")
                    }
                    setBody(buildJsonObject {
                        put("progressMs", progressMs)
                        put("durationMs", durationMs)
                        put("completed", completed)
                    })
                }
            } else {
                client.post(url) {
                    contentType(ContentType.Application.Json)
                    headers {
                        append(HttpHeaders.Authorization, "Bearer $token")
                    }
                    setBody(buildJsonObject {
                        put("user", userId)
                        put("providerId", providerId)
                        put("type", type)
                        put("itemId", itemId)
                        if (seriesId != null) put("seriesId", seriesId)
                        if (season != null) put("season", season)
                        if (episode != null) put("episode", episode)
                        put("progressMs", progressMs)
                        put("durationMs", durationMs)
                        put("completed", completed)
                    })
                }
            }
            if (response.status == HttpStatusCode.OK || response.status == HttpStatusCode.Created) {
                response.body()
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun deleteWatchHistory(recordId: String): Boolean {
        if (!isAuthorized()) return false
        if (isOfflineMode) {
            val removed = offlineHistory.removeAll { it.id == recordId }
            if (removed) saveOfflineHistory()
            return removed
        }

        val url = "$baseUrl/api/collections/watch_history/records/$recordId"
        return try {
            val response = client.delete(url) {
                headers {
                    append(HttpHeaders.Authorization, "Bearer $token")
                }
            }
            response.status == HttpStatusCode.NoContent || response.status == HttpStatusCode.OK
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
