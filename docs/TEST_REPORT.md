# Test Report

**Date:** 10 November 2026
**Build tested:** F7 Download Manager 1.0.0 (versionCode 1), `com.f7.downloadmanager.debug`
**Environment:** Android Emulator (Pixel 7 profile), Android 15 / API 35, 1080×2400, 420 dpi,
WHPX acceleration; host HTTP test server over `adb reverse tcp:8080`.
**Physical phone:** not used after the user asked to stop using it. All device tests below were
performed on a dedicated emulator.

## Build results

| Command | Result |
| --- | --- |
| `:app:assembleDebug` | ✅ success |
| `:app:assembleRelease` | ✅ success, signed with upload keystore |
| `:app:bundleRelease` | ✅ success |
| `:core:model:testDebugUnitTest` | ✅ 8 tests passed |
| `:core:download:testDebugUnitTest` | ✅ 9 tests passed |
| `bundletool validate` (AAB) | ✅ valid |
| `apksigner verify` (release APK) | ✅ verified, v1/v2/v3 signature |

## Functional device tests (real bytes, hashes verified)

The test server generates deterministic bytes. Every completed file was hashed on-device
with `sha256sum` and compared with the host-generated hash.

| # | Scenario | Result | Evidence |
| --- | --- | --- | --- |
| 1 | Fresh 20 MB HTTP download, 8 connections, MediaStore | ✅ | final size 20,971,520; SHA-256 matches |
| 2 | Fresh 30 MB download, finalize `IS_PENDING` | ✅ | MediaStore row finalized (`rows=1`); hash matches |
| 3 | Pause at ~15 % | ✅ | status PAUSED, progress persisted, resume button shown |
| 4 | Force-stop at ~31 %, relaunch | ✅ | same MediaStore URI reopened (`ok=true`, 8 segments), resumed from stored offsets |
| 5 | 40 MB download resumed after force-stop | ✅ | final size 41,943,040; SHA-256 matches `2acd2713…` |
| 6 | Server closes connection after 8 MB (`/drop-big`) | ✅ | retried Range segments, final 40 MB hash matches |
| 7 | Network disabled mid-download | ✅ | status `PAUSED / waiting_network`, service stopped |
| 8 | Network re-enabled | ✅ | service restarted, row resumed, final 40 MB hash matches |
| 9 | HLS VOD master playlist | ✅ | 1,515,000-byte `.m2ts`; SHA-256 matches concatenated segments |
| 10 | AES-128 HLS | ✅ | 727,500-byte decrypted output; SHA-256 matches plaintext segments |
| 11 | Live HLS / SAMPLE-AES | ✅ rejected (unit test) | explicit error, no output file |
| 12 | Notification on completion | ✅ | "Download complete" notification observed |
| 13 | Schedule creation | ✅ | row stored with trigger time; card renders |
| 14 | Schedule receiver enqueue path | ✅ | direct receiver broadcast (emulator root) added a download row, disabled the once-schedule and attempted the service |
| 15 | Foreground service start | ✅ | service runs in foreground; notification visible |
| 16 | MediaStore finalize after resume | ✅ (after fix) | rows=1, file visible in `Download/F7` |
| 17 | Release APK smoke test (signed, non-debuggable) | ✅ | installed, launched, 12 MB demo download finished and SHA-256 matched |

### Bugs found and fixed during device testing

1. **Resumed MediaStore targets never cleared `IS_PENDING`** — `UriStorageTarget` only cleared
   pending when created in the same process. Fixed by always finalizing MediaStore rows on
   success, including reopened targets.
2. **Progress could be persisted ahead of flushed bytes** — resume could trust byte offsets that
   were still buffered. Added `flush()` (fsync) before every progress/segment persistence and for
   each HLS segment; re-tested force-stop and network-loss resume with matching hashes.
3. **HLS output had a double extension** (`….ts.m2ts`) because MediaStore appends the MIME
   extension. HLS TS output now uses `.m2ts` from the start.
4. **Paused HLS could mark a partial segment complete** — a stop mid-segment advanced the
   completed boundary. Fixed by breaking before persisting a partial segment.
5. **Vector icons were invisible** — Material Symbols use a 960×960 viewBox with a negative
   origin; the converter assumed 24×24. All 75 icons regenerated with the correct viewport and
   translation.
6. **Auto-retry and notification settings were ignored** — downloaders now receive
   `maxRetries = 0` when auto-retry is off; per-download/completion notifications respect the
   notifications setting (the mandatory foreground notification remains, as Android requires).

## Automated coverage (17 JVM tests)

`core:model`:

- byte/speed/ETA formatting in English units;
- filename sanitisation and length bounds;
- `Content-Disposition` filename and RFC 5987 `filename*` decoding;
- URL-derived filenames;
- playlist detection and MIME/extension category mapping.

`core:download`:

- segment planner covers the file exactly, no gaps or overlaps;
- small files use one connection; unknown size produces no segments;
- connection count capped at 16;
- HLS media playlist parsing, relative/absolute URLs, media sequence, VOD detection;
- live playlist detection;
- master playlist variants with bandwidth/resolution;
- AES key, IV, `EXT-X-MAP`, and `EXT-X-BYTERANGE` parsing;
- SAMPLE-AES/DRM rejection.

## Not yet verified on-device

| Area | Status |
| --- | --- |
| Storage Access Framework folder selection and resume | Not yet tested |
| AlarmManager timed delivery (UI schedule) | Receiver path tested; timed alarm not observed |
| Notification action buttons (pause/resume/cancel) | Buttons implemented; not tapped in test |
| Wi-Fi-only enforcement | Not yet tested |
| Global speed limit accuracy | Not yet tested |
| Browser download capture from a real site | Not yet tested |
| Dark mode / other themes screenshots | Not yet captured |
| Memory/battery profiling on 1 GB+ files | Not yet measured |
| Android 8/9 compatibility | Not yet tested (minSdk 26) |

## Reproducing the tests

```bash
python scripts/testserver.py 8080
adb -s <emulator> reverse tcp:8080 tcp:8080
# Share a URL to the app:
adb -s <emulator> shell "am start -n com.f7.downloadmanager.debug/com.f7.downloadmanager.MainActivity \
  -a android.intent.action.SEND -t text/plain \
  --es android.intent.extra.TEXT 'http://127.0.0.1:8080/big-file'"
```

Useful endpoints: `/big-file` (30 MB), `/resume-test` (40 MB throttled), `/drop-big`
(connection dropped after 8 MB), `/slow`/`/very-slow`, `/hls/master.m3u8`,
`/hls/aes/master.m3u8`, `/demo/<name>` for realistic filenames.

## Benchmark policy

No speed claim is made in the app or store listing. The dashboard shows live bytes/second from
the app’s own counters. For a reproducible benchmark, use a fixed file, the same device/network,
three runs, median throughput, and checksum verification. On the local emulator, a 30 MB file
finished in a few seconds, but host loopback results are not representative of real networks and
are therefore not advertised.

## 1.1.0 physical-device verification (Redmi Note 11, Android 13, 2026-11-10)

All checks were performed over ADB against the signed release package
`com.f7.downloadmanager` unless noted. No root.

| Check | Result |
| --- | --- |
| Fresh 30 MB download (`/big-file`) | **PASS** — exact SHA-256 `fcc32668…d0c80` |
| Queue screen + pause all + resume all | **PASS** — status changed Paused → Downloading and byte offset continued |
| Statistics screen | **PASS** — completed count, bytes, 7-day chart, category, recent activity |
| Auto SHA-256 after completion | **PASS** — stored hash `bfe74807…e963c5` displayed in advanced detail |
| Browser page load + bookmark | **PASS** — example.com loaded; bookmark saved and listed |
| Browser Library (bookmarks/history) | **PASS** — sheet opened, bookmark listed, delete/clear controls present |
| Advanced settings screen | **PASS** — performance, timeouts, buffer, user agent, battery/network, browser, data sections |
| DB migration v1 → v3 | **PASS** — release 1.0.0 data (User-Manual.pdf) survived upgrade to 1.1.0 |
| Cold launch / splash | **PASS** — no crash; app reached dashboard |

Still not fully verified on a physical device: SAF folder selection, exact timed alarm
delivery, speed-limit accuracy, notification action buttons, Android 8/9, and long-duration
battery/RAM behavior. These are documented as unverified rather than claimed.
