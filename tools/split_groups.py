"""Splits a Blockbench Java block export into one model per group set.

Usage: split_groups.py EXPORT.json MODELS_DIR model_name=group[,group...] [model_name=...]
Each model keeps the export's textures (plus particle) and only the elements of its groups.
"""
import json
import os
import sys

export_path, models_dir, *specs = sys.argv[1:]
with open(export_path, encoding="utf-8") as f:
    export = json.load(f)

groups = {g["name"]: g["children"] for g in export["groups"]}
particle = next(iter(export["textures"].values()))
os.makedirs(models_dir, exist_ok=True)

for spec in specs:
    name, group_list = spec.split("=")
    elements = []
    for group in group_list.split(","):
        elements.extend(export["elements"][i] for i in groups[group])
    used = {face["texture"][1:] for element in elements for face in element["faces"].values()}
    textures = {key: value for key, value in export["textures"].items() if key in used}
    textures["particle"] = particle
    model = {"credit": "Made with Blockbench", "textures": textures, "elements": elements}
    with open(os.path.join(models_dir, name + ".json"), "w", encoding="utf-8", newline="\n") as f:
        json.dump(model, f, indent=2)
        f.write("\n")
    print(name, len(elements), "elements")
