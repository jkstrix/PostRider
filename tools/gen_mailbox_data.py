"""Writes the mailbox loot tables, recipes and tags into the mod's data folder."""
import json
import os
import sys

root = sys.argv[1]
NS = "postrider"
COLOURS = ["white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
           "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"]


def write(rel, data):
    path = os.path.join(root, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


for c in COLOURS:
    block = f"{NS}:{c}_mailbox"
    write(f"data/{NS}/loot_table/blocks/{c}_mailbox.json", {
        "type": "minecraft:block",
        "pools": [{
            "rolls": 1,
            "bonus_rolls": 0,
            "entries": [{"type": "minecraft:item", "name": block}],
            "conditions": [
                {"condition": "minecraft:survives_explosion"},
                {"condition": "minecraft:block_state_property", "block": block, "properties": {"half": "lower"}}
            ]
        }]
    })
    write(f"data/{NS}/recipe/dye_{c}_mailbox.json", {
        "type": "minecraft:crafting_shapeless",
        "category": "misc",
        "group": "postrider_dye_mailbox",
        "ingredients": [{"tag": f"{NS}:mailboxes"}, {"tag": f"c:dyes/{c}"}],
        "result": {"id": block, "count": 1}
    })

write(f"data/{NS}/recipe/mailbox.json", {
    "type": "minecraft:crafting_shaped",
    "category": "misc",
    "pattern": ["III", "ICI", " F "],
    "key": {
        "I": {"item": "minecraft:iron_ingot"},
        "C": {"tag": "c:chests/wooden"},
        "F": {"tag": "minecraft:wooden_fences"}
    },
    "result": {"id": f"{NS}:white_mailbox", "count": 1}
})

mailboxes = [f"{NS}:{c}_mailbox" for c in COLOURS]
write(f"data/{NS}/tags/item/mailboxes.json", {"values": mailboxes})
write(f"data/{NS}/tags/block/mailboxes.json", {"values": mailboxes})
write("data/minecraft/tags/block/mineable/axe.json", {"values": [f"#{NS}:mailboxes"]})
print("ok")
