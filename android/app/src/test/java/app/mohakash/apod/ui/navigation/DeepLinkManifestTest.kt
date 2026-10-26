package app.mohakash.apod.ui.navigation

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the App Links contract that lets public archive URLs open in the app.
 *
 * The manifest uses the `websiteHost` placeholder supplied by the Gradle build
 * from `config/site.json`, so this test checks the source manifest while the
 * merged manifest is covered by the build.
 */
class DeepLinkManifestTest {

    private fun manifest(): String = findManifest().readText()

    private fun findManifest(): File {
        var directory: File? = File(System.getProperty("user.dir") ?: ".")
        repeat(5) {
            val candidate = File(directory, "src/main/AndroidManifest.xml")
            if (candidate.isFile) return candidate
            directory = directory?.parentFile
        }
        error("Could not find src/main/AndroidManifest.xml from ${System.getProperty("user.dir")}")
    }

    @Test
    fun `https website links are verified`() {
        val manifest = manifest()
        assertTrue(manifest.contains("android:autoVerify=\"true\""))
        assertTrue(manifest.contains("android:scheme=\"https\""))
        assertTrue(manifest.contains("android:host=\"\${websiteHost}\""))
    }

    @Test
    fun `root and archive paths have intent filters`() {
        val manifest = manifest()
        assertTrue(manifest.contains("android:path=\"/archive\""))
        assertTrue(manifest.contains("android:pathPrefix=\"/archive/\""))
        assertTrue(manifest.contains("android:path=\"/\""))
    }
}
