#!/usr/bin/env python3
"""Generates the scenery SHOWCASE map (2001 x 2001 blocks): terrain and buildings only, no residents or quests.

    python3 tools/showcase_src/showcase.py   ->  src/main/resources/data/hollowatlas/region/showcase_wilds.json

Everything is deterministic (fixed seeds). Local coordinates: x east, z south, map spans -1000..1000.
"""
import json
import math
import os
import random

OUT = os.path.join(os.path.dirname(__file__), "..", "..", "src", "main", "resources", "data", "hollowatlas", "region", "showcase_wilds.json")
R = random.Random(20261118)

L = dict(id="showcase_wilds", name="The Hollow Atlas: Showcase", mode="showcase", arrival_anchor="sc_arrival",
         origin=[0, 0], half_size=1000, base_y=66, water_y=62, seed=77177, border_size=2002,
         snow_line=205, surface_style="mountain", rock_bands=True, road_steps=True, max_height=312, relief=3.2,
         village_center=[-170, 320], village_radius=0,
         zones=[], peaks=[], ranges=[], spires=[], hills=[], flats=[], falls=[],
         edge_ridge=dict(width=70, height=60, gaps=[dict(edge="south", at=0, half=14)]),
         rivers=[], ponds=[], roads=[], woods=[], fields=[], buildings=[], caves=[], features=[], anchors={})

# ------------------------------------------------------------------------------------------- regions of the map
L["zones"] = [
    dict(id="sc_z_cherry", x=230, z=640, r=240, palette="cherry"),      # listed first: wins where zones overlap
    dict(id="sc_z_volcano", x=640, z=-560, r=330, palette="volcanic"),
    dict(id="sc_z_badlands", x=650, z=310, r=330, palette="badlands"),
    dict(id="sc_z_taiga", x=-580, z=-430, r=380, palette="taiga"),
]

L["peaks"] = [
    dict(id="sc_crown", x=0, z=-540, r=430, h=210, crag=0.5),
    dict(id="sc_westhorn", x=-330, z=-660, r=240, h=150, crag=0.6),
    dict(id="sc_easthorn", x=280, z=-680, r=240, h=165, crag=0.6),
    dict(id="sc_tooth", x=-270, z=-300, r=120, h=95, crag=0.75),
    dict(id="sc_cinder", x=640, z=-560, r=290, h=170, crag=0.3, crater=62, crater_depth=70),
    dict(id="sc_ash_cone", x=820, z=-300, r=110, h=70, crag=0.4, crater=18, crater_depth=20),
    dict(id="sc_red_dome", x=860, z=560, r=140, h=60, crag=0.5),
]
L["ranges"] = [
    dict(id="sc_north_spine", width=170, h=120, scale=95, points=[[-990, -900], [-520, -800], [0, -880], [520, -800], [990, -900]]),
    dict(id="sc_west_wall", width=120, h=75, scale=80, points=[[-990, -560], [-940, -60], [-980, 360]]),
]
L["hills"] = [
    dict(id="sc_h1", x=-20, z=460, r=120, h=16), dict(id="sc_h2", x=-330, z=120, r=90, h=22),
    dict(id="sc_h3", x=120, z=200, r=110, h=18), dict(id="sc_h4", x=-120, z=720, r=100, h=14),
    dict(id="sc_h5", x=380, z=40, r=120, h=26), dict(id="sc_h6", x=-760, z=160, r=140, h=30),
]

# badlands: mesas and hoodoos, kept clear of the outpost mesa, canyon and roads
MESA = dict(id="sc_outpost_mesa", x=560, z=300, r=62, h=56)
spires = [MESA]
taken = [(MESA["x"], MESA["z"], 90), (520, 470, 40), (700, 220, 30), (600, 330, 30)]


def free(x, z, r):
    return all(math.hypot(x - a, z - b) > r + c for a, b, c in taken)


for k in range(14):                      # mesas
    for _ in range(40):
        ang, dist = R.random() * 6.283, R.random() * 320
        x, z = int(630 + math.cos(ang) * dist), int(330 + math.sin(ang) * dist)
        r = R.randint(22, 46)
        if abs(x) < 960 - r and abs(z) < 960 - r and free(x, z, r):
            spires.append(dict(id=f"sc_mesa{k}", x=x, z=z, r=r, h=R.randint(30, 72)))
            taken.append((x, z, r))
            break
for k in range(60):                      # hoodoos
    for _ in range(40):
        ang, dist = R.random() * 6.283, R.random() * 340
        x, z = int(630 + math.cos(ang) * dist), int(330 + math.sin(ang) * dist)
        r = R.randint(3, 8)
        if abs(x) < 960 and abs(z) < 960 and free(x, z, r + 4):
            spires.append(dict(id=f"sc_hoodoo{k}", x=x, z=z, r=r, h=R.randint(18, 64)))
            taken.append((x, z, r + 2))
            break
for k in range(18):                      # needles along the great gorge
    for _ in range(40):
        x, z = R.randint(-640, -260), R.randint(-200, 420)
        r = R.randint(4, 9)
        if free(x, z, r + 6):
            spires.append(dict(id=f"sc_needle{k}", x=x, z=z, r=r, h=R.randint(30, 80)))
            taken.append((x, z, r + 6))
            break
L["spires"] = spires

# ------------------------------------------------------------------------------------------- water
def meander(pts, amp, wave, seed):
    """Subdivide a polyline and bend it side to side so rivers wind instead of running straight."""
    rr = random.Random(seed)
    out = [pts[0]]
    for (x0, z0), (x1, z1) in zip(pts, pts[1:]):
        length = math.hypot(x1 - x0, z1 - z0)
        n = max(2, int(length / 18))
        nx, nz = -(z1 - z0) / length, (x1 - x0) / length
        ph = rr.random() * 6.28
        for k in range(1, n + 1):
            t = k / n
            off = math.sin(t * length / wave * 6.28 + ph) * amp * math.sin(t * math.pi) if k < n else 0
            out.append([int(x0 + (x1 - x0) * t + nx * off), int(z0 + (z1 - z0) * t + nz * off)])
    return out


GORGE = [[-250, -390], [-390, -170], [-330, 40], [-470, 230], [-520, 400]]
L["rivers"] = [
    dict(id="sc_great_gorge", width=16, depth=3, water_y=32, gorge=True, points=meander(GORGE, 22, 140, 1)),
    dict(id="sc_red_canyon", width=11, depth=2, water_y=42, gorge=True, points=meander([[960, 90], [760, 200], [660, 260], [610, 420], [500, 520]], 16, 90, 2)),
    dict(id="sc_willow", width=10, depth=3, points=meander([[70, -60], [40, 120], [-40, 260], [20, 430], [110, 560], [150, 600]], 26, 120, 3)),
]
L["ponds"] = [
    dict(id="sc_mirror_lake", x=-600, z=660, r=250, depth=10, y=62),
    dict(id="sc_blossom_lake", x=190, z=660, r=115, depth=7, y=62),
    dict(id="sc_crater_lava", x=640, z=-560, r=34, depth=3, fluid="lava"),
    dict(id="sc_ash_lava", x=820, z=-300, r=9, depth=2, fluid="lava"),
    dict(id="sc_tarn", x=-560, z=-320, r=26, depth=4),
    dict(id="sc_city_pool", x=-120, z=-205, r=7, depth=2),
]
ISLANDS = [(-560, 520, 152, 18), (-700, 640, 176, 24), (-470, 720, 140, 14), (-640, 800, 196, 16), (-780, 480, 165, 13), (170, 650, 132, 15)]
L["falls"] = [
    dict(id="sc_gorge_head_fall", x=-246, z=-394, w=4, axis="x"),
    dict(id="sc_city_fall", x=-104, z=-208, w=3, axis="z"),
] + [dict(id=f"sc_isle_fall{i}", x=x + s + 1, z=z - 1, w=2, axis="z", top=y) for i, (x, z, y, s) in enumerate(ISLANDS) if i in (0, 1, 3, 5)]

# ------------------------------------------------------------------------------------------- the cliff city
CITY_X = 0
LEVELS = [(-120, 84, 180), (-160, 97, 180), (-200, 110, 180), (-240, 123, 170), (-280, 136, 160), (-320, 150, 110)]   # (z centre, y, width)
STONE_HOUSE = dict(wall="minecraft:stone_bricks", frame="minecraft:stripped_dark_oak_log", roof="deepslate", foundation="minecraft:cobbled_deepslate")
TIMBER_HOUSE = dict(wall="minecraft:white_terracotta", frame="minecraft:stripped_spruce_log", roof="spruce", foundation="minecraft:stone_bricks")
PLASTER = dict(wall="minecraft:smooth_sandstone", frame="minecraft:stripped_dark_oak_log", roof="dark_oak", foundation="minecraft:stone_bricks")
STYLES = [STONE_HOUSE, TIMBER_HOUSE, PLASTER, dict(wall="minecraft:spruce_planks", frame="minecraft:stripped_dark_oak_log", roof="dark_oak", foundation="minecraft:cobblestone")]
TYPES = [("house", 9, 8), ("house", 9, 7), ("bakery", 11, 9), ("store", 11, 9), ("smithy", 11, 9), ("house", 9, 8), ("inn", 15, 11), ("house", 9, 7)]
bcount = [0]


def building(btype, x, z, w, d, facing, style, label, floors=1, beds=1):
    bcount[0] += 1
    b = dict(id=f"sc_b{bcount[0]}", type=btype, label=label, x=int(x), z=int(z), w=w, d=d, facing=facing, floors=floors, beds=beds)
    b.update(style)
    if btype in ("smithy", "relay", "tower"):
        b["floor"] = "minecraft:stone_bricks"
    L["buildings"].append(b)
    return b


for i, (zc, y, w) in enumerate(LEVELS):
    L["flats"].append(dict(id=f"sc_city_l{i}", shape="rect", x=CITY_X, z=zc, w=w, d=40, blend=2, y=y, wall=True, town=True))
    half = w // 2 - 6
    L["roads"].append(dict(id=f"sc_city_street{i}", width=5, surface="cobble", graded=True, points=[[-half, zc, y], [half, zc, y]]))
    if i == len(LEVELS) - 1:
        # the citadel: a great hall flanked by towers
        building("hall", 0, zc - 10, 21, 13, "south", STONE_HOUSE, "Citadel Hall", floors=2)
        building("tower", -40, zc - 6, 7, 7, "south", STONE_HOUSE, "West Tower", floors=4)
        building("tower", 40, zc - 6, 7, 7, "south", STONE_HOUSE, "East Tower", floors=4)
        L["features"].append(dict(id="sc_citadel_statue", type="statue", x=0, z=zc + 9))
        continue
    # two rows of buildings facing the street, leaving the grand stair (x -9..9) clear
    for row, facing in ((-1, "south"), (1, "north")):
        x = -half + 2
        k = i * 3 + (row > 0)
        while True:
            btype, bw, bd = TYPES[k % len(TYPES)]
            k += 1
            if x + bw > half:
                break
            if x < 10 and x + bw > -10:
                x = 10
                continue
            zz = zc + row * (2.5 + 1 + bd / 2 + 1)
            building(btype, x + bw / 2, zz, bw, bd, facing, STYLES[(k + i) % len(STYLES)],
                     f"Cliff City {btype}", floors=2 if btype in ("inn", "house") and k % 3 == 0 else 1, beds=2 if btype == "house" else 1)
            x += bw + 3 + (k % 3)

# ramps between levels, alternating east/west outside the terraces
for i in range(len(LEVELS) - 1):
    (z0, y0, w0), (z1, y1, w1) = LEVELS[i], LEVELS[i + 1]
    s = 1 if i % 2 == 0 else -1
    e0, e1 = w0 // 2, w1 // 2
    rise = y1 - y0
    pts = [[s * (e0 - 4), z0, y0], [s * (e0 + 10), z0, y0], [s * (e0 + 40), z0 - 14, y0 + int(rise * 0.4)],
           [s * (e1 + 14), z1 + 10, y0 + int(rise * 0.85)], [s * (e1 + 6), z1, y1], [s * (e1 - 4), z1, y1]]
    L["roads"].append(dict(id=f"sc_city_ramp{i}", width=4, surface="cobble", graded=True, points=pts))
# the grand stair straight up the middle, cut through every retaining wall
stair = [[0, LEVELS[0][0], LEVELS[0][1]]]
for i in range(len(LEVELS) - 1):
    edge = LEVELS[i][0] - 20
    stair += [[0, edge + 9, LEVELS[i][1]], [0, edge - 9, LEVELS[i + 1][1]]]
stair.append([0, LEVELS[-1][0], LEVELS[-1][1]])
L["roads"].append(dict(id="sc_grand_stair", width=7, surface="cobble", graded=True, points=stair))

# ------------------------------------------------------------------------------------------- meadow village
VX, VZ = -170, 320
L["flats"].append(dict(id="sc_village", x=VX, z=VZ, r=72, blend=26, y=0, town=True))
L["roads"] += [
    dict(id="sc_village_main", width=6, surface="cobble", graded=True, points=[[VX - 64, VZ], [VX + 64, VZ]]),
    dict(id="sc_village_cross", width=5, points=[[VX, VZ - 60], [VX, VZ + 60]]),
]
VSTYLE = [TIMBER_HOUSE, dict(wall="minecraft:oak_planks", frame="minecraft:stripped_spruce_log", roof="spruce", foundation="minecraft:cobblestone"),
          dict(wall="minecraft:birch_planks", frame="minecraft:stripped_dark_oak_log", roof="dark_oak", foundation="minecraft:cobblestone")]
vtypes = [("inn", 15, 11), ("bakery", 11, 9), ("store", 11, 9), ("house", 9, 8), ("smithy", 11, 9), ("house", 9, 7), ("hall", 17, 13), ("house", 9, 8)]
k = 0
for side, facing in ((-1, "south"), (1, "north")):
    for slot in (-50, -28, 22, 44):
        bt, bw, bd = vtypes[k % len(vtypes)]
        k += 1
        building(bt, VX + slot, VZ + side * (3 + 1 + bd / 2 + 1), bw, bd, facing, VSTYLE[k % 3], f"Willowmere {bt}",
                 floors=2 if bt == "inn" else 1, beds=2)
for slot, facing in ((-40, "east"), (40, "west")):
    for zz in (-34, 36):
        building("house", VX + (-12 if facing == "east" else 12) + (0 if facing == "east" else 0), VZ + zz, 9, 8, facing, VSTYLE[k % 3], "Willowmere house")
        k += 1
building("mill", 60, 300, 11, 11, "west", VSTYLE[1], "Willowmere Mill")
building("farmhouse", VX - 130, VZ - 70, 13, 9, "south", VSTYLE[0], "Westfield Farm", beds=2)
building("barn", VX - 150, VZ - 30, 13, 11, "east", dict(wall="minecraft:red_terracotta", frame="minecraft:stripped_oak_log", roof="dark_oak", foundation="minecraft:cobblestone"), "Westfield Barn")
building("farmhouse", VX + 140, VZ + 110, 13, 9, "west", VSTYLE[2], "Brookside Farm", beds=2)
L["features"] += [dict(id="sc_village_well", type="statue", x=VX + 10, z=VZ + 9)]
L["fields"] += [
    dict(id="sc_f1", crop="wheat", x1=VX - 140, z1=VZ - 20, x2=VX - 95, z2=VZ + 40),
    dict(id="sc_f2", crop="carrots", x1=VX - 140, z1=VZ + 50, x2=VX - 95, z2=VZ + 90),
    dict(id="sc_f3", crop="potatoes", x1=VX + 95, z1=VZ + 70, x2=VX + 130, z2=VZ + 130),
    dict(id="sc_f4", crop="beetroots", x1=VX + 95, z1=VZ + 140, x2=VX + 125, z2=VZ + 170),
    dict(id="sc_orchard", crop="orchard", x1=VX - 60, z1=VZ + 95, x2=VX + 40, z2=VZ + 140),
]

# ------------------------------------------------------------------------------------------- blossom lake town
PX, PZ = 340, 520
L["flats"].append(dict(id="sc_petalshore", x=PX, z=PZ, r=56, blend=20, y=0, town=True))
L["roads"].append(dict(id="sc_petal_street", width=5, surface="cobble", graded=True, points=[[PX - 48, PZ], [PX + 48, PZ]]))
PSTYLE = [dict(wall="minecraft:cherry_planks", frame="minecraft:stripped_cherry_log", roof="cherry", foundation="minecraft:stone_bricks"),
          dict(wall="minecraft:white_terracotta", frame="minecraft:stripped_cherry_log", roof="dark_oak", foundation="minecraft:stone_bricks")]
for j, (slot, side) in enumerate([(-34, -1), (-12, -1), (14, -1), (36, -1), (-30, 1), (-8, 1), (18, 1), (38, 1)]):
    bt, bw, bd = [("house", 9, 8), ("bakery", 11, 9), ("house", 9, 7), ("inn", 15, 11), ("store", 11, 9), ("house", 9, 8), ("house", 9, 7), ("house", 9, 8)][j]
    building(bt, PX + slot, PZ + side * (2.5 + 1 + bd / 2 + 1), bw, bd, "south" if side < 0 else "north", PSTYLE[j % 2], f"Petalshore {bt}", beds=2)
L["features"] += [dict(id="sc_petal_jetty", type="jetty", x=268, z=588, facing="south"), dict(id="sc_petal_statue", type="statue", x=PX, z=PZ + 34)]

# ------------------------------------------------------------------------------------------- badlands outpost on a mesa
MX, MZ = MESA["x"], MESA["z"]
TOP = 66 + MESA["h"] - 1
L["flats"].append(dict(id="sc_outpost_top", x=MX, z=MZ, r=42, blend=3, y=TOP, town=True))
SAND = dict(wall="minecraft:cut_red_sandstone", frame="minecraft:stripped_acacia_log", roof="mud_brick", foundation="minecraft:red_sandstone")
building("tower", MX, MZ - 18, 7, 7, "south", SAND, "Mesa Watch", floors=4)
building("store", MX - 20, MZ + 4, 11, 9, "east", SAND, "Outpost Trading Post")
building("house", MX + 20, MZ + 2, 9, 8, "west", SAND, "Outpost house")
building("smithy", MX + 4, MZ + 20, 11, 9, "north", SAND, "Outpost Forge")
L["roads"] += [
    dict(id="sc_mesa_plaza", width=5, surface="cobble", graded=True, points=[[MX - 12, MZ - 4, TOP], [MX + 12, MZ - 4, TOP]]),
    dict(id="sc_mesa_stair", width=5, surface="cobble", graded=True, points=[[MX, MZ + 126, 66], [MX, MZ + 70, 66 + 24], [MX, MZ + 36, TOP], [MX, MZ + 10, TOP]]),
]

# ------------------------------------------------------------------------------------------- monastery and observatory
MOX, MOZ = -330, -470
L["flats"].append(dict(id="sc_monastery", x=MOX, z=MOZ, r=36, blend=4, y=0, wall=True, town=True))
MONK = dict(wall="minecraft:polished_andesite", frame="minecraft:stripped_dark_oak_log", roof="deepslate", foundation="minecraft:cobbled_deepslate")
building("hall", MOX, MOZ - 12, 17, 13, "south", MONK, "Monastery Hall", floors=2)
building("tower", MOX - 24, MOZ + 6, 7, 7, "east", MONK, "Bell Tower", floors=5)
building("house", MOX + 22, MOZ + 8, 9, 8, "west", MONK, "Monastery cells", beds=2)
L["roads"].append(dict(id="sc_monastery_court", width=4, surface="cobble", graded=True, points=[[MOX - 14, MOZ + 8], [MOX + 14, MOZ + 8]]))
OX, OZ = 470, -410
L["flats"].append(dict(id="sc_observatory", x=OX, z=OZ, r=20, blend=6, y=0, wall=True, town=True))
building("tower", OX, OZ - 6, 7, 7, "south", dict(wall="minecraft:polished_blackstone_bricks", frame="minecraft:stripped_crimson_stem", roof="deepslate", foundation="minecraft:blackstone"), "Ash Observatory", floors=5)
building("relay", OX + 12, OZ + 8, 9, 7, "west", dict(wall="minecraft:polished_blackstone", frame="minecraft:stripped_dark_oak_log", roof="deepslate", foundation="minecraft:blackstone"), "Observatory workshop")

# ------------------------------------------------------------------------------------------- roads linking it all
top_city = LEVELS[-1]
L["roads"] += [
    dict(id="sc_kings_road", width=5, graded=True, points=[[0, 990, 66], [0, 860], [-40, 700], [-60, 560], [-120, 430], [VX, VZ + 62]]),
    dict(id="sc_north_road", width=5, graded=True, points=[[VX, VZ - 62], [-140, 160], [-60, 40], [-20, -40, 70], [0, -70, 72]]),
    dict(id="sc_city_gate", width=7, surface="cobble", graded=True, points=[[0, -70, 72], [0, -97, LEVELS[0][1]], [0, LEVELS[0][0], LEVELS[0][1]]]),
    dict(id="sc_lake_road", width=4, graded=True, points=[[VX + 64, VZ], [-20, 360], [120, 430], [240, 480], [PX - 50, PZ]]),
    dict(id="sc_mesa_road", width=4, graded=True, points=[[PX + 50, PZ], [460, 480], [MX, MZ + 126, 66]]),
    dict(id="sc_west_road", width=4, graded=True, bridge="rope", points=[[VX - 64, VZ], [-300, 300], [-420, 260], [-560, 300], [-620, 400]]),
    dict(id="sc_monastery_road", width=3, surface="lane", graded=True,
         points=[[-30, top_city[0], top_city[1]], [-top_city[2] // 2 - 4, top_city[0], top_city[1]], [-90, -330], [-140, -360], [-190, -350], [-240, -390], [-210, -430], [-270, -445], [MOX + 14, MOZ + 8]]),
    dict(id="sc_volcano_road", width=3, surface="lane", graded=True,
         points=[[30, top_city[0], top_city[1]], [top_city[2] // 2 + 4, top_city[0], top_city[1]], [110, -330], [200, -360], [300, -380], [380, -400], [OX - 12, OZ + 10]]),
    dict(id="sc_summit_trail", width=2, surface="lane", graded=True,
         points=[[0, top_city[0] - 12, top_city[1]], [0, top_city[0] - 24, top_city[1]], [30, -370], [-20, -400], [30, -430], [-10, -460], [20, -490], [0, -515]]),
]
L["flats"].append(dict(id="sc_summit_flat", x=0, z=-530, r=7, blend=5, y=0))
L["features"].append(dict(id="sc_summit", type="summit_cairn", x=0, z=-532))

# ------------------------------------------------------------------------------------------- features, woods
for i, (x, z, y, s) in enumerate(ISLANDS):
    L["features"].append(dict(id=f"sc_isle{i}", type="sky_island", x=x, z=z, y=y, size=s))
L["features"] += [
    dict(id="sc_arch_red1", type="rock_arch", x=760, z=420, y=66, size=34),
    dict(id="sc_arch_red2", type="rock_arch", x=500, z=180, y=66, size=24),
    dict(id="sc_arch_gorge", type="rock_arch", x=-400, z=330, y=70, size=30),
    dict(id="sc_ruin1", type="ruin", x=-440, z=-40, size=9), dict(id="sc_ruin2", type="ruin", x=220, z=150, size=7),
    dict(id="sc_ruin3", type="ruin", x=-60, z=820, size=11), dict(id="sc_ruin4", type="ruin", x=420, z=-210, size=8),
    dict(id="sc_shrine1", type="shrine", x=-640, z=-120), dict(id="sc_overlook1", type="overlook", x=-230, z=180),
    dict(id="sc_wagon1", type="wagon", x=-40, z=640), dict(id="sc_camp1", type="camp", x=700, z=600),
    dict(id="sc_mirror_jetty", type="jetty", x=-420, z=520, facing="west"),
]
L["woods"] = [
    dict(id="sc_taiga", x=-580, z=-380, r=330, density=0.5, mix=["spruce", "spruce", "mega_spruce", "pine", "mega_pine"]),
    dict(id="sc_greenwood", x=-760, z=170, r=170, density=0.45, mix=["oak", "birch", "dark_oak", "fancy_oak"]),
    dict(id="sc_birch_hill", x=150, z=210, r=90, density=0.35, mix=["super_birch", "birch"]),
    dict(id="sc_blossoms", x=240, z=600, r=210, density=0.22, mix=["cherry"]),
    dict(id="sc_south_wood", x=-260, z=860, r=120, density=0.4, mix=["oak", "dark_oak", "azalea"]),
    dict(id="sc_crown_pines", x=-120, z=-260, r=140, density=0.3, mix=["spruce", "pine"]),
]

L["anchors"] = {
    "sc_arrival": [0, 0, 940], "sc_city_gate": [0, 0, -75], "sc_citadel": [0, 0, -305], "sc_village": [VX, 0, VZ + 6],
    "sc_petalshore": [PX, 0, PZ + 6], "sc_mesa_top": [MX, 0, MZ - 2], "sc_monastery": [MOX, 0, MOZ + 8],
    "sc_observatory": [OX, 0, OZ + 12], "sc_gorge_view": [-300, 0, 60], "sc_lake_view": [-520, 0, 330],
    "sc_volcano_rim": [600, 0, -640], "sc_badlands_view": [700, 0, 520], "sc_summit_view": [0, 0, -528],
}

with open(OUT, "w") as f:
    json.dump(L, f, indent=1)
print("showcase layout:", len(L["buildings"]), "buildings,", len(L["spires"]), "spires/mesas,", len(L["roads"]), "roads ->", os.path.normpath(OUT))
