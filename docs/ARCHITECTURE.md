# Architecture

## Modules

| Module | Responsibility |
| --- | --- |
| `:app` | Compose UI, navigation, ViewModels, app graph, share intake, locale wrapper, FileProvider. |
| `:core:model` | Pure Kotlin data classes (`DownloadItem`, segments, settings), filename/MIME/format helpers. No Android dependencies beyond the Kotlin stdlib. |
| `:core:data` | Room entities/DAOs, `DownloadsRepository`, DataStore settings, storage abstraction (`FileStorageTarget`, `UriStorageTarget` for MediaStore/SAF). |
| `:core:download` | HTTP probe, segmented/streaming downloaders, HLS parser/loader/downloader, `DownloadEngine`, `DownloadService`, notifications, scheduler. |
| `:core:ui` | Theme palettes, Manrope typography, design-system components, progress/segment charts. |

Dependency direction: `app → download → data → model`, `app → ui → model`.

## Download lifecycle

1. UI inserts a `QUEUED` row (or a scheduler does) and starts `DownloadService`.
2. The service immediately promotes itself to foreground with a `dataSync`
   notification and starts `DownloadEngine`.
3. The engine's monitor reads settings, enforces network rules, and starts jobs up
   to the configured concurrency limit.
4. The HTTP task probes the URL (`HEAD`, then a 1-byte ranged `GET` when needed) to
   learn size, filename, MIME, ETag, and Range support.
5. A storage target is created/reopened. Segments are planned (minimum 1 MiB each,
   at most 16) and persisted before payload bytes are written.
6. Workers claim segments, issue `Range` requests with `If-Range`, and write
   positionally with `FileChannel` while a shared token bucket enforces the speed
   limit. Connection count adapts every 5 s between 2 and the configured maximum,
   and only while pending segments remain.
7. Progress is persisted about every 1.2 s and on pause/completion. On success the
   target is flushed, MediaStore `IS_PENDING` is cleared, and the row is marked
   `COMPLETED`.

## Resume and crash safety

- The database stores segment start/end and downloaded byte counts. The engine
  reopens the same URI/path and clamps progress to the file length.
- Re-probing compares ETag and total size; a mismatch restarts from zero and
  deletes the stale partial file.
- `START_STICKY` plus a Room scan means an interrupted foreground service resumes
  active rows when Android restarts it, and the app also starts the service on
  launch if active rows exist.
- Servers without Range support are downloaded as a single stream; a resume
  restarts that stream from zero because no byte offset can be validated.

## HLS

- Master playlist → highest/lowest bandwidth variant (setting).
- Media playlist → VOD only (`EXT-X-ENDLIST`), optional `EXT-X-MAP`, optional
  `EXT-X-BYTERANGE`, one AES-128 key (no rotation).
- Segments are fetched sequentially and appended; the metadata JSON records the
  completed segment index and output byte offset so a resume can truncate to the
  last complete boundary.
- TS output is concatenated to `.ts`; fMP4 initialisation segment plus media
  segments are concatenated to `.mp4`.

## Background execution

- Foreground service type `dataSync`, wake lock held only while jobs are active.
- Android 15 `onTimeout` pauses active rows and stops the service.
- Schedules use `AlarmManager.setAndAllowWhileIdle` (no exact-alarm permission).
  If Android blocks starting the foreground service from the receiver, a
  notification lets the user start the download manually.
- Boot receiver only restores schedule alarms; it does not start downloads.

## UI state

`DownloadsRepository.downloads` combines the Room `Flow` with a live-stats
`StateFlow` (speed, active connections, ETA). The UI never owns download state and
never writes progress. Actions are sent to the service; the database remains the
single source of truth for restart recovery.

## Storage

- API 29+ default: MediaStore `Downloads` collection under `Download/F7`, with
  `IS_PENDING` while downloading.
- Fallback/default on older APIs: app-specific external files.
- Optional SAF tree URI with persisted read/write permission.
- All targets expose `writeAt(position)`, `truncate(size)`, `currentSize()`, and
  finalisation, so the downloader is storage-agnostic.

## Security boundaries

- WebView: JavaScript on, DOM storage on, file/content access off, mixed content
  never allowed, no `addJavascriptInterface`, SSL errors cancelled, popups
  blocked, third-party cookies disabled.
- Only `http`/`https` URLs are accepted; non-HTTP schemes are never handed to the
  system from the browser.
- Downloaded files are shared through FileProvider with temporary read grants.
- No exported services or receivers; only the launcher/share activity is exported.
