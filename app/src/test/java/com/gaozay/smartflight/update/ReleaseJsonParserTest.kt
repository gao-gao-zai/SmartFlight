package com.gaozay.smartflight.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseJsonParserTest {
    private val parser = ReleaseJsonParser()

    @Test
    fun parsesGiteeReleaseJson() {
        val result = parser.parse(
            source = UpdateSource.Gitee,
            json = """
                {
                  "tag_name": "v0.0.17",
                  "name": "SmartFlight 0.0.17",
                  "body": "更新说明",
                  "html_url": "https://gitee.com/gaogaozai/SmartFlight/releases/tag/v0.0.17",
                  "assets": [
                    { "name": "app.apk", "browser_download_url": "https://example.com/app.apk" }
                  ]
                }
            """.trimIndent(),
        )

        val release = result.getOrThrow()
        assertEquals(UpdateSource.Gitee, release.source)
        assertEquals("v0.0.17", release.tagName)
        assertEquals("SmartFlight 0.0.17", release.name)
        assertEquals("更新说明", release.body)
        assertEquals(1, release.assets.size)
    }

    @Test
    fun parsesGithubReleaseJson() {
        val result = parser.parse(
            source = UpdateSource.GitHub,
            json = """
                {
                  "tag_name": "0.0.17",
                  "name": "",
                  "body": "github notes",
                  "html_url": "https://github.com/gao-gao-zai/SmartFlight/releases/tag/v0.0.17",
                  "assets": []
                }
            """.trimIndent(),
        )

        val release = result.getOrThrow()
        assertEquals(UpdateSource.GitHub, release.source)
        assertEquals("0.0.17", release.tagName)
        assertEquals("0.0.17", release.name)
        assertEquals("github notes", release.body)
    }

    @Test
    fun missingTagNameReturnsFailure() {
        val result = parser.parse(UpdateSource.Gitee, """{"name":"broken"}""")

        assertTrue(result.isFailure)
    }
}
