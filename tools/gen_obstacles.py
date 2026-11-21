#!/usr/bin/env python3
"""Generate a large, diverse suite of .obstacle test fixtures.

Each course is designed to be *solvable* by the bot, mirroring the patterns of
the hand-curated seed fixtures (which are left untouched). Grids are emitted with
uniform row widths so the parser's FLOOR-padding never adds unintended floor.
"""
import os

# Resolve the fixtures dir relative to this script (repo-portable).
OUT = os.path.normpath(os.path.join(
    os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "obstacles"))

# Stems of the hand-curated seeds we must NOT overwrite.
SEEDS = {
    "walk-flat", "gap-1", "gap-2", "gap-3", "stair-up", "stair-down",
    "corner-turn", "pillar-jump", "mine-through-wall", "bridge-gap", "pillar-up",
}

count = 0
written = []


def write(stem, desc, grid, *, material=None, floor_material=None, bot_tool=None,
          bot_blocks=None, ceiling=None, timeout=200, yaw=270, origin_y=None, expect=None,
          blocks=None, bot_items=None, place=None, knockback=None):
    global count
    assert stem not in SEEDS, f"would clobber seed: {stem}"
    assert "/" not in stem
    header = [f"name: {stem}", f"description: {desc}"]
    if material:       header.append(f"material: {material}")
    if floor_material: header.append(f"floor_material: {floor_material}")
    if bot_tool:       header.append(f"bot_tool: {bot_tool}")
    if bot_blocks:     header.append(f"bot_blocks: {bot_blocks}")
    if bot_items:      header.append(f"bot_items: {bot_items}")
    if blocks:         header.append(f"blocks: {blocks}")
    if place:          header.append(f"place: {place}")
    if knockback:      header.append(f"knockback: {knockback}")
    if ceiling:        header.append(f"ceiling: {ceiling}")
    if expect:         header.append(f"expect: {expect}")
    header.append(f"timeout_ticks: {timeout}")
    header.append(f"spawn_yaw: {yaw}")
    header.append(f"origin_y: {origin_y if origin_y else 120}")
    # grid: list of rows; each row a list of tokens (uniform width enforced).
    width = max(len(r) for r in grid)
    rows = []
    for r in grid:
        toks = list(r) + ["."] * (width - len(r))  # pad with floor explicitly
        rows.append(" ".join(toks))
    # sanity: exactly one S and one E
    flat = " ".join(rows).split()
    s = sum(1 for t in flat if t[0] in "Ss")
    e = sum(1 for t in flat if t[0] in "Ee")
    assert s == 1 and e == 1, f"{stem}: S={s} E={e}"
    body = "\n".join(header) + "\n---\n" + "\n".join(rows) + "\n"
    with open(os.path.join(OUT, stem + ".obstacle"), "w") as f:
        f.write(body)
    count += 1
    written.append(stem)


def row_walk(n):
    return ["S"] + ["."] * (n - 2) + ["E"]


# =====================================================================
# A. Flat walks (horizontal, west->east)
# =====================================================================
for L in (2, 3, 4, 6, 8, 10, 12, 16, 24, 30):
    write(f"walk-{L:02d}", f"{L}-cell straight walk on flat ground.",
          [row_walk(L)], timeout=max(200, L * 12), yaw=270)

# B. Vertical walks (north->south)
for L in (3, 5, 8, 12):
    grid = [["S"]] + [["."] for _ in range(L - 2)] + [["E"]]
    write(f"walkv-{L:02d}", f"{L}-cell straight walk heading south.",
          grid, timeout=max(200, L * 12), yaw=0)

# C. Diagonal open-field walks (S corner -> E corner)
for R in (3, 4, 5, 6, 8):
    grid = []
    for r in range(R):
        rowt = ["."] * R
        if r == 0:
            rowt[0] = "S"
        if r == R - 1:
            rowt[R - 1] = "E"
        grid.append(rowt)
    write(f"diag-{R:02d}", f"{R}x{R} open field; optimal path is diagonal.",
          grid, timeout=max(200, R * 18), yaw=315)

# =====================================================================
# D. Jumps / gaps (pure sprint-jump, no blocks)
# =====================================================================
def gap_course(widths):
    """Chained gaps with generous run-ups, landing platforms, and a run-out past
    the goal. Tight single-cell landings were flaky: the bot overshoots or
    short-jumps non-deterministically and falls into the void. Giving every gap a
    3-cell platform (room to rebuild sprint speed) and centring the goal inside a
    5-cell landing platform (so drift/overshoot stays on floor) makes them
    reliable."""
    row = ["S", ".", ".", "."]                              # 3-cell run-up
    for w in widths[:-1]:
        row += ["_"] * w + [".", ".", "."]                  # 3-cell mid platform
    row += ["_"] * widths[-1] + [".", ".", "E", ".", "."]   # landing platform, goal centred
    return row

write("jump-1-double", "Two 1-block gaps with run-ups.", [gap_course([1, 1])], timeout=340)
write("jump-1-triple", "Three 1-block gaps with run-ups.", [gap_course([1, 1, 1])], timeout=380)
write("jump-1-quad", "Four 1-block gaps with run-ups.", [gap_course([1, 1, 1, 1])], timeout=440)
write("jump-1-series5", "Five 1-block gaps with run-ups.", [gap_course([1, 1, 1, 1, 1])], timeout=480)
write("jump-2-double", "Two 2-block gaps with run-ups.", [gap_course([2, 2])], timeout=400)
write("jump-3-double", "Two 3-block (max sprint) gaps with run-ups.", [gap_course([3, 3])], timeout=440)
# NOTE: jump-stagger (mixed 1,2,1 gaps) is omitted — alternating gap widths defeat
# a fixed run-up cadence and it stays flaky even with generous platforms.
write("jump-2-3", "A 2-gap then a 3-gap with run-ups.", [gap_course([2, 3])], timeout=420)
write("jump-long-runway", "Long runway into a 3-block gap, with a landing platform.",
      [["S", ".", ".", ".", ".", "_", "_", "_", ".", ".", "E", ".", "."]], timeout=300)
# vertical jumps (landing platform before the goal, run-out after)
write("jumpv-1", "One-block gap heading south.",
      [["S"], ["."], ["."], ["_"], ["."], ["."], ["E"], ["."]], timeout=280, yaw=0)
write("jumpv-2", "Two-block gap heading south.",
      [["S"], ["."], ["."], ["_"], ["_"], ["."], ["."], ["E"], ["."]], timeout=300, yaw=0)

# =====================================================================
# E / F. Staircases up & down (1 step per cell)
# =====================================================================
for H in range(1, 9):
    up = ["S"] + [str(i) for i in range(1, H + 1)] + ["E" + str(H)]
    write(f"stair-up-{H:02d}", f"Ascend {H} blocks, one step per cell.",
          [up], timeout=150 + H * 45, yaw=270)
    dn = ["S" + str(H)] + [str(i) for i in range(H - 1, 0, -1)] + ["E"]
    write(f"stair-down-{H:02d}", f"Descend {H} blocks, one step per cell.",
          [dn], timeout=150 + H * 45, yaw=270)

# G. Stair combos
for H in (3, 4, 5):
    pyr = ["S"] + [str(i) for i in range(1, H + 1)] + [str(i) for i in range(H - 1, 0, -1)] + ["E"]
    write(f"pyramid-{H:02d}", f"Up to {H} then back down to ground.",
          [pyr], timeout=200 + H * 60, yaw=270)
# valley: down into a pit then back up
write("valley-03", "Descend 3 into a pit, then climb 3 back out.",
      [["S3"] + [str(i) for i in (2, 1)] + ["."] + [str(i) for i in (1, 2)] + ["E3"]],
      timeout=400, yaw=270)
# long single ascent / descent already covered; add a tall round-trip
write("ascent-06-walk", "Walk, climb 6, then a short flat top.",
      [[".", ".", "S"] + [str(i) for i in range(1, 7)] + ["6", "E6"]],
      timeout=520, yaw=270)

# =====================================================================
# H. Pillar-to-pillar jumps
# =====================================================================
def pj(h_start, gap, h_end):
    return ["S" + str(h_start)] + ["_"] * gap + ["E" + str(h_end)]

write("pj-1-1g", "Jump between two 1-tall pillars over a 1 gap.", [pj(1, 1, 1)], timeout=260)
write("pj-2-1g", "Jump between two 2-tall pillars over a 1 gap.", [pj(2, 1, 2)], timeout=260)
write("pj-2-2g", "Jump between two 2-tall pillars over a 2 gap.", [pj(2, 2, 2)], timeout=280)
write("pj-3-3g", "Run along a 2-wide 3-tall pillar and jump a 3 gap to another.",
      [["S3", "3", "_", "_", "_", "E3"]], timeout=320)
# NOTE: pillar-to-LOWER-pillar jumps (jump + descend across a gap) need FallSafe,
# which is currently broken, so they are omitted (see the drop section note).
# pillar hop series (single-cell pillars give no run-up, so keep the chain short)
write("pj-series-3", "Hop across three 2-tall pillars (gap 1 between).",
      [["S2", "_", "2", "_", "E2"]], timeout=360)

# =====================================================================
# I. Cliff drops / safe falls
# =====================================================================
# NOTE: multi-block ledge drops (descend >=2 to an adjacent column) are NOT
# included — the FallSafe move never matches today (its isFallClear scans the
# *source* column, which is always solid underfoot), so the pathfinder returns
# no_path. Multi-block descents are exercised as staircases (stair-down-*) and
# the valley course instead. Re-add true drop tests once FallSafe is fixed.
write("drop-1", "Step off a standalone 1-block ledge, then walk to the goal.",
      [["S1", ".", ".", ".", "E", "."]], timeout=260)

# =====================================================================
# J. Corners & mazes (locomotion only; '_' = wall/pit)
# =====================================================================
mazes = {
    "turn-se": ("Turn south then continue east.",
                [["S", ".", "."],
                 ["_", "_", "."],
                 ["_", "_", "E"]], 270),
    "turn-sw": ("Head east, then turn south-west to the goal.",
                [["S", ".", ".", "."],
                 [".", "_", "_", "_"],
                 ["E", "_", "_", "_"]], 270),
    "u-turn": ("A U-shaped corridor.",
               [["S", "_", "E"],
                [".", "_", "."],
                [".", ".", "."]], 0),
    "s-curve": ("An S-shaped corridor.",
                [["S", ".", "."],
                 ["_", "_", "."],
                 [".", ".", "."],
                 [".", "_", "_"],
                 [".", ".", "E"]], 270),
    "zigzag-1": ("Zig-zag corridor.",
                 [["S", ".", "_", "_", "_"],
                  ["_", ".", "_", "_", "_"],
                  ["_", ".", ".", ".", "_"],
                  ["_", "_", "_", ".", "_"],
                  ["_", "_", "_", ".", "E"]], 0),
    "spiral-in": ("Spiral inward to the centre.",
                  [["S", ".", ".", ".", "."],
                   ["_", "_", "_", "_", "."],
                   [".", ".", ".", "_", "."],
                   [".", "_", "E", "_", "."],
                   [".", ".", ".", ".", "."]], 270),
    "plus": ("Cross/plus-shaped arena.",
             [["_", "_", "S", "_", "_"],
              ["_", "_", ".", "_", "_"],
              [".", ".", ".", ".", "."],
              ["_", "_", ".", "_", "_"],
              ["_", "_", "E", "_", "_"]], 0),
    "dogleg": ("Two right-angle turns (dog-leg).",
               [["S", ".", ".", "_", "_"],
                ["_", "_", ".", "_", "_"],
                ["_", "_", ".", ".", "E"]], 270),
    "comb": ("Comb / boustrophedon sweep.",
             [["S", ".", ".", ".", "."],
              ["_", "_", "_", "_", "."],
              [".", ".", ".", ".", "."],
              [".", "_", "_", "_", "_"],
              [".", ".", ".", ".", "E"]], 270),
    "comb-wide": ("Wider boustrophedon sweep.",
                  [["S", ".", ".", ".", ".", ".", "."],
                   ["_", "_", "_", "_", "_", "_", "."],
                   [".", ".", ".", ".", ".", ".", "."],
                   [".", "_", "_", "_", "_", "_", "_"],
                   [".", ".", ".", ".", ".", ".", "E"]], 270),
    "switchback": ("Three-row switchback.",
                   [["S", ".", "."],
                    ["_", "_", "."],
                    [".", ".", "."],
                    [".", "_", "_"],
                    [".", ".", "."],
                    ["_", "_", "."],
                    [".", ".", "."],
                    [".", "_", "_"],
                    ["E", ".", "."]], 270),
    "narrow-bend": ("Narrow single-width bend.",
                    [["S", ".", ".", ".", "."],
                     ["_", "_", "_", "_", "."],
                     ["_", "_", "_", "_", "."],
                     ["E", ".", ".", ".", "."]], 270),
}
for stem, (desc, grid, yaw) in mazes.items():
    write(f"maze-{stem}", desc, grid, timeout=400, yaw=yaw)

# =====================================================================
# K. Mining (mine-through). Enclosed by a ceiling to force tunnelling.
# =====================================================================
def mine_row(thickness):
    return ["S", "."] + ["2"] * thickness + [".", "E"]

# wall thickness (stone, diamond pickaxe)
for T in (2, 3, 4):
    write(f"mine-thick-{T}", f"Mine straight through a {T}-deep stone wall.",
          [mine_row(T)], material="stone", floor_material="gold_block",
          bot_tool="diamond_pickaxe", ceiling=3, timeout=200 + T * 60, yaw=270)
write("mine-tunnel-6", "Bore a 6-deep stone tunnel.",
      [mine_row(6)], material="stone", floor_material="gold_block",
      bot_tool="diamond_pickaxe", ceiling=3, timeout=600, yaw=270)

# tool tiers (single stone wall)
for tool in ("wooden_pickaxe", "stone_pickaxe", "iron_pickaxe", "golden_pickaxe", "netherite_pickaxe"):
    tier = tool.split("_")[0]
    write(f"mine-tool-{tier}", f"Mine a stone wall with a {tier} pickaxe.",
          [mine_row(1)], material="stone", floor_material="gold_block",
          bot_tool=tool, ceiling=3, timeout=400, yaw=270)

# material variants (matched tool)
write("mine-dirt", "Dig through a packed dirt wall with a shovel.",
      [mine_row(2)], material="dirt", floor_material="gold_block",
      bot_tool="diamond_shovel", ceiling=3, timeout=400, yaw=270)
write("mine-planks", "Chop through a plank wall with an axe.",
      [mine_row(2)], material="oak_planks", floor_material="gold_block",
      bot_tool="diamond_axe", ceiling=3, timeout=400, yaw=270)
write("mine-cobble", "Mine through a cobblestone wall.",
      [mine_row(2)], material="cobblestone", floor_material="gold_block",
      bot_tool="diamond_pickaxe", ceiling=3, timeout=400, yaw=270)
write("mine-deepslate", "Mine through a tougher deepslate wall.",
      [mine_row(1)], material="deepslate", floor_material="gold_block",
      bot_tool="diamond_pickaxe", ceiling=3, timeout=500, yaw=270)
write("mine-gap-mine", "Mine a wall, cross floor, mine a second wall.",
      [["S", ".", "2", ".", ".", "2", ".", "E"]], material="stone",
      floor_material="gold_block", bot_tool="diamond_pickaxe", ceiling=3,
      timeout=500, yaw=270)

# =====================================================================
# L. Bridging (place blocks across wide voids).
# =====================================================================
def bridge_row(width):
    return ["S", "."] + ["_"] * width + [".", "E"]

for W in (4, 6, 7, 8):
    write(f"bridge-{W}", f"Bridge across a {W}-wide void gap.",
          [bridge_row(W)], bot_blocks="cobblestone 256", timeout=200 + W * 70, yaw=270)
write("bridge-10", "Bridge across a 10-wide void gap.",
      [bridge_row(10)], bot_blocks="cobblestone 256", timeout=900, yaw=270)
write("bridge-dirt", "Bridge a 5-wide gap using dirt.",
      [bridge_row(5)], bot_blocks="dirt 256", timeout=600, yaw=270)
write("bridge-planks", "Bridge a 5-wide gap using planks.",
      [bridge_row(5)], bot_blocks="oak_planks 256", timeout=600, yaw=270)
write("bridge-double", "Bridge two voids separated by a landing.",
      [["S", ".", "_", "_", "_", "_", ".", "_", "_", "_", "_", ".", "E"]],
      bot_blocks="cobblestone 256", timeout=900, yaw=270)
write("bridge-vert", "Bridge a wide void heading south.",
      [["S"], ["."], ["_"], ["_"], ["_"], ["_"], ["."], ["E"]],
      bot_blocks="cobblestone 256", timeout=600, yaw=0)

# =====================================================================
# M. Pillar-up (place blocks under the feet to reach an elevated goal).
# =====================================================================
for H in (1, 3, 4):
    write(f"pillarup-{H}", f"Pillar straight up {H} blocks to the goal.",
          [["S", "E" + str(H)]], bot_blocks="cobblestone 64", timeout=200 + H * 90, yaw=270)
write("pillarup-far", "Walk, then pillar up 3 to reach the goal.",
      [["S", ".", ".", "E3"]], bot_blocks="cobblestone 64", timeout=500, yaw=270)

# =====================================================================
# N. Combined gauntlets
# =====================================================================
write("combo-stair-gap", "Climb 3, get a run-up, sprint a 2-gap onto a 3-ledge.",
      [["S", "1", "2", "3", "3", "_", "_", "3", "E3", "3"]], timeout=420, yaw=270)
# NOTE: combo-up-gap-down (climb + mid-air gap + descend) is omitted — the mid-air
# height jump is exactly the non-deterministic case the bot can't yet clear reliably.
write("combo-walk-jump-walk", "Walk, clear a 3-gap onto a platform, walk to the goal.",
      [["S", ".", ".", ".", "_", "_", "_", ".", ".", "E", ".", "."]], timeout=350, yaw=270)
write("combo-bridge-stair", "Bridge a 4-gap then climb a short staircase.",
      [["S", ".", "_", "_", "_", "_", ".", "1", "2", "E2"]],
      bot_blocks="cobblestone 256", timeout=700, yaw=270)
write("combo-mine-walk", "Mine through a wall then walk to the goal.",
      [["S", ".", "2", ".", ".", ".", "E"]], material="stone",
      floor_material="gold_block", bot_tool="diamond_pickaxe", ceiling=3,
      timeout=500, yaw=270)

# =====================================================================
# O. Extra deterministic coverage (no jumps) for headroom above 100.
# =====================================================================
for L in (5, 14, 20):
    write(f"walk-{L:02d}", f"{L}-cell straight walk on flat ground.",
          [row_walk(L)], timeout=max(200, L * 12), yaw=270)
for R in (7, 10):
    grid = []
    for r in range(R):
        rowt = ["."] * R
        if r == 0: rowt[0] = "S"
        if r == R - 1: rowt[R - 1] = "E"
        grid.append(rowt)
    write(f"diag-{R:02d}", f"{R}x{R} open field; optimal path is diagonal.",
          grid, timeout=max(200, R * 18), yaw=315)
for H in (9, 10):
    up = ["S"] + [str(i) for i in range(1, H + 1)] + ["E" + str(H)]
    write(f"stair-up-{H:02d}", f"Ascend {H} blocks, one step per cell.",
          [up], timeout=150 + H * 45, yaw=270)
    dn = ["S" + str(H)] + [str(i) for i in range(H - 1, 0, -1)] + ["E"]
    write(f"stair-down-{H:02d}", f"Descend {H} blocks, one step per cell.",
          [dn], timeout=150 + H * 45, yaw=270)
write("mine-thick-5", "Mine straight through a 5-deep stone wall.",
      [mine_row(5)], material="stone", floor_material="gold_block",
      bot_tool="diamond_pickaxe", ceiling=3, timeout=560, yaw=270)
write("mine-tunnel-4", "Bore a 4-deep stone tunnel.",
      [mine_row(4)], material="stone", floor_material="gold_block",
      bot_tool="diamond_pickaxe", ceiling=3, timeout=500, yaw=270)
for W in (5, 9):
    write(f"bridge-{W}", f"Bridge across a {W}-wide void gap.",
          [bridge_row(W)], bot_blocks="cobblestone 256", timeout=200 + W * 70, yaw=270)
for H in (2, 5):
    write(f"pillarup-{H}", f"Pillar straight up {H} blocks to the goal.",
          [["S", "E" + str(H)]], bot_blocks="cobblestone 64", timeout=200 + H * 90, yaw=270)
write("maze-serpentine", "Long boustrophedon sweep across seven columns.",
      [["S", ".", ".", ".", ".", ".", "."],
       ["_", "_", "_", "_", "_", "_", "."],
       [".", ".", ".", ".", ".", ".", "."],
       [".", "_", "_", "_", "_", "_", "_"],
       [".", ".", ".", ".", ".", ".", "E"]], timeout=450, yaw=270)
write("maze-zigzag-2", "Tall zig-zag descent-free corridor.",
      [["S", ".", ".", ".", "."],
       [".", "_", "_", "_", "_"],
       [".", ".", ".", ".", "."],
       ["_", "_", "_", "_", "."],
       [".", ".", ".", ".", "."],
       [".", "_", "_", "_", "_"],
       [".", ".", ".", ".", "E"]], timeout=450, yaw=0)

# =====================================================================
# P. Drops, descending jumps and mixed chains (added once FallSafe scanned
#    the landing column; these are the "known gaps" the earlier suite skipped).
# =====================================================================
# Multi-block ledge drops onto an adjacent column (FallSafe: 2-3 blocks).
for H in (2, 3):
    write(f"drop-{H}", f"Step off a {H}-tall ledge onto the floor, then walk to the goal.",
          [["S" + str(H), ".", ".", "E", "."]], timeout=260)
# Walk along a raised ledge, then drop.
write("ledge-drop-2", "Walk a 2-high ledge, drop 2 to the floor, reach the goal.",
      [["S2", "2", "2", "2", ".", ".", "E", "."]], timeout=320)
# Drop heading south (different axis).
write("dropv-2", "Drop 2 heading south.",
      [["S2"], ["."], ["."], ["E"], ["."]], timeout=260, yaw=0)
# Diagonal drop: ledge corner onto open floor.
write("drop-diag-2", "Drop 2 off a ledge corner diagonally onto open floor.",
      [["S2", "2", "_"],
       ["2", ".", "."],
       ["_", ".", "E"]], timeout=300, yaw=315)
# Unsafe (damaging) falls: 4 and 5 blocks. allow_unsafe_falls is on by default.
for H in (4, 5):
    write(f"drop-{H}", f"Drop {H} blocks (takes damage) onto the floor, then walk to the goal.",
          [["S" + str(H), ".", ".", "E", "."]], timeout=300)
# Descending sprint-jump: pillar to a lower pillar across a gap (dy=-1).
write("pj-2-1g-down", "Jump from a 2-tall pillar across a 1 gap to a 1-tall pillar.",
      [["S2", "_", "E1", "1"]], timeout=280)
write("pj-3-2g-down", "Jump from a 3-tall pillar across a 2 gap down to a 2-tall landing.",
      [["S3", "_", "_", "E2", "2"]], timeout=300)
# Ascending sprint-jump: 1-gap up onto a 1-higher ledge with a run-up.
write("pj-up-1", "Sprint-jump a 1 gap up onto a ledge one block higher.",
      [["S", ".", ".", "_", "E1", "1"]], timeout=300)
write("pj-up-2g", "Sprint-jump a 2 gap up onto a ledge one block higher.",
      [["S", ".", ".", ".", "_", "_", "E1", "1"]], timeout=320)
# Mixed-width gap chains (previously too flaky to include).
write("jump-stagger", "Alternating 1,2,1 gaps with platforms.",
      [gap_course([1, 2, 1])], timeout=420)
write("jump-3-2-1", "Descending gap widths 3,2,1 with platforms.",
      [gap_course([3, 2, 1])], timeout=440)
# Climb, sprint a gap, descend (previously omitted).
write("combo-up-gap-down", "Climb 2, sprint a 2-gap at height, descend to the goal.",
      [["S", "1", "2", "2", "2", "_", "_", "2", "2", "1", "E"]], timeout=460)
# Tight landings: single-cell platforms between gaps (no run-up to rebuild).
write("jump-1-tight", "Three 1-gaps separated by single-cell platforms.",
      [["S", ".", ".", "_", ".", "_", ".", "_", "E", "."]], timeout=380)
write("jump-2-tight", "Two 2-gaps separated by a single-cell platform.",
      [["S", ".", ".", ".", "_", "_", ".", "_", "_", "E", "."]], timeout=400)
# Jump then immediate turn: landing platform leads 90 degrees away.
write("jump-then-turn", "Sprint a 2-gap, then turn south to the goal.",
      [["S", ".", ".", "_", "_", ".", "."],
       ["_", "_", "_", "_", "_", "_", "."],
       ["_", "_", "_", "_", "_", "_", "E"]], timeout=400)

# =====================================================================
# Q. Impossible under vanilla physics: the planner must refuse, not suicide.
# =====================================================================
# A 3 gap from a lone 1x1 pillar is possible with vanilla inputs: four
# sprint ticks on the pillar, then take off hanging over its edge.
write("pj-3g-standing", "3 gap from a lone 1x1 pillar (no run-up): sprint to the edge and jump.",
      [["S3", "_", "_", "_", "E3"]], timeout=120)
write("impossible-pj-4g-standing", "4 gap from a lone 1x1 pillar (no run-up): impossible.",
      [["S3", "_", "_", "_", "_", "E3"]], timeout=120, expect="no_path")
write("impossible-gap-5", "5-block gap: beyond any sprint-jump.",
      [["S", ".", ".", ".", "_", "_", "_", "_", "_", "E"]], timeout=120, expect="no_path")
write("impossible-up-2", "Ledge 2 higher across a 1 gap: beyond jump height.",
      [["S", ".", ".", "_", "E2"]], timeout=120, expect="no_path")

# =====================================================================
# R. Angled parkour: landings off the X/Z axes, like scattered blocks or
#    trees in real terrain. Pillars over void, so a missed jump fails.
# =====================================================================
def angled(dx, dz, runway=3, dy=0, h=1):
    """Runway of `runway` cells at height h along row 0, then a lone landing
    pillar at offset (dx, dz) from the runway's last cell, dy higher."""
    cols = runway + dx
    rows = dz + 1
    grid = [["_"] * cols for _ in range(rows)]
    for c in range(runway):
        grid[0][c] = str(h)
    grid[0][0] = f"S{h}"
    grid[dz][runway - 1 + dx] = f"E{h + dy}"
    return grid

for dx, dz, run in ((2, 1, 3), (3, 1, 3), (2, 2, 3), (3, 2, 3), (4, 1, 4), (3, 3, 4)):
    write(f"pk-angle-{dx}-{dz}", f"Sprint-jump to a lone pillar offset ({dx},{dz}).",
          angled(dx, dz, run), timeout=200)
write("pk-angle-up-2-1", "Jump to a pillar offset (2,1) and one block higher.",
      angled(2, 1, 3, dy=1), timeout=200)
write("pk-angle-up-3-1", "Jump to a pillar offset (3,1) and one block higher.",
      angled(3, 1, 3, dy=1), timeout=200)
write("pk-angle-down-4-1", "Jump to a pillar offset (4,1) and one block lower.",
      angled(4, 1, 4, dy=-1, h=2), timeout=200)
write("pk-angle-down2-3-2", "Jump to a pillar offset (3,2) and two blocks lower.",
      angled(3, 2, 3, dy=-2, h=3), timeout=200)

# Lone 1x1 pillars zig-zagging off-axis: no run-up anywhere.
write("pk-zigzag", "Zig-zag across lone 1x1 pillars at (2,1)/(2,-1) offsets.",
      [["S1", "_", "_", "_", "1", "_", "_", "_", "1", "_"],
       ["_", "_", "1", "_", "_", "_", "1", "_", "_", "E1"]], timeout=300)
# Rising scattered blocks, like hand-placed logs up to a ledge.
write("pk-scatter-up", "Climb scattered lone blocks rising one at a time at odd angles.",
      [["S", ".", ".", ".", ".", ".", "."],
       [".", ".", ".", ".", ".", ".", "."],
       [".", "1", ".", ".", ".", ".", "."],
       [".", ".", ".", "2", ".", ".", "."],
       [".", ".", ".", ".", ".", ".", "."],
       [".", ".", "3", ".", ".", ".", "."],
       [".", ".", ".", ".", "4", ".", "."],
       [".", ".", ".", ".", ".", ".", "E5"]], timeout=400)

# =====================================================================
# S. Trees: climb a parkour line of logs up into an oak canopy.
# =====================================================================
leaf = "oak_leaves[persistent=true]"
canopy = []
for x in range(4, 7):
    for z in range(4, 7):
        if (x, z) not in ((5, 5), (4, 5)):
            canopy.append(f"{x},5,{z}={leaf}")
canopy.append(f"5,6,5={leaf}")
write("tree-canopy", "Parkour up scattered oak logs onto the leaves of a tree.",
      [["S", ".", ".", ".", ".", ".", ".", "."],
       [".", ".", ".", "2*l", ".", ".", ".", "."],
       [".", "l", ".", ".", ".", ".", ".", "."],
       [".", ".", ".", "3*l", ".", ".", ".", "."],
       [".", ".", ".", ".", ".", ".", ".", "."],
       [".", ".", "4*l", ".", "E4f", "5*l", ".", "."],
       [".", ".", ".", ".", ".", ".", ".", "."],
       [".", ".", ".", ".", ".", ".", ".", "."]],
      blocks=f"l=oak_log f={leaf}", place=" ".join(canopy), timeout=500)
write("tree-leaf-hop", "Hop across the tops of three separate leaf canopies.",
      [["S2", "_", "2f", "_", "_", "2f", "_", "E2f"]],
      blocks=f"f={leaf}", timeout=300)

# =====================================================================
# T. Water: shallow wading, stepping out, swimming across and climbing out.
# =====================================================================
W = "w=water"
write("water-shallow-walk", "Wade through a stretch of one-deep water.",
      [["S", ".", "w", "w", "w", "w", ".", "E"]], blocks=W, timeout=240)
write("water-step-out", "Start in one-deep water and step up onto a bank.",
      [["Sw", "w", "w", "1", "E1"]], blocks=W, timeout=240)
write("water-step-out-2", "Wade, then climb two one-block steps out of shallow water.",
      [["S", "w", "w", "w", "1", "2", "E2"]], blocks=W, timeout=300)
def pond(width, bank=3, depth=3):
    # Side walls two above the banks, so swimming is the only way across.
    edge = [str(bank + 2)] * (width + 4)
    mid = [f"S{bank}", str(bank)] + [f"{depth}*w"] * width + [str(bank), f"E{bank}"]
    return [edge, mid, edge]
write("water-swim-6", "Swim across a three-deep pond and climb out.",
      pond(6), blocks=W, timeout=400)
write("water-swim-14", "Swim across a long three-deep pond and climb out.",
      pond(14), blocks=W, timeout=700)
# A bank a full block above the water surface is out of reach: the climb-out
# boost peaks ~0.1 short (measured). The planner must not swim into the trap.
write("impossible-water-ledge", "Banks a block above the water: no way out, refuse.",
      [["6"] * 10,
       ["S4", "4"] + ["3*w"] * 6 + ["4", "E4"],
       ["6"] * 10], blocks=W, timeout=120, expect="no_path")
write("water-dive-in", "Drop ten blocks into a deep pool, then swim out.",
      [["_", "5", "5", "5", "5"],
       ["S12", "3*w", "3*w", "3", "E3"],
       ["_", "5", "5", "5", "5"]], blocks=W, timeout=400)
write("water-dive-tall", "Drop thirty blocks into a one-deep puddle: water stops all fall damage.",
      [["_", "_", "_", "_"],
       ["S30", "w", ".", "E"],
       ["_", "_", "_", "_"]], blocks=W, timeout=400)

# =====================================================================
# U. Water-bucket clutch: survive a lethal drop by placing water below.
# =====================================================================
write("clutch-mlg-25", "Drop 25 blocks and water-bucket clutch the landing.",
      [["S25", ".", ".", "E"]], bot_items="water_bucket", timeout=300)
write("clutch-mlg-40", "Drop 40 blocks and water-bucket clutch the landing.",
      [["S40", ".", ".", "E"]], bot_items="water_bucket", timeout=400)
write("impossible-clutch-no-bucket", "Lethal 25-block drop with no bucket: refuse.",
      [["S25", ".", ".", "E"]], timeout=120, expect="no_path")

# =====================================================================
# V. Speedbridging: cross wide voids fast (timeouts demand > 3 b/s).
# =====================================================================
for n in (10, 20, 30):
    write(f"speedbridge-{n}", f"Speedbridge across a {n}-block void.",
          [["S", "."] + ["_"] * n + [".", "E"]],
          bot_blocks="COBBLESTONE 128", timeout=60 + n * 7)
write("speedbridge-turn", "Speedbridge east, then south across void.",
      [["S", "."] + ["_"] * 10,
       ["_"] * 11 + ["_"],
       ["_"] * 11 + ["_"],
       ["_"] * 11 + ["_"],
       ["_"] * 11 + ["_"],
       ["_"] * 11 + ["E"]],
      bot_blocks="COBBLESTONE 128", timeout=260)

# =====================================================================
# W. Parkour gauntlet: classic course jumps back to back (MCPK-style
#    1b/2b/3b/4b gaps, +1 ascents, -1 drops, angled 2+1 and 3+1).
# =====================================================================
def gauntlet(segments, start_run=3):
    """segments: (gap, dy, run, dz): clear `gap` void cells along x, land
    `dz` rows over and `dy` higher, then a platform `run` cells long."""
    cells = {(c, 0): 1 for c in range(start_run)}
    col, row, h = start_run - 1, 0, 1
    for gap, dy, run, dz in segments:
        col += gap + 1
        row += dz
        h += dy
        for i in range(run):
            cells[(col + i, row)] = h
        col += run - 1
    width = col + 1
    rows = max(r for _, r in cells) + 1
    grid = [["_"] * width for _ in range(rows)]
    for (c, r), hh in cells.items():
        grid[r][c] = str(hh)
    grid[0][0] = "S" + grid[0][0]
    grid[row][col] = "E" + grid[row][col]
    return grid

write("parkour-gauntlet", "Back-to-back course jumps: 1-4 block gaps, ascents, drops, angled.",
      gauntlet([(1, 0, 2, 0), (2, 0, 2, 0), (3, 0, 3, 0), (2, 1, 2, 0), (1, 1, 2, 0),
                (3, -1, 2, 0), (1, 0, 2, 1), (2, 0, 3, 1), (1, 0, 1, 0), (2, 0, 1, 0)]),
      timeout=900)
write("parkour-gauntlet-lone", "Course jumps between lone 1x1 blocks only (no run-up).",
      gauntlet([(1, 0, 1, 0), (2, 0, 1, 0), (1, 1, 1, 0), (1, 0, 1, 1), (2, 0, 1, 1),
                (2, -1, 1, 0), (1, 1, 1, 0), (3, 0, 1, 0)], start_run=1),
      timeout=700)

# =====================================================================
# X. Reflexes: an unplanned shove off an edge mid-walk. The bot must save
#    itself (block clutch over void, bucket clutch over a long drop).
# =====================================================================
write("reflex-block-clutch", "Knocked off a 1-wide walkway over void: clutch a block against it.",
      [["_"] * 12,
       ["S"] + ["."] * 10 + ["E"],
       ["_"] * 12],
      bot_blocks="COBBLESTONE 64", knockback="25 0 0.36 0.45", timeout=300)
write("reflex-water-clutch", "Knocked off a walkway 24 up: bucket-clutch, then pillar back up.",
      [["."] * 12] * 5 +
      [["S24"] + ["24"] * 10 + ["E24"]] +
      [["."] * 12] * 5,
      bot_items="water_bucket", bot_blocks="COBBLESTONE 64", knockback="25 0 0.36 0.45", timeout=800)

print(f"Generated {count} obstacle files.")
print("Stems:", ", ".join(sorted(written)))
