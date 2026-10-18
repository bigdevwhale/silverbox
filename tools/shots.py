"""Builds README / Stillcraft screenshots from raw phone captures.

python tools/shots.py <raw_dir> <out_dir>

<raw_dir> holds full-screen captures (1080x2400) named <name>.png and <name>.ru.png.
Each one is cropped below the status bar, scaled to 540 px wide with rounded corners, and
four of them are laid out on the cream paper of a photo counter as overview.png / overview.ru.png.
"""
import random
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

STATUS = 110  # status bar height at 1080 px wide
OVERVIEW = ["camera", "stack", "album", "shelf"]
SINGLE = ["camera", "envelope", "stack", "back", "album", "shoebox", "shelf", "roll", "film"]

PAPER = (242, 234, 219)
KRAFT = (214, 186, 140)


def card(src: Path) -> Image.Image:
    im = Image.open(src).convert("RGB")
    w, h = im.size
    im = im.crop((0, STATUS, w, STATUS + 2290))
    im = im.resize((540, 1145), Image.LANCZOS)
    mask = Image.new("L", im.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, im.size[0] - 1, im.size[1] - 1), 34, fill=255)
    out = Image.new("RGBA", im.size)
    out.paste(im, (0, 0), mask)
    return out


def ground(w: int, h: int) -> Image.Image:
    g = Image.new("RGB", (w, h), PAPER)
    d = ImageDraw.Draw(g)
    # Cream counter top, warming towards kraft at the bottom.
    for y in range(h):
        t = (y / h) ** 1.6
        c = tuple(int(PAPER[i] * (1 - t) + KRAFT[i] * t) for i in range(3))
        d.line([(0, y), (w, y)], fill=c)
    # Loose prints and film boxes scattered under the phones.
    rnd = random.Random(5)
    for _ in range(26):
        x, y = rnd.randint(-60, w), rnd.randint(-60, h)
        pw, ph = rnd.choice([(150, 100), (100, 150), (120, 80)])
        col = rnd.choice([(252, 250, 244), (252, 250, 244), (227, 167, 47), (30, 91, 92), (196, 70, 44)])
        card = Image.new("RGBA", (pw, ph), col + (120,))
        card = card.rotate(rnd.uniform(-25, 25), expand=True)
        g.paste(card, (x, y), card)
    return g


def overview(cards: list[Image.Image]) -> Image.Image:
    W, H = 2400, 1300
    g = ground(W, H).convert("RGBA")
    xs = [64, 646, 1228, 1810]
    ys = [30, 92, 30, 92]
    for c, x, y in zip(cards, xs, ys):
        shadow = Image.new("RGBA", (c.size[0] + 80, c.size[1] + 80), (0, 0, 0, 0))
        ImageDraw.Draw(shadow).rounded_rectangle((40, 40, 40 + c.size[0], 40 + c.size[1]), 34, fill=(60, 40, 20, 110))
        shadow = shadow.filter(ImageFilter.GaussianBlur(18))
        g.alpha_composite(shadow, (x - 40, y - 26))
        frame = Image.new("RGBA", (c.size[0] + 4, c.size[1] + 4), (0, 0, 0, 0))
        ImageDraw.Draw(frame).rounded_rectangle((0, 0, frame.size[0] - 1, frame.size[1] - 1), 36, outline=(170, 140, 100), width=2)
        g.alpha_composite(c, (x, y))
        g.alpha_composite(frame, (x - 2, y - 2))
    return g.convert("RGB")


def main() -> None:
    raw, out = Path(sys.argv[1]), Path(sys.argv[2])
    out.mkdir(parents=True, exist_ok=True)
    for suffix in ["", ".ru"]:
        made = {}
        for name in SINGLE:
            src = raw / f"{name}{suffix}.png"
            if src.exists():
                made[name] = card(src)
                made[name].save(out / f"{name}{suffix}.png", optimize=True)
        if all(n in made for n in OVERVIEW):
            overview([made[n] for n in OVERVIEW]).save(out / f"overview{suffix}.png", optimize=True)


if __name__ == "__main__":
    main()
