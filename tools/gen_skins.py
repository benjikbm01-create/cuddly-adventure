#!/usr/bin/env python3
"""Generates original 64x64 player-layout skins for Hollow Atlas residents and enemies.

Output: src/main/resources/assets/hollowatlas/textures/entity/npc/<id>.png
All art is procedurally drawn here (no third-party assets). Re-run after editing CHARACTERS.
Requires Pillow (dev only).
"""
import os
import random
from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "hollowatlas",
                   "textures", "entity", "npc")

SKIN = {"fair": (236, 196, 164), "light": (222, 172, 132), "olive": (198, 150, 104),
        "tan": (176, 124, 84), "brown": (128, 86, 58), "deep": (92, 60, 40)}

# id: skin tone, hair colour, hair style, shirt, pants, extra
CHARACTERS = {
    "nella":   dict(skin="light", hair=(120, 52, 30), style="bun",   shirt=(118, 44, 50),  pants=(70, 52, 40),  apron=(222, 214, 196)),
    "corin":   dict(skin="tan",   hair=(52, 36, 24),  style="messy", shirt=(64, 98, 124),  pants=(92, 74, 52),  satchel=True, scarf=(196, 132, 40)),
    "tobin":   dict(skin="olive", hair=(150, 120, 80),style="short", shirt=(132, 120, 72), pants=(80, 70, 50),  beard=(140, 110, 70), hat=(180, 150, 90)),
    "wenna":   dict(skin="fair",  hair=(196, 150, 70),style="long",  shirt=(96, 132, 72),  pants=(110, 84, 60), apron=(160, 120, 80)),
    "garrick": dict(skin="brown", hair=(30, 24, 20),  style="bald",  shirt=(90, 84, 80),   pants=(60, 50, 44),  beard=(34, 28, 22), apron=(92, 60, 36)),
    "bram":    dict(skin="light", hair=(200, 110, 50),style="messy", shirt=(160, 120, 70), pants=(70, 74, 90)),
    "ada":     dict(skin="deep",  hair=(28, 22, 20),  style="bun",   shirt=(70, 92, 140),  pants=(60, 56, 70),  apron=(200, 196, 180)),
    "maren":   dict(skin="fair",  hair=(222, 196, 140),style="long", shirt=(200, 168, 120),pants=(120, 96, 70), apron=(240, 236, 226)),
    "sabine":  dict(skin="olive", hair=(70, 40, 60),  style="long",  shirt=(150, 70, 130), pants=(80, 60, 90),  scarf=(60, 140, 140)),
    "elsie":   dict(skin="light", hair=(214, 214, 214),style="bun",  shirt=(110, 110, 140),pants=(80, 80, 90),  satchel=True),
    "ilse":    dict(skin="tan",   hair=(160, 70, 40), style="short", shirt=(90, 96, 104),  pants=(56, 60, 70),  tabard=(40, 90, 70)),
    "oswin":   dict(skin="fair",  hair=(110, 90, 70), style="short", shirt=(176, 170, 150),pants=(100, 90, 70), beard=(120, 96, 76), dusty=True),
    "jory":    dict(skin="brown", hair=(40, 30, 26),  style="short", shirt=(150, 90, 50),  pants=(70, 62, 52),  satchel=True),
    "lom":     dict(skin="olive", hair=(90, 90, 90),  style="bald",  shirt=(80, 70, 60),   pants=(70, 60, 50),  beard=(150, 150, 150), hat=(60, 50, 40)),
    "hester":  dict(skin="tan",   hair=(60, 80, 40),  style="long",  shirt=(84, 110, 60),  pants=(90, 76, 56),  scarf=(160, 60, 60)),
    # enemies
    "brigand": dict(skin="olive", hair=(40, 30, 24),  style="hood",  shirt=(74, 60, 48),   pants=(54, 46, 40),  hood=(56, 58, 52), mask=(40, 40, 40)),
    "slinger": dict(skin="tan",   hair=(70, 50, 30),  style="hood",  shirt=(92, 84, 60),   pants=(60, 54, 46),  hood=(110, 70, 40), mask=(80, 40, 30), satchel=True),
    # developer test world residents (noncanonical)
    "dt_alda":    dict(skin="fair",  hair=(40, 40, 40),   style="bun",   shirt=(230, 200, 60),  pants=(60, 60, 60)),
    "dt_bertram": dict(skin="tan",   hair=(90, 60, 30),   style="short", shirt=(60, 160, 220),  pants=(60, 60, 60), apron=(240, 240, 240)),
    "dt_kade":    dict(skin="brown", hair=(20, 20, 20),   style="short", shirt=(200, 60, 60),   pants=(40, 40, 40)),
    "dt_juno":    dict(skin="olive", hair=(180, 120, 60), style="long",  shirt=(90, 200, 90),   pants=(60, 60, 60)),
    "dt_remy":    dict(skin="light", hair=(120, 120, 120),style="bald",  shirt=(200, 120, 200), pants=(60, 60, 60), beard=(120, 120, 120)),
    # neutral sandbox test resident
    "tester":  dict(skin="light", hair=(80, 80, 80),  style="short", shirt=(200, 200, 60), pants=(60, 60, 60)),
}


def jitter(c, rnd, amt=10):
    return tuple(max(0, min(255, v + rnd.randint(-amt, amt))) for v in c) + (255,)


def rect(img, rnd, x0, y0, x1, y1, color, amt=8):
    for y in range(y0, y1):
        for x in range(x0, x1):
            img.putpixel((x, y), jitter(color, rnd, amt))


def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c)


def draw(cid, d):
    rnd = random.Random(hash(cid) & 0xFFFFFF)
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    skin = SKIN[d["skin"]]
    hair = d["hair"]
    # ---- head (8x8 faces)
    rect(img, rnd, 0, 8, 32, 16, skin, 5)        # sides/front/back
    rect(img, rnd, 8, 0, 24, 8, skin, 5)         # top/bottom
    style = d["style"]
    if style != "bald":
        rect(img, rnd, 8, 0, 16, 8, hair, 12)    # top of head
        rect(img, rnd, 24, 8, 32, 14 if style in ("long", "bun") else 12, hair, 12)   # back
        rect(img, rnd, 0, 8, 8, 11, hair, 12)    # right side
        rect(img, rnd, 16, 8, 24, 11, hair, 12)  # left side
        rect(img, rnd, 8, 8, 16, 9, hair, 12)    # fringe
        if style == "messy":
            for x in range(8, 16):
                if rnd.random() < 0.5:
                    img.putpixel((x, 9), jitter(hair, rnd))
        if style == "long":
            rect(img, rnd, 0, 11, 2, 16, hair, 12)
            rect(img, rnd, 22, 11, 24, 16, hair, 12)
            rect(img, rnd, 24, 14, 32, 16, hair, 12)
        if style == "bun":
            rect(img, rnd, 26, 9, 30, 12, shade(hair, 0.8), 8)
    else:
        rect(img, rnd, 8, 0, 16, 8, shade(skin, 0.95), 6)
    if style == "hood":
        hood = d["hood"]
        rect(img, rnd, 8, 0, 16, 8, hood, 8)
        rect(img, rnd, 0, 8, 8, 16, hood, 8)
        rect(img, rnd, 16, 8, 24, 16, hood, 8)
        rect(img, rnd, 24, 8, 32, 16, hood, 8)
        rect(img, rnd, 8, 8, 16, 10, hood, 8)
        img.putpixel((8, 10), jitter(hood, rnd)); img.putpixel((15, 10), jitter(hood, rnd))
    # face
    eye_white = (240, 240, 236, 255)
    iris = jitter((60, 70, 90) if rnd.random() < 0.5 else (70, 50, 30), rnd, 6)
    img.putpixel((9, 12), eye_white); img.putpixel((10, 12), iris)
    img.putpixel((13, 12), iris); img.putpixel((14, 12), eye_white)
    brow = shade(hair if style not in ("bald", "hood") else (60, 50, 40), 0.8)
    for x in (9, 10, 13, 14):
        img.putpixel((x, 11), jitter(brow, rnd, 4))
    img.putpixel((11, 13), jitter(shade(skin, 0.85), rnd, 3)); img.putpixel((12, 13), jitter(shade(skin, 0.85), rnd, 3))
    for x in range(10, 14):
        img.putpixel((x, 14), jitter(shade(skin, 0.7), rnd, 4))
    if "beard" in d:
        rect(img, rnd, 9, 14, 15, 16, d["beard"], 10)
        rect(img, rnd, 0, 14, 8, 16, d["beard"], 10)
        rect(img, rnd, 16, 14, 24, 16, d["beard"], 10)
        img.putpixel((11, 14), jitter(shade(skin, 0.6), rnd, 2)); img.putpixel((12, 14), jitter(shade(skin, 0.6), rnd, 2))
    if "mask" in d:
        rect(img, rnd, 8, 13, 16, 16, d["mask"], 6)
        rect(img, rnd, 0, 13, 8, 16, d["mask"], 6)
        rect(img, rnd, 16, 13, 24, 16, d["mask"], 6)
        rect(img, rnd, 24, 13, 32, 16, d["mask"], 6)
    if "hat" in d:   # wide hat on the overlay layer
        hat = d["hat"]
        rect(img, rnd, 40, 0, 48, 8, hat, 8)
        rect(img, rnd, 32, 8, 64, 11, hat, 8)
    # ---- body
    shirt, pants = d["shirt"], d["pants"]
    rect(img, rnd, 16, 20, 40, 32, shirt)
    rect(img, rnd, 20, 16, 36, 20, shirt)
    rect(img, rnd, 16, 29, 40, 30, shade(pants, 0.7), 4)   # belt
    for x in range(20, 28):
        img.putpixel((x, 20), jitter(shade(shirt, 0.8), rnd))  # collar shadow
    if "apron" in d:
        rect(img, rnd, 21, 23, 27, 32, d["apron"], 6)
    if "tabard" in d:
        rect(img, rnd, 22, 20, 26, 32, d["tabard"], 6)
        rect(img, rnd, 34, 20, 38, 32, d["tabard"], 6)
        img.putpixel((23, 24), (200, 180, 90, 255)); img.putpixel((24, 24), (200, 180, 90, 255))
    if "scarf" in d:
        rect(img, rnd, 20, 20, 28, 22, d["scarf"], 8)
        rect(img, rnd, 32, 20, 40, 22, d["scarf"], 8)
    if d.get("satchel"):
        for i in range(8):
            img.putpixel((20 + i, 20 + i), jitter((96, 64, 36), rnd, 6))
        rect(img, rnd, 26, 27, 28, 30, (110, 74, 40), 6)
    if d.get("dusty"):
        for _ in range(30):
            x, y = rnd.randint(16, 39), rnd.randint(20, 31)
            img.putpixel((x, y), jitter((220, 214, 196), rnd, 8))
    # ---- arms (right: 40-56, left: 32-48 @ y48)
    for (ax, ay) in ((40, 16), (32, 48)):
        rect(img, rnd, ax, ay + 4, ax + 16, ay + 13, shirt)        # sleeves
        rect(img, rnd, ax, ay + 13, ax + 16, ay + 16, skin, 5)     # hands
        rect(img, rnd, ax + 4, ay, ax + 8, ay + 4, shirt)          # top
        rect(img, rnd, ax + 8, ay, ax + 12, ay + 4, skin, 5)       # bottom (hand)
    # ---- legs (right: 0-16 @16, left: 16-32 @48)
    boots = shade(pants, 0.55)
    for (lx, ly) in ((0, 16), (16, 48)):
        rect(img, rnd, lx, ly + 4, lx + 16, ly + 13, pants)
        rect(img, rnd, lx, ly + 13, lx + 16, ly + 16, boots, 6)
        rect(img, rnd, lx + 4, ly, lx + 8, ly + 4, pants)
        rect(img, rnd, lx + 8, ly, lx + 12, ly + 4, boots, 6)
    # body back/sides of left layer are reused; done
    os.makedirs(OUT, exist_ok=True)
    img.save(os.path.join(OUT, cid + ".png"))


if __name__ == "__main__":
    for cid, d in CHARACTERS.items():
        draw(cid, d)
    print("wrote", len(CHARACTERS), "skins to", os.path.normpath(OUT))
