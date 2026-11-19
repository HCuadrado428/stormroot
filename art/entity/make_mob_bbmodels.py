"""Crea los .bbmodel (Blockbench, formato «Modded Entity», UV de caja) de los mobs a partir de la misma geometría que
client/entity/*Model.java (la de preview_mobs.py), con la textura dentro del archivo.

Blockbench usa Y hacia arriba y X al revés que Minecraft: x_bb = -x, y_bb = 24 - y, z_bb = z; los giros en X e Y
cambian de signo.

Uso: python3 art/entity/make_mob_bbmodels.py carpeta_de_salida
"""
import base64
import json
import math
import os
import sys
import uuid

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import preview_mobs as pm  # noqa: E402

NAMES = {"sporeling": "Esporín", "weaver": "Tejedor", "mycelium_guardian": "Guardián del micelio"}


def bb_point(p):
    return [-p[0], 24 - p[1], p[2]]


def box_faces(u, v, w, h, d, mirror):
    f = {
        "east": [u, v + d, u + d, v + d + h],
        "north": [u + d, v + d, u + d + w, v + d + h],
        "west": [u + d + w, v + d, u + 2 * d + w, v + d + h],
        "south": [u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h],
        "up": [u + d + w, v + d, u + d, v],
        "down": [u + d + 2 * w, v, u + d + w, v + d],
    }
    if mirror:
        f["east"], f["west"] = f["west"], f["east"]
        for k in f:
            a, b, c, e = f[k]
            f[k] = [c, b, a, e]
    return {k: {"uv": uv, "texture": 0} for k, uv in f.items()}


def build(name, parts, tex_path):
    elements = []
    counter = {"n": 0}

    def group(p, parent_abs, label):
        pivot = tuple(parent_abs[i] + p["offset"][i] for i in range(3))
        rot = p["rot"]
        g = {"name": label, "origin": bb_point(pivot),
             "rotation": [-math.degrees(rot[0]), -math.degrees(rot[1]), math.degrees(rot[2])],
             "color": 0, "uuid": str(uuid.uuid4()), "export": True, "isOpen": True, "locked": False,
             "visibility": True, "autouv": 0, "children": []}
        for b in p["boxes"]:
            u, v, x, y, z, w, h, d, mirror = b
            lo = (pivot[0] + x, pivot[1] + y, pivot[2] + z)
            hi = (lo[0] + w, lo[1] + h, lo[2] + d)
            a, c = bb_point(lo), bb_point(hi)
            counter["n"] += 1
            el = {"name": label if len(p["boxes"]) == 1 else f"{label}_{counter['n']}",
                  "box_uv": True, "rescale": False, "locked": False, "render_order": "default",
                  "allow_mirror_modeling": True,
                  "from": [min(a[i], c[i]) for i in range(3)], "to": [max(a[i], c[i]) for i in range(3)],
                  "autouv": 0, "color": counter["n"] % 8, "origin": bb_point(pivot), "uv_offset": [u, v],
                  "mirror_uv": mirror, "faces": box_faces(u, v, w, h, d, mirror), "type": "cube",
                  "uuid": str(uuid.uuid4())}
            elements.append(el)
            g["children"].append(el["uuid"])
        for i, ch in enumerate(p["children"]):
            g["children"].append(group(ch, pivot, ch.get("name", f"{label}_hijo{i + 1}")))
        return g

    outliner = [group(p, (0, 0, 0), p.get("name", f"pieza_{i + 1}")) for i, p in enumerate(parts)]
    im = Image.open(tex_path)
    tex = {"path": "", "name": name + ".png", "folder": "entity", "namespace": "stormroot", "id": "0",
           "width": im.width, "height": im.height, "uv_width": im.width, "uv_height": im.height,
           "particle": False, "render_mode": "default", "render_sides": "auto", "frame_time": 1,
           "frame_order_type": "loop", "frame_order": "", "frame_interpolate": False, "visible": True,
           "internal": True, "saved": True, "uuid": str(uuid.uuid4()), "relative_path": "",
           "source": "data:image/png;base64," + base64.b64encode(open(tex_path, "rb").read()).decode()}
    return {"meta": {"format_version": "4.10", "model_format": "modded_entity", "box_uv": True},
            "name": name, "model_identifier": name, "visible_box": [1, 1, 0], "variable_placeholders": "",
            "variable_placeholder_buttons": [], "timeline_setups": [], "unhandled_root_fields": {},
            "resolution": {"width": im.width, "height": im.height},
            "elements": elements, "outliner": outliner, "textures": [tex]}


def named(parts, names):
    for p, n in zip(parts, names):
        p["name"] = n
    return parts


def models():
    s = named(pm.sporeling(), ["body", "left_front", "right_front", "left_back", "right_back"])
    s[0]["children"][0]["name"] = "cap"
    w = pm.weaver()
    leg_names = []
    for i in range(3):
        leg_names += [f"left_leg_{i}", f"right_leg_{i}"]
    named(w, ["body", "head"] + leg_names)
    w[1]["children"][0]["name"] = "cap"
    g = named(pm.guardian(), ["body", "head", "left_arm", "right_arm", "left_leg", "right_leg"])
    g[1]["children"][0]["name"] = "cap"
    return {"sporeling": s, "weaver": w, "mycelium_guardian": g}


if __name__ == "__main__":
    out = sys.argv[1]
    os.makedirs(out, exist_ok=True)
    for name, parts in models().items():
        bb = build(name, parts, os.path.join(pm.TEX, name + ".png"))
        with open(os.path.join(out, name + ".bbmodel"), "w") as f:
            json.dump(bb, f, indent=1)
        print(name, len(bb["elements"]), "cubos")
