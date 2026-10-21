"""Splits the Blockbench mailbox export into the mod's block, item and blockstate files.

The model is two blocks tall. Each half gets its own block model (the upper one shifted down by 16) so that
Minecraft lights every part from the block it is actually in; the item model keeps the whole mailbox.
"""
import json
import os
import sys

export_path, assets = sys.argv[1], sys.argv[2]
with open(export_path, encoding="utf-8") as f:
    export = json.load(f)

NS = "ponyexpress"
COLOURS = ["white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
           "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"]

elements = export["elements"]
groups = {g["name"]: g["children"] for g in export["groups"]}


def pick(*group_names, half=None):
    """Elements of the groups, tinting the box; half picks the lower (y < 16) or upper elements, shifted into place."""
    picked = []
    for name in group_names:
        for index in groups[name]:
            element = json.loads(json.dumps(elements[index]))
            upper = element["from"][1] >= 16
            if half == "lower" and upper or half == "upper" and not upper:
                continue
            if element["from"][1] < 16 < element["to"][1] and half is not None:
                raise SystemExit(f"{element.get('name')} crosses the block line; split it in Blockbench")
            if half == "upper":
                element["from"][1] -= 16
                element["to"][1] -= 16
                if "rotation" in element:
                    element["rotation"]["origin"][1] -= 16
            for face in element["faces"].values():
                if face["texture"] == "#box":
                    face["tintindex"] = 0
            picked.append(element)
    return picked


def tex(name):
    return f"{NS}:block/{name}"


def write(rel, data):
    path = os.path.join(assets, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


credit = "Made with Blockbench"
textures = {"post": tex("mailbox_post"), "box": tex("mailbox_box"), "iron": tex("mailbox_iron"), "particle": tex("mailbox_post")}
write("models/block/mailbox_lower.json", {"credit": credit, "textures": textures, "elements": pick("post", "box", half="lower")})
write("models/block/mailbox_upper.json", {"credit": credit, "textures": textures, "elements": pick("post", "box", half="upper")})
for state in ("down", "up"):
    write(f"models/block/mailbox_flag_{state}.json", {
        "credit": credit,
        "textures": {"flag": tex("mailbox_flag"), "particle": tex("mailbox_flag")},
        "elements": pick(f"flag_{state}", half="upper"),
    })
    write(f"models/block/mailbox_flag_{state}_yellow.json", {
        "parent": f"{NS}:block/mailbox_flag_{state}",
        "textures": {"flag": tex("mailbox_flag_yellow"), "particle": tex("mailbox_flag_yellow")},
    })

write("models/item/mailbox.json", {
    "credit": credit,
    "textures": {"post": tex("mailbox_post"), "box": tex("mailbox_box"), "iron": tex("mailbox_iron"),
                 "flag": tex("mailbox_flag"), "particle": tex("mailbox_post")},
    "elements": pick("post", "box", "flag_down"),
    "display": export["display"],
})
write("models/item/mailbox_yellow_flag.json", {
    "parent": f"{NS}:item/mailbox",
    "textures": {"flag": tex("mailbox_flag_yellow")},
})

FACING_Y = {"north": 0, "east": 90, "south": 180, "west": 270}
for colour in COLOURS:
    yellow = colour == "red"
    parts = []
    for facing, y in FACING_Y.items():
        rot = {"y": y} if y else {}
        parts.append({"when": {"half": "lower", "facing": facing}, "apply": {"model": f"{NS}:block/mailbox_lower", **rot}})
        parts.append({"when": {"half": "upper", "facing": facing}, "apply": {"model": f"{NS}:block/mailbox_upper", **rot}})
        for flag, state in (("false", "down"), ("true", "up")):
            model = f"{NS}:block/mailbox_flag_{state}" + ("_yellow" if yellow else "")
            parts.append({"when": {"half": "upper", "facing": facing, "flag": flag}, "apply": {"model": model, **rot}})
    write(f"blockstates/{colour}_mailbox.json", {"multipart": parts})
    write(f"models/item/{colour}_mailbox.json",
          {"parent": f"{NS}:item/mailbox" + ("_yellow_flag" if yellow else "")})

print("wrote models for", len(COLOURS), "colours")
