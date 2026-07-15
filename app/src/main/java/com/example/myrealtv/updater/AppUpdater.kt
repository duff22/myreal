package com.example.myrealtv.updater

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.myrealtv.data.ServiceLocator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object AppUpdater {
    private var downloadId: Long = -1L
    private var downloadReceiver: BroadcastReceiver? = null

    data class UpdateResult(
        val isUpdateAvailable: Boolean,
        val latestVersion: String,
        val downloadUrl: String?
    )

    fun getCurrentVersionName(context: Context): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "1.0"
        } catch (e: Exception) {
            "1.0"
        }
    }

    suspend fun checkForUpdate(context: Context): UpdateResult = withContext(Dispatchers.IO) {
        val currentVersion = getCurrentVersionName(context)
        try {
            val latestRelease = ServiceLocator.githubApi.getLatestRelease()
            val latestTag = latestRelease.tag_name.trim().removePrefix("v")
            val cleanCurrent = currentVersion.trim().removePrefix("v")
            
            if (isNewerVersion(cleanCurrent, latestTag)) {
                val apkAsset = latestRelease.assets.find { it.name.endsWith(".apk") }
                UpdateResult(
                    isUpdateAvailable = true,
                    latestVersion = latestRelease.tag_name,
                    downloadUrl = apkAsset?.browser_download_url
                )
            } else {
                UpdateResult(false, latestRelease.tag_name, null)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            UpdateResult(false, currentVersion, null)
        }
    }

    private fun isNewerVersion(current: String, latest: String): Boolean {
        val currentParts = current.split(".").mapNotNull { it.toIntOrNull() }
        val latestParts = latest.split(".").mapNotNull { it.toIntOrNull() }
        val minSize = minOf(currentParts.size, latestParts.size)
        for (i in 0 until minSize) {
            if (latestParts[i] > currentParts[i]) return true
            if (latestParts[i] < currentParts[i]) return false
        }
        return latestParts.size > currentParts.size
    }

    fun startDownload(context: Context, url: String) {
        val appContext = context.applicationContext
        
        // Clean up previous update files in public downloads first
        try {
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val oldFile = File(downloadDir, "myrealtv-update.apk")
            if (oldFile.exists()) {
                oldFile.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val request = DownloadManager.Request(Uri.parse(url)).apply {
            setTitle("MyRealTV Update")
            setDescription("Downloading latest update...")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "myrealtv-update.apk")
            setMimeType("application/vnd.android.package-archive")
        }

        val downloadManager = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        downloadId = downloadManager.enqueue(request)

        Toast.makeText(appContext, "Update download started...", Toast.LENGTH_SHORT).show()

        // Unregister previous receiver if any
        downloadReceiver?.let {
            try {
                appContext.unregisterReceiver(it)
            } catch (e: Exception) {
                // ignore
            }
        }

        downloadReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                if (id == downloadId) {
                    installApk(appContext)
                    try {
                        appContext.unregisterReceiver(this)
                    } catch (e: Exception) {
                        // ignore
                    }
                    downloadReceiver = null
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(
                downloadReceiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                Context.RECEIVER_EXPORTED
            )
        } else {
            appContext.registerReceiver(
                downloadReceiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
            )
        }
    }

    private fun installApk(context: Context) {
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val apkFile = File(downloadDir, "myrealtv-update.apk")
        
        if (!apkFile.exists()) {
            Toast.makeText(context, "Update file not found.", Toast.LENGTH_LONG).show()
            return
        }

        try {
            val authority = "${context.packageName}.fileprovider"
            val apkUri = FileProvider.getUriForFile(context, authority, apkFile)

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(installIntent)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Failed to start installation: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }
}
