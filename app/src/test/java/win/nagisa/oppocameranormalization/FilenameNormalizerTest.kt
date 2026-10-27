package win.nagisa.oppocameranormalization

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import win.nagisa.oppocameranormalization.util.FilenameNormalizer

class FilenameNormalizerTest {

    @Test
    fun testPhotoNormalization() {
        val input = "IMG20261027171025.jpg"
        assertTrue(FilenameNormalizer.needNormalize(input))
        assertEquals("IMG_20261027_171025.jpg", FilenameNormalizer.normalize(input))
    }

    @Test
    fun testHeicAndDngNormalization() {
        assertEquals("IMG_20261027_171025.heic", FilenameNormalizer.normalize("IMG20261027171025.heic"))
        assertEquals("IMG_20261027_171025.dng", FilenameNormalizer.normalize("IMG20261027171025.dng"))
    }

    @Test
    fun testVideoNormalization() {
        val input = "VID20261027171025.mp4"
        assertTrue(FilenameNormalizer.needNormalize(input))
        assertEquals("VID_20261027_171025.mp4", FilenameNormalizer.normalize(input))
    }

    @Test
    fun testMotionPhotoNormalization() {
        val input = "MOD20261027171025.mp4"
        assertTrue(FilenameNormalizer.needNormalize(input))
        assertEquals("MOD_20261027_171025.mp4", FilenameNormalizer.normalize(input))
    }

    @Test
    fun testBurstAndCoverSuffixPreserved() {
        assertEquals(
            "IMG_20261027_171025_BURST01.jpg",
            FilenameNormalizer.normalize("IMG20261027171025_BURST01.jpg")
        )
        assertEquals(
            "IMG_20261027_171025_COVER.jpg",
            FilenameNormalizer.normalize("IMG20261027171025_COVER.jpg")
        )
        assertEquals(
            "IMG_20261027_171025_MP4_COVER.jpg",
            FilenameNormalizer.normalize("IMG20261027171025_MP4_COVER.jpg")
        )
    }

    @Test
    fun testFullPathPreserved() {
        val input = "/storage/emulated/0/DCIM/Camera/IMG20261027171025.jpg"
        assertTrue(FilenameNormalizer.needNormalize(input))
        assertEquals(
            "/storage/emulated/0/DCIM/Camera/IMG_20261027_171025.jpg",
            FilenameNormalizer.normalize(input)
        )
    }

    @Test
    fun testIdempotency() {
        val standard = "IMG_20261027_171025.jpg"
        assertFalse(FilenameNormalizer.needNormalize(standard))
        assertEquals(standard, FilenameNormalizer.normalize(standard))

        val standardVideo = "VID_20261027_171025.mp4"
        assertFalse(FilenameNormalizer.needNormalize(standardVideo))
        assertEquals(standardVideo, FilenameNormalizer.normalize(standardVideo))
    }

    @Test
    fun testPartialUnderscoreFixed() {
        // 部分下划线情况也应纠正为完整双下划线
        assertEquals("IMG_20261027_171025.jpg", FilenameNormalizer.normalize("IMG_20261027171025.jpg"))
        assertEquals("IMG_20261027_171025.jpg", FilenameNormalizer.normalize("IMG20261027_171025.jpg"))
    }

    @Test
    fun testNonTargetStringUntouched() {
        val other = "Screenshot_20261027-171025.png"
        assertFalse(FilenameNormalizer.needNormalize(other))
        assertEquals(other, FilenameNormalizer.normalize(other))
    }
}
