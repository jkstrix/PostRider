"""Brings blockbench/postman_slim.bbmodel's animations, props and base texture into line with blockbench/postman.bbmodel.

The slim build (the women of the crew) shares every bone and animation with the regular one; only its head and arms are
narrower, and the props in its hands sit half a block further in. Animations are made and approved on the regular model,
so after saving it, run this to copy them over:

    python tools/sync_slim_animations.py

Animators are matched by bone name (the two files' bones have different uuids), empty animators are dropped, and the
shared "postman" texture is copied across so a newly painted prop shows on both. A prop group that's in the regular
file only is added to the slim one, with its cubes shifted by the slim hand offset (a right-hand prop half a block
right, a left-hand one half a block left), which is how the Java model places them too (PostmanModel.createBodyLayer).
"""
import copy
import json
import sys
import uuid

REGULAR = "blockbench/postman.bbmodel"
SLIM = "blockbench/postman_slim.bbmodel"
# A right-hand prop's group sits at x -6 on the regular build and -5.5 on the slim one; a left-hand one at 6 and 5.5
HAND_SHIFT = {"right_arm": 0.5, "left_arm": -0.5}


def load(path):
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def groups_by_name(model):
    return {g["name"]: g for g in model["groups"]}


def outliner_node(outliner, group_uuid):
    for entry in outliner:
        if isinstance(entry, dict):
            if entry["uuid"] == group_uuid:
                return entry
            found = outliner_node(entry.get("children", []), group_uuid)
            if found:
                return found
    return None


def parent_of(outliner, child_uuid, parent=None):
    for entry in outliner:
        if isinstance(entry, dict):
            if entry["uuid"] == child_uuid:
                return parent
            found = parent_of(entry.get("children", []), child_uuid, entry)
            if found:
                return found
        elif entry == child_uuid:
            return parent
    return None


def add_missing_props(regular, slim):
    """Copies prop groups (and their cubes) the regular file has and the slim one hasn't, shifted for the slim hands."""
    regular_groups, slim_groups = groups_by_name(regular), groups_by_name(slim)
    regular_uuids = {g["uuid"]: g["name"] for g in regular["groups"]}
    slim_uuid_of = {name: g["uuid"] for name, g in slim_groups.items()}
    cubes_by_uuid = {c["uuid"]: c for c in regular["elements"]}
    added = []
    for name, group in regular_groups.items():
        if name in slim_groups:
            continue
        node = outliner_node(regular["outliner"], group["uuid"])
        parent_node = parent_of(regular["outliner"], group["uuid"])
        parent_name = regular_uuids[parent_node["uuid"]] if parent_node else None
        if parent_name not in HAND_SHIFT or any(isinstance(c, dict) for c in node.get("children", [])):
            sys.exit(f"{name} is new on the regular model but isn't a plain prop in a hand: add it to the slim model in Blockbench")
        shift = HAND_SHIFT[parent_name]
        new_group = copy.deepcopy(group)
        new_group["uuid"] = str(uuid.uuid4())
        new_group["origin"] = [group["origin"][0] + shift, group["origin"][1], group["origin"][2]]
        slim["groups"].append(new_group)
        new_node = {"uuid": new_group["uuid"], "isOpen": False, "children": []}
        for cube_uuid in node.get("children", []):
            cube = copy.deepcopy(cubes_by_uuid[cube_uuid])
            cube["uuid"] = str(uuid.uuid4())
            for key in ("from", "to", "origin"):
                cube[key] = [cube[key][0] + shift, cube[key][1], cube[key][2]]
            slim["elements"].append(cube)
            new_node["children"].append(cube["uuid"])
        outliner_node(slim["outliner"], slim_uuid_of[parent_name])["children"].append(new_node)
        slim_uuid_of[name] = new_group["uuid"]
        added.append(name)
    return added


def sync_animations(regular, slim):
    slim_uuid_of = {g["name"]: g["uuid"] for g in slim["groups"]}
    old = {a["name"]: a for a in slim["animations"]}
    animations = []
    for animation in regular["animations"]:
        copied = copy.deepcopy(animation)
        copied["uuid"] = old[animation["name"]]["uuid"] if animation["name"] in old else str(uuid.uuid4())
        copied["selected"] = False
        animators = {}
        for animator in animation["animators"].values():
            if not animator.get("keyframes"):
                continue
            if animator["name"] not in slim_uuid_of:
                sys.exit(f"{animation['name']} animates {animator['name']}, which the slim model hasn't got")
            animators[slim_uuid_of[animator["name"]]] = copy.deepcopy(animator)
        copied["animators"] = animators
        animations.append(copied)
    slim["animations"] = animations
    return [a["name"] for a in animations]


def sync_texture(regular, slim):
    base = next(t for t in regular["textures"] if t["name"] == "postman")
    for texture in slim["textures"]:
        if texture["name"] == "postman":
            texture["source"] = base["source"]
            return True
    return False


def main():
    regular, slim = load(REGULAR), load(SLIM)
    added = add_missing_props(regular, slim)
    names = sync_animations(regular, slim)
    textured = sync_texture(regular, slim)
    # Written the way Blockbench writes it: compact, on one line
    with open(SLIM, "w", encoding="utf-8", newline="\n") as f:
        json.dump(slim, f, separators=(",", ":"), ensure_ascii=False)
    message = f"{SLIM}: {len(names)} animations copied"
    if added:
        message += ", props added: " + ", ".join(added)
    if textured:
        message += ", base texture copied"
    print(message)


if __name__ == "__main__":
    main()
