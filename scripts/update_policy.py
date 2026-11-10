from pathlib import Path

md = Path(r"C:\Download Manager v1\F7DownloadManager\docs\privacy-policy.md")
s = md.read_text(encoding="utf-8")
section = """# Privacy Policy

_Last updated: 2026-11-10_

## Advertising and purchases

F7 shows optional advertising supplied by Google AdMob (banner, interstitial, and
rewarded formats). Google and its partners may collect device identifiers
(including the advertising ID), approximate location derived from IP, and ad
interaction data to serve and measure ads. That processing is governed by
Google's own policies, not by F7.

You can remove all advertising at any time:

- A one-time **Remove ads** purchase in Google Play disables every ad format
  permanently for your Google account.
- Watching a **rewarded ad** grants a temporary ad-free window without paying.

While you are ad-free, F7 does not request or render any ad, so no ad request
leaves the device. Purchases are processed by Google Play Billing; F7 never sees
or stores your payment details.

You can reset or delete your advertising ID in Android Settings > Google > Ads.
"""
if not s.startswith("# Privacy Policy"):
    raise SystemExit("unexpected privacy policy header")
rest = s.split("\n", 1)[1] if "\n" in s else ""
if "Advertising and purchases" in rest:
    print("already updated")
else:
    md.write_text(section + rest, encoding="utf-8")
    print("privacy md updated")

check = Path(r"C:\Download Manager v1\F7DownloadManager\docs\PLAY_CHECKLIST.md")
c = check.read_text(encoding="utf-8")
c = c.replace("Version: `1.1.0` (versionCode 2)", "Version: `1.2.0` (versionCode 3)")
if "AdMob" not in c:
    c = c.replace(
        "## ",
        """## Advertising and monetization

- [x] AdMob SDK integrated (banner, interstitial, rewarded) with test ad units by default.
- [ ] Replace `admobAppId` and the three ad unit IDs in `app/build.gradle.kts` with production values.
- [ ] Set `USE_TEST_ADS = false` for the Play build.
- [ ] Create the one-time in-app product `f7_remove_ads` priced at $4.99 in Play Console.
- [ ] Declare Advertising ID usage in Data safety (the `AD_ID` permission is declared).
- [ ] Confirm the banner never overlaps system navigation (it sits above the app bottom bar).
- [ ] Update the privacy policy URL with the advertising section.

## """,
        1,
    )
    check.write_text(c, encoding="utf-8")
    print("checklist updated")
else:
    check.write_text(c, encoding="utf-8")
    print("checklist version only")
