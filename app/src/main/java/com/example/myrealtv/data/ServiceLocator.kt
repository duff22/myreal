package com.example.myrealtv.data

import android.content.Context
import android.content.SharedPreferences
import com.example.myrealtv.data.local.AppDatabase
import com.example.myrealtv.data.remote.ConfigApi
import com.example.myrealtv.data.remote.SyncApi
import com.example.myrealtv.data.remote.XtreamApi
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ServiceLocator {
    private lateinit var appContext: Context

    private val sharedPrefs: SharedPreferences by lazy {
        appContext.getSharedPreferences("myrealtv_prefs", Context.MODE_PRIVATE)
    }

    val database: AppDatabase by lazy {
        AppDatabase.getDatabase(appContext)
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .build()
                chain.proceed(request)
            }
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.HEADERS
            })
            .build()
    }

    // Default VPS subdomain configuration
    private const val VPS_BASE_URL = "https://sync.myreal.cc/"

    // In Xtream Codes, the Server URL is entered by the user
    var xtreamBaseUrl: String
        get() = sharedPrefs.getString("xtream_server_url", "https://example.com:8080/") ?: "https://example.com:8080/"
        set(value) {
            var sanitized = value.trim()
            val index = sanitized.indexOf("/player_api.php", ignoreCase = true)
            if (index != -1) {
                sanitized = sanitized.substring(0, index)
            }
            val formatted = if (sanitized.endsWith("/")) sanitized else "$sanitized/"
            sharedPrefs.edit().putString("xtream_server_url", formatted).apply()
        }

    val xtreamApi: XtreamApi
        get() {
            return Retrofit.Builder()
                .baseUrl(xtreamBaseUrl)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(XtreamApi::class.java)
        }

    val syncApi: SyncApi by lazy {
        Retrofit.Builder()
            .baseUrl(VPS_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SyncApi::class.java)
    }

    val configApi: ConfigApi by lazy {
        Retrofit.Builder()
            .baseUrl(VPS_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ConfigApi::class.java)
    }

    val mdbListApi: com.example.myrealtv.data.remote.MdbListApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://mdblist.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(com.example.myrealtv.data.remote.MdbListApi::class.java)
    }

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    // Session Management
    fun saveLogin(serverUrl: String, householdId: String, pass: String) {
        xtreamBaseUrl = serverUrl
        sharedPrefs.edit()
            .putString("household_id", householdId)
            .putString("password", pass)
            .apply()
    }

    fun getHouseholdId(): String? {
        return sharedPrefs.getString("household_id", null)
    }

    fun getPassword(): String {
        return sharedPrefs.getString("password", "") ?: ""
    }

    fun saveActiveProfile(profileName: String) {
        sharedPrefs.edit().putString("active_profile", profileName).apply()
    }

    fun getActiveProfile(): String? {
        return sharedPrefs.getString("active_profile", null)
    }

    fun getActiveUserId(): String? {
        val hid = getHouseholdId() ?: return null
        val profile = getActiveProfile() ?: return null
        return "${hid}_${profile}"
    }

    fun logout() {
        sharedPrefs.edit()
            .remove("household_id")
            .remove("active_profile")
            .apply()
    }
}
