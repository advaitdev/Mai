#!/usr/bin/env python3
"""Checks per-tick logs against vanilla player movement, tick by tick.

Every obstacle, chase and course log (bot or human) records, per tick, the
position after the tick and the keys, sprint state and yaw it ticked with.
Vanilla movement is a closed formula over exactly those (LocalPlayer.aiStep
-> LivingEntity.travel), so each tick's horizontal displacement can be
predicted from the previous tick's and compared with what happened:

    d[t] = d[t-1] * drag + key acceleration(keys, yaw, sprint, ground) [+ 0.2 sprint-jump boost]

with drag = 0.91 in the air, slipperiness * 0.91 on the ground. Vertically,
an airborne tick is (d[t-1] - 0.08) * 0.98 and a jump is 0.42.

A tick that moved *less* than predicted hit something (a wall, a step, a
landing) and is not a violation. A tick that moved *further* than the keys
allow is: nothing in vanilla adds horizontal speed except knockback, water
current, ice (slipperiness) and pistons. The script counts those ("excess")
and prints the worst. The same script run over a person's logs (a real
client) gives the baseline to compare a bot's against.

Usage: physics_check.py [--tolerance 0.004] [--worst N] <log>...
Exit status 1 if any log has excess ticks beyond what `--allow` permits (default 0).
"""
import argparse
import math
import re
import sys

TICK = re.compile(r'^t=(\d+) x=(\S+) y=(\S+) z=(\S+) .*?onGround=(\w+) .*?keys=(\S+) yaw=(\S+) pitch=(\S+)(.*)')
SLIP = re.compile(r'slip=(\S+)')
EVENT = re.compile(r'^\s+\[(\d+)\]\s+(.*)')

INPUT = 0.98          # LocalPlayer.modifyInput
AI_STEP = 0.98        # LivingEntity.aiStep scales xxa/zza again
SNEAK = 0.3
WALK = 0.1
SPRINT = 1.3
AIR = 0.02
AIR_SPRINT = 0.025999999
JUMP_BOOST = 0.2
AIR_DRAG = 0.91
SLIP_DEFAULT = 0.6   # logs written before `slip=` was recorded


def key_input(keys):
    """(left, forward) after the client's input transform and aiStep scaling."""
    parts = keys.split('+')
    wasd = parts[0]
    f = (1 if 'W' in wasd else 0) - (1 if 'S' in wasd else 0)
    left = (1 if 'A' in wasd else 0) - (1 if 'D' in wasd else 0)
    x, z = float(left), float(f)
    n = math.hypot(x, z)
    if n == 0:
        return 0.0, 0.0
    x, z = x / n * INPUT, z / n * INPUT
    if 'sneak' in parts:
        x, z = x * SNEAK, z * SNEAK
    # modifyInputSpeedForSquareMovement
    length = math.hypot(x, z)
    dx, dz = x / length, z / length
    ax, az = abs(dx), abs(dz)
    tan = ax / az if az > ax else az / ax
    mod = min(length * math.sqrt(1 + tan * tan), 1.0)
    return dx * mod * AI_STEP, dz * mod * AI_STEP


def accel(keys, yaw, speed):
    x, z = key_input(keys)
    n2 = x * x + z * z
    if n2 < 1e-7:
        return 0.0, 0.0
    if n2 > 1:
        n = math.sqrt(n2)
        x, z = x / n, z / n
    x, z = x * speed, z * speed
    r = math.radians(yaw)
    s, c = math.sin(r), math.cos(r)
    return x * c - z * s, z * c + x * s


def axis_over(moved, predicted):
    """How far past the prediction one axis went (0 if it went no further, in the same direction)."""
    if predicted >= 0:
        return max(moved - predicted, -moved)
    return max(predicted - moved, moved)


def check(path, tol):
    rows, events = [], {}
    for line in open(path, encoding='utf-8', errors='replace'):
        m = TICK.match(line)
        if m:
            rest = m.group(9)
            slip = SLIP.search(rest)
            rows.append((int(m.group(1)), float(m.group(2)), float(m.group(3)), float(m.group(4)),
                         m.group(5) == 'true', m.group(6), float(m.group(7)),
                         float(slip.group(1)) if slip else SLIP_DEFAULT, ' in=' in rest,
                         ' hc=-' not in rest))
            continue
        m = EVENT.match(line)
        if m:
            events.setdefault(int(m.group(1)), []).append(m.group(2))
    stats = dict(ticks=0, exact=0, slower=0, excess=0, vertical_bad=0, vertical=0, skipped=0, worst=[])
    for i in range(2, len(rows)):
        a, b, c = rows[i - 2], rows[i - 1], rows[i]
        if c[0] != b[0] + 1 or b[0] != a[0] + 1:
            continue
        # Water and ladders move by other rules (and the tick after leaving them starts from their speed).
        if a[8] or b[8] or c[8]:
            stats['skipped'] += 1
            continue
        # Displacements of the previous tick (b) and this one (c).
        pdx, pdy, pdz = b[1] - a[1], b[2] - a[2], b[3] - a[3]
        dx, dy, dz = c[1] - b[1], c[2] - b[2], c[3] - b[3]
        ground_prev, ground_now = a[4], b[4]   # on the ground when the previous / this tick began
        keys, yaw = c[5], c[6]
        parts = keys.split('+')
        sprint = 'sprint' in parts
        drag = (a[7] if ground_prev else 1.0) * AIR_DRAG
        vx, vz = pdx * drag, pdz * drag
        if vx * vx + vz * vz < 9e-6:
            vx = vz = 0.0
        # Jump held on the ground: a jump unless the 10-tick repeat delay is running (not in the log),
        # so both readings are tried. Under a low ceiling the body barely rises but still gets the boost.
        may_jump = ground_now and 'jump' in parts
        # The logged sprint flag is read after the tick; a sprint that started or ended on it reads either way.
        exact = False
        over = None   # smallest overshoot of any reading, and that reading's numbers
        for sp in (sprint, not sprint):
            for jumped in ((True, False) if may_jump else (False,)):
                speed = (WALK * (SPRINT if sp else 1.0) * (0.21600002 / b[7] ** 3)) if ground_now else (AIR_SPRINT if sp else AIR)
                ax, az = accel(keys, yaw, speed)
                px, pz = vx + ax, vz + az
                if jumped and sp:
                    r = math.radians(yaw)
                    px -= math.sin(r) * JUMP_BOOST
                    pz += math.cos(r) * JUMP_BOOST
                if math.hypot(dx - px, dz - pz) <= tol:
                    exact = True
                # A wall stops one axis and leaves the other alone, so the bound is per axis:
                # no further than predicted, and not the other way.
                # A wall hit on the previous tick zeroed the velocity on that axis, though the
                # displacement shows the part of it that was covered before the wall.
                ox, oz = axis_over(dx, px), axis_over(dz, pz)
                if b[9]:
                    ox, oz = min(ox, axis_over(dx, ax + (px - vx - ax))), min(oz, axis_over(dz, az + (pz - vz - az)))
                    if math.hypot(dx - (px if ox == axis_over(dx, px) else px - vx),
                                  dz - (pz if oz == axis_over(dz, pz) else pz - vz)) <= tol:
                        exact = True
                o = max(ox, oz)
                if over is None or o < over[0]:
                    over = (o, math.hypot(dx, dz), math.hypot(px, pz))
        stats['ticks'] += 1
        if exact:
            stats['exact'] += 1
        elif over[0] <= tol:
            stats['slower'] += 1
        else:
            stats['excess'] += 1
            stats['worst'].append((over[0], c[0], keys, over[1], over[2], ground_now,
                                   ' | '.join(events.get(b[0], []) + events.get(c[0], []))[:120]))
        # Vertical: an airborne tick that follows an airborne tick and ends airborne is pure gravity.
        if not ground_prev and not ground_now and not c[4]:
            stats['vertical'] += 1
            if abs(dy - (pdy - 0.08) * 0.98) > tol and dy < (pdy - 0.08) * 0.98 + tol:
                pass  # slower than gravity alone: head bump, water, ladder
            elif abs(dy - (pdy - 0.08) * 0.98) > tol:
                stats['vertical_bad'] += 1
    return stats


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('logs', nargs='+')
    ap.add_argument('--tolerance', type=float, default=0.004, help='blocks; logs are rounded to 0.001')
    ap.add_argument('--worst', type=int, default=0, help='print this many of the worst excess ticks per log')
    ap.add_argument('--allow', type=int, default=0, help='excess ticks tolerated in total')
    ap.add_argument('--quiet', action='store_true', help='only the total line')
    args = ap.parse_args()
    total = dict(ticks=0, exact=0, slower=0, excess=0, vertical=0, vertical_bad=0, skipped=0)
    for path in args.logs:
        try:
            s = check(path, args.tolerance)
        except OSError as e:
            print('%s: %s' % (path, e))
            continue
        if s['ticks'] == 0:
            continue
        for k in total:
            total[k] += s[k]
        if not args.quiet or s['excess']:
            name = '/'.join(path.replace('\\', '/').split('/')[-2:])
            print('%-58s ticks=%4d exact=%5.1f%% slower=%4d excess=%3d  vertical %d/%d off' % (
                name[-58:], s['ticks'], 100.0 * s['exact'] / s['ticks'], s['slower'], s['excess'],
                s['vertical_bad'], s['vertical']))
            for w in sorted(s['worst'], reverse=True)[:args.worst]:
                print('    t=%d +%.3f moved=%.3f allowed=%.3f keys=%s ground=%s %s' % (w[1], w[0], w[3], w[4], w[2], w[5], w[6]))
    if total['ticks']:
        print('TOTAL logs=%d ticks=%d exact=%.1f%% slower=%d excess=%d vertical_off=%d/%d skipped_water_ladder=%d' % (
            len(args.logs), total['ticks'], 100.0 * total['exact'] / total['ticks'], total['slower'], total['excess'],
            total['vertical_bad'], total['vertical'], total['skipped']))
    sys.exit(1 if total['excess'] > args.allow else 0)


if __name__ == '__main__':
    main()
