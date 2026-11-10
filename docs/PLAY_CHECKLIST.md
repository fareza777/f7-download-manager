# Google Play submission checklist

Package: `com.f7.downloadmanager`
Version: `1.2.0` (versionCode 3)
Min SDK: 26 (Android 8.0)
Target/compile SDK: 36 (Android 16)

## Advertising and monetization

- [x] AdMob SDK integrated (banner, interstitial, rewarded) with test ad units by default.
- [ ] Replace `admobAppId` and the three ad unit IDs in `app/build.gradle.kts` with production values.
- [ ] Set `USE_TEST_ADS = false` for the Play build.
- [ ] Create the one-time in-app product `f7_remove_ads` priced at $4.99 in Play Console.
- [ ] Declare Advertising ID usage in Data safety (the `AD_ID` permission is declared).
- [ ] Confirm the banner never overlaps system navigation (it sits above the app bottom bar).
- [ ] Update the privacy policy URL with the advertising section.

## 1. Build artifacts

- [x] Signed AAB: `app/build/outputs/bundle/release/app-release.aab`
- [x] Signed APK for local testing: `app/build/outputs/apk/release/app-release.apk`
- [x] App icon 512×512: `docs/play-assets/icon-512.png`
- [x] Feature graphic 1024×500: `docs/play-assets/feature-graphic-1024x500.png`
- [x] Phone screenshots (6, 864×1920) in `docs/play-assets/screenshots/`
- [ ] 7-inch and 10-inch tablet screenshots (optional but recommended)
- [x] Privacy policy text: `docs/privacy-policy.md`
- [x] Privacy policy HTML for hosting: `docs/privacy-policy.html`
- [x] GPL-3.0 license: `LICENSE`
- [x] Attribution/notices: `NOTICE.md`

> Replace the development upload keystore (`keystore/f7-upload.jks`) with a
> production key stored outside the repository before the first production upload.
> Back it up; losing it blocks future updates unless Play App Signing recovery is
> used.

## 2. Store listing (English)

**App name:** F7 Download Manager

**Short description (80 chars):**
Fast downloads with reliable resume, HLS video, and a built-in browser.

**Full description:** see `docs/PLAY_LISTING.md`.

**Category:** Tools
**Tags:** download manager, downloader, browser, HLS
**Contact email:** the developer’s support email (required in Play Console)
**Website / privacy policy URL:** host `docs/privacy-policy.html` on public HTTPS

## 3. App content declarations

### Ads
- Contains ads: **No**
- Ad IDs / ad SDKs: **none**

### App access
- All functionality is available without login, account, subscription, or invite.
- No demo credentials needed.

### Content rating questionnaire
- Violence, sexuality, profanity, drugs, gambling: **No**
- User-generated content hosted by the app: **No**
- The app includes a general-purpose browser; users can visit third-party sites.
  Answer the questionnaire truthfully for a utility/browser tool; typically this
  results in “Everyone” / 3+ or 13+ depending on the questionnaire. The app itself
  does not host or recommend adult content.
- No simulated gambling, no loot boxes, no in-app purchases.

### Target audience
- General utility. Do **not** opt into “Designed for Families” unless the browser is
  removed or restricted.

### News, health, financial features, government apps
- None of these. No declaration required.

### Data safety (suggested answers)
- Does your app collect or share any of the required user data types? **No**
  (no analytics, crash reporting, ads, account, or server operated by the developer).
- Data processed only on-device: download URLs, file metadata, settings — not
  transmitted to the developer.
- Users can request deletion? Not applicable (no server data). In-app deletion is
  available for all local data and files.
- Data is encrypted in transit? **Not always** — HTTP links are allowed with a
  warning, so do not claim universal encryption. If Play requires a selection, mark
  “not encrypted in transit” for cleartext HTTP and explain in the description.
- Committed to the Play Families policy? Only if targeting children; not
  recommended.

### Foreground service declaration
- Type: `dataSync`
- Permissions: `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`
- Purpose: continue user-initiated downloads while the app is backgrounded, with a
  visible progress notification. No background location, media playback, or
  microphone use.

### Photo/video and files access
- No `READ_MEDIA_*`, `MANAGE_EXTERNAL_STORAGE`, or broad storage permissions.
- Uses MediaStore Downloads or the Storage Access Framework folder chosen by the
  user.

### Exact alarms / special permissions
- No `SCHEDULE_EXACT_ALARM` and no `USE_EXACT_ALARM`; schedules use
  `setAndAllowWhileIdle` and may be deferred by the OS.

### Accessibility / VPN / device admin / overlay
- None.

## 4. GPL-3.0 compliance

- [x] Source code complete and buildable in this repository.
- [x] App license is GPL-3.0-or-later; `LICENSE` present.
- [x] About screen states the license and source obligation.
- [ ] Publish the corresponding source at a public URL (or include the source
      archive with every distributed APK). Google Play listing must not impose
      additional restrictions that conflict with GPL-3.0.
- [x] No code from the reference desktop project is copied; attribution is in
      `NOTICE.md`.
- [x] Third-party licenses listed.

## 5. Policy risk review

| Policy area | Status |
| --- | --- |
| Copyrighted content / DRM circumvention | No content is bundled or decrypted from DRM; only non-DRM HLS. HLS parser rejects SAMPLE-AES/DRM. |
| Deceptive behaviour | No fake features or speed claims; torrent is documented as not included and has no UI. |
| Permissions minimisation | Only internet, network state, notifications, foreground service, wake lock, boot for schedules. |
| Data safety | No collection or sharing by the developer. |
| Ads | None. |
| WebView security | No JS bridge, no local file access, SSL errors blocked. |
| Cleartext traffic | Allowed for download compatibility, warned in UI; declare accurately. |
| Account deletion | No accounts. |
| Attractive to children | Generic utility; browser may access external sites. Choose 13+ if in doubt. |
| Deceptive download of other apps | The app downloads user-provided links; it does not install APKs automatically. `REQUEST_INSTALL_PACKAGES` is **not** requested. |

## 6. Testing track

1. Upload AAB to Internal testing.
2. Install from Play on Android 13+ and Android 8/9 if available.
3. Verify notification permission, foreground service, a 20 MB download, pause/resume,
   and a forced process kill.
4. Verify privacy policy URL loads over HTTPS.
5. Promote to closed testing, then production.

## 7. Release notes (v1.1.0)

```
First release.
• Multi-connection segmented downloads with reliable resume
• Background downloads with progress notifications
• Non-DRM HLS video support
• Built-in secure browser with download capture
• Batch queue, schedules, speed limit, categories
• MediaStore/SAF storage, checksums, duplicate finder
• Premium light theme, five color themes, dark mode
```


