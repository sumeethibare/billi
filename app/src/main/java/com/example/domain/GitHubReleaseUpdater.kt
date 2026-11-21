package com.example.domain

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.example.BuildConfig
import com.example.data.preferences.AppPreferences
import com.example.data.preferences.AppPreferencesRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.TimeUnit

data class GitHubReleaseInfo(
    val tagName: String,
    val versionName: String,
    val remoteVersionCode: Int?,
    val releaseTitle: String,
    val releaseNotesMarkdown: String,
    val htmlUrl: String,
    val apkDownloadUrl: String?,
    val apkFileName: String,
    val apkSizeBytes: Long,
    val expectedSha256: String? = null,
    val isNewerThanCurrent: Boolean
) {
    val canDownloadInApp: Boolean
        get() = !apkDownloadUrl.isNullOrBlank()
}

data class DownloadedApk(
    val file: File,
    val checksumVerified: Boolean,
    val sha256Hex: String
)

sealed interface AppUpdateDownloadState {
    data object Idle : AppUpdateDownloadState
    data class Downloading(
        val progress: Float,
        val downloadedBytes: Long,
        val totalBytes: Long
    ) : AppUpdateDownloadState
    data class ReadyToInstall(
        val apkFile: File,
        val checksumVerified: Boolean = false
    ) : AppUpdateDownloadState
    data class Error(val message: String) : AppUpdateDownloadState
}

/**
 * In-app updater polling public GitHub Releases for `sumeethibare/billi`.
 *
 * - Sends `If-None-Match` (ETag) on every app launch so `304 Not Modified` responses consume
 *   zero GitHub API rate-limit quota while detecting newly pushed CI releases immediately.
 * - Requires zero API tokens or bundled secrets.
 * - Streams APK downloads over trusted HTTPS GitHub/object-storage hosts into an isolated cache
 *   file while computing SHA-256 on the fly and verifying against the published release digest
 *   before permitting Android Package Installer handoff.
 */
object GitHubReleaseUpdater {

    const val REPO_OWNER = "sumeethibare"
    const val REPO_NAME = "billi"
    const val RELEASES_API_URL = "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases/latest"
    private const val GITHUB_API_VERSION = "2022-11-28"

    private val apiClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    private val downloadClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    /**
     * Reconstructs the last cached release from DataStore if it is still newer than the
     * currently installed app version and has a downloadable APK. Drives the persistent
     * Dashboard avatar dot & Settings update badge instantaneously on startup.
     */
    fun parseCachedAvailableUpdate(
        prefs: AppPreferences,
        currentVersionName: String = BuildConfig.VERSION_NAME,
        currentVersionCode: Int = BuildConfig.VERSION_CODE
    ): GitHubReleaseInfo? {
        if (prefs.cachedReleaseJson.isBlank()) return null
        val parsed = runCatching {
            parseReleaseJson(
                rawJson = prefs.cachedReleaseJson,
                currentVersionName = currentVersionName,
                currentVersionCode = currentVersionCode
            )
        }.getOrNull() ?: return null
        return parsed.takeIf { it.isNewerThanCurrent && it.canDownloadInApp }
    }

    /**
     * Checks public GitHub Releases using ETag conditional caching (`If-None-Match`).
     * - When `force = false` (automatic launch/resume check): returns the release only if newer AND has an APK asset.
     * - When `force = true` (manual Settings check): returns the latest release even if already up to date,
     *   and surfaces helpful status/network errors if the check fails.
     */
    suspend fun checkForUpdate(
        prefsRepo: AppPreferencesRepository,
        force: Boolean = false
    ): Result<GitHubReleaseInfo?> = withContext(Dispatchers.IO) {
        try {
            val now = System.currentTimeMillis()
            val prefs = prefsRepo.currentPreferences()

            val requestBuilder = Request.Builder()
                .url(RELEASES_API_URL)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", GITHUB_API_VERSION)
                .header("User-Agent", "Billi-Android/${BuildConfig.VERSION_NAME}")
                .get()

            if (prefs.cachedReleaseEtag.isNotBlank() && prefs.cachedReleaseJson.isNotBlank()) {
                requestBuilder.header("If-None-Match", prefs.cachedReleaseEtag)
            }

            apiClient.newCall(requestBuilder.build()).execute().use { response ->
                when (response.code) {
                    304 -> {
                        prefsRepo.setLastUpdateCheckMillis(now)
                        val cached = parseReleaseJson(
                            rawJson = prefs.cachedReleaseJson,
                            currentVersionName = BuildConfig.VERSION_NAME,
                            currentVersionCode = BuildConfig.VERSION_CODE
                        )
                        return@withContext Result.success(
                            if (force || (cached.isNewerThanCurrent && cached.canDownloadInApp)) cached else null
                        )
                    }

                    404 -> {
                        return@withContext if (force) {
                            Result.failure(
                                IllegalStateException("No published Billi releases found yet on $REPO_OWNER/$REPO_NAME.")
                            )
                        } else {
                            Result.success(null)
                        }
                    }

                    403 -> {
                        val remaining = response.header("X-RateLimit-Remaining")
                        val message = if (remaining == "0") {
                            "GitHub API rate limit reached. Try again in a few minutes."
                        } else {
                            "GitHub Releases access denied (HTTP 403)."
                        }
                        return@withContext Result.failure(IllegalStateException(message))
                    }
                }

                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IllegalStateException("GitHub release check returned HTTP ${response.code}")
                    )
                }

                val body = response.body?.string().orEmpty()
                if (body.isBlank()) return@withContext Result.success(null)

                val info = parseReleaseJson(
                    rawJson = body,
                    currentVersionName = BuildConfig.VERSION_NAME,
                    currentVersionCode = BuildConfig.VERSION_CODE
                )

                // Cache ETag only once the APK asset is attached so mid-upload checks retry cleanly next launch
                if (info.canDownloadInApp) {
                    val etag = response.header("ETag").orEmpty().trim()
                    prefsRepo.saveReleaseCache(
                        etag = etag,
                        rawJson = body,
                        checkedAtMillis = now
                    )
                } else {
                    prefsRepo.setLastUpdateCheckMillis(now)
                }

                Result.success(
                    if (force || (info.isNewerThanCurrent && info.canDownloadInApp)) info else null
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    internal fun parseReleaseJson(
        rawJson: String,
        currentVersionName: String = BuildConfig.VERSION_NAME,
        currentVersionCode: Int = BuildConfig.VERSION_CODE
    ): GitHubReleaseInfo {
        val json = JSONObject(rawJson)
        val tag = json.optString("tag_name", "").trim()
        val version = tag.removePrefix("v").removePrefix("V").trim()
        val title = json.optString("name", "").ifBlank { "Billi $tag" }
        val notes = json.optString("body", "").ifBlank { "Performance improvements and bug fixes." }
        val htmlUrl = json.optString("html_url", "https://github.com/$REPO_OWNER/$REPO_NAME/releases")

        var apkDownloadUrl: String? = null
        var apkName = "Billi-${tag.ifBlank { "update" }}.apk"
        var apkSize = 0L

        val assets = json.optJSONArray("assets")
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                val name = asset.optString("name", "")
                if (name.endsWith(".apk", ignoreCase = true)) {
                    apkDownloadUrl = asset.optString("browser_download_url", "").takeIf { it.isNotBlank() }
                    apkName = sanitizeApkFileName(name)
                    apkSize = asset.optLong("size", 0L)
                    break
                }
            }
        }

        val parsedCode = extractVersionCode(version, notes)
        val expectedSha256 = extractExpectedSha256(notes)
        val isNewer = isReleaseNewer(
            remoteVersion = version,
            remoteVersionCode = parsedCode,
            currentVersion = currentVersionName,
            currentVersionCode = currentVersionCode
        )

        return GitHubReleaseInfo(
            tagName = tag.ifBlank { "v$version" },
            versionName = version.ifBlank { currentVersionName },
            remoteVersionCode = parsedCode,
            releaseTitle = title,
            releaseNotesMarkdown = notes,
            htmlUrl = htmlUrl,
            apkDownloadUrl = apkDownloadUrl,
            apkFileName = apkName,
            apkSizeBytes = apkSize,
            expectedSha256 = expectedSha256,
            isNewerThanCurrent = isNewer
        )
    }

    suspend fun downloadApk(
        context: Context,
        release: GitHubReleaseInfo,
        onProgress: (AppUpdateDownloadState.Downloading) -> Unit
    ): Result<DownloadedApk> = withContext(Dispatchers.IO) {
        val publicUrl = release.apkDownloadUrl
            ?: return@withContext Result.failure(
                IllegalStateException("No downloadable APK asset found in this GitHub release.")
            )
        if (!isTrustedAssetDownloadHost(publicUrl)) {
            return@withContext Result.failure(IllegalArgumentException("Untrusted APK download URL."))
        }

        val safeFileName = sanitizeApkFileName(release.apkFileName)
        val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
        val outFile = File(updatesDir, safeFileName)
        val tempFile = File(updatesDir, "$safeFileName.part")

        // Verify file path remains strictly confined inside cache/updates/
        val baseCanonical = updatesDir.canonicalPath + File.separator
        if (!outFile.canonicalPath.startsWith(baseCanonical) || !tempFile.canonicalPath.startsWith(baseCanonical)) {
            return@withContext Result.failure(IllegalArgumentException("Invalid APK destination path."))
        }

        try {
            val request = Request.Builder()
                .url(publicUrl)
                .header("User-Agent", "Billi-Android/${BuildConfig.VERSION_NAME}")
                .get()
                .build()

            downloadClient.newCall(request).execute().use { response ->
                val finalUrl = response.request.url.toString()
                if (!isTrustedAssetDownloadHost(finalUrl)) {
                    return@withContext Result.failure(
                        IllegalStateException("Blocked unexpected APK redirect host.")
                    )
                }
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IllegalStateException("APK download failed (HTTP ${response.code})")
                    )
                }
                val body = response.body
                    ?: return@withContext Result.failure(IllegalStateException("Empty APK download response."))

                val totalBytes = body.contentLength().takeIf { it > 0L } ?: release.apkSizeBytes

                // Clean stale cached APKs before writing the new one
                updatesDir.listFiles()?.forEach { old ->
                    if (old.name != safeFileName && old.name != tempFile.name) {
                        old.delete()
                    }
                }

                val sha256Digest = MessageDigest.getInstance("SHA-256")

                body.byteStream().use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(16 * 1024)
                        var downloaded = 0L
                        var lastEmit = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            sha256Digest.update(buffer, 0, read)
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

                val actualSha256 = sha256Digest.digest().toLowerHex()
                val expectedSha256 = release.expectedSha256
                val checksumVerified = if (expectedSha256 != null) {
                    if (!verifySha256(actualSha256, expectedSha256)) {
                        tempFile.delete()
                        return@withContext Result.failure(
                            IllegalStateException(
                                "APK integrity check failed (SHA-256 mismatch). Download was discarded for safety."
                            )
                        )
                    }
                    true
                } else {
                    false
                }

                if (outFile.exists()) outFile.delete()
                if (!tempFile.renameTo(outFile)) {
                    tempFile.copyTo(outFile, overwrite = true)
                    tempFile.delete()
                }
                Result.success(
                    DownloadedApk(
                        file = outFile,
                        checksumVerified = checksumVerified,
                        sha256Hex = actualSha256
                    )
                )
            }
        } catch (e: CancellationException) {
            tempFile.delete()
            throw e
        } catch (e: Exception) {
            tempFile.delete()
            Result.failure(e)
        }
    }

    internal fun extractExpectedSha256(releaseNotes: String): String? {
        val match = Regex("""(?i)sha-?256\s*[:=]\s*`?([0-9a-f]{64})`?""").find(releaseNotes)
        return match?.groupValues?.getOrNull(1)?.lowercase(Locale.US)
    }

    internal fun verifySha256(actualHex: String, expectedHex: String): Boolean {
        val normalizedActual = actualHex.trim().lowercase(Locale.US)
        val normalizedExpected = expectedHex.trim().lowercase(Locale.US)
        if (normalizedActual.length != 64 || normalizedExpected.length != 64) return false
        return MessageDigest.isEqual(
            normalizedActual.toByteArray(Charsets.US_ASCII),
            normalizedExpected.toByteArray(Charsets.US_ASCII)
        )
    }

    internal fun computeSha256Hex(bytes: ByteArray): String {
        return MessageDigest.getInstance("SHA-256").digest(bytes).toLowerHex()
    }

    private fun ByteArray.toLowerHex(): String = buildString(size * 2) {
        for (byte in this@toLowerHex) {
            val v = byte.toInt() and 0xFF
            append(HEX_CHARS[v ushr 4])
            append(HEX_CHARS[v and 0x0F])
        }
    }

    private const val HEX_CHARS = "0123456789abcdef"

    internal fun isTrustedAssetDownloadHost(url: String): Boolean {
        val parsed = url.toHttpUrlOrNull() ?: return false
        if (!parsed.isHttps) return false
        val host = parsed.host.lowercase(Locale.US)
        return host == "github.com" ||
            host.endsWith(".github.com") ||
            host.endsWith(".githubusercontent.com") ||
            host.endsWith(".amazonaws.com")
    }

    internal fun sanitizeApkFileName(rawName: String): String {
        val leaf = rawName.substringAfterLast('/').substringAfterLast('\\')
        val cleaned = leaf.replace(Regex("[^A-Za-z0-9._-]"), "_").trim('.')
        val base = cleaned.ifBlank { "billi_update.apk" }
        return if (base.endsWith(".apk", ignoreCase = true)) base else "$base.apk"
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
            )
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS)
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
        return if (mb >= 1.0) String.format(Locale.US, "%.1f MB", mb) else "${bytes / 1024L} KB"
    }

    internal fun extractVersionCode(versionName: String, releaseNotes: String): Int? {
        // First check explicit CI release body metadata ("Version code: 14" or "(code 14)")
        val bodyMatch = Regex("""(?i)(?:version\s*code\s*[:=]\s*|code\s+)(\d+)""").find(releaseNotes)
        bodyMatch?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { return it }
        // Fallback: for Billi's automated 1.0.<run_number> scheme, use the 3rd numeric segment
        val parts = versionName.split(".", "-", "_").mapNotNull { it.toIntOrNull() }
        return if (parts.size >= 3) parts[2] else null
    }

    fun isReleaseNewer(
        remoteVersion: String,
        remoteVersionCode: Int?,
        currentVersion: String,
        currentVersionCode: Int
    ): Boolean {
        if (isVersionNewer(remote = remoteVersion, current = currentVersion)) return true
        // If semver parts match (or remote uses same base), fall back to monotonic versionCode
        if (remoteVersionCode != null && remoteVersionCode > currentVersionCode) {
            return true
        }
        return false
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
