import os, urllib.request, xml.etree.ElementTree as ET

ROOT = r"C:\Download Manager v1\F7DownloadManager"
OUT = os.path.join(ROOT, "app", "src", "main", "res", "drawable")
os.makedirs(OUT, exist_ok=True)

names = [
    "download", "add", "close", "search", "settings", "folder_open", "schedule",
    "public", "pause", "play_arrow", "delete", "share", "more_vert", "check_circle",
    "error", "wifi", "speed", "storage", "verified", "content_copy", "open_in_new",
    "link", "refresh", "arrow_back", "home", "lock", "shield", "info", "tune",
    "cloud_download", "movie", "music_note", "image", "description", "archive",
    "android", "calculate", "filter_list", "done_all", "cancel", "folder", "edit",
    "calendar_month", "timer", "cleaning_services", "privacy_tip", "code",
    "chevron_right", "expand_more", "notifications", "language", "check", "bolt",
    "memory", "sync", "save_alt", "delete_sweep", "rule", "fingerprint", "policy",
    "download_done", "downloading", "hourglass_top", "stop_circle", "add_circle",
    "arrow_forward", "keyboard_arrow_down", "sort", "grid_view", "list", "open_with",
    "apps", "file_open", "drive_file_rename_outline", "undo", "workspace_premium",
    "shopping_cart", "bar_chart", "insights", "bookmark", "bookmark_add", "bookmark_remove", "history",
    "arrow_upward", "arrow_downward", "queue", "sort_by_alpha", "find_in_page",
    "desktop_windows", "cookie", "javascript", "home", "clear_all", "data_usage",
]

BASE = "https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/{0}/materialsymbolsoutlined/{0}_24px.svg"
NS = "{http://www.w3.org/2000/svg}"


def esc(value):
    return (value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace('"', "&quot;"))


ok, failed = 0, []
for name in names:
    url = BASE.format(name)
    try:
        with urllib.request.urlopen(url, timeout=25) as r:
            svg = r.read().decode("utf-8")
    except Exception as e:
        failed.append((name, str(e)))
        continue
    root = ET.fromstring(svg)
    view_box = root.attrib.get("viewBox", "0 0 24 24").replace(",", " ").split()
    if len(view_box) != 4:
        view_box = ["0", "0", "24", "24"]
    min_x, min_y, width, height = (float(v) for v in view_box)
    paths = []
    for path in root.iter(NS + "path"):
        d = path.attrib.get("d")
        if d:
            paths.append((d, path.attrib.get("fill-rule", "")))
    if not paths:
        failed.append((name, "no paths"))
        continue
    body = []
    for d, fill_rule in paths:
        fr = ' android:fillType="evenOdd"' if fill_rule == "evenodd" else ""
        body.append('        <path android:fillColor="#FF000000" android:pathData="%s"%s />' % (esc(d), fr))
    inner = "\n".join(body)
    if min_x != 0 or min_y != 0:
        inner = ('    <group android:translateX="%s" android:translateY="%s">\n%s\n    </group>'
                 % (-min_x, -min_y, inner))
    xml = ('<?xml version="1.0" encoding="utf-8"?>\n'
           '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
           '    android:width="24dp" android:height="24dp"\n'
           '    android:viewportWidth="%s" android:viewportHeight="%s">\n'
           '%s\n</vector>\n' % (width, height, inner))
    with open(os.path.join(OUT, "ic_f7_%s.xml" % name), "w", encoding="utf-8") as f:
        f.write(xml)
    ok += 1

print("converted", ok, "of", len(names))
if failed:
    print("failed:", failed)


