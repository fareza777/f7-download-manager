package com.f7.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class F7FormatTest {

    @Test
    fun `bytes are formatted with sensible units`() {
        assertEquals("0 B", F7Format.bytes(0))
        assertEquals("1.0 KB", F7Format.bytes(1024))
        assertEquals("1.5 MB", F7Format.bytes(1536 * 1024))
        assertEquals("2.0 GB", F7Format.bytes(2L * 1024 * 1024 * 1024))
    }

    @Test
    fun `eta uses english units`() {
        assertEquals("45s", F7Format.eta(45))
        assertEquals("2m 5s", F7Format.eta(125))
        assertEquals("1h 1m", F7Format.eta(3661))
    }

    @Test
    fun `file names are sanitized`() {
        assertEquals("a_b_c.mp4", F7Format.sanitizeFileName("a/b:c.mp4"))
        assertEquals("download", F7Format.sanitizeFileName("   "))
        assertTrue(F7Format.sanitizeFileName("x".repeat(400)).length <= 180)
    }

    @Test
    fun `name comes from content disposition first`() {
        val name = F7Format.guessFileName(
            "https://example.com/download?id=1",
            "attachment; filename=\"report final.pdf\"",
            "application/pdf"
        )
        assertEquals("report final.pdf", name)
    }

    @Test
    fun `utf8 filename star is decoded`() {
        val name = F7Format.guessFileName(
            "https://example.com/get",
            "attachment; filename*=UTF-8''caf%C3%A9%20menu.pdf",
            "application/pdf"
        )
        assertEquals("café menu.pdf", name)
    }

    @Test
    fun `name is derived from the url path`() {
        assertEquals("video.mp4", F7Format.guessFileName("https://cdn.example.com/a/video.mp4?token=1", null, "video/mp4"))
    }

    @Test
    fun `playlist urls are detected`() {
        assertTrue(F7Format.isPlaylistUrl("https://x.example/master.m3u8?t=1"))
        assertFalse(F7Format.isPlaylistUrl("https://x.example/file.mp4"))
    }

    @Test
    fun `categories use mime and extension`() {
        assertEquals(FileCategory.VIDEO, FileCategory.fromMime("video/mp4", "a.mp4"))
        assertEquals(FileCategory.AUDIO, FileCategory.fromMime(null, "song.mp3"))
        assertEquals(FileCategory.ARCHIVE, FileCategory.fromMime("application/zip", "pack.zip"))
        assertEquals(FileCategory.OTHER, FileCategory.fromMime(null, "unknown.bin"))
    }
}
