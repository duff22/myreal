package com.myrealtv.app

expect fun getCurrentTimeMillis(): Long

expect fun decompressGzip(bytes: ByteArray): String

expect fun decodeBase64(base64Str: String): String

expect fun formatTimeLabel(epochMs: Long): String

expect fun saveLocalString(key: String, value: String)

expect fun getLocalString(key: String, defaultValue: String = ""): String

expect fun saveCacheFile(filename: String, content: String)

expect fun readCacheFile(filename: String): String
