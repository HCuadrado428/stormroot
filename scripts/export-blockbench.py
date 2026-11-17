# Exporta cada bloque e item del mod a Blockbench (.bbmodel con la textura dentro) y PNG (texturas y vistas).
# Uso, desde la raíz del repo: python3 scripts/export-blockbench.py  ->  build/blockbench/
# Los .bbmodel de art/blockbench/ salen de aquí.
import json, base64, uuid, os, shutil
from PIL import Image
A="src/main/resources/assets/stormroot"
OUT="build/blockbench"
for d in ["bbmodel","png/texturas","png/vistas"]: os.makedirs(f"{OUT}/{d}",exist_ok=True)

def tex_path(ref):
    ns,p=ref.split(":") if ":" in ref else ("minecraft",ref)
    if ns!="stormroot": return None
    return f"{A}/textures/{p}.png"

def auto_uv(face,f,t):
    x1,y1,z1=f; x2,y2,z2=t
    return {"north":[16-x2,16-y2,16-x1,16-y1],"south":[x1,16-y2,x2,16-y1],
            "east":[16-z2,16-y2,16-z1,16-y1],"west":[z1,16-y2,z2,16-y1],
            "up":[x1,z1,x2,z2],"down":[x1,16-z2,x2,16-z1]}[face]

def cross(tex):
    return [{"from":[0.8,0,8],"to":[15.2,16,8],"rotation":{"origin":[8,8,8],"axis":"y","angle":45,"rescale":True},
             "faces":{"north":{"uv":[0,0,16,16],"texture":tex},"south":{"uv":[0,0,16,16],"texture":tex}}},
            {"from":[8,0,0.8],"to":[8,16,15.2],"rotation":{"origin":[8,8,8],"axis":"y","angle":45,"rescale":True},
             "faces":{"west":{"uv":[0,0,16,16],"texture":tex},"east":{"uv":[0,0,16,16],"texture":tex}}}]

def resolve(model):
    textures=dict(model.get("textures",{})); elements=model.get("elements")
    parent=model.get("parent","")
    if elements is None:
        if parent.endswith("cube_all"):
            elements=[{"from":[0,0,0],"to":[16,16,16],"faces":{f:{"texture":"#all"} for f in ["north","south","east","west","up","down"]}}]
        elif parent.endswith("block/cross"):
            elements=cross("#cross")
        elif parent.endswith("item/generated"):
            elements=[{"from":[0,0,8],"to":[16,16,8],"faces":{"north":{"uv":[0,0,16,16],"texture":"#layer0"},"south":{"uv":[0,0,16,16],"texture":"#layer0"}}}]
        elif parent.startswith("stormroot:block/"):
            m=json.load(open(f"{A}/models/block/{parent.split('/')[-1]}.json")); t,e=resolve(m); t.update(textures); return t,e
    return textures,elements

def build(name, model, kind):
    textures,elements=resolve(model)
    keys={}; texlist=[]
    def tid(ref):
        while ref.startswith("#"): ref=textures.get(ref[1:],ref)
        if ref.startswith("#"): return None
        if ref not in keys:
            path=tex_path(ref)
            if not path or not os.path.exists(path): return None
            im=Image.open(path); keys[ref]=len(texlist)
            src="data:image/png;base64,"+base64.b64encode(open(path,"rb").read()).decode()
            folder,fname=ref.split(":")[1].rsplit("/",1)
            texlist.append({"path":"","name":fname+".png","folder":folder,"namespace":"stormroot","id":str(len(texlist)),
                "width":im.width,"height":im.height,"uv_width":16,"uv_height":16,"particle":False,"render_mode":"default",
                "render_sides":"auto","frame_time":1,"frame_order_type":"loop","frame_order":"","frame_interpolate":False,
                "visible":True,"internal":True,"saved":True,"uuid":str(uuid.uuid4()),"relative_path":"","source":src})
            shutil.copy(path,f"{OUT}/png/texturas/{fname}.png")
        return keys[ref]
    els=[]
    for i,e in enumerate(elements or []):
        faces={}
        for fn,fc in e.get("faces",{}).items():
            t=tid(fc["texture"])
            faces[fn]={"uv":fc.get("uv",auto_uv(fn,e["from"],e["to"])),"texture":t}
            if "rotation" in fc: faces[fn]["rotation"]=fc["rotation"]
        rot=[0,0,0]; origin=[8,8,8]; rescale=False
        if "rotation" in e:
            r=e["rotation"]; rot["xyz".index(r["axis"])]=r["angle"]; origin=r["origin"]; rescale=r.get("rescale",False)
        els.append({"name":e.get("name",f"pieza_{i+1}"),"box_uv":False,"rescale":rescale,"locked":False,"render_order":"default",
            "allow_mirror_modeling":True,"from":e["from"],"to":e["to"],"autouv":0,"color":i%8,"origin":origin,"rotation":rot,
            "faces":faces,"type":"cube","uuid":str(uuid.uuid4())})
    bb={"meta":{"format_version":"4.10","model_format":"java_block","box_uv":False},"name":name,"parent":"",
        "ambientocclusion":True,"front_gui_light":False,"visible_box":[1,1,0],"variable_placeholders":"",
        "variable_placeholder_buttons":[],"timeline_setups":[],"unhandled_root_fields":{},
        "resolution":{"width":16,"height":16},"elements":els,"outliner":[e["uuid"] for e in els],"textures":texlist}
    if "display" in model: bb["display"]=model["display"]
    json.dump(bb,open(f"{OUT}/bbmodel/{name}.bbmodel","w"),indent=1)
    return textures,elements,tid

# ---- vista isométrica sencilla (cubos sin rotación; las cruces y los items se dibujan planos)
def view(name, elements, textures, tid_map):
    S=24  # px por unidad
    W=H=int(16*S*2.2)
    img=Image.new("RGBA",(W,H),(0,0,0,0))
    import math
    def P(x,y,z):  # isométrica: x a la derecha-abajo, z a la izquierda-abajo
        return (W/2+(x-z)*S*0.87, H*0.62+(x+z)*S*0.5-y*S)
    rotated=any("rotation" in e for e in elements)
    if rotated or all(e["from"][2]==e["to"][2] for e in elements):
        # plano: la textura ampliada
        ref=None
        for e in elements:
            for fc in e["faces"].values(): ref=fc["texture"]; break
            break
        while ref and ref.startswith("#"): ref=textures.get(ref[1:])
        p=tex_path(ref) if ref else None
        if p: Image.open(p).convert("RGBA").resize((256,256),Image.NEAREST).save(f"{OUT}/png/vistas/{name}.png")
        return
    faces=[]
    for e in elements:
        (x1,y1,z1),(x2,y2,z2)=e["from"],e["to"]
        for fn,quad,shade in [("up",[(x1,y2,z1),(x2,y2,z1),(x2,y2,z2),(x1,y2,z2)],1.0),
                              ("south",[(x1,y2,z2),(x2,y2,z2),(x2,y1,z2),(x1,y1,z2)],0.8),
                              ("east",[(x2,y2,z2),(x2,y2,z1),(x2,y1,z1),(x2,y1,z2)],0.6)]:
            fc=e["faces"].get(fn)
            if not fc: continue
            ref=fc["texture"]
            while ref.startswith("#"): ref=textures.get(ref[1:],ref)
            p=tex_path(ref)
            if not p: continue
            uv=fc.get("uv",auto_uv(fn,e["from"],e["to"]))
            depth=sum(q[0]+q[2]+q[1]*0.01 for q in quad)/4
            faces.append((depth,quad,p,uv,shade))
    faces.sort(key=lambda f:f[0])
    for _,quad,p,uv,shade in faces:
        tex=Image.open(p).convert("RGBA")
        u1,v1,u2,v2=uv; sx=tex.width/16
        box=(int(min(u1,u2)*sx),int(min(v1,v2)*sx),max(int(max(u1,u2)*sx),int(min(u1,u2)*sx)+1),max(int(max(v1,v2)*sx),int(min(v1,v2)*sx)+1))
        crop=tex.crop(box).resize((64,64),Image.NEAREST)
        px=[c*shade for c in (1,)] 
        r,g,b,a=crop.split()
        crop=Image.merge("RGBA",[ch.point(lambda v:int(v*shade)) for ch in (r,g,b)]+[a])
        pts=[P(*q) for q in quad]
        # transformación afín de la cara (paralelogramo: p0, p1, p3)
        (x0,y0),(x1_,y1_),_,(x3,y3)=pts
        ax,ay=(x1_-x0)/64,(y1_-y0)/64; bx,by=(x3-x0)/64,(y3-y0)/64
        det=ax*by-ay*bx
        if abs(det)<1e-9: continue
        ia,ib,ic,id_=by/det,-bx/det,-ay/det,ax/det
        coeffs=(ia,ib,-(ia*x0+ib*y0),ic,id_,-(ic*x0+id_*y0))
        layer=crop.transform((W,H),Image.AFFINE,coeffs,resample=Image.NEAREST)
        img.alpha_composite(layer)
    bbox=img.getbbox()
    if bbox: img=img.crop(bbox)
    img.save(f"{OUT}/png/vistas/{name}.png")

done=[]
for kind in ["block","item"]:
    for f in sorted(os.listdir(f"{A}/models/{kind}")):
        name=f[:-5]
        if kind=="item" and os.path.exists(f"{A}/models/block/{name}.json") and json.load(open(f"{A}/models/item/{f}")).get("parent","").startswith("stormroot:block"):
            continue  # el item usa el modelo del bloque
        if name.startswith("storm_fire"): continue  # usa las llamas de vanilla
        model=json.load(open(f"{A}/models/{kind}/{f}"))
        textures,elements,tid=build(name if kind=="block" else name+"_item" if os.path.exists(f"{A}/models/block/{name}.json") else name, model, kind)
        view(name if kind=="block" else name+"_item" if os.path.exists(f"{A}/models/block/{name}.json") else name, elements, textures, None)
        done.append((kind,name))
shutil.copy(f"{A}/icon.png" if os.path.exists(f"{A}/icon.png") else "/dev/null", f"{OUT}/png/texturas/icon.png")
print(done)
