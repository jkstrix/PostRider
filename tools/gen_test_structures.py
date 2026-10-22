"""Writes the empty structure templates the GameTests (src/main/java/.../gametest) are placed in.

Each is just a size: the framework clears the space before a test and the test builds its own scene in code.
Run from the repository root: python tools/gen_test_structures.py
"""
import gzip
import os
import struct

# Name: (x, y, z) in blocks
ARENAS = {
    "river_40x12": (40, 12, 12),
    "arena_48x32": (48, 16, 32),
    "arena_64x48": (64, 16, 48),
    "arena_80x64": (80, 16, 64),
    # Taller and wider, for hills with caves in them, towers, mazes and long approaches to a river
    "arena_96x96": (96, 48, 96),
    "arena_128x64": (128, 32, 64),
    "tower_48x48": (48, 80, 48),
}
DATA_VERSION = 3955  # Minecraft 1.21.1
OUT = os.path.join("src", "main", "resources", "data", "postrider", "structure", "gametest")


def name(text):
    raw = text.encode("utf-8")
    return struct.pack(">H", len(raw)) + raw


def tag_int(key, value):
    return b"\x03" + name(key) + struct.pack(">i", value)


def tag_int_list(key, values):
    return b"\x09" + name(key) + b"\x03" + struct.pack(">i", len(values)) + b"".join(struct.pack(">i", v) for v in values)


def tag_empty_compound_list(key):
    return b"\x09" + name(key) + b"\x0a" + struct.pack(">i", 0)


def tag_palette_of_air(key):
    entry = b"\x08" + name("Name") + name("minecraft:air") + b"\x00"
    return b"\x09" + name(key) + b"\x0a" + struct.pack(">i", 1) + entry


def structure(size):
    body = (tag_int("DataVersion", DATA_VERSION) + tag_int_list("size", list(size)) + tag_palette_of_air("palette")
            + tag_empty_compound_list("blocks") + tag_empty_compound_list("entities"))
    return b"\x0a" + name("") + body + b"\x00"


def main():
    os.makedirs(OUT, exist_ok=True)
    for arena, size in ARENAS.items():
        path = os.path.join(OUT, arena + ".nbt")
        with open(path, "wb") as f:
            # A fixed timestamp keeps the files identical from one run to the next
            f.write(gzip.compress(structure(size), mtime=0))
        print("wrote", path, size)


if __name__ == "__main__":
    main()
