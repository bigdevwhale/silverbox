"""Draws the launcher icon (a 35 mm cartridge of Sunny 200 on deep teal) and docs/logo.png.

python tools/icon.py
"""
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "app/src/main/res"
TEAL = (30, 91, 92)
MUSTARD = (227, 167, 47)
RED = (196, 70, 44)
LEADER = (58, 36, 22)
HOLE = (26, 15, 9)
S = 8  # supersampling


def canister(size: int) -> Image.Image:
    """The cartridge alone, transparent around it, on a canvas of size x size (108 dp units scaled)."""
    W = size * S
    im = Image.new("RGBA", (W, W), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    u = W / 108  # one dp
    cx, cy = W / 2, W / 2
    # Body spans the safe zone horizontally.
    bl, br = cx - 27 * u, cx + 9 * u
    top, bot = cy - 17 * u, cy + 17 * u
    # The leader, out to the right.
    d.polygon([(br - 2 * u, top + 7 * u), (cx + 31 * u, top + 8 * u), (cx + 33 * u, cy), (cx + 31 * u, bot - 8 * u), (br - 2 * u, bot - 5 * u)], fill=LEADER)
    for i in range(3):
        x = br + 3 * u + i * 7 * u
        d.rounded_rectangle((x, top + 9 * u, x + 3.4 * u, top + 12.5 * u), radius=u * 0.6, fill=HOLE)
        d.rounded_rectangle((x, bot - 12.5 * u, x + 3.4 * u, bot - 9 * u), radius=u * 0.6, fill=HOLE)
    # Spool ends: brushed metal.
    for x0, x1 in ((bl - 5 * u, bl + 1 * u), (br - 1 * u, br + 4 * u)):
        metal = Image.new("RGBA", (int(x1 - x0), int(bot - top - 4 * u)))
        md = ImageDraw.Draw(metal)
        h = metal.size[1]
        for y in range(h):
            t = y / h
            v = int(232 - 90 * abs(t - 0.3) * 1.6)
            md.line([(0, y), (metal.size[0], y)], fill=(v, v - 3, v - 8, 255))
        mask = Image.new("L", metal.size, 0)
        ImageDraw.Draw(mask).rounded_rectangle((0, 0, metal.size[0] - 1, h - 1), radius=int(1.6 * u), fill=255)
        im.paste(metal, (int(x0), int(top + 2 * u)), mask)
    d.rounded_rectangle((bl - 8 * u, cy - 4 * u, bl - 4 * u, cy + 4 * u), radius=u, fill=(85, 81, 74))
    # The body with its label and the red band.
    body = Image.new("RGBA", (int(br - bl), int(bot - top)), MUSTARD + (255,))
    bd = ImageDraw.Draw(body)
    bw, bh = body.size
    bd.polygon([(0, bh * 0.66), (bw, bh * 0.28), (bw, bh * 0.5), (0, bh * 0.88)], fill=RED)
    # Round shading: light from above.
    shade = Image.new("RGBA", body.size)
    sd = ImageDraw.Draw(shade)
    for y in range(bh):
        t = y / bh
        if t < 0.35:
            a = int(70 * (1 - t / 0.35))
            sd.line([(0, y), (bw, y)], fill=(255, 255, 255, a))
        else:
            a = int(95 * ((t - 0.35) / 0.65) ** 1.4)
            sd.line([(0, y), (bw, y)], fill=(0, 0, 0, a))
    body = Image.alpha_composite(body, shade)
    mask = Image.new("L", body.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, bw - 1, bh - 1), radius=int(3 * u), fill=255)
    im.paste(body, (int(bl), int(top)), mask)
    # "200" on the label.
    d.text((bl + 4 * u, top + 3 * u), "", fill=(40, 30, 10))
    # Soft shadow under it all.
    shadow = Image.new("RGBA", im.size, (0, 0, 0, 0))
    ImageDraw.Draw(shadow).ellipse((bl - 6 * u, bot + 1 * u, cx + 32 * u, bot + 7 * u), fill=(0, 0, 0, 90))
    shadow = shadow.filter(ImageFilter.GaussianBlur(3 * u))
    im = Image.alpha_composite(shadow, im)
    return im.resize((size, size), Image.LANCZOS)


def main():
    dens = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}
    for name, k in dens.items():
        d = RES / f"mipmap-{name}"
        d.mkdir(parents=True, exist_ok=True)
        fg = canister(int(108 * k))
        fg.save(d / "ic_launcher_foreground.png")
        # Legacy 48 dp icons: the adaptive layers flattened, square-ish and round.
        n = int(48 * k)
        full = Image.new("RGBA", fg.size, TEAL + (255,))
        full = Image.alpha_composite(full, fg)
        crop = int(fg.size[0] * 18 / 108)
        flat = full.crop((crop, crop, fg.size[0] - crop, fg.size[0] - crop)).resize((n, n), Image.LANCZOS)
        sq = Image.new("L", (n * 4, n * 4), 0)
        ImageDraw.Draw(sq).rounded_rectangle((0, 0, n * 4 - 1, n * 4 - 1), radius=n * 4 // 5, fill=255)
        out = Image.new("RGBA", (n, n), (0, 0, 0, 0))
        out.paste(flat, (0, 0), sq.resize((n, n), Image.LANCZOS))
        out.save(d / "ic_launcher.png")
        rd = Image.new("L", (n * 4, n * 4), 0)
        ImageDraw.Draw(rd).ellipse((0, 0, n * 4 - 1, n * 4 - 1), fill=255)
        out = Image.new("RGBA", (n, n), (0, 0, 0, 0))
        out.paste(flat, (0, 0), rd.resize((n, n), Image.LANCZOS))
        out.save(d / "ic_launcher_round.png")
    # The logo for the README and Stillcraft: the round icon, large.
    big = 512
    fg = canister(int(big * 108 / 72))
    full = Image.alpha_composite(Image.new("RGBA", fg.size, TEAL + (255,)), fg)
    c = (fg.size[0] - big) // 2
    flat = full.crop((c, c, c + big, c + big))
    m = Image.new("L", (big * 4, big * 4), 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, big * 4 - 1, big * 4 - 1), radius=big * 4 // 5, fill=255)
    logo = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    logo.paste(flat, (0, 0), m.resize((big, big), Image.LANCZOS))
    (ROOT / "docs").mkdir(exist_ok=True)
    logo.save(ROOT / "docs/logo.png")
    print("ok")


if __name__ == "__main__":
    main()
