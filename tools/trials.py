"""Reads the in-world trials' results (run-gametest/trials.jsonl, one JSON line per trial) and prints them as a
table, against the saved baseline (docs/trials/baseline.jsonl) if there is one, so a change to the route finding
can be seen to help or hurt.

    python tools/trials.py                 the latest run against the baseline
    python tools/trials.py --save-baseline  keep the latest run as the baseline (commit docs/trials/baseline.jsonl)

Run from the repository root after ./gradlew runGameTestServer.
"""
import json
import os
import shutil
import sys

LATEST = os.path.join("run-gametest", "trials.jsonl")
BASELINE = os.path.join("docs", "trials", "baseline.jsonl")
# What's compared, and whether lower is better
COLUMNS = [("roadTicks", True), ("launches", True), ("jumpsFree", True), ("mostVisits", True), ("longestStill", True),
           ("routeCells", True), ("stretches", True), ("stretchesFailed", True), ("stretchBlocks", True)]
# A change this big (as a share of the baseline) is worth pointing out
NOTABLE = 0.2


def read(path):
    if not os.path.exists(path):
        return {}
    trials = {}
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line:
                row = json.loads(line)
                trials[row["trial"]] = row
    return trials


def main():
    if "--save-baseline" in sys.argv:
        os.makedirs(os.path.dirname(BASELINE), exist_ok=True)
        shutil.copyfile(LATEST, BASELINE)
        print("saved", LATEST, "as", BASELINE)
        return
    latest, baseline = read(LATEST), read(BASELINE)
    if not latest:
        print("no results at", LATEST, "(run ./gradlew runGameTestServer first)")
        return
    width = max(len(t) for t in latest) + 2
    print("trial".ljust(width) + "pass  " + "".join(c.rjust(15) for c, _ in COLUMNS))
    worse = []
    for name, row in latest.items():
        cells = []
        base = baseline.get(name)
        for column, lower_better in COLUMNS:
            value = row.get(column, 0)
            text = str(value)
            if base is not None and column in base:
                was = base[column]
                if was != value:
                    text += " (%+d)" % (value - was)
                    if was > 0 and abs(value - was) / was >= NOTABLE and (value > was) == lower_better:
                        worse.append("%s: %s %s -> %s" % (name, column, was, value))
            cells.append(text.rjust(15))
        print(name.ljust(width) + ("yes   " if row.get("passed") else "NO    ") + "".join(cells))
    if baseline:
        for name in baseline:
            if name not in latest:
                print(name.ljust(width) + "(not in this run)")
    if worse:
        print()
        print("worse than the baseline:")
        for line in worse:
            print(" ", line)
    elif baseline:
        print()
        print("nothing notably worse than the baseline")


if __name__ == "__main__":
    main()
