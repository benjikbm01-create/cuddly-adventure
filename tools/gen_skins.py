#!/usr/bin/env python3
"""Generates original 64x64 player-layout skins for Hollow Atlas residents and enemies.

Output: src/main/resources/assets/hollowatlas/textures/entity/npc/<id>.png
        build/skin_sheet.png (front + back preview of every skin, for review; not shipped)
All art is procedurally drawn here (no third-party assets). Re-run after editing CHARACTERS.
Requires Pillow (dev only). Output is deterministic.

Outfits follow the CLIMATE of where a person lives:
  meadow   - linen shirts, vests, suspenders, rolled sleeves, kerchiefs, straw hats
  mountain - heavy wool coats with fur collars and toggles, knit caps or fur hats, mittens,
             fur-trimmed boots, scarves; cold-rosy cheeks
  road     - travel cloaks/hoods (enemies, travellers)
Both skin layers are used: the outer layer carries coats, collars, hats and trims so they stand
out from the body in 3D.
"""
import hashlib
import os
import random
from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), "..")
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "hollowatlas", "textures", "entity", "npc")

SKIN = {"fair": (238, 200, 170), "light": (224, 176, 138), "olive": (198, 150, 106),
        "tan": (176, 126, 88), "brown": (130, 88, 60), "deep": (92, 60, 42)}
EYES = {"blue": (70, 110, 170), "grey": (110, 120, 130), "green": (70, 120, 70), "brown": (90, 60, 34),
        "dark": (50, 36, 28), "hazel": (120, 100, 50)}

# Each entry: climate + look. Unspecified details are chosen deterministically from the id.
CHARACTERS = {
    # ---- starting region (meadow villagers)
    "nella":   dict(climate="meadow", skin="light", hair=(120, 52, 30), style="bun",   shirt=(118, 44, 50),  pants=(70, 52, 40),  apron=(222, 214, 196), eyes="green"),
    "corin":   dict(climate="road",   skin="tan",   hair=(52, 36, 24),  style="messy", shirt=(64, 98, 124),  pants=(92, 74, 52),  satchel=True, scarf=(196, 132, 40), cloak=(84, 70, 56)),
    "tobin":   dict(climate="meadow", skin="olive", hair=(150, 120, 80),style="short", shirt=(132, 120, 72), pants=(80, 70, 50),  beard="full", beard_color=(140, 110, 70), hat="straw", suspenders=(90, 60, 40)),
    "wenna":   dict(climate="meadow", skin="fair",  hair=(196, 150, 70),style="braid", shirt=(96, 132, 72),  pants=(110, 84, 60), apron=(160, 120, 80), freckles=True),
    "garrick": dict(climate="meadow", skin="brown", hair=(30, 24, 20),  style="bald",  shirt=(90, 84, 80),   pants=(60, 50, 44),  beard="full", beard_color=(34, 28, 22), apron=(92, 60, 36), rolled=True, apron_leather=True),
    "bram":    dict(climate="meadow", skin="light", hair=(200, 110, 50),style="messy", shirt=(160, 120, 70), pants=(70, 74, 90),  rolled=True, freckles=True),
    "ada":     dict(climate="meadow", skin="deep",  hair=(28, 22, 20),  style="curly", shirt=(70, 92, 140),  pants=(60, 56, 70),  apron=(200, 196, 180)),
    "maren":   dict(climate="meadow", skin="fair",  hair=(222, 196, 140),style="long", shirt=(200, 168, 120),pants=(120, 96, 70), apron=(240, 236, 226), kerchief=(180, 70, 60)),
    "sabine":  dict(climate="meadow", skin="olive", hair=(70, 40, 60),  style="long",  shirt=(150, 70, 130), pants=(80, 60, 90),  scarf=(60, 140, 140), vest=(70, 40, 70)),
    "elsie":   dict(climate="meadow", skin="light", hair=(214, 214, 214),style="bun",  shirt=(110, 110, 140),pants=(80, 80, 90),  satchel=True, shawl=(150, 120, 160)),
    "ilse":    dict(climate="meadow", skin="tan",   hair=(160, 70, 40), style="ponytail", shirt=(90, 96, 104), pants=(56, 60, 70), tabard=(40, 90, 70)),
    "oswin":   dict(climate="meadow", skin="fair",  hair=(110, 90, 70), style="side",  shirt=(176, 170, 150),pants=(100, 90, 70), beard="mustache", beard_color=(120, 96, 76), dusty=True, vest=(120, 100, 70)),
    "jory":    dict(climate="meadow", skin="brown", hair=(40, 30, 26),  style="crop",  shirt=(150, 90, 50),  pants=(70, 62, 52),  satchel=True, suspenders=(60, 44, 30)),
    "lom":     dict(climate="meadow", skin="olive", hair=(90, 90, 90),  style="bald",  shirt=(80, 70, 60),   pants=(70, 60, 50),  beard="full", beard_color=(150, 150, 150), hat="felt", hat_color=(60, 50, 40)),
    "hester":  dict(climate="meadow", skin="tan",   hair=(60, 80, 40),  style="long",  shirt=(84, 110, 60),  pants=(90, 76, 56),  scarf=(160, 60, 60), shawl=(110, 90, 60)),
    # ---- enemies
    "brigand": dict(climate="road", skin="olive", hair=(40, 30, 24), style="hood", shirt=(74, 60, 48), pants=(54, 46, 40), hood=(56, 58, 52), mask=(40, 40, 40), bracers=(70, 50, 34)),
    "slinger": dict(climate="road", skin="tan",   hair=(70, 50, 30), style="hood", shirt=(92, 84, 60), pants=(60, 54, 46), hood=(110, 70, 40), mask=(80, 40, 30), satchel=True),
    # ---- developer test world: Proving Grounds (meadow test residents, deliberately bright)
    "dt_alda":    dict(climate="meadow", skin="fair",  hair=(40, 40, 40),   style="bun",   shirt=(230, 200, 60),  pants=(60, 60, 60), vest=(60, 60, 60)),
    "dt_bertram": dict(climate="meadow", skin="tan",   hair=(90, 60, 30),   style="short", shirt=(60, 160, 220),  pants=(60, 60, 60), apron=(240, 240, 240)),
    "dt_kade":    dict(climate="meadow", skin="brown", hair=(20, 20, 20),   style="crop",  shirt=(200, 60, 60),   pants=(40, 40, 40), tabard=(140, 30, 30), bracers=(60, 40, 30)),
    "dt_juno":    dict(climate="meadow", skin="olive", hair=(180, 120, 60), style="braid", shirt=(90, 200, 90),   pants=(60, 60, 60), hat="straw", rolled=True),
    "dt_remy":    dict(climate="meadow", skin="light", hair=(120, 120, 120),style="bald",  shirt=(200, 120, 200), pants=(60, 60, 60), beard="full", beard_color=(120, 120, 120), apron=(230, 230, 230)),
    # ---- developer test world: Test Heights (mountain test residents)
    "dt_h_ivo":   dict(climate="mountain", skin="tan",   hair=(60, 40, 30),  style="short", shirt=(150, 60, 50),  coat=(92, 70, 52),  fur=(216, 206, 190), pants=(70, 62, 56), hat="fur", hat_color=(120, 90, 60), beard="stubble", beard_color=(60, 40, 30)),
    "dt_h_marta": dict(climate="mountain", skin="fair",  hair=(170, 80, 40), style="braid", shirt=(220, 210, 190), coat=(46, 90, 110), fur=(240, 236, 228), pants=(80, 70, 70), hat="knit", hat_color=(200, 60, 60), knit_stripe=(240, 230, 210), scarf=(200, 60, 60)),
    "dt_h_pell":  dict(climate="mountain", skin="deep",  hair=(24, 20, 18),  style="crop", shirt=(80, 110, 70),  coat=(64, 64, 70),  fur=(150, 130, 110), pants=(56, 54, 60), beard="full", beard_color=(96, 84, 76), apron=(80, 56, 36), apron_leather=True, eyes="hazel"),
    "dt_h_sorrel":dict(climate="mountain", skin="olive", hair=(210, 200, 180), style="long", shirt=(120, 90, 140), coat=(130, 50, 60), fur=(220, 214, 200), pants=(70, 60, 66), hat="knit", hat_color=(70, 120, 90), knit_stripe=(230, 220, 160), shawl=(170, 140, 90)),
    # neutral sandbox test resident
    "tester":  dict(climate="meadow", skin="light", hair=(80, 80, 80), style="short", shirt=(200, 200, 60), pants=(60, 60, 60)),
}

# ------------------------------------------------------------------------------------------------ texture helpers

FACE_SHADE = {"top": 1.10, "front": 1.0, "right": 0.88, "left": 0.88, "back": 0.92, "bottom": 0.72}


def rng_for(cid, salt=""):
    return random.Random(int(hashlib.sha256((cid + salt).encode()).hexdigest()[:12], 16))


def clamp(c):
    return tuple(max(0, min(255, int(v))) for v in c)


def mul(c, f):
    return clamp(v * f for v in c)


def mix(a, b, t):
    return clamp(a[i] * (1 - t) + b[i] * t for i in range(3))


def faces(u, v, w, h, d):
    """UV rectangles (x, y, width, height) of each face of a Minecraft cuboid at texture offset (u, v)."""
    return {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "right": (u, v + d, d, h),
            "front": (u + d, v + d, w, h), "left": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h)}


# part: (base uv, overlay uv, w, h, d)
PARTS = {"head": ((0, 0), (32, 0), 8, 8, 8), "body": ((16, 16), (16, 32), 8, 12, 4),
         "rarm": ((40, 16), (40, 32), 4, 12, 4), "larm": ((32, 48), (48, 48), 4, 12, 4),
         "rleg": ((0, 16), (0, 32), 4, 12, 4), "lleg": ((16, 48), (0, 48), 4, 12, 4)}


class Skin:
    def __init__(self, cid, d):
        self.cid, self.d = cid, d
        self.img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
        self.r = rng_for(cid)

    def paint(self, part, layer, fn):
        """fn(face, x, y, w, h) -> color tuple, (color, alpha) or None, x/y within the face."""
        (bu, bv), (ou, ov), w, h, d = PARTS[part]
        u, v = (bu, bv) if layer == 0 else (ou, ov)
        for face, (fx, fy, fw, fh) in faces(u, v, w, h, d).items():
            for y in range(fh):
                for x in range(fw):
                    c = fn(face, x, y, fw, fh)
                    if c is None:
                        continue
                    shade = FACE_SHADE[face]
                    # gentle top-to-bottom gradient on the sides
                    if face not in ("top", "bottom"):
                        shade *= 1.04 - 0.08 * (y / max(1, fh - 1))
                    col = mul(c, shade)
                    self.img.putpixel((fx + x, fy + y), col + (255,))

    def fabric(self, c, kind="linen", x=0, y=0):
        n = self.r.random()
        if kind == "wool":
            f = 0.92 + 0.10 * ((x + y) % 2) + (n - 0.5) * 0.06
        elif kind == "fur":
            f = 0.82 + n * 0.32
        elif kind == "leather":
            f = 0.9 + (n - 0.5) * 0.12
        elif kind == "knit":
            f = 0.9 + 0.12 * (x % 2) + (n - 0.5) * 0.05
        else:
            f = 0.96 + (n - 0.5) * 0.08
        return mul(c, f)

    def skin_px(self, x=0, y=0):
        return mul(SKIN[self.d["skin"]], 0.97 + self.r.random() * 0.05)


# ------------------------------------------------------------------------------------------------ painting


def paint_head(s):
    d, r = s.d, s.r
    skin = SKIN[d["skin"]]
    hair = d["hair"]
    style = d["style"]
    eye = EYES[d.get("eyes") or r.choice(list(EYES))]
    rosy = d.get("climate") == "mountain" or d.get("rosy")

    def base(face, x, y, w, h):
        c = s.skin_px()
        if style in ("bald",) and face == "top":
            return mul(c, 0.96)
        if style == "hood" and (face != "front" or y < 2 or x in (0, 7)):
            return s.fabric(d["hood"], "wool", x, y)
        hy = 0 if style == "hood" else {"short": 2, "crop": 1, "messy": 3, "side": 2, "long": 3, "bun": 2, "braid": 2, "ponytail": 2, "curly": 3}.get(style, 0)
        if face == "top" and style not in ("bald", "hood"):
            return s.fabric(hair, "fur" if style == "curly" else "linen")
        if face in ("right", "left") and style not in ("bald", "hood"):
            depth = 8 if style in ("long", "curly") else 6 if style in ("bun", "braid", "ponytail", "messy") else 3
            front_edge = x >= 6 if face == "right" else x <= 1
            if y < hy + (2 if not front_edge else 0) or (y < depth and not front_edge):
                return s.fabric(hair)
        if face == "back" and style not in ("bald", "hood"):
            depth = 8 if style in ("long", "curly", "braid", "ponytail") else 6 if style in ("bun", "messy") else 4
            if y < depth:
                c2 = s.fabric(hair)
                if style == "braid" and y >= 5 and x in (3, 4):
                    return mul(c2, 0.82 if (x + y) % 2 else 1.0)
                return c2
        if face == "front":
            if style not in ("bald", "hood") and y < hy:
                if style == "messy" and y == hy - 1 and r.random() < 0.5:
                    return c
                if style == "side" and y == hy - 1 and x > 4:
                    return c
                return s.fabric(hair)
            # eyes / brows / nose / mouth
            if y == 3 and x in (1, 2, 5, 6):
                return mul(hair if style not in ("bald",) else (70, 56, 46), 0.75)
            if y == 4:
                if x in (1, 6):
                    return (238, 236, 230)
                if x in (2, 5):
                    return eye
            if y == 5 and x in (3, 4):
                return mul(skin, 0.86)
            if y == 6 and x in (3, 4):
                return mul(skin, 0.72) if not d.get("beard") else mul(skin, 0.8)
            if y == 5 and rosy and x in (1, 6):
                return mix(skin, (210, 90, 90), 0.35)
            if d.get("freckles") and y == 5 and x in (1, 2, 5, 6) and (x + y) % 2:
                return mul(skin, 0.8)
            # long hair frames the face
            if style in ("long", "curly") and x in (0, 7) and y >= hy:
                return s.fabric(hair)
        return c

    s.paint("head", 0, base)

    beard = d.get("beard")
    if beard:
        bc = d.get("beard_color", hair)

        def b(face, x, y, w, h):
            if face == "front":
                if beard == "mustache":
                    return bc if y == 6 and 1 <= x <= 6 else None
                if beard == "stubble":
                    return mul(mix(SKIN[d["skin"]], bc, 0.45), 1.0) if y >= 6 and (x + y) % 2 == 0 else None
                if y == 6 and x in (3, 4):
                    return mul(SKIN[d["skin"]], 0.6)
                return s.fabric(bc, "fur") if y >= 6 or (y == 5 and x in (0, 7)) else None
            if face in ("right", "left") and beard == "full":
                return s.fabric(bc, "fur") if y >= 5 and (x >= 5 if face == "right" else x <= 2) else None
            return None
        s.paint("head", 0, lambda f, x, y, w, h: b(f, x, y, w, h) or base(f, x, y, w, h))

    if "mask" in d:
        def m(face, x, y, w, h):
            return d["mask"] if face != "top" and y >= 5 else None
        s.paint("head", 1, m)

    # head overlay: hats and hair volume
    hat = d.get("hat")
    hc = d.get("hat_color", (196, 170, 100))
    if hat == "straw":
        straw = (214, 184, 110)

        def f(face, x, y, w, h):
            if face == "top":
                return s.fabric(straw, "knit", x, y)
            if face != "bottom" and y <= 1:
                return mul(s.fabric(straw, "knit", x, y), 0.85 if y == 1 else 1.0) if y == 0 or face != "front" else (150, 60, 50)
            return None
        s.paint("head", 1, f)
    elif hat == "felt":
        def f(face, x, y, w, h):
            return s.fabric(hc, "wool", x, y) if face == "top" or (face != "bottom" and y <= 1) else None
        s.paint("head", 1, f)
    elif hat == "knit":
        stripe = d.get("knit_stripe", (230, 230, 230))

        def f(face, x, y, w, h):
            if face == "top":
                return stripe if (x, y) in ((3, 3), (4, 3), (3, 4), (4, 4)) else s.fabric(hc, "knit", x, y)
            if face != "bottom" and y <= 2:
                return s.fabric(stripe if y == 2 else hc, "knit", x, y)
            return None
        s.paint("head", 1, f)
    elif hat == "fur":
        def f(face, x, y, w, h):
            if face == "top":
                return s.fabric(hc, "fur")
            if face != "bottom" and y <= 2:
                return s.fabric(mul(hc, 1.15) if y == 2 else hc, "fur")
            if face in ("right", "left") and y <= 5 and (x in (2, 3, 4, 5)):
                return s.fabric(hc, "fur")      # ear flaps
            return None
        s.paint("head", 1, f)
    elif d["style"] in ("curly", "bun"):
        def f(face, x, y, w, h):
            if d["style"] == "bun" and face == "back" and 1 <= y <= 3 and 2 <= x <= 5:
                return s.fabric(mul(hair, 0.85))
            if d["style"] == "curly" and face in ("top", "back", "right", "left") and y <= 2 and s.r.random() < 0.55:
                return s.fabric(hair, "fur")
            return None
        s.paint("head", 1, f)
    if d["style"] == "ponytail":
        s.paint("head", 1, lambda face, x, y, w, h: s.fabric(hair) if face == "back" and x in (3, 4) and y >= 3 else None)


def garment_rows(d):
    return d.get("climate", "meadow")


def paint_body_and_arms(s):
    d = s.d
    skin = SKIN[d["skin"]]
    shirt, pants = d["shirt"], d["pants"]
    climate = d.get("climate", "meadow")
    coat = d.get("coat")
    fur = d.get("fur", (220, 210, 200))
    belt = mul(pants, 0.55)

    def body(face, x, y, w, h):
        c = s.fabric(shirt, "wool" if climate == "mountain" else "linen", x, y)
        if face == "front":
            if y == 0 and x in (3, 4) and not d.get("scarf"):
                return skin                                # open collar
            if y in (1, 2, 3) and x in (3, 4) and climate == "meadow":
                return mul(c, 0.85)                        # placket
        if face != "top" and face != "bottom" and y in (9, 10):
            if face == "front" and y == 9 and x in (3, 4):
                return (200, 180, 100)                     # buckle
            return belt
        if face != "top" and face != "bottom" and y >= 11:
            return s.fabric(pants)
        if "vest" in d and face in ("front", "back", "right", "left") and y < 9:
            if face != "front" or x not in (3, 4):
                return s.fabric(d["vest"], "wool", x, y)
        if "suspenders" in d and face in ("front", "back") and y < 9 and x in (1, 6):
            return d["suspenders"]
        if "tabard" in d and face in ("front", "back") and 2 <= x <= 5:
            t = d["tabard"]
            return (200, 180, 90) if face == "front" and y == 4 and x in (3, 4) else s.fabric(t)
        if "apron" in d and face == "front" and y >= 3 and 1 <= x <= 6:
            a = d["apron"]
            return s.fabric(a, "leather" if d.get("apron_leather") else "linen")
        if "apron" in d and face == "front" and y == 2 and x in (2, 5):
            return mul(d["apron"], 0.8)
        if d.get("satchel") and face in ("front", "back"):
            if (face == "front" and x == y - 1 and y >= 1) or (face == "back" and x == 8 - y and y >= 1):
                return (96, 64, 36)
            if face == "front" and 6 <= y <= 8 and x in (5, 6):
                return (120, 80, 44)
        if d.get("dusty") and s.r.random() < 0.12:
            return mix(c, (220, 214, 196), 0.6)
        return c

    s.paint("body", 0, body)

    # outer layer: coats, collars, scarves, shawls, cloaks
    def body_over(face, x, y, w, h):
        if coat:
            if face == "top":
                return s.fabric(fur, "fur")
            if y <= 1 and face != "bottom":
                return s.fabric(fur, "fur")                # fur collar all round
            if face == "front":
                if x in (3, 4) and y <= 8:
                    if y in (3, 6) and x == 4:
                        return (200, 180, 130)             # toggles
                    return mul(coat, 0.8) if x == 4 else s.fabric(coat, "wool", x, y)
                if y in (9, 10):
                    return belt if y == 9 else None
            if face != "bottom" and y <= 11:
                return s.fabric(coat, "wool", x, y) if y != 11 else s.fabric(fur, "fur")
            return None
        if "cloak" in d and face in ("back", "right", "left", "top"):
            return s.fabric(d["cloak"], "wool", x, y) if y <= 11 else None
        if "shawl" in d and face != "bottom" and (y <= 3 or face == "top"):
            if face == "front" and x in (3, 4) and y >= 2:
                return None
            return s.fabric(d["shawl"], "knit", x, y)
        if "scarf" in d and face != "bottom" and (y <= 1 or face == "top"):
            return s.fabric(d["scarf"], "knit", x, y)
        if "scarf" in d and face == "front" and x in (5, 6) and y <= 5:
            return s.fabric(d["scarf"], "knit", x, y)
        if "kerchief" in d and face == "front" and y <= 1 and 2 <= x <= 5:
            return d["kerchief"]
        return None

    s.paint("body", 1, body_over)

    rolled = d.get("rolled")

    def arm(face, x, y, w, h):
        if face == "bottom" or (face != "top" and y >= 11):
            return s.skin_px()
        if rolled and face != "top" and y >= 6:
            return s.skin_px() if y > 6 else mul(shirt, 0.85)
        c = s.fabric(shirt, "wool" if climate == "mountain" else "linen", x, y)
        if "bracers" in d and face != "top" and 6 <= y <= 10:
            return s.fabric(d["bracers"], "leather")
        return c

    def arm_over(face, x, y, w, h):
        if coat:
            if face == "bottom":
                return mul(coat, 0.7)
            if face != "top" and y >= 10:
                return s.fabric(mul(coat, 0.75), "knit", x, y) if y >= 11 else s.fabric(fur, "fur")   # mittens + cuff
            return s.fabric(coat, "wool", x, y)
        if "cloak" in d and face in ("back", "top", "right", "left") and y <= 6:
            return s.fabric(d["cloak"], "wool", x, y)
        if "shawl" in d and face != "bottom" and (face == "top" or y <= 1):
            return s.fabric(d["shawl"], "knit", x, y)
        return None

    for part in ("rarm", "larm"):
        s.paint(part, 0, arm)
        s.paint(part, 1, arm_over)


def paint_legs(s):
    d = s.d
    pants = d["pants"]
    climate = d.get("climate", "meadow")
    boots = (58, 42, 32) if climate != "meadow" else mul(pants, 0.5)
    fur = d.get("fur", (220, 210, 200))
    tall = 6 if climate == "mountain" else 9

    def leg(face, x, y, w, h):
        if face == "bottom":
            return mul(boots, 0.6)
        if face != "top" and y >= tall:
            c = s.fabric(boots, "leather")
            return mul(c, 1.25) if y == tall and climate != "mountain" else c
        c = s.fabric(pants, "wool" if climate == "mountain" else "linen", x, y)
        if face == "front" and x == 0 and y < tall:
            return mul(c, 0.9)                              # crease
        return c

    def leg_over(face, x, y, w, h):
        if climate == "mountain" and face not in ("top", "bottom") and y in (tall, tall + 1):
            return s.fabric(fur, "fur")                     # fur boot trim
        if d.get("coat") and face not in ("top", "bottom") and y <= 1:
            return s.fabric(d["coat"], "wool", x, y)        # coat hem below the belt
        return None

    for part in ("rleg", "lleg"):
        s.paint(part, 0, leg)
        s.paint(part, 1, leg_over)


def draw(cid, d):
    s = Skin(cid, d)
    paint_head(s)
    paint_body_and_arms(s)
    paint_legs(s)
    os.makedirs(OUT, exist_ok=True)
    s.img.save(os.path.join(OUT, cid + ".png"))
    return s.img


# ------------------------------------------------------------------------------------------------ review sheet

def flat_view(img, back=False):
    """16x32 front (or back) view assembled from the faces of both layers."""
    view = Image.new("RGBA", (16, 32), (0, 0, 0, 0))
    face = "back" if back else "front"
    place = {"head": (4, 0), "body": (4, 8), "rarm": (0, 8), "larm": (12, 8), "rleg": (4, 20), "lleg": (8, 20)}
    if back:
        place.update({"rarm": (12, 8), "larm": (0, 8), "rleg": (8, 20), "lleg": (4, 20)})
    for layer in (0, 1):
        for part, (px, py) in place.items():
            (bu, bv), (ou, ov), w, h, dd = PARTS[part]
            u, v = (bu, bv) if layer == 0 else (ou, ov)
            fx, fy, fw, fh = faces(u, v, w, h, dd)[face]
            region = img.crop((fx, fy, fx + fw, fy + fh))
            view.alpha_composite(region, (px, py))
    return view


def sheet(images):
    scale, pad = 6, 6
    cols = 8
    rows = (len(images) + cols - 1) // cols
    cell_w, cell_h = 32 * scale + pad * 3, 32 * scale + pad * 2
    out = Image.new("RGBA", (cols * cell_w, rows * cell_h), (60, 66, 72, 255))
    for i, (cid, img) in enumerate(images):
        f = flat_view(img).resize((16 * scale, 32 * scale), Image.NEAREST)
        b = flat_view(img, True).resize((16 * scale, 32 * scale), Image.NEAREST)
        x, y = (i % cols) * cell_w + pad, (i // cols) * cell_h + pad
        out.alpha_composite(f, (x, y))
        out.alpha_composite(b, (x + 16 * scale + pad, y))
    return out


if __name__ == "__main__":
    import sys
    imgs = [(cid, draw(cid, d)) for cid, d in CHARACTERS.items()]
    print("wrote", len(imgs), "skins to", os.path.normpath(OUT))
    which = sys.argv[1] if len(sys.argv) > 1 else "devtest"
    picked = [(c, i) for c, i in imgs if (which == "all") or (which == "devtest" and c.startswith("dt_"))]
    os.makedirs(os.path.join(ROOT, "build"), exist_ok=True)
    sheet(picked).save(os.path.join(ROOT, "build", "skin_sheet.png"))
    print("review sheet: build/skin_sheet.png (" + which + ")")
