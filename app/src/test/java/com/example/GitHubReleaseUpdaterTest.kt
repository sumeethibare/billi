package com.example

import com.example.data.preferences.AppPreferences
import com.example.domain.GitHubReleaseUpdater
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GitHubReleaseUpdaterTest {

    @Test
    fun parseReleaseJson_extractsPublicApkUrlVersionCodeAndSha256() {
        val expectedDigest = GitHubReleaseUpdater.computeSha256Hex("billi-apk".toByteArray())
        val rawJson = """
            {
              "tag_name": "v1.0.9",
              "name": "Billi v1.0.9",
              "body": "Automated Billi Android release.\n- Version: 1.0.9 (code 9)\n- SHA-256: `$expectedDigest`",
              "html_url": "https://github.com/sumeethibare/billi/releases/tag/v1.0.9",
              "assets": [
                {
                  "name": "Billi-v1.0.9.apk.sha256",
                  "browser_download_url": "https://github.com/sumeethibare/billi/releases/download/v1.0.9/Billi-v1.0.9.apk.sha256",
                  "size": 84
                },
                {
                  "name": "Billi-v1.0.9.apk",
                  "browser_download_url": "https://github.com/sumeethibare/billi/releases/download/v1.0.9/Billi-v1.0.9.apk",
                  "size": 15728640
                }
              ]
            }
        """.trimIndent()

        val parsed = GitHubReleaseUpdater.parseReleaseJson(
            rawJson = rawJson,
            currentVersionName = "1.0.8",
            currentVersionCode = 8
        )
        assertTrue(parsed.isNewerThanCurrent)
        assertTrue(parsed.canDownloadInApp)
        assertEquals(9, parsed.remoteVersionCode)
        assertEquals(expectedDigest, parsed.expectedSha256)
        assertEquals("Billi-v1.0.9.apk", parsed.apkFileName)
        assertEquals(
            "https://github.com/sumeethibare/billi/releases/download/v1.0.9/Billi-v1.0.9.apk",
            parsed.apkDownloadUrl
        )

        val cached = GitHubReleaseUpdater.parseCachedAvailableUpdate(
            prefs = AppPreferences(cachedReleaseJson = rawJson),
            currentVersionName = "1.0.8",
            currentVersionCode = 8
        )
        assertNotNull(cached)
        assertEquals("v1.0.9", cached?.tagName)

        val alreadyInstalled = GitHubReleaseUpdater.parseCachedAvailableUpdate(
            prefs = AppPreferences(cachedReleaseJson = rawJson),
            currentVersionName = "1.0.9",
            currentVersionCode = 9
        )
        assertNull(alreadyInstalled)
    }
}
