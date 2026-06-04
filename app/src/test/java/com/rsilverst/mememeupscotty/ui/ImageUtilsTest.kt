package com.rsilverst.mememeupscotty.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ImageUtilsTest {

    @Test
    fun cleanCacheDirectory_deletesExpectedFilesAndKeepsOthers() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "cleanup-test-${System.currentTimeMillis()}")
        tempDir.mkdirs()

        try {
            // Direct cache files (should be deleted)
            val genFile = File(tempDir, "generated_meme_123.png").apply { writeText("dummy") }
            val gallFile = File(tempDir, "gallery_meme_456.png").apply { writeText("dummy") }
            val sharedFileDirect = File(tempDir, "shared_meme_direct.png").apply { writeText("dummy") }

            // Subdirectories & nested files
            val imagesSubdir = File(tempDir, "images").apply { mkdirs() }
            val sharedFileNested = File(imagesSubdir, "shared_meme_789.webp").apply { writeText("dummy") }
            val unrelatedFileNested = File(imagesSubdir, "other_file.txt").apply { writeText("dummy") }
            val unrelatedCacheFileDirect = File(tempDir, "other_cache_file.txt").apply { writeText("dummy") }

            // Run cleanup
            cleanCacheDirectory(tempDir)

            // Verify direct files are deleted
            assertFalse("generated_meme file should be deleted", genFile.exists())
            assertFalse("gallery_meme file should be deleted", gallFile.exists())
            assertFalse("direct shared_meme file should be deleted", sharedFileDirect.exists())

            // Verify nested files inside 'images'
            assertFalse("nested shared_meme file should be deleted", sharedFileNested.exists())
            assertTrue("unrelated nested file should be kept", unrelatedFileNested.exists())
            assertTrue("unrelated direct file should be kept", unrelatedCacheFileDirect.exists())
            
            // Verify images subdir is kept because it still has unrelatedFileNested
            assertTrue("images folder should be kept because it has unrelated files", imagesSubdir.exists())

        } finally {
            // Cleanup test sandbox recursively
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun cleanCacheDirectory_deletesImagesSubdirWhenEmpty() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "cleanup-empty-test-${System.currentTimeMillis()}")
        tempDir.mkdirs()

        try {
            // Setup images subdirectory with ONLY meme files
            val imagesSubdir = File(tempDir, "images").apply { mkdirs() }
            val sharedFileNested = File(imagesSubdir, "shared_meme_789.webp").apply { writeText("dummy") }

            // Run cleanup
            cleanCacheDirectory(tempDir)

            // Verify nested file is deleted
            assertFalse("nested shared_meme file should be deleted", sharedFileNested.exists())

            // Verify images subdir is deleted because it is now empty
            assertFalse("images folder should be deleted because it became empty after cleanup", imagesSubdir.exists())

        } finally {
            // Cleanup test sandbox recursively
            tempDir.deleteRecursively()
        }
    }
}
