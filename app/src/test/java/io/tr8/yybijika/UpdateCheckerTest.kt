package io.tr8.yybijika

import io.tr8.yybijika.update.UpdateChecker
import io.tr8.yybijika.BuildConfig
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {

    /**
     * This build's own artifact, and the other build's.
     *
     * Written against BuildConfig rather than a literal name on purpose: an
     * earlier version of these tests asserted that the personal APK always
     * wins, which is false in the starter build and passed only because nobody
     * had run the other flavour.
     */
    private val mine get() = BuildConfig.ASSET_PREFIX
    private val theirs get() =
        if (mine == "YingyingBijika") "yybijika-starter" else "YingyingBijika"


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
        val release = UpdateChecker.parse(releaseJson("$mine-0.2.0-release.apk"))!!
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
            releaseJson("$mine-0.2.0-debug.apk", "$mine-0.2.0-release.apk")
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
        assertNull(UpdateChecker.parse(releaseJson("$mine-0.2.0-debug.apk")))
    }

    // ----------------------------------------------------------------------
    // Picking the right deck
    // ----------------------------------------------------------------------

    /**
     * The invariant, stated in the only way that holds for both builds: each
     * one installs its own artifact and never the other's.
     *
     * Written against BuildConfig rather than a literal name on purpose. An
     * earlier version of these tests asserted that the personal APK always
     * wins, which is false in the starter build and would have passed only
     * because nobody ran the other flavour.
     */
    /**
     * The worst failure this app can have. A release carries two APKs — one
     * with the owner's own vocabulary, one with the sample deck — and
     * content.db is replaced wholesale on install. Downloading the wrong one
     * silently overwrites years of notes with forty sample words.
     */
    @Test
    fun `a build never installs the other deck over its own`() {
        val release = UpdateChecker.parse(
            releaseJson("$theirs-0.2.0-release.apk", "$mine-0.2.0-release.apk")
        )!!
        assertTrue("picked ${release.apkUrl}", release.apkUrl.contains(mine))
    }

    /**
     * And the order the assets come back in must not decide it. That order is
     * GitHub's business, not something to stake a vocabulary on.
     */
    @Test
    fun `order in the release does not decide which deck is installed`() {
        listOf(
            releaseJson("$mine-0.2.0-release.apk", "$theirs-0.2.0-release.apk"),
            releaseJson("$theirs-0.2.0-release.apk", "$mine-0.2.0-release.apk"),
        ).forEach { json ->
            assertTrue(UpdateChecker.parse(json)!!.apkUrl.contains(mine))
        }
    }

    /**
     * A release holding only the other build's artifact is no update at all.
     * Offering it would be offering to replace the deck.
     */
    @Test
    fun `a release with only the other deck is ignored`() {
        assertNull(UpdateChecker.parse(releaseJson("$theirs-0.2.0-release.apk")))
    }
}