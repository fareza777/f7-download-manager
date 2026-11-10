# Privacy Policy — F7 Download Manager

**Last updated: 10 November 2026**

F7 Download Manager (“F7”, “the app”) is a local download manager for Android. It
does not require an account, contains no advertising, and includes no analytics or
tracking SDKs.

## 1. Data processed on your device

F7 stores the following information locally in the app database and preferences:

- download URLs and original URLs;
- file names, MIME types, sizes, progress, segment offsets, and ETags;
- optional SHA-256 checksums that you explicitly compute;
- schedules you create;
- app settings (theme, connection limits, storage location, speed limit, etc.);
- temporary file handles and locations needed to resume downloads.

This data never leaves your device except as described in section 2. Uninstalling
the app removes its database and preferences.

## 2. Network connections

F7 connects directly to the servers you choose:

- For a download, F7 sends HTTP requests (including `Range` requests, cookies,
  referer, and user agent when the link came from the built-in browser) to the
  download server.
- In the browser, F7 loads the sites you navigate to, using Android’s WebView.
- HLS playlists and segments are requested from the media server you select.

F7 does not operate a relay server, proxy, cloud account, or synchronization
service. The developer does not receive your URLs, files, browsing history, or
settings.

## 3. Permissions

- **Internet and network state** — to download files and detect connectivity.
- **Foreground service (`dataSync`)** — to keep downloads running while the app is
  in the background, with a visible progress notification.
- **Notifications (Android 13+)** — to show progress and pause/resume controls.
- **Wake lock** — to keep the CPU awake only while downloads are active.
- **Receive boot completed** — to restore download schedules after a reboot.

F7 does not request location, contacts, camera, microphone, SMS, phone, or broad
storage permissions. Files are written through MediaStore or the Storage Access
Framework folder you choose.

## 4. Advertising, analytics, and sharing

F7 contains no ads, no advertising identifiers, no analytics SDK, no crash
reporting SDK, and no social SDK. The developer does not sell or share personal
data. Android’s own backup is disabled for this app (`allowBackup=false`).

## 5. Children

F7 is a general-purpose utility and does not knowingly collect personal
information from anyone, including children. The built-in browser can access
third-party websites whose content is outside the developer’s control.

## 6. Data retention and deletion

All data is stored locally. You can:

- delete individual downloads and files in the app;
- use “Clear completed downloads” in Settings;
- delete browser data from the browser menu;
- uninstall the app to remove all app data.

There is no server-side account or data to delete.

## 7. Security

F7 uses HTTPS where the server supports it, blocks invalid SSL certificates, and
disables local file access and JavaScript bridges in the browser. Cleartext HTTP
links are supported for compatibility and are flagged in the add-download sheet.

## 8. Changes

If this policy changes, the “last updated” date above will change. Material changes
will be reflected in the app’s release notes.

## 9. Contact

For privacy questions, use the project repository or the developer contact listed
on the app’s Google Play listing. The app itself collects no contact data.

---

This document is provided as the basis for the Google Play privacy policy URL. Host
the HTML version (`docs/privacy-policy.html`) on a public HTTPS URL before
submitting the app.
