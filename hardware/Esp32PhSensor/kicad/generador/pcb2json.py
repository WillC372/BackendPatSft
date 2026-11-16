"""Exports from the .kicad_pcb what the 3D view needs: outline, holes, footprints and their 3D models (KiCad transforms)."""
import json
import os
import sys

import pcbnew

PCB, OUT = sys.argv[1], sys.argv[2]
b = pcbnew.LoadBoard(PCB)
mm = pcbnew.ToMM
env = {"KICAD7_3DMODEL_DIR": "/usr/share/kicad/3dmodels", "KICAD6_3DMODEL_DIR": "/usr/share/kicad/3dmodels",
       "KIPRJMOD": os.path.dirname(os.path.abspath(PCB))}

poly = pcbnew.SHAPE_POLY_SET()
b.GetBoardPolygonOutlines(poly)
outline = poly.Outline(0)
pts = [(mm(outline.CPoint(i).x), mm(outline.CPoint(i).y)) for i in range(outline.PointCount())]
bbox = b.GetBoardEdgesBoundingBox()

holes = []
for fp in b.GetFootprints():
    for pad in fp.Pads():
        d = pad.GetDrillSize()
        if d.x > 0:
            p = pad.GetPosition()
            holes.append({"x": mm(p.x), "y": mm(p.y), "d": mm(d.x), "plated": pad.GetAttribute() == pcbnew.PAD_ATTRIB_PTH,
                          "pad": mm(pad.GetSize().x)})
for t in b.GetTracks():
    if t.GetClass() == "PCB_VIA":
        p = t.GetPosition()
        holes.append({"x": mm(p.x), "y": mm(p.y), "d": mm(t.GetDrillValue()), "plated": True, "pad": mm(t.GetWidth())})

fps = []
for fp in b.GetFootprints():
    models = []
    for m in fp.Models():
        path = m.m_Filename
        for k, v in env.items():
            path = path.replace("${" + k + "}", v)
        if not m.m_Show or not os.path.exists(path):
            continue
        models.append({"file": path, "offset": [m.m_Offset.x, m.m_Offset.y, m.m_Offset.z],
                       "rotation": [m.m_Rotation.x, m.m_Rotation.y, m.m_Rotation.z], "scale": [m.m_Scale.x, m.m_Scale.y, m.m_Scale.z]})
    p = fp.GetPosition()
    fps.append({"ref": fp.GetReference(), "x": mm(p.x), "y": mm(p.y), "rot": fp.GetOrientationDegrees(),
                "back": fp.GetLayer() == pcbnew.B_Cu, "models": models})

json.dump({"outline": pts, "bbox": [mm(bbox.GetX()), mm(bbox.GetY()), mm(bbox.GetWidth()), mm(bbox.GetHeight())],
           "thickness": mm(b.GetDesignSettings().GetBoardThickness()), "holes": holes, "footprints": fps},
          open(OUT, "w"), indent=1)
print("json ok", len(pts), "outline points,", len(holes), "holes,", len(fps), "footprints")
