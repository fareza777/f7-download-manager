from pathlib import Path

root = Path(r"C:\Download Manager v1\F7DownloadManager")
readme = root / "README.md"
s = readme.read_text(encoding="utf-8")

s = s.replace("**Status:** **1.1.0**", "**Status:** **1.2.0**")

new_section = """
## Monetization (1.2.0)

F7 is free and supported by Google AdMob. Three formats are wired through a single
`AdController`:

| Format | Placement | Rules |
| --- | --- | --- |
| Banner | Anchored adaptive banner in the Scaffold bottom slot, above the app bottom bar | Never overlaps the Android navigation bar or the app tabs; hidden while a sheet is open; removed entirely when the ad fails |
| Interstitial | After a batch of downloads is added | 3-minute cooldown, never shown to ad-free users, delayed until the sheet has closed |
| Rewarded | "Watch an ad for 2 hours ad-free" on the Remove ads screen | Grants a real 2-hour ad-free window, opt-in only |

**Remove ads** is a one-time Google Play in-app product (`f7_remove_ads`) priced at
**$4.99**. The price shown in the UI comes from Play Billing, not a hard-coded string,
and purchases are acknowledged, restored on every launch, and applied instantly.

Ad-free state is stored in DataStore (`remove_ads_purchased`, `ad_free_until`) and is
checked before every request, so a paying user never downloads an ad creative.

### Before publishing

The build ships with Google's official **test** ad units and `USE_TEST_ADS = true` so
the project compiles and runs without an AdMob account. Replace these in
`app/build.gradle.kts`:

```kotlin
manifestPlaceholders["admobAppId"] = "ca-app-pub-XXXXXXXXXXXXXXXX~XXXXXXXXXX"
buildConfigField("String", "ADMOB_BANNER_ID", "\\"ca-app-pub-.../...\\"")
buildConfigField("String", "ADMOB_INTERSTITIAL_ID", "\\"ca-app-pub-.../...\\"")
buildConfigField("String", "ADMOB_REWARDED_ID", "\\"ca-app-pub-.../...\\"")
buildConfigField("boolean", "USE_TEST_ADS", "false")
```

Then create the `f7_remove_ads` in-app product in Play Console with a $4.99 price.
"""

if "## Monetization (1.2.0)" not in s:
    s = s.replace("## Screenshots", new_section + "\n## Screenshots", 1)
readme.write_text(s, encoding="utf-8")
print("README monetization section written")

policy = root / "docs" / "privacy-policy.html"
html = policy.read_text(encoding="utf-8")
if "Advertising and purchases" not in html:
    block = """<h2>Advertising and purchases</h2>
<p>F7 shows optional advertising supplied by Google AdMob (banner, interstitial, and rewarded
formats). Google and its partners may collect device identifiers (including the advertising ID),
approximate location derived from IP, and ad interaction data to serve and measure ads. That
processing is governed by Google&rsquo;s policies, not by F7.</p>
<p>You can remove all advertising at any time. A one-time <strong>Remove ads</strong> purchase in
Google Play disables every ad format permanently for your Google account. Watching a
<strong>rewarded ad</strong> grants a temporary ad-free window without paying. While you are
ad-free, F7 never requests or renders an ad, so no ad request leaves the device. Purchases are
handled by Google Play Billing; F7 never sees your payment details.</p>
<p>You can reset or delete your advertising ID in Android Settings &rarr; Google &rarr; Ads.</p>
"""
    html = html.replace("<h2>", block + "<h2>", 1)
    policy.write_text(html, encoding="utf-8")
    print("privacy html updated")
else:
    print("privacy html already has section")
