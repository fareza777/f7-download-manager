#!/usr/bin/env python3
"""Local test server for F7 Download Manager.

Endpoints:
  /file/<name>?size=<bytes>       deterministic binary file, supports HEAD + Range
  /drop/<name>?size=<bytes>&after=<bytes>  drops the connection after N bytes (resume test)
  /slow/<name>?size=<bytes>&rate=<bytes_per_sec>  throttled download
  /norange/<name>?size=<bytes>    always answers 200 and ignores Range
  /hls/master.m3u8                master playlist
  /hls/media.m3u8                 media playlist (VOD, 6 segments)
  /hls/seg<N>.ts                  small media segments
  /hls/aes/master.m3u8            AES-128 encrypted variant
  /hls/aes/key.bin                16-byte key
  /hls/aes/media.m3u8
  /hls/aes/seg<N>.ts
  /redirect/<name>?size=<bytes>  302 to /file
Run: python testserver.py [port]
"""
import hashlib
import mimetypes
import os
import sys
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse, parse_qs

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "pylibs"))
try:
    from Crypto.Cipher import AES
except Exception:  # AES HLS endpoints will fail closed if the lib is absent
    AES = None
import struct

LOCK = threading.Lock()
CACHE = {}


def pattern(size: int) -> bytes:
    with LOCK:
        if size in CACHE:
            return CACHE[size]
    block = bytes(((i * 31 + 7) % 256) for i in range(65536))
    data = bytearray()
    while len(data) < size:
        data.extend(block[: size - len(data)])
    result = bytes(data)
    with LOCK:
        CACHE[size] = result
    return result


def aes_key():
    return b"F7DownloadTestK!"  # exactly 16 bytes


def encrypt_segment(data: bytes, iv: bytes) -> bytes:
    if AES is None:
        raise RuntimeError("pycryptodome is required for AES HLS endpoints")
    pad = 16 - len(data) % 16
    data = data + bytes([pad]) * pad
    return AES.new(aes_key(), AES.MODE_CBC, iv).encrypt(data)


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"
    server_version = "F7TestServer/1.0"

    def log_message(self, fmt, *args):
        sys.stderr.write("%s - %s\n" % (self.address_string(), fmt % args))

    def do_HEAD(self):
        self.handle_request(head=True)

    def do_GET(self):
        self.handle_request(head=False)

    def handle_request(self, head: bool):
        parsed = urlparse(self.path)
        qs = parse_qs(parsed.query)
        path = parsed.path

        if path == "/hls/master.m3u8":
            body = (
                "#EXTM3U\n"
                "#EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=640x360\n"
                "/hls/media.m3u8\n"
                "#EXT-X-STREAM-INF:BANDWIDTH=2400000,RESOLUTION=1280x720\n"
                "/hls/media.m3u8\n"
            ).encode()
            self.send_bytes(body, "application/vnd.apple.mpegurl", head)
            return

        if path == "/hls/media.m3u8":
            body = b"#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:4\n#EXT-X-MEDIA-SEQUENCE:0\n"
            for i in range(6):
                body += b"#EXTINF:4.0,\n"
                body += ("/hls/seg%d.ts\n" % i).encode()
            body += b"#EXT-X-ENDLIST\n"
            self.send_bytes(body, "application/vnd.apple.mpegurl", head)
            return

        if path.startswith("/hls/seg") and path.endswith(".ts"):
            index = int(path[len("/hls/seg"):-3])
            payload = pattern(300_000)[: 250_000 + index * 1000]
            self.send_bytes(payload, "video/mp2t", head)
            return

        if path == "/hls/aes/master.m3u8":
            body = (
                "#EXTM3U\n"
                "#EXT-X-STREAM-INF:BANDWIDTH=1200000,RESOLUTION=854x480\n"
                "/hls/aes/media.m3u8\n"
            ).encode()
            self.send_bytes(body, "application/vnd.apple.mpegurl", head)
            return

        if path == "/hls/aes/key.bin":
            self.send_bytes(aes_key(), "application/octet-stream", head)
            return

        if path == "/hls/aes/media.m3u8":
            body = (
                "#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:4\n"
                '#EXT-X-KEY:METHOD=AES-128,URI="/hls/aes/key.bin",IV=0x000102030405060708090a0b0c0d0e0f\n'
                "#EXT-X-MEDIA-SEQUENCE:0\n"
            ).encode()
            for i in range(6):
                body += b"#EXTINF:4.0,\n"
                body += ("/hls/aes/seg%d.ts\n" % i).encode()
            body += b"#EXT-X-ENDLIST\n"
            self.send_bytes(body, "application/vnd.apple.mpegurl", head)
            return

        if path.startswith("/hls/aes/seg") and path.endswith(".ts"):
            index = int(path[len("/hls/aes/seg"):-3])
            raw = pattern(300_000)[: 120_000 + index * 500]
            try:
                encrypted = encrypt_segment(raw, bytes(range(16)))
            except Exception:
                encrypted = raw
            self.send_bytes(encrypted, "video/mp2t", head)
            return

        # Screenshot-friendly sample files with realistic names and MIME types.
        if path.startswith("/demo/"):
            name = path.split("/")[-1]
            demo_sizes = {
                "F7-Design-Kit.zip": 12 * 1024 * 1024,
                "Launch-Film.mp4": 24 * 1024 * 1024,
                "Product-Photography.jpg": 6 * 1024 * 1024,
                "User-Manual.pdf": 3 * 1024 * 1024,
            }
            size = demo_sizes.get(name)
            if size is None:
                self.send_error(404)
                return
            data = pattern(size)
            etag = '"%s"' % hashlib.md5(data).hexdigest()
            ctype = mimetypes.guess_type(name)[0] or "application/octet-stream"
            self.stream_file(name, data, etag, head, False, 0, 0, content_type=ctype)
            return

        if path.startswith("/redirect/"):
            name = path.split("/")[-1]
            size = int(qs.get("size", ["1048576"])[0])
            self.send_response(302)
            self.send_header("Location", "/file/%s?size=%d" % (name, size))
            self.send_header("Content-Length", "0")
            self.end_headers()
            return

        # Query-free endpoints make ADB-driven UI tests easy.
        if path == "/big-slow":
            data = pattern(100 * 1024 * 1024)
            etag = '"%s"' % hashlib.md5(data).hexdigest()
            self.stream_file("big-slow.bin", data, etag, head, False, 2 * 1024 * 1024, 0)
            return

        if path == "/very-slow":
            data = pattern(100 * 1024 * 1024)
            etag = '"%s"' % hashlib.md5(data).hexdigest()
            self.stream_file("very-slow.bin", data, etag, head, False, 128 * 1024, 0)
            return

        if path == "/resume-test":
            data = pattern(40 * 1024 * 1024)
            etag = '"%s"' % hashlib.md5(data).hexdigest()
            self.stream_file("resume-test.bin", data, etag, head, False, 256 * 1024, 0)
            return

        if path == "/big-file":
            data = pattern(30 * 1024 * 1024)
            etag = '"%s"' % hashlib.md5(data).hexdigest()
            self.stream_file("big-file.bin", data, etag, head, False, 0, 0)
            return

        if path == "/drop-big":
            data = pattern(40 * 1024 * 1024)
            etag = '"%s"' % hashlib.md5(data).hexdigest()
            self.stream_file("drop-big.bin", data, etag, head, False, 0, 8 * 1024 * 1024)
            return

        if path.startswith("/file/") or path.startswith("/drop/") or path.startswith("/slow/") or path.startswith("/norange/"):
            name = path.split("/")[-1]
            size = int(qs.get("size", ["1048576"])[0])
            size = max(1, min(size, 512 * 1024 * 1024))
            data = pattern(size)
            etag = '"%s"' % hashlib.md5(data).hexdigest()
            ignore_range = path.startswith("/norange/")
            rate = int(qs.get("rate", ["0"])[0]) if path.startswith("/slow/") else 0
            drop_after = int(qs.get("after", ["0"])[0]) if path.startswith("/drop/") else 0
            self.stream_file(name, data, etag, head, ignore_range, rate, drop_after)
            return

        self.send_error(404)

    def send_bytes(self, body: bytes, content_type: str, head: bool, extra=None):
        self.send_response(200)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Accept-Ranges", "bytes")
        self.send_header("Cache-Control", "no-store")
        if extra:
            for k, v in extra.items():
                self.send_header(k, v)
        self.end_headers()
        if not head:
            try:
                self.wfile.write(body)
            except (BrokenPipeError, ConnectionResetError):
                pass

    def stream_file(self, name, data, etag, head, ignore_range, rate, drop_after, content_type="application/octet-stream"):
        total = len(data)
        start, end = 0, total - 1
        status = 200
        range_header = self.headers.get("Range")
        if_range = self.headers.get("If-Range")
        use_range = bool(range_header) and not ignore_range
        if use_range and (if_range is None or if_range == etag):
            try:
                spec = range_header.strip().split("=")[1]
                first, last = spec.split("-", 1)
                start = int(first) if first else 0
                end = int(last) if last else total - 1
                if start >= total:
                    self.send_response(416)
                    self.send_header("Content-Range", "bytes */%d" % total)
                    self.send_header("Content-Length", "0")
                    self.end_headers()
                    return
                end = min(end, total - 1)
                if end < start:
                    end = total - 1
                status = 206
            except Exception:
                start, end, status = 0, total - 1, 200

        length = end - start + 1
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(length))
        self.send_header("Accept-Ranges", "bytes")
        self.send_header("ETag", etag)
        self.send_header("Last-Modified", "Wed, 01 Jan 2025 00:00:00 GMT")
        self.send_header("Content-Disposition", 'attachment; filename="%s"' % name)
        self.send_header("Cache-Control", "no-store")
        if status == 206:
            self.send_header("Content-Range", "bytes %d-%d/%d" % (start, end, total))
        self.end_headers()
        if head:
            return

        chunk = 64 * 1024
        sent = 0
        limit = length if drop_after <= 0 or status != 200 else min(length, drop_after)
        try:
            while sent < limit:
                piece = min(chunk, limit - sent, length - sent)
                if rate > 0:
                    started = time.time()
                self.wfile.write(data[start + sent: start + sent + piece])
                sent += piece
                if rate > 0:
                    expected = piece / rate
                    elapsed = time.time() - started
                    if expected > elapsed:
                        time.sleep(expected - elapsed)
            if drop_after > 0 and status == 200 and sent < length:
                self.close_connection = True
                try:
                    self.connection.shutdown(2)
                except Exception:
                    pass
        except (BrokenPipeError, ConnectionResetError):
            pass


def main():
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8080
    server = ThreadingHTTPServer(("0.0.0.0", port), Handler)
    server.daemon_threads = True
    print("F7 test server on http://0.0.0.0:%d" % port, flush=True)
    server.serve_forever()


if __name__ == "__main__":
    main()

