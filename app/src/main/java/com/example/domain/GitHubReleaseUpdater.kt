package com.example.domain

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.example.BuildConfig
import com.example.data.preferences.AppPreferencesRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

data class GitHubReleaseInfo(
    val tagName: String,
    val versionName: String,
    val releaseTitle: String,
    val releaseNotesMarkdown: String,
    val htmlUrl: String,
    val apkDownloadUrl: String?,
    val apkFileName: String,
    val apkSizeBytes: Long,
    val isNewerThanCurrent: Boolean
)

sealed interface AppUpdateDownloadState {
    data object Idle : AppUpdateDownloadState
    data class Downloading(
        val progress: Float,
        val downloadedBytes: Long,
        val totalBytes: Long
    ) : AppUpdateDownloadState
    data class ReadyToInstall(val apkFile: File) : AppUpdateDownloadState
    data class Error(val message: String) : AppUpdateDownloadState
}

/**
 * In-app updater polling GitHub Releases for `sumeethibare/billi`.
 * Throttles automatic startup checks to once every 12 hours, downloads the APK in-app
 * with real-time progress, and checks REQUEST_INSTALL_PACKAGES strictly at install time.
 */
object GitHubReleaseUpdater {

    const val RELEASES_API_URL = "https://api.github.com/repos/sumeethibare/billi/releases/latest"
    private const val CHECK_INTERVAL_MS = 12L * 60L * 60L * 1000L // 12 hours

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    suspend fun checkForUpdate(
        prefsRepo: AppPreferencesRepository,
        force: Boolean = false
    ): Result<GitHubReleaseInfo?> = withContext(Dispatchers.IO) {
        try {
            val now = System.currentTimeMillis()
            if (!force) {
                val lastCheck = prefsRepo.currentPreferences().lastUpdateCheckMillis
                if (lastCheck > 0L && (now - lastCheck) < CHECK_INTERVAL_MS) {
                    return@withContext Result.success(null)
                }
            }

            val request = Request.Builder()
                .url(RELEASES_API_URL)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "Billi-Android/${BuildConfig.VERSION_NAME}")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                prefsRepo.setLastUpdateCheckMillis(now)
                if (response.code == 404) {
                    // Repo has no published releases yet
                    return@withContext Result.success(null)
                }
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IllegalStateException("GitHub release check returned HTTP ${response.code}")
                    )
                }
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) return@withContext Result.success(null)

                val json = JSONObject(body)
                val tag = json.optString("tag_name", "").trim()
                val version = tag.removePrefix("v").removePrefix("V").trim()
                val title = json.optString("name", "").ifBlank { "Billi $tag" }
                val notes = json.optString("body", "").ifBlank { "Performance improvements and bug fixes." }
                val htmlUrl = json.optString("html_url", "https://github.com/sumeethibare/billi/releases")

                var apkUrl: String? = null
                var apkName = "billi-$version.apk"
                var apkSize = 0L
                val assets = json.optJSONArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.optJSONObject(i) ?: continue
                        val name = asset.optString("name", "")
                        if (name.endsWith(".apk", ignoreCase = true)) {
                            apkUrl = asset.optString("browser_download_url", "").takeIf { it.isNotBlank() }
                            apkName = name
                            apkSize = asset.optLong("size", 0L)
                            break
                        }
                    }
                }

                val isNewer = isVersionNewer(remote = version, current = BuildConfig.VERSION_NAME)
                val info = GitHubReleaseInfo(
                    tagName = tag.ifBlank { "v$version" },
                    versionName = version.ifBlank { BuildConfig.VERSION_NAME },
                    releaseTitle = title,
                    releaseNotesMarkdown = notes,
                    htmlUrl = htmlUrl,
                    apkDownloadUrl = apkUrl,
                    apkFileName = apkName,
                    apkSizeBytes = apkSize,
                    isNewerThanCurrent = isNewer
                )
                Result.success(if (force || isNewer) info else null)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadApk(
        context: Context,
        release: GitHubReleaseInfo,
        onProgress: (AppUpdateDownloadState.Downloading) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        val url = release.apkDownloadUrl
            ?: return@withContext Result.failure(IllegalStateException("No APK asset found in this GitHub release."))
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Billi-Android/${BuildConfig.VERSION_NAME}")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IllegalStateException("APK download failed (HTTP ${response.code})")
                    )
                }
                val body = response.body
                    ?: return@withContext Result.failure(IllegalStateException("Empty APK download response."))

                val totalBytes = body.contentLength().takeIf { it > 0L } ?: release.apkSizeBytes
                val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
                // Clean stale cached APKs
                updatesDir.listFiles()?.forEach { old ->
                    if (old.name != release.apkFileName) old.delete()
                }
                val outFile = File(updatesDir, release.apkFileName.ifBlank { "billi_update.apk" })

                body.byteStream().use { input ->
                    FileOutputStream(outFile).use { output ->
                        val buffer = ByteArray(16 * 1024)
                        var downloaded = 0L
                        var lastEmit = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            val now = System.currentTimeMillis()
                            if (now - lastEmit >= 80L || (totalBytes > 0L && downloaded >= totalBytes)) {
                                lastEmit = now
                                val progress = if (totalBytes > 0L) {
                                    (downloaded.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                                } else 0f
                                onProgress(
                                    AppUpdateDownloadState.Downloading(
                                        progress = progress,
                                        downloadedBytes = downloaded,
                                        totalBytes = totalBytes
                                    )
                                )
                            }
                        }
                        output.flush()
                    }
                }
                Result.success(outFile)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun canRequestPackageInstalls(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun createUnknownSourcesSettingsIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun createInstallApkIntent(context: Context, apkFile: File): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0L) return "APK"
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb >= 1.0) String.format("%.1f MB", mb) else "${bytes / 1024L} KB"
    }

    fun isVersionNewer(remote: String, current: String): Boolean {
        val rParts = remote.split(".", "-", "_").mapNotNull { it.toIntOrNull() }
        val cParts = current.split(".", "-", "_").mapNotNull { it.toIntOrNull() }
        if (rParts.isEmpty()) return false
        val maxLen = maxOf(rParts.size, cParts.size)
        for (i in 0 until maxLen) {
            val r = rParts.getOrElse(i) { 0 }
            val c = cParts.getOrElse(i) { 0 }
            if (r != c) return r > c
        }
        return false
    }
}
