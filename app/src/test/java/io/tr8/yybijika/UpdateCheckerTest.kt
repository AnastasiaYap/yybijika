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

    // ----------------------------------------------------------------------
    // Picking the right deck
    // ----------------------------------------------------------------------

    /**
     * The worst failure this app can have.
     *
     * A release now carries two APKs — one with the owner's own vocabulary and
     * one with the sample deck for everybody else. content.db is replaced
     * wholesale on install, so downloading the wrong one silently overwrites
     * years of notes with forty sample words. "The first APK in the release" is
     * not good enough, and the order assets come back in is not something this
     * app gets to rely on.
     */
    @Test
    fun `the starter apk is never installed over the personal one`() {
        val release = UpdateChecker.parse(
            releaseJson(
                "yybijika-starter-0.2.0-release.apk",
                "YingyingBijika-0.2.0-release.apk",
            )
        )!!
        assertTrue(
            "picked ${release.apkUrl}",
            release.apkUrl.contains("YingyingBijika"),
        )
    }

    @Test
    fun `order in the release does not decide which deck is installed`() {
        val either = listOf(
            releaseJson("YingyingBijika-0.2.0-release.apk", "yybijika-starter-0.2.0-release.apk"),
            releaseJson("yybijika-starter-0.2.0-release.apk", "YingyingBijika-0.2.0-release.apk"),
        )
        either.forEach { json ->
            assertTrue(UpdateChecker.parse(json)!!.apkUrl.contains("YingyingBijika"))
        }
    }

    /**
     * And the converse: a release holding only the other build's artifact is no
     * update at all. Offering it would be offering to replace the deck.
     */
    @Test
    fun `a release with only the other deck is ignored`() {
        assertNull(UpdateChecker.parse(releaseJson("yybijika-starter-0.2.0-release.apk")))
    }
}
