package com.myrealtv.app

import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.GZIPInputStream

actual fun getCurrentTimeMillis(): Long = System.currentTimeMillis()

actual fun decompressGzip(bytes: ByteArray): String {
    return try {
        val bis = ByteArrayInputStream(bytes)
        val gzis = GZIPInputStream(bis)
        val reader = InputStreamReader(gzis, "UTF-8")
        val out = StringBuilder()
        val buf = CharArray(1024)
        var read: Int
        while (reader.read(buf).also { read = it } >= 0) {
            out.append(buf, 0, read)
        }
        out.toString()
    } catch (e: Exception) {
        e.printStackTrace()
        bytes.decodeToString()
    }
}

actual fun decodeBase64(base64Str: String): String {
    return try {
        val cleaned = base64Str.trim().replace("\n", "").replace("\r", "")
        val decodedBytes = android.util.Base64.decode(cleaned, android.util.Base64.DEFAULT)
        String(decodedBytes, Charsets.UTF_8)
    } catch (e: Exception) {
        base64Str
    }
}

actual fun formatTimeLabel(epochMs: Long): String {
    return try {
        val date = Date(epochMs)
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        sdf.format(date)
    } catch (e: Exception) {
        "00:00"
    }
}

actual fun saveLocalString(key: String, value: String) {
    try {
        val sharedPref = MyRealTvApplication.instance.getSharedPreferences("myrealtv_prefs", android.content.Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putString(key, value)
            apply()
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

actual fun getLocalString(key: String, defaultValue: String): String {
    return try {
        val sharedPref = MyRealTvApplication.instance.getSharedPreferences("myrealtv_prefs", android.content.Context.MODE_PRIVATE)
        sharedPref.getString(key, defaultValue) ?: defaultValue
    } catch (e: Exception) {
        defaultValue
    }
}

actual fun saveCacheFile(filename: String, content: String) {
    try {
        val file = java.io.File(MyRealTvApplication.instance.cacheDir, filename)
        file.writeText(content)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

actual fun readCacheFile(filename: String): String {
    return try {
        val file = java.io.File(MyRealTvApplication.instance.cacheDir, filename)
        if (file.exists()) file.readText() else ""
    } catch (e: Exception) {
        e.printStackTrace()
        ""
    }
}
