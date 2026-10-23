"""Copies a prop's pixels from the base skin onto every rider's skin.

The rider skins (textures/entity/rider/<id>.png) are painted from the base skin (textures/entity/postman.png) by
tools/rider_skins.js, so a prop painted on the base afterwards has to reach them too. Rather than regenerate all
twenty, this stamps the prop's region of the base onto each:

    python tools/stamp_prop.py X Y WIDTH HEIGHT

Pure Python (no Pillow): reads and writes 8-bit PNGs.
"""
import glob
import struct
import sys
import zlib

BASE = "src/main/resources/assets/postrider/textures/entity/postman.png"
RIDERS = "src/main/resources/assets/postrider/textures/entity/rider/*.png"


def read_png(path):
    """The image as rows of RGBA bytes."""
    with open(path, "rb") as f:
        data = f.read()
    assert data[:8] == b"\x89PNG\r\n\x1a\n", path
    pos, idat, width, height, depth, colour = 8, b"", 0, 0, 0, 0
    while pos < len(data):
        length, = struct.unpack(">I", data[pos:pos + 4])
        kind, chunk = data[pos + 4:pos + 8], data[pos + 8:pos + 8 + length]
        pos += 12 + length
        if kind == b"IHDR":
            width, height, depth, colour = struct.unpack(">IIBB", chunk[:10])
        elif kind == b"IDAT":
            idat += chunk
    assert depth == 8 and colour in (6, 2), f"{path}: only 8-bit RGB(A) PNGs"
    bpp = 4 if colour == 6 else 3
    raw, stride, rows, prev, pos = zlib.decompress(idat), width * bpp, [], bytearray(width * bpp), 0
    for _ in range(height):
        kind, line = raw[pos], bytearray(raw[pos + 1:pos + 1 + stride])
        pos += 1 + stride
        for i in range(stride):
            a = line[i - bpp] if i >= bpp else 0
            b = prev[i]
            c = prev[i - bpp] if i >= bpp else 0
            if kind == 1:
                line[i] = (line[i] + a) & 255
            elif kind == 2:
                line[i] = (line[i] + b) & 255
            elif kind == 3:
                line[i] = (line[i] + (a + b) // 2) & 255
            elif kind == 4:
                pa, pb, pc = abs(b - c), abs(a - c), abs(a + b - 2 * c)
                line[i] = (line[i] + (a if pa <= pb and pa <= pc else b if pb <= pc else c)) & 255
        if bpp == 3:
            rows.append(bytearray(b"".join(bytes(line[i:i + 3]) + b"\xff" for i in range(0, stride, 3))))
        else:
            rows.append(line)
        prev = line
    return width, height, rows


def write_png(path, width, height, rows):
    def chunk(kind, body):
        return struct.pack(">I", len(body)) + kind + body + struct.pack(">I", zlib.crc32(kind + body) & 0xFFFFFFFF)
    raw = b"".join(b"\x00" + bytes(row) for row in rows)
    with open(path, "wb") as f:
        f.write(b"\x89PNG\r\n\x1a\n")
        f.write(chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)))
        f.write(chunk(b"IDAT", zlib.compress(raw, 9)))
        f.write(chunk(b"IEND", b""))


def main():
    x, y, w, h = map(int, sys.argv[1:5])
    width, height, base = read_png(BASE)
    for path in sorted(glob.glob(RIDERS)):
        rider_width, rider_height, rows = read_png(path)
        assert (rider_width, rider_height) == (width, height), path
        for row in range(y, y + h):
            rows[row][x * 4:(x + w) * 4] = base[row][x * 4:(x + w) * 4]
        write_png(path, width, height, rows)
        print(f"{path}: stamped {w}x{h} at {x},{y}")


if __name__ == "__main__":
    main()
