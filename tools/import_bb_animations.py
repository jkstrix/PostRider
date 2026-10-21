"""Turns Blockbench's "Export Animations" (Mojang mappings) Java file into a class in the mod.

Usage: import_bb_animations.py EXPORT.java OUT.java PACKAGE CLASS_NAME SOURCE_BBMODEL
Field names become UPPER_SNAKE_CASE; the keyframes are left exactly as Blockbench wrote them.
"""
import re
import sys

src_path, out_path, package, class_name, source = sys.argv[1:]
with open(src_path, encoding="utf-8") as f:
    text = f.read()

body = text[text.index("public static final AnimationDefinition"):text.rindex("}")].rstrip()
body = re.sub(r"public static final AnimationDefinition (\w+) =",
              lambda m: "public static final AnimationDefinition " + m.group(1).upper() + " =", body)
body = "\n".join(line.replace("\t", "", 1) if line.startswith("\t") else line for line in body.splitlines())

header = f"""package {package};

import net.minecraft.client.animation.AnimationChannel;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.Keyframe;
import net.minecraft.client.animation.KeyframeAnimations;

/**
 * Generated from {source} by Blockbench's Mojang-mappings animation export and tools/import_bb_animations.py.
 * Change the animations in Blockbench and re-export rather than editing this file.
 */
public final class {class_name} {{
\tprivate {class_name}() {{}}

\t"""
with open(out_path, "w", encoding="utf-8", newline="\n") as f:
    f.write(header + body.replace("\n", "\n\t").replace("\n\t\n", "\n\n") + "\n}\n")
print("wrote", out_path)
