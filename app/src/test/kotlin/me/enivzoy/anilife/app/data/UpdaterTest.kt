package me.enivzoy.anilife.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UpdaterTest {
    @Test
    fun parsesMatchingPlatformAssetFromManifest() {
        val manifest = """
            {
              "version": "1.0.3",
              "releaseNotes": "Bug fixes",
              "releasedAt": 1794697773,
              "assets": [
                {"platform": "linux", "url": "https://example.com/anilife.AppImage"},
                {"platform": "windows", "url": "https://example.com/anilife.exe", "sha256": "abc123", "size": 42}
              ]
            }
        """.trimIndent()

        val update = parseUpdateManifest(manifest, currentVersion = "1.0.1", platform = "windows")

        assertEquals("1.0.3", update?.version)
        assertEquals("https://example.com/anilife.exe", update?.url)
        assertEquals("abc123", update?.sha256)
        assertEquals(42L, update?.sizeBytes)
    }

    @Test
    fun ignoresNewerManifestWithoutAValidDownloadUrl() {
        val manifest = """
            {"version":"1.0.3","assets":[{"platform":"windows","url":""}]}
        """.trimIndent()

        assertNull(parseUpdateManifest(manifest, currentVersion = "1.0.1", platform = "windows"))
    }

    @Test
    fun repairsLegacyGitHubReleaseAssetUrl() {
        val manifest = """
            {
              "version": "1.0.3",
              "assets": [{
                "platform": "windows",
                "url": "https://github.com/anilifecommunity/kotlindesktop/releases/download/v1.0.3/dist/anilife-1.0.3#base.exe"
              }]
            }
        """.trimIndent()

        val update = parseUpdateManifest(manifest, currentVersion = "1.0.1", platform = "windows")

        assertEquals(
            "https://github.com/anilifecommunity/kotlindesktop/releases/download/v1.0.3/anilife-1.0.3.base.exe",
            update?.url,
        )
    }

    @Test
    fun parsesMsiAssetFromLatestManifest() {
        val manifest = """
            {
              "version": "1.0.9",
              "assets": [{
                "platform": "windows",
                "url": "https://github.com/anilifecommunity/kotlindesktop/releases/download/v1.0.9/anilife-1.0.9.base.msi",
                "sha256": "abc123",
                "sizeBytes": 42
              }]
            }
        """.trimIndent()

        val update = parseUpdateManifest(manifest, currentVersion = "1.0.1", platform = "windows")

        assertEquals("https://github.com/anilifecommunity/kotlindesktop/releases/download/v1.0.9/anilife-1.0.9.base.msi", update?.url)
        assertEquals("abc123", update?.sha256)
        assertEquals(42L, update?.sizeBytes)
    }

    @Test
    fun prefersMsiAssetFromGithubRelease() {
        val release = """
            {
              "tag_name": "v1.0.6",
              "assets": [
                {"name": "anilife-1.0.6.base.exe", "browser_download_url": "https://example.com/anilife.exe"},
                {"name": "anilife-1.0.6.base.msi", "browser_download_url": "https://example.com/anilife.msi", "size": 42, "digest": "sha256:abc123"}
              ]
            }
        """.trimIndent()

        val update = parseUpdateManifest(release, currentVersion = "1.0.1", platform = "windows")

        assertEquals("https://example.com/anilife.msi", update?.url)
        assertEquals("abc123", update?.sha256)
    }

    @Test
    fun doesNotOfferTheInstalledVersionAgain() {
        val manifest = """
            {
              "version": "1.0.4",
              "assets": [{
                "platform": "windows",
                "url": "https://example.com/anilife-1.0.4.exe"
              }]
            }
        """.trimIndent()

        assertNull(parseUpdateManifest(manifest, currentVersion = "1.0.4", platform = "windows"))
    }

    @Test
    fun parsesGitHubReleaseApiResponseWithoutFollowingAssetRedirects() {
        val release = """
            {
              "tag_name": "v1.0.5",
              "body": "Release notes",
              "published_at": "2026-11-15T00:16:53Z",
              "assets": [
                {
                  "name": "version.json",
                  "browser_download_url": "https://github.com/example/version.json"
                },
                {
                  "name": "anilife-1.0.5.base.exe",
                  "url": "https://api.github.com/repos/example/releases/assets/123",
                  "browser_download_url": "https://github.com/example/anilife-1.0.5.base.exe",
                  "size": 42,
                  "digest": "sha256:abc123"
                }
              ]
            }
        """.trimIndent()

        val update = parseUpdateManifest(release, currentVersion = "1.0.4", platform = "windows")

        assertEquals("1.0.5", update?.version)
        assertEquals("Release notes", update?.changelog)
        assertEquals("https://github.com/example/anilife-1.0.5.base.exe", update?.url)
        assertEquals("abc123", update?.sha256)
        assertEquals(42L, update?.sizeBytes)
        assertEquals(1_794_701_813L, update?.publishedAt)
    }
}
