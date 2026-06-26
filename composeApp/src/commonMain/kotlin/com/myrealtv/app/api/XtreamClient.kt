package com.myrealtv.app.api

import com.myrealtv.app.data.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement

class XtreamClient(private val client: HttpClient) {

    private val jsonParser = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    suspend fun login(creds: ProviderCredentials): XtreamLoginResponse? {
        val url = "${creds.url}/player_api.php?username=${creds.username}&password=${creds.password}"
        return try {
            client.get(url).body()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun getLiveCategories(creds: ProviderCredentials): List<XtreamCategory> {
        val url = "${creds.url}/player_api.php?username=${creds.username}&password=${creds.password}&action=get_live_categories"
        return try {
            client.get(url).body()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun getVodCategories(creds: ProviderCredentials): List<XtreamCategory> {
        val url = "${creds.url}/player_api.php?username=${creds.username}&password=${creds.password}&action=get_vod_categories"
        return try {
            client.get(url).body()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun getSeriesCategories(creds: ProviderCredentials): List<XtreamCategory> {
        val url = "${creds.url}/player_api.php?username=${creds.username}&password=${creds.password}&action=get_series_categories"
        return try {
            client.get(url).body()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun getLiveStreams(creds: ProviderCredentials, categoryId: String? = null): List<XtreamLiveStream> {
        val catFilter = if (categoryId != null) "&category_id=$categoryId" else ""
        val url = "${creds.url}/player_api.php?username=${creds.username}&password=${creds.password}&action=get_live_streams$catFilter"
        return try {
            client.get(url).body()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun getVodStreams(creds: ProviderCredentials, categoryId: String? = null): List<XtreamMovie> {
        val catFilter = if (categoryId != null) "&category_id=$categoryId" else ""
        val url = "${creds.url}/player_api.php?username=${creds.username}&password=${creds.password}&action=get_vod_streams$catFilter"
        return try {
            client.get(url).body()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun getSeries(creds: ProviderCredentials, categoryId: String? = null): List<XtreamSeries> {
        val catFilter = if (categoryId != null) "&category_id=$categoryId" else ""
        val url = "${creds.url}/player_api.php?username=${creds.username}&password=${creds.password}&action=get_series$catFilter"
        return try {
            client.get(url).body()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun getSeriesDetails(creds: ProviderCredentials, seriesId: Int): XtreamSeriesDetails? {
        val url = "${creds.url}/player_api.php?username=${creds.username}&password=${creds.password}&action=get_series_info&series_id=$seriesId"
        return try {
            val raw: JsonElement = client.get(url).body()
            jsonParser.decodeFromJsonElement<XtreamSeriesDetails>(raw)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun getMovieDetails(creds: ProviderCredentials, movieId: Int): XtreamMovieDetails? {
        val url = "${creds.url}/player_api.php?username=${creds.username}&password=${creds.password}&action=get_vod_info&vod_id=$movieId"
        return try {
            val raw: JsonElement = client.get(url).body()
            jsonParser.decodeFromJsonElement<XtreamMovieDetails>(raw)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun getEpg(
        creds: ProviderCredentials,
        streamId: Int,
        startMinMs: Long = 0L,
        endMaxMs: Long = Long.MAX_VALUE
    ): List<EpgProgram> {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            val url = "${creds.url}/player_api.php?username=${creds.username}&password=${creds.password}&action=get_simple_data_table&stream_id=$streamId"
            try {
                val response = client.get(url) {
                    headers {
                        append("Accept-Encoding", "gzip")
                    }
                }
                val bytes = response.body<ByteArray>()
                // Decompress if gzip magic header is matched
                val decodedString = if (bytes.size >= 2 && bytes[0] == 0x1F.toByte() && bytes[1] == 0x8B.toByte()) {
                    com.myrealtv.app.decompressGzip(bytes)
                } else {
                    bytes.decodeToString()
                }

                val trimmed = decodedString.trim()
                if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
                    val raw = jsonParser.parseToJsonElement(trimmed)
                    val programs = mutableListOf<EpgProgram>()
                    
                    val addIfInRange = { elem: kotlinx.serialization.json.JsonElement ->
                        try {
                            val prog = jsonParser.decodeFromJsonElement<EpgProgram>(elem)
                            val startMs = prog.getStartMs()
                            val endMs = prog.getEndMs()
                            if (endMs > startMinMs && startMs < endMaxMs) {
                                programs.add(
                                    prog.copy(
                                        title = com.myrealtv.app.decodeBase64(prog.title),
                                        description = prog.description?.let { com.myrealtv.app.decodeBase64(it) }
                                    )
                                )
                            }
                        } catch (e: Exception) {
                            // skip
                        }
                    }

                    if (raw is kotlinx.serialization.json.JsonObject) {
                        if (raw.containsKey("epg_listings")) {
                            val listings = raw["epg_listings"]
                            if (listings is kotlinx.serialization.json.JsonArray) {
                                for (elem in listings) {
                                    if (programs.size >= 100) break
                                    addIfInRange(elem)
                                }
                            } else if (listings is kotlinx.serialization.json.JsonObject) {
                                for (elem in listings.values) {
                                    if (programs.size >= 100) break
                                    addIfInRange(elem)
                                }
                            }
                        } else {
                            for (elem in raw.values) {
                                if (programs.size >= 100) break
                                addIfInRange(elem)
                            }
                        }
                    } else if (raw is kotlinx.serialization.json.JsonArray) {
                        for (elem in raw) {
                            if (programs.size >= 100) break
                            addIfInRange(elem)
                        }
                    }
                    programs
                } else {
                    parseXmlEpg(decodedString).filter { prog ->
                        val startMs = prog.getStartMs()
                        val endMs = prog.getEndMs()
                        endMs > startMinMs && startMs < endMaxMs
                    }.map { prog ->
                        prog.copy(
                            title = com.myrealtv.app.decodeBase64(prog.title),
                            description = prog.description?.let { com.myrealtv.app.decodeBase64(it) }
                        )
                    }
                }
            } catch (e: Throwable) {
                e.printStackTrace()
                emptyList()
            }
        }
    }

    private fun parseXmlEpg(xmlString: String): List<EpgProgram> {
        val programs = mutableListOf<EpgProgram>()
        // Simple XML Regex extractor
        val programmeRegex = Regex("<programme[^>]*start=\"([^\"]+)\"[^>]*stop=\"([^\"]+)\"[^>]*>(.*?)</programme>", RegexOption.DOT_MATCHES_ALL)
        val titleRegex = Regex("<title[^>]*>(.*?)</title>")
        val descRegex = Regex("<desc[^>]*>(.*?)</desc>")

        var matchResult = programmeRegex.find(xmlString)
        var count = 0
        while (matchResult != null && count < 100) {
            val startRaw = matchResult.groupValues[1]
            val stopRaw = matchResult.groupValues[2]
            val innerContent = matchResult.groupValues[3]

            val title = titleRegex.find(innerContent)?.groupValues?.get(1)?.trim() ?: "No Title"
            val description = descRegex.find(innerContent)?.groupValues?.get(1)?.trim() ?: ""

            val formattedStart = formatXmlTime(startRaw)
            val formattedEnd = formatXmlTime(stopRaw)
            val startEpoch = xmlTimeToEpoch(startRaw)
            val endEpoch = xmlTimeToEpoch(stopRaw)

            programs.add(
                EpgProgram(
                    title = title,
                    start = formattedStart,
                    end = formattedEnd,
                    description = description,
                    startTimestamp = startEpoch.toString(),
                    endTimestamp = endEpoch.toString()
                )
            )
            matchResult = matchResult.next()
            count++
        }
        return programs
    }

    private fun formatXmlTime(timeStr: String): String {
        val clean = timeStr.trim().take(14)
        if (clean.length < 14) return timeStr
        val year = clean.substring(0, 4)
        val month = clean.substring(4, 6)
        val day = clean.substring(6, 8)
        val hour = clean.substring(8, 10)
        val min = clean.substring(10, 12)
        val sec = clean.substring(12, 14)
        return "$year-$month-$day $hour:$min:$sec"
    }

    private fun xmlTimeToEpoch(timeStr: String): Long {
        val clean = timeStr.trim().take(14)
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

            totalDays * 86400L + hour * 3600L + min * 60L + sec
        } catch (e: Exception) {
            0L
        }
    }

    // URL Construction helpers

    fun buildLiveStreamUrl(creds: ProviderCredentials, streamId: Int): String {
        return "${creds.url}/live/${creds.username}/${creds.password}/$streamId.ts"
    }

    fun buildMovieUrl(creds: ProviderCredentials, streamId: Int, extension: String): String {
        val ext = if (extension.startsWith(".")) extension.substring(1) else extension
        return "${creds.url}/movie/${creds.username}/${creds.password}/$streamId.$ext"
    }

    fun buildEpisodeUrl(creds: ProviderCredentials, streamId: String, extension: String): String {
        val ext = if (extension.startsWith(".")) extension.substring(1) else extension
        return "${creds.url}/series/${creds.username}/${creds.password}/$streamId.$ext"
    }

    fun buildTimeshiftUrl(creds: ProviderCredentials, streamId: Int, startEpgTime: String, durationMinutes: Int): String {
        val formattedTime = convertEpgTimeToTimeshift(startEpgTime)
        return "${creds.url}/timeshift/${creds.username}/${creds.password}/$durationMinutes/$formattedTime/$streamId.ts"
    }

    private fun convertEpgTimeToTimeshift(epgTime: String): String {
        val parts = epgTime.trim().split(" ")
        if (parts.size < 2) return epgTime
        val date = parts[0]
        val timeParts = parts[1].split(":")
        if (timeParts.size < 2) return "$date:00-00"
        val hour = timeParts[0]
        val minute = timeParts[1]
        return "$date:$hour-$minute"
    }
}
