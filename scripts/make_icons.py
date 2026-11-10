import os
from PIL import Image, ImageDraw, ImageFont, ImageFilter

ROOT = r"C:\Download Manager v1\F7DownloadManager"
FONT = os.path.join(ROOT, "core", "ui", "src", "main", "res", "font", "manrope.ttf")
ASSETS = os.path.join(ROOT, "docs", "play-assets")
os.makedirs(ASSETS, exist_ok=True)


def font(size):
    f = ImageFont.truetype(FONT, size)
    try:
        f.set_variation_by_axes([800])
    except Exception:
        pass
    return f


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def gradient(size):
    img = Image.new("RGBA", (size, size))
    px = img.load()
    start = (9, 19, 47)
    mid = (24, 48, 132)
    end = (46, 92, 235)
    for y in range(size):
        for x in range(size):
            t = (x / size * 0.45) + (y / size * 0.55)
            if t < 0.55:
                c = lerp(start, mid, t / 0.55)
            else:
                c = lerp(mid, end, (t - 0.55) / 0.45)
            px[x, y] = (c[0], c[1], c[2], 255)
    # mint glow
    glow = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow)
    gd.ellipse(
        [int(size * 0.55), int(-size * 0.18), int(size * 1.18), int(size * 0.45)],
        fill=(46, 230, 190, 62),
    )
    glow = glow.filter(ImageFilter.GaussianBlur(size // 9))
    img.alpha_composite(glow)
    # bottom vignette
    vig = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    vd = ImageDraw.Draw(vig)
    vd.ellipse(
        [int(-size * 0.3), int(size * 0.62), int(size * 1.3), int(size * 1.35)],
        fill=(3, 8, 24, 70),
    )
    vig = vig.filter(ImageFilter.GaussianBlur(size // 8))
    img.alpha_composite(vig)
    return img


def squircle_mask(size, radius_ratio=0.225):
    mask = Image.new("L", (size, size), 0)
    d = ImageDraw.Draw(mask)
    r = int(size * radius_ratio)
    d.rounded_rectangle([0, 0, size - 1, size - 1], radius=r, fill=255)
    return mask


def draw_mark(img, size, scale=1.0, glow=True):
    d = ImageDraw.Draw(img)
    if glow:
        shadow = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        sd = ImageDraw.Draw(shadow)
        f = font(int(size * 0.50 * scale))
        bbox = sd.textbbox((0, 0), "F7", font=f, stroke_width=2)
        w, h = bbox[2] - bbox[0], bbox[3] - bbox[1]
        x = (size - w) / 2 - bbox[0]
        y = (size - h) / 2 - bbox[1] - size * 0.035
        sd.text((x, y + size * 0.02), "F7", font=f, fill=(0, 0, 0, 120))
        shadow = shadow.filter(ImageFilter.GaussianBlur(size // 22))
        img.alpha_composite(shadow)
    f = font(int(size * 0.50 * scale))
    bbox = d.textbbox((0, 0), "F7", font=f)
    w, h = bbox[2] - bbox[0], bbox[3] - bbox[1]
    x = (size - w) / 2 - bbox[0]
    y = (size - h) / 2 - bbox[1] - size * 0.035
    d.text((x, y), "F7", font=f, fill=(255, 255, 255, 255))
    # mint underline offset to the right
    bar_w = int(size * 0.20 * scale)
    bar_h = max(3, int(size * 0.030 * scale))
    bx = int(size * 0.55)
    by = int(size * 0.705)
    d.rounded_rectangle([bx, by, bx + bar_w, by + bar_h], radius=bar_h // 2, fill=(46, 230, 190, 255))
    # small mint dot
    r = max(2, int(size * 0.014 * scale))
    cx, cy = int(size * 0.50), int(size * 0.735)
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=(255, 255, 255, 150))


def legacy_icon(size, round_icon=False):
    img = gradient(size)
    draw_mark(img, size)
    mask = Image.new("L", (size, size), 0)
    md = ImageDraw.Draw(mask)
    if round_icon:
        md.ellipse([0, 0, size - 1, size - 1], fill=255)
    else:
        md.rounded_rectangle([0, 0, size - 1, size - 1], radius=int(size * 0.225), fill=255)
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    out.paste(img, (0, 0), mask)
    return out


def adaptive_background(size):
    return gradient(size)


def adaptive_foreground(size):
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw_mark(img, size, glow=False)
    return img


densities = {
    "mdpi": 1.0,
    "hdpi": 1.5,
    "xhdpi": 2.0,
    "xxhdpi": 3.0,
    "xxxhdpi": 4.0,
}

for name, factor in densities.items():
    folder = os.path.join(ROOT, "app", "src", "main", "res", f"mipmap-{name}")
    os.makedirs(folder, exist_ok=True)
    legacy = int(48 * factor)
    adaptive = int(108 * factor)
    legacy_icon(legacy, round_icon=False).save(os.path.join(folder, "ic_launcher.png"))
    legacy_icon(legacy, round_icon=True).save(os.path.join(folder, "ic_launcher_round.png"))
    adaptive_foreground(adaptive).save(os.path.join(folder, "ic_launcher_foreground.png"))
    adaptive_background(adaptive).save(os.path.join(folder, "ic_launcher_background.png"))

play_icon = legacy_icon(512)
play_icon.save(os.path.join(ASSETS, "icon-512.png"))

# Feature graphic 1024x500
W, H = 1024, 500
fg = Image.new("RGBA", (W, H))
px = fg.load()
for y in range(H):
    for x in range(W):
        t = (x / W * 0.55) + (y / H * 0.45)
        c = lerp((10, 19, 45), (28, 58, 150), t) if t < 0.6 else lerp((28, 58, 150), (44, 87, 220), (t - 0.6) / 0.4)
        px[x, y] = (c[0], c[1], c[2], 255)
glow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
gd = ImageDraw.Draw(glow)
gd.ellipse([600, -220, 1200, 360], fill=(46, 230, 190, 55))
gd.ellipse([-200, 250, 380, 720], fill=(80, 130, 255, 60))
glow = glow.filter(ImageFilter.GaussianBlur(90))
fg.alpha_composite(glow)
# icon on feature graphic
d = ImageDraw.Draw(fg)
icon = legacy_icon(190)
fg.alpha_composite(icon, (86, 155))
d.text((318, 168), "F7 Download Manager", font=font(58), fill=(255, 255, 255, 255))
d.text((320, 246), "Fast. Light. No ads. No account.", font=font(27), fill=(210, 224, 255, 235))
d.text((320, 300), "Multi-connection downloads  ·  Reliable resume  ·  HLS video", font=font(20), fill=(150, 190, 255, 220))
d.rounded_rectangle([320, 350, 568, 398], radius=24, fill=(46, 230, 190, 235))
d.text((344, 361), "GPL-3.0  ·  Open source", font=font(20), fill=(8, 24, 32, 255))
fg.convert("RGB").save(os.path.join(ASSETS, "feature-graphic-1024x500.png"))

print("icons generated:", os.listdir(ASSETS))
