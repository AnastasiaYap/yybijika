package io.tr8.yybijika

import io.tr8.yybijika.update.UpdateChecker
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {

    @Test
    fun `newer versions are recognised`() {
        assertTrue(UpdateChecker.isNewer("0.2.0", "0.1.0"))
        assertTrue(UpdateChecker.isNewer("1.0.0", "0.9.9"))
        assertTrue(UpdateChecker.isNewer("0.1.1", "0.1.0"))
    }

    @Test
    fun `the same version is not newer`() {
        assertFalse(UpdateChecker.isNewer("0.1.0", "0.1.0"))
        assertFalse(UpdateChecker.isNewer("v0.1.0", "0.1.0"))
    }

    @Test
    fun `older versions are not offered`() {
        assertFalse(UpdateChecker.isNewer("0.1.0", "0.2.0"))
        assertFalse(UpdateChecker.isNewer("0.9.9", "1.0.0"))
    }

    /** The comparison a string sort gets backwards, and the reason this is numeric. */
    @Test
    fun `double digit segments compare numerically`() {
        assertTrue(UpdateChecker.isNewer("0.10.0", "0.9.0"))
        assertFalse(UpdateChecker.isNewer("0.9.0", "0.10.0"))
        assertTrue(UpdateChecker.isNewer("0.2.10", "0.2.9"))
    }

    @Test
    fun `a v prefix on either side is ignored`() {
        assertTrue(UpdateChecker.isNewer("v0.2.0", "v0.1.0"))
        assertTrue(UpdateChecker.isNewer("v0.2.0", "0.1.0"))
    }

    @Test
    fun `missing segments count as zero`() {
        assertTrue(UpdateChecker.isNewer("0.2", "0.1.9"))
        assertFalse(UpdateChecker.isNewer("0.1", "0.1.0"))
    }

    // ----------------------------------------------------------------------

    private fun releaseJson(vararg assetNames: String): JSONObject {
        val assets = assetNames.joinToString(",") { name ->
            """{"name":"$name",
                "browser_download_url":"https://example.invalid/$name",
                "size":1467558}"""
        }
        return JSONObject(
            """{"tag_name":"v0.2.0","body":"Notes here","assets":[$assets]}"""
        )
    }

    @Test
    fun `the release apk is picked out of the assets`() {
        val release = UpdateChecker.parse(releaseJson("YingyingBijika-0.2.0-release.apk"))!!
        assertEquals("0.2.0", release.versionName)
        assertEquals("Notes here", release.notes)
        assertTrue(release.apkUrl.endsWith("release.apk"))
        assertEquals(1467558L, release.sizeBytes)
    }

    /**
     * A debug APK is signed with a different key, so installing it over a
     * release build fails. Picking it would strand the user on a download that
     * can never complete.
     */
    @Test
    fun `debug apks are never chosen`() {
        val release = UpdateChecker.parse(
            releaseJson(
                "YingyingBijika-0.2.0-debug.apk",
                "YingyingBijika-0.2.0-release.apk",
            )
        )!!
        assertTrue(release.apkUrl.contains("release"))
    }

    @Test
    fun `a release with no apk is ignored`() {
        assertNull(UpdateChecker.parse(releaseJson("content.db", "notes.txt")))
        assertNull(UpdateChecker.parse(JSONObject("""{"tag_name":"v0.2.0"}""")))
    }

    @Test
    fun `a debug-only release is ignored rather than offered`() {
        assertNull(UpdateChecker.parse(releaseJson("YingyingBijika-0.2.0-debug.apk")))
    }
}
