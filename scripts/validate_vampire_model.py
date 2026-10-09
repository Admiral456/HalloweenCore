#!/usr/bin/env python3
"""Validate the Vampire King Blockbench/ModelEngine model and rig contract."""
import base64, binascii, json, pathlib, struct, sys, zlib

MODEL = pathlib.Path("mythicmobs/models/vampire_king.bbmodel")
TEXTURE = pathlib.Path("mythicmobs/models/vampire_king.png")
ALLOWED_CUBE_ROTATIONS = {-45.0, -22.5, 0.0, 22.5, 45.0}

def fail(message):
    print("ERROR:", message, file=sys.stderr)
    raise SystemExit(1)

def main():
    if not MODEL.exists() or not TEXTURE.exists():
        fail("Model or PNG is missing")
    data = json.loads(MODEL.read_text(encoding="utf-8"))
    if data.get("meta", {}).get("model_format") != "free":
        fail("Expected Blockbench Generic/Free format")
    if data.get("meta", {}).get("box_uv", False):
        fail("Box UV is not supported by this ModelEngine blueprint")
    textures = data.get("textures", [])
    if not textures or not textures[0].get("source", "").startswith("data:image/png;base64,"):
        fail("Texture must be embedded in the bbmodel")
    embedded = base64.b64decode(textures[0]["source"].split(",", 1)[1], validate=True)
    png = TEXTURE.read_bytes()
    if embedded != png:
        fail("Embedded and external PNG differ")
    if png[:8] != b"\x89PNG\r\n\x1a\n":
        fail("PNG signature invalid")
    width, height = struct.unpack(">II", png[16:24])
    if (width, height) != (128, 128):
        fail("Texture atlas must be 128×128")
    offset, idat, found_iend = 8, bytearray(), False
    while offset < len(png):
        length = struct.unpack(">I", png[offset:offset+4])[0]
        kind = png[offset+4:offset+8]
        content = png[offset+8:offset+8+length]
        crc_read = struct.unpack(">I", png[offset+8+length:offset+12+length])[0]
        if (binascii.crc32(kind + content) & 0xffffffff) != crc_read:
            fail("PNG CRC mismatch")
        if kind == b"IDAT":
            idat.extend(content)
        offset += length + 12
        if kind == b"IEND":
            found_iend = True
            break
    if not found_iend:
        fail("PNG missing IEND")
    try:
        raw = zlib.decompress(bytes(idat))
    except zlib.error as exc:
        fail("PNG image data cannot be decompressed: " + str(exc))
    if len(raw) != height * (1 + width * 4):
        fail("PNG pixel data length invalid")

    bones, element_paths, parents = {}, {}, {}
    def visit(node, parent=None, ancestors=()):
        if isinstance(node, str):
            element_paths[node] = ancestors
            return
        name = node.get("name", "")
        uuid = node.get("uuid")
        if uuid:
            bones[uuid] = name
            parents[name] = parent
        next_path = ancestors + ((name,) if name else ())
        for child in node.get("children", []):
            visit(child, name or parent, next_path)
    for item in data.get("outliner", []):
        visit(item)
    required = {"root","body","head","left_arm","right_arm","left_leg","right_leg",
                "left_wing","right_wing","cloak","weapon","hitbox","shadow"}
    if not required.issubset(set(bones.values())):
        fail("Required bones missing: " + ", ".join(sorted(required-set(bones.values()))))
    if parents.get("body") != "root":
        fail("Body bone must be parented to root")
    for name in ("head","left_arm","right_arm","left_leg","right_leg","left_wing","right_wing","cloak"):
        if parents.get(name) != "body":
            fail(f"{name} must be a child of body")
    if parents.get("weapon") != "right_arm":
        fail("Weapon must follow the right arm bone")
    if parents.get("hitbox") != "root" or parents.get("shadow") != "root":
        fail("ModelEngine hitbox and shadow bones must remain root children")

    elements = data.get("elements", [])
    seen, visible = set(), []
    for e in elements:
        uid = e.get("uuid")
        if not uid or uid in seen:
            fail("Missing or duplicate element UUID")
        seen.add(uid)
        if e.get("type") != "cube":
            fail("Unsupported element type: " + str(e.get("type")))
        hidden = "hitbox" in element_paths.get(uid, ()) or "shadow" in element_paths.get(uid, ())
        if not hidden:
            visible.append(e)
        rotation = e.get("rotation")
        if rotation is not None:
            if len(rotation) != 3:
                fail("Malformed cube rotation: " + e.get("name", "?"))
            nonzero = [angle for angle in rotation if abs(angle) > 0.001]
            if len(nonzero) > 1:
                fail("Cube rotation uses more than one axis: " + e.get("name", "?"))
            if any(min(ALLOWED_CUBE_ROTATIONS, key=lambda a: abs(a-angle)) != angle for angle in rotation):
                fail("Cube rotation is not one of 0/±22.5/±45: " + e.get("name", "?"))
        faces = e.get("faces", {})
        if set(faces) != {"north","east","south","west","up","down"}:
            fail("Cube is missing face UVs: " + e.get("name", "?"))
        for face in faces.values():
            uv = face.get("uv")
            if uv is None or len(uv) != 4:
                fail("Malformed face UV")
            if face.get("texture") == 0 and (min(uv) < 0 or max(uv[0],uv[2]) > width or max(uv[1],uv[3]) > height):
                fail("UV is outside the atlas")

    for anim in data.get("animations", []):
        for bone_id, track in anim.get("animators", {}).items():
            if bone_id not in bones or not track.get("keyframes"):
                fail("Animation references missing bone or empty track")
    names = {a.get("name") for a in data.get("animations", [])}
    for name in ("idle","walk","attack","fly"):
        if name not in names:
            fail("Missing animation " + name)
    for name in ("false_sigil","blood_pulse","mirror_strike","nightfall"):
        if name not in names:
            fail("Missing phase-specific boss animation " + name)
    coords = [e for e in visible if e.get("name") not in {"hitbox_volume","shadow_caster"}]
    min_x = min(min(e["from"][0],e["to"][0]) for e in coords)
    max_x = max(max(e["from"][0],e["to"][0]) for e in coords)
    min_y = min(min(e["from"][1],e["to"][1]) for e in coords)
    max_y = max(max(e["from"][1],e["to"][1]) for e in coords)
    model_height = (max_y-min_y)/16
    wing_span = (max_x-min_x)/16
    if model_height < 10:
        fail(f"Visible height {model_height:.2f} blocks is below 10")
    if wing_span < 8:
        fail(f"Wing span {wing_span:.2f} blocks is below 8")
    hitbox = next((g for g in data["outliner"][0]["children"] if isinstance(g,dict) and g.get("name")=="hitbox"),None)
    if not hitbox or hitbox.get("origin", [0,0,0])[1] < 120:
        fail("Hitbox bone pivot must be high enough for a 10-block boss")
    print(f"PASS: {len(visible)-2} visible cubes, {len(bones)} bones, {len(data.get('animations', []))} animations")
    print(f"PASS: {model_height:.2f}-block height, {wing_span:.2f}-block wing span")
    print("PASS: parenting, cube rotations, hitbox pivot, UVs, PNG and phase-specific boss animations")
if __name__ == "__main__":
    main()
