#!/usr/bin/env python3
"""Structural validation for the Vampire King Blockbench/ModelEngine blueprint."""
import base64,binascii,json,pathlib,struct,sys,zlib
MODEL=pathlib.Path("mythicmobs/models/vampire_king.bbmodel")
TEXTURE=pathlib.Path("mythicmobs/models/vampire_king.png")
def fail(m): print("ERROR:",m,file=sys.stderr);raise SystemExit(1)
def main():
 if not MODEL.exists() or not TEXTURE.exists():fail("model or PNG missing")
 d=json.loads(MODEL.read_text(encoding="utf-8"))
 if d.get("meta",{}).get("model_format")!="free":fail("Expected Generic/Free format")
 if d.get("meta",{}).get("box_uv",False):fail("Box UV not allowed for ModelEngine")
 ts=d.get("textures",[])
 if not ts or not ts[0].get("source","").startswith("data:image/png;base64,"):fail("Embedded texture missing")
 data=base64.b64decode(ts[0]["source"].split(",",1)[1],validate=True);png=TEXTURE.read_bytes()
 if data!=png:fail("Embedded and external PNG differ")
 if png[:8]!=b"\x89PNG\r\n\x1a\n":fail("PNG signature invalid")
 w,h=struct.unpack(">II",png[16:24])
 if(w,h)!=(128,128):fail("Atlas is not 128x128")
 off=8;idat=bytearray();iend=False
 while off<len(png):
  n=struct.unpack(">I",png[off:off+4])[0];kind=png[off+4:off+8];chunk=png[off+8:off+8+n];crc=struct.unpack(">I",png[off+8+n:off+12+n])[0]
  if(binascii.crc32(kind+chunk)&0xffffffff)!=crc:fail("PNG CRC mismatch")
  if kind==b"IDAT":idat.extend(chunk)
  off+=n+12
  if kind==b"IEND":iend=True;break
 if not iend:fail("PNG missing IEND")
 try:raw=zlib.decompress(bytes(idat))
 except zlib.error as e:fail("PNG deflate stream invalid: "+str(e))
 if len(raw)!=h*(1+w*4):fail("PNG scanline length invalid")
 bones={}; paths={}
 def walk(n,parents=()):
  if isinstance(n,str):paths[n]=parents;return
  name=n.get("name","")
  if n.get("uuid"):bones[n["uuid"]]=name
  p=parents+((name,) if name else ())
  for c in n.get("children",[]):walk(c,p)
 for n in d.get("outliner",[]):walk(n)
 required={"root","body","head","left_arm","right_arm","left_leg","right_leg","left_wing","right_wing","cloak","weapon","hitbox","shadow"}
 if not required.issubset(set(bones.values())):fail("Missing bones: "+str(required-set(bones.values())))
 seen=set();vis=[]
 for e in d.get("elements",[]):
  u=e.get("uuid")
  if not u or u in seen:fail("duplicate/missing element UUID")
  seen.add(u)
  if e.get("type")!="cube":fail("unsupported element")
  if "hitbox" not in paths.get(u,()) and "shadow" not in paths.get(u,()):vis.append(e)
  f=e.get("faces",{})
  if set(f)!={"north","east","south","west","up","down"}:fail("missing cube face: "+e.get("name",""))
  for face in f.values():
   uv=face.get("uv")
   if uv is None or len(uv)!=4:fail("malformed per-face UV")
   if face.get("texture")==0 and (min(uv)<0 or max(uv[0],uv[2])>w or max(uv[1],uv[3])>h):fail("UV out of bounds")
 for a in d.get("animations",[]):
  for bid,track in a.get("animators",{}).items():
   if bid not in bones or not track.get("keyframes"):fail("invalid animation bone track")
 names={a.get("name") for a in d.get("animations",[])}
 for name in ("idle","walk","attack","fly"):
  if name not in names:fail("missing animation "+name)
 x0=min(min(e["from"][0],e["to"][0]) for e in vis);x1=max(max(e["from"][0],e["to"][0]) for e in vis)
 y0=min(min(e["from"][1],e["to"][1]) for e in vis);y1=max(max(e["from"][1],e["to"][1]) for e in vis)
 height=(y1-y0)/16;width=(x1-x0)/16
 if height<10:fail(f"height {height:.2f} blocks < 10")
 if width<8:fail(f"wing span {width:.2f} blocks < 8")
 print(f"PASS model: {len(vis)} visible cubes; {len(bones)} bones; {len(d.get('animations',[]))} animations")
 print(f"PASS bounds: height {height:.2f} blocks; wing span {width:.2f} blocks")
 print(f"PASS texture: embedded/external {w}x{h} PNGs match and decode")
if __name__=="__main__":main()
