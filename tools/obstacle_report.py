#!/usr/bin/env python3
"""Aggregate obstacle-harness results across one or more batch runs.

Reads `[Obstacle] RESULT` lines from server logs (plain or .gz) and prints a
per-fixture reliability table: runs, passes, pass rate, mean/max ticks, and
the failure reasons seen. Use it to separate genuinely broken fixtures from
flaky ones after running `mai test all` several times.

    tools/obstacle_report.py mc-server/logs/latest.log
    tools/obstacle_report.py mc-server/logs/*.log.gz --since 2026-10-09
    tools/obstacle_report.py latest.log --only-failing

Exit status is 1 if any fixture's pass rate is below --min-rate (default 1.0),
so it can gate CI.
"""
import argparse
import gzip
import re
import sys
from collections import defaultdict

RESULT = re.compile(
    r"\[Obstacle\] RESULT name=(?P<name>\S+) status=(?P<status>\S+) "
    r"ticks=(?P<ticks>\d+) reason=(?P<reason>\S+)")


def lines(path):
    opener = gzip.open if path.endswith(".gz") else open
    with opener(path, "rt", encoding="utf-8", errors="replace") as f:
        yield from f


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("logs", nargs="+")
    ap.add_argument("--only-failing", action="store_true", help="hide fixtures that always passed")
    ap.add_argument("--min-rate", type=float, default=1.0, help="pass-rate threshold for exit status")
    args = ap.parse_args()

    runs = defaultdict(list)  # name -> [(status, ticks, reason)]
    for path in args.logs:
        for line in lines(path):
            m = RESULT.search(line)
            if m:
                runs[m["name"]].append((m["status"], int(m["ticks"]), m["reason"]))

    if not runs:
        print("no RESULT lines found", file=sys.stderr)
        return 2

    below = 0
    rows = []
    for name, rs in sorted(runs.items()):
        passes = [t for s, t, _ in rs if s == "PASS"]
        rate = len(passes) / len(rs)
        if rate < args.min_rate:
            below += 1
        if args.only_failing and rate == 1.0:
            continue
        reasons = sorted({f"{s}:{r}" for s, _, r in rs if s != "PASS"})
        mean = sum(passes) / len(passes) if passes else 0
        rows.append((name, len(rs), len(passes), rate, mean, max(passes, default=0), ",".join(reasons)))

    w = max([len(r[0]) for r in rows] + [7])
    print(f"{'fixture':<{w}}  runs  pass  rate   mean_t  max_t  failures")
    for name, n, p, rate, mean, mx, reasons in rows:
        print(f"{name:<{w}}  {n:4d}  {p:4d}  {rate:5.0%}  {mean:6.1f}  {mx:5d}  {reasons}")
    total = sum(len(v) for v in runs.values())
    passed = sum(1 for v in runs.values() for s, _, _ in v if s == "PASS")
    print(f"\n{len(runs)} fixtures, {total} runs, {passed} passed ({passed / total:.1%}); "
          f"{below} fixture(s) below {args.min_rate:.0%}")
    return 1 if below else 0


if __name__ == "__main__":
    sys.exit(main())
