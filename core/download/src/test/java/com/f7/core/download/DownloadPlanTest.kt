package com.f7.core.download

import com.f7.core.download.hls.HlsPlaylistException
import com.f7.core.download.hls.HlsPlaylistParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadPlanTest {

    @Test
    fun `segments cover the whole file without gaps`() {
        val total = 10L * 1024 * 1024
        val segments = planSegments(downloadId = 7, totalBytes = total, maxConnections = 4)
        assertEquals(4, segments.size)
        assertEquals(0L, segments.first().start)
        assertEquals(total - 1, segments.last().end)
        assertEquals(total, segments.sumOf { it.end - it.start + 1 })
        segments.zipWithNext().forEach { (a, b) ->
            assertEquals(a.end + 1, b.start)
        }
        assertTrue(segments.all { it.downloadId == 7L && it.downloaded == 0L })
    }

    @Test
    fun `small files use a single connection`() {
        val segments = planSegments(1, 500L * 1024, 8)
        assertEquals(1, segments.size)
        assertEquals(0L, segments.first().start)
        assertEquals(500L * 1024 - 1, segments.first().end)
    }

    @Test
    fun `unknown size produces no segments`() {
        assertTrue(planSegments(1, 0, 8).isEmpty())
    }

    @Test
    fun `connection count is capped at 16`() {
        val segments = planSegments(1, 100L * 1024 * 1024, 64)
        assertEquals(16, segments.size)
    }
}

class HlsPlaylistParserTest {

    @Test
    fun `media playlist resolves relative segment urls`() {
        val text = """
            #EXTM3U
            #EXT-X-VERSION:3
            #EXT-X-TARGETDURATION:4
            #EXT-X-MEDIA-SEQUENCE:10
            #EXTINF:4.0,
            seg0.ts
            #EXTINF:3.5,
            /abs/seg1.ts
            #EXT-X-ENDLIST
        """.trimIndent()
        val playlist = HlsPlaylistParser.parseMedia(text, "https://cdn.example.com/video/index.m3u8")
        assertEquals(2, playlist.segments.size)
        assertEquals("https://cdn.example.com/video/seg0.ts", playlist.segments[0].url)
        assertEquals("https://cdn.example.com/abs/seg1.ts", playlist.segments[1].url)
        assertEquals(10L, playlist.sequence)
        assertTrue(!playlist.isLive)
    }

    @Test
    fun `live playlist is detected`() {
        val text = """
            #EXTM3U
            #EXTINF:4.0,
            seg0.ts
        """.trimIndent()
        val playlist = HlsPlaylistParser.parseMedia(text, "https://cdn.example.com/live.m3u8")
        assertTrue(playlist.isLive)
    }

    @Test
    fun `master playlist returns variants with bandwidth`() {
        val text = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=640x360
            360/index.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=2400000,RESOLUTION=1280x720
            https://cdn.example.com/720.m3u8
        """.trimIndent()
        val variants = HlsPlaylistParser.parseMaster(text, "https://cdn.example.com/master.m3u8")
        assertEquals(2, variants.size)
        assertEquals(800000L, variants[0].bandwidth)
        assertEquals("640x360", variants[0].resolution)
        assertEquals("https://cdn.example.com/360/index.m3u8", variants[0].url)
        assertEquals("https://cdn.example.com/720.m3u8", variants[1].url)
    }

    @Test
    fun `aes key and byte ranges are parsed`() {
        val text = """
            #EXTM3U
            #EXT-X-KEY:METHOD=AES-128,URI="key.bin",IV=0x000102030405060708090a0b0c0d0e0f
            #EXT-X-MAP:URI="init.mp4"
            #EXT-X-BYTERANGE:1000@0
            #EXTINF:4.0,
            all.ts
            #EXT-X-ENDLIST
        """.trimIndent()
        val playlist = HlsPlaylistParser.parseMedia(text, "https://cdn.example.com/v/index.m3u8")
        assertEquals("https://cdn.example.com/v/key.bin", playlist.key?.url)
        assertEquals("000102030405060708090a0b0c0d0e0f", playlist.key?.ivHex)
        assertEquals("https://cdn.example.com/v/init.mp4", playlist.initSegment)
        assertEquals(0L, playlist.segments[0].rangeStart)
        assertEquals(1000L, playlist.segments[0].rangeLength)
    }

    @Test(expected = HlsPlaylistException::class)
    fun `sample-aes drm is rejected`() {
        val text = """
            #EXTM3U
            #EXT-X-KEY:METHOD=SAMPLE-AES,URI="key.bin"
            #EXTINF:4.0,
            seg.ts
            #EXT-X-ENDLIST
        """.trimIndent()
        HlsPlaylistParser.parseMedia(text, "https://cdn.example.com/v/index.m3u8")
    }
}

