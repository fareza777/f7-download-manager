# Security notes

## WebView

The integrated browser is configured as follows:

| Setting | Value | Reason |
| --- | --- | --- |
| JavaScript | enabled | Most sites need it. |
| DOM storage | enabled | Login state and site preferences. |
| File access | disabled | Prevents reading local files from `file://` and WebView APIs. |
| Content access | disabled | Prevents `content://` access from page context. |
| `allowFileAccessFromFileURLs` / `allowUniversalAccessFromFileURLs` | false | Blocks cross-origin file reads. |
| Mixed content | `MIXED_CONTENT_NEVER_ALLOW` | No HTTPS page loading HTTP subresources. |
| JavaScript bridge (`addJavascriptInterface`) | none | No native object exposed to page JavaScript. |
| SSL errors | cancelled | No “proceed anyway” path. |
| Popups / new windows | blocked | `onCreateWindow` returns false and multiple windows are off. |
| Third-party cookies | disabled | Reduces cross-site tracking. |
| Safe Browsing | enabled (API 26+) | Google Safe Browsing checks. |
| Non-HTTP schemes | rejected | `intent:`, `file:`, `content:`, `javascript:` are not handed to the system. |

Download capture forwards the page’s cookies, referer, and user agent only for a
download the user triggered from that page. It does not export cookies to disk or
to the developer.

The browser does not implement incognito mode, ad blocking, or a custom DNS
resolver. It is a general-purpose WebView; users should treat visited sites as
untrusted.

## Network

- `usesCleartextTraffic=true` because download managers must accept HTTP links.
  The add-download sheet shows a warning and requires confirmation for HTTP.
- TLS certificate validation is handled by Android/OkHttp defaults. SSL errors in
  the browser are rejected.
- Optional `If-Range` with ETag is used on resume; ETag/size changes trigger a full
  restart rather than mixing versions of a file.
- `Content-Range` start offsets returned by servers are validated against the
  requested offset to avoid corrupt segment placement.
- Redirects are followed by OkHttp; the final URL is stored for resume.

## Storage

- Default public location is MediaStore `Downloads` under `Download/F7`, using
  `IS_PENDING` so incomplete files are not visible to other apps.
- Optional SAF folders are opened with persisted read/write permission only for the
  tree the user selected.
- App-specific storage requires no permission.
- Sharing/opening uses FileProvider with temporary read grants; no `file://` URI is
  exposed to other apps.
- No `MANAGE_EXTERNAL_STORAGE`, `READ_MEDIA_*`, or `WRITE_EXTERNAL_STORAGE`.

## Components and permissions

- Only `MainActivity` is exported (launcher and text share target).
- `DownloadService` is `exported=false`, type `dataSync`.
- `ScheduleReceiver` and `BootReceiver` are `exported=false`.
- No dynamically registered exported receivers.
- Permissions: INTERNET, ACCESS_NETWORK_STATE, FOREGROUND_SERVICE,
  FOREGROUND_SERVICE_DATA_SYNC, POST_NOTIFICATIONS, WAKE_LOCK,
  RECEIVE_BOOT_COMPLETED. No dangerous runtime permissions besides notifications.

## Privacy

No analytics, ads, crash reporting, account, or developer-operated server. See
`docs/privacy-policy.md`.

## Reporting

Security issues should be reported privately to the developer contact on the Play
listing. Please include the app version, Android version, and reproduction steps.
Do not include personal download URLs in public reports.
