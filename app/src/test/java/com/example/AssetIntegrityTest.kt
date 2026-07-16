package com.example

import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * Guards the binary game assets against corruption.
 *
 * External tooling (AI Studio) has repeatedly mangled PNG/JPG files by passing
 * their bytes through a text encoder, which made the app silently fall back to
 * low-fidelity code-drawn graphics. This test fails the build loudly instead:
 * it verifies the magic bytes of every image under res/drawable-nodpi and the
 * gradle wrapper jar. If it fails, restore the files from the last good commit
 * (git log -- <file> to find it) - do NOT ship the build.
 */
class AssetIntegrityTest {

    /** Resolves a path whether the test runs from the app module dir or the repo root. */
    private fun locate(relative: String): File? {
        val candidates = listOf(File(relative), File("app/$relative"), File("../$relative"))
        return candidates.firstOrNull { it.exists() }
    }

    private fun magic(file: File, count: Int): ByteArray =
        file.inputStream().use { stream ->
            val bytes = ByteArray(count)
            var read = 0
            while (read < count) {
                val n = stream.read(bytes, read, count - read)
                if (n < 0) break
                read += n
            }
            bytes.copyOf(read)
        }

    private fun looksLikePng(file: File): Boolean {
        val m = magic(file, 8)
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        return m.size == 8 && m.contentEquals(png)
    }

    private fun looksLikeJpeg(file: File): Boolean {
        val m = magic(file, 3)
        return m.size == 3 && m[0] == 0xFF.toByte() && m[1] == 0xD8.toByte() && m[2] == 0xFF.toByte()
    }

    private fun looksLikeZip(file: File): Boolean {
        val m = magic(file, 4)
        return m.size == 4 && m[0] == 'P'.code.toByte() && m[1] == 'K'.code.toByte()
    }

    @Test
    fun `all drawable image assets have valid binary headers`() {
        val dir = locate("src/main/res/drawable-nodpi")
            ?: fail("drawable-nodpi directory not found from ${File(".").absolutePath}").let { return }

        val images = dir.listFiles { f -> f.extension in setOf("png", "jpg", "jpeg") }.orEmpty()
        assertTrue("expected image assets in ${dir.path}, found none", images.isNotEmpty())

        val corrupt = images.filter { f ->
            when (f.extension) {
                "png" -> !looksLikePng(f)
                else -> !looksLikeJpeg(f)
            }
        }
        assertTrue(
            "CORRUPT image assets detected (likely mangled by an external text encoder - " +
                "restore from the last good git commit, do not ship): " +
                corrupt.joinToString { it.name },
            corrupt.isEmpty()
        )
    }

    @Test
    fun `gradle wrapper jar is a valid archive`() {
        val jar = locate("../gradle/wrapper/gradle-wrapper.jar")
            ?: locate("gradle/wrapper/gradle-wrapper.jar")
            ?: return // wrapper not present in this checkout layout; nothing to verify
        assertTrue(
            "gradle-wrapper.jar is corrupt (not a zip archive) - restore it from the last good commit",
            looksLikeZip(jar)
        )
    }
}
