package com.myrealtv.app.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive

object StringOrIntSerializer : KSerializer<String> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("StringOrInt", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: String) {
        encoder.encodeString(value)
    }
    override fun deserialize(decoder: Decoder): String {
        val jsonDecoder = decoder as? JsonDecoder ?: return decoder.decodeString()
        val element = jsonDecoder.decodeJsonElement()
        return if (element is JsonPrimitive) {
            element.content
        } else {
            element.toString()
        }
    }
}

object StringOrIntToIntSerializer : KSerializer<Int> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("StringOrIntToInt", PrimitiveKind.INT)
    override fun serialize(encoder: Encoder, value: Int) {
        encoder.encodeInt(value)
    }
    override fun deserialize(decoder: Decoder): Int {
        val jsonDecoder = decoder as? JsonDecoder ?: return decoder.decodeInt()
        val element = jsonDecoder.decodeJsonElement()
        return if (element is JsonPrimitive) {
            element.content.toIntOrNull() ?: 0
        } else {
            0
        }
    }
}

@Serializable
data class ProviderCredentials(
    val id: String = "",
    val name: String,
    val url: String,
    val username: String,
    val password: String
)

@Serializable
data class FavoriteRecord(
    val id: String = "",
    val user: String = "",
    val providerId: String = "",
    val type: String, // "live", "movie", "series"
    val itemId: String // stream_id or series_id
)

@Serializable
data class WatchHistoryRecord(
    val id: String = "",
    val user: String = "",
    val providerId: String = "",
    val type: String, // "movie", "episode"
    val itemId: String, // stream_id
    val seriesId: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val progressMs: Long,
    val durationMs: Long,
    val completed: Boolean,
    val updatedAt: String = ""
)

// Xtream Codes API Responses

@Serializable
data class XtreamUser(
    val username: String = "",
    val status: String = "",
    @SerialName("exp_date") val expDate: String? = null,
    @SerialName("active_cons") val activeCons: String? = null,
    @SerialName("max_connections") val maxConnections: String? = null
)

@Serializable
data class XtreamServer(
    val url: String = "",
    val port: String = "",
    @SerialName("https_port") val httpsPort: String? = null,
    @SerialName("server_protocol") val protocol: String? = null,
    @SerialName("timezone") val timezone: String? = null
)

@Serializable
data class XtreamLoginResponse(
    @SerialName("user_info") val userInfo: XtreamUser? = null,
    @SerialName("server_info") val serverInfo: XtreamServer? = null
)

@Serializable
data class XtreamCategory(
    @Serializable(with = StringOrIntSerializer::class)
    @SerialName("category_id") val id: String,
    @SerialName("category_name") val name: String,
    @Serializable(with = StringOrIntToIntSerializer::class)
    @SerialName("parent_id") val parentId: Int = 0
)

@Serializable
data class XtreamLiveStream(
    @Serializable(with = StringOrIntToIntSerializer::class)
    @SerialName("num") val num: Int = 0,
    @SerialName("name") val name: String,
    @Serializable(with = StringOrIntToIntSerializer::class)
    @SerialName("stream_id") val streamId: Int,
    @SerialName("stream_icon") val icon: String? = null,
    @Serializable(with = StringOrIntSerializer::class)
    @SerialName("category_id") val categoryId: String,
    @SerialName("epg_channel_id") val epgChannelId: String? = null,
    @Serializable(with = StringOrIntToIntSerializer::class)
    @SerialName("tv_archive") val tvArchive: Int = 0,
    @Serializable(with = StringOrIntToIntSerializer::class)
    @SerialName("tv_archive_duration") val tvArchiveDuration: Int = 0
)

@Serializable
data class XtreamMovie(
    @Serializable(with = StringOrIntToIntSerializer::class)
    @SerialName("num") val num: Int = 0,
    @SerialName("name") val name: String,
    @Serializable(with = StringOrIntToIntSerializer::class)
    @SerialName("stream_id") val streamId: Int,
    @SerialName("stream_icon") val icon: String? = null,
    @Serializable(with = StringOrIntSerializer::class)
    @SerialName("category_id") val categoryId: String,
    @Serializable(with = StringOrIntSerializer::class)
    @SerialName("rating") val rating: String? = null,
    @Serializable(with = StringOrIntSerializer::class)
    @SerialName("added") val added: String? = null,
    @SerialName("container_extension") val extension: String? = "mp4"
)

@Serializable
data class XtreamSeries(
    @Serializable(with = StringOrIntToIntSerializer::class)
    @SerialName("num") val num: Int = 0,
    @SerialName("name") val name: String,
    @Serializable(with = StringOrIntToIntSerializer::class)
    @SerialName("series_id") val seriesId: Int,
    @SerialName("cover") val cover: String? = null,
    @Serializable(with = StringOrIntSerializer::class)
    @SerialName("category_id") val categoryId: String,
    @Serializable(with = StringOrIntSerializer::class)
    @SerialName("rating") val rating: String? = null,
    @Serializable(with = StringOrIntSerializer::class)
    @SerialName("last_modified") val lastModified: String? = null
)

@Serializable
data class XtreamEpisodeInfo(
    @SerialName("movie_image") val image: String? = null,
    @Serializable(with = StringOrIntSerializer::class)
    @SerialName("duration") val duration: String? = null,
    @SerialName("container_extension") val extension: String? = "mp4"
)

@Serializable
data class XtreamEpisode(
    @Serializable(with = StringOrIntSerializer::class)
    @SerialName("id") val id: String,
    @SerialName("title") val title: String,
    @SerialName("container_extension") val extension: String = "mp4",
    @Serializable(with = StringOrIntSerializer::class)
    @SerialName("episode_num") val episodeNum: String = "0",
    @Serializable(with = StringOrIntToIntSerializer::class)
    @SerialName("season") val season: Int = 1,
    @SerialName("info") val info: XtreamEpisodeInfo? = null
)

@Serializable
data class XtreamMovieInfo(
    val plot: String? = null,
    val genre: String? = null,
    val director: String? = null,
    val cast: String? = null,
    @SerialName("releaseDate") val releaseDate: String? = null,
    val duration: String? = null,
    val rating: String? = null
)

@Serializable
data class XtreamMovieData(
    @Serializable(with = StringOrIntToIntSerializer::class)
    @SerialName("stream_id") val streamId: Int,
    val name: String = "",
    @SerialName("container_extension") val extension: String? = "mp4"
)

@Serializable
data class XtreamMovieDetails(
    val info: XtreamMovieInfo? = null,
    @SerialName("movie_data") val movieData: XtreamMovieData? = null
)

@Serializable
data class XtreamSeriesInfo(
    val plot: String? = null,
    val genre: String? = null,
    val director: String? = null,
    val cast: String? = null,
    @SerialName("releaseDate") val releaseDate: String? = null,
    val rating: String? = null,
    val cover: String? = null
)

@Serializable
data class XtreamSeriesDetails(
    val info: XtreamSeriesInfo? = null,
    val episodes: Map<String, List<XtreamEpisode>> = emptyMap()
)

@Serializable
data class EpgProgram(
    @SerialName("title") val title: String,
    @SerialName("start") val start: String? = null,
    @SerialName("end") val end: String? = null,
    @SerialName("description") val description: String? = null,
    @SerialName("start_timestamp") val startTimestamp: String? = null,
    @SerialName("end_timestamp") val endTimestamp: String? = null
) {
    fun getStartMs(): Long {
        val ts = startTimestamp?.toLongOrNull()
        if (ts != null && ts > 0) {
            return ts * 1000L
        }
        val startStr = start ?: return 0L
        return parseEpgTimeToEpochMs(startStr)
    }

    fun getEndMs(): Long {
        val ts = endTimestamp?.toLongOrNull()
        if (ts != null && ts > 0) {
            return ts * 1000L
        }
        val endStr = end ?: return 0L
        return parseEpgTimeToEpochMs(endStr)
    }
}

fun parseEpgTimeToEpochMs(timeStr: String): Long {
    val clean = timeStr.replace(Regex("[^0-9]"), "")
    if (clean.length < 14) return 0L
    return try {
        val year = clean.substring(0, 4).toInt()
        val month = clean.substring(4, 6).toInt()
        val day = clean.substring(6, 8).toInt()
        val hour = clean.substring(8, 10).toInt()
        val min = clean.substring(10, 12).toInt()
        val sec = clean.substring(12, 14).toInt()

        val daysInMonths = intArrayOf(0, 31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        var totalDays = (year - 1970) * 365 + (year - 1969) / 4
        for (m in 1 until month) {
            totalDays += daysInMonths[m]
        }
        if (month > 2 && year % 4 == 0) totalDays += 1
        totalDays += (day - 1)

        (totalDays * 86400L + hour * 3600L + min * 60L + sec) * 1000L
    } catch (e: Exception) {
        0L
    }
}

@Serializable
data class EpgTable(
    @SerialName("epg_listings") val listings: List<EpgProgram> = emptyList()
)
