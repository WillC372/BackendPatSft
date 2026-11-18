"""Builds AquaAssistantPh.kicad_pcb with the KiCad API: footprints, nets, copper tracks, GND pours, antenna keep-out,
silkscreen, dimensions and the link of every footprint to its schematic symbol."""
import json
import os
import sys

import pcbnew
from pcbnew import VECTOR2I, FromMM

sys.path.insert(0, os.path.dirname(__file__))
from design import PARTS, PROJECT, TITLE, REV, DATE, COMPANY  # noqa: E402

OUT = sys.argv[1]
SYM_UUIDS = json.load(open(os.path.join(OUT, ".symbol_uuids.json")))  # written by gen_sch.py
FP_DIRS = {"Resistor_THT": "/usr/share/kicad/footprints/Resistor_THT.pretty",
           "Connector_PinSocket_2.54mm": "/usr/share/kicad/footprints/Connector_PinSocket_2.54mm.pretty",
           "MountingHole": "/usr/share/kicad/footprints/MountingHole.pretty",
           "AquaAssistant": os.path.join(OUT, "AquaAssistant.pretty")}
OX, OY = 100.0, 70.0  # board origin on the drawing sheet
W, H, RC = 80.0, 44.0, 2.0  # board size and corner radius (mm)


def P(x, y):
    return VECTOR2I(FromMM(OX + x), FromMM(OY + y))


board = pcbnew.NewBoard(os.path.join(OUT, PROJECT + ".kicad_pcb"))
board.SetCopperLayerCount(2)
ds = board.GetDesignSettings()
ds.SetBoardThickness(FromMM(1.6))
ds.m_TrackMinWidth = FromMM(0.25)
ds.m_ViasMinSize = FromMM(0.6)
ds.m_MinThroughDrill = FromMM(0.3)
ds.m_CopperEdgeClearance = FromMM(0.5)
nc = ds.m_NetSettings.m_DefaultNetClass
nc.SetClearance(FromMM(0.25))
nc.SetTrackWidth(FromMM(0.5))
nc.SetViaDiameter(FromMM(0.8))
nc.SetViaDrill(FromMM(0.4))
tb = board.GetTitleBlock()
tb.SetTitle(TITLE)
tb.SetRevision(REV)
tb.SetDate(DATE)
tb.SetCompany(COMPANY)
tb.SetComment(0, "PCB 2 capas, FR-4 1,6 mm, cobre 35 um, acabado HASL, mascara verde, serigrafia blanca")
tb.SetComment(1, "Plano de GND en ambas caras; sin cobre bajo la antena del ESP32")

nets = {}
for name in ("GND", "+5V", "/PH_OUT", "/PH_ADC"):
    n = pcbnew.NETINFO_ITEM(board, name)
    board.Add(n)
    nets[name] = n

# ---------- footprints ----------
PLACE = {  # ref: (x, y, orientation)
    "U1": (48.0, 22.0, 0),
    "J1": (4.5, 10.0, 0),
    "R1": (10.0, 8.0, 270),
    "R2": (15.0, 18.16, 270),
    "H1": (3.5, 3.5, 0), "H2": (76.5, 3.5, 0), "H3": (3.5, 40.5, 0), "H4": (76.5, 40.5, 0),
}
PAD_NETS = {
    ("J1", "1"): "/PH_OUT", ("J1", "2"): "GND", ("J1", "3"): "+5V",
    ("R1", "1"): "/PH_OUT", ("R1", "2"): "/PH_ADC",
    ("R2", "1"): "/PH_ADC", ("R2", "2"): "GND",
    ("U1", "5"): "/PH_ADC", ("U1", "14"): "GND", ("U1", "19"): "+5V",
}
fps = {}
for ref, (lib_fp, value, _desc) in PARTS.items():
    lib, name = lib_fp.split(":")
    fp = pcbnew.FootprintLoad(FP_DIRS[lib], name)
    fp.SetFPID(pcbnew.LIB_ID(lib, name))
    fp.SetReference(ref)
    fp.SetValue(value)
    x, y, rot = PLACE[ref]
    fp.SetPosition(P(x, y))
    fp.SetOrientationDegrees(rot)
    fp.SetPath(pcbnew.KIID_PATH("/" + SYM_UUIDS[ref]))
    fp.SetProperty("Sheetname", "")
    fp.SetProperty("Sheetfile", PROJECT + ".kicad_sch")
    board.Add(fp)
    fps[ref] = fp
    for pad in fp.Pads():
        key = (ref, pad.GetNumber())
        if key in PAD_NETS:
            pad.SetNet(nets[PAD_NETS[key]])
        elif ref == "U1":
            pad.SetNet(board.GetNetInfo().OrphanedItem())
# Reference texts that would fall on pads or tracks.
fps["R1"].Reference().SetPosition(P(12.6, 13.08))
fps["R1"].Reference().SetTextAngleDegrees(90)
fps["R1"].Value().SetPosition(P(10.0, 13.08))
fps["R2"].Reference().SetPosition(P(17.6, 23.24))
fps["R2"].Reference().SetTextAngleDegrees(90)
fps["R2"].Value().SetPosition(P(15.0, 23.24))
fps["J1"].Reference().SetPosition(P(4.5, 6.7))
for h in ("H1", "H2", "H3", "H4"):
    fps[h].Reference().SetVisible(False)


def pad_pos(ref, num):
    p = fps[ref].FindPadByNumber(num).GetPosition()
    return (pcbnew.ToMM(p.x) - OX, pcbnew.ToMM(p.y) - OY)


# ---------- tracks (top layer) ----------
def track(points, net, width, layer=pcbnew.F_Cu):
    for (a, b) in zip(points, points[1:]):
        t = pcbnew.PCB_TRACK(board)
        t.SetStart(P(*a))
        t.SetEnd(P(*b))
        t.SetWidth(FromMM(width))
        t.SetLayer(layer)
        t.SetNet(nets[net])
        board.Add(t)


j1 = [pad_pos("J1", n) for n in ("1", "2", "3")]
r1 = [pad_pos("R1", n) for n in ("1", "2")]
r2 = [pad_pos("R2", n) for n in ("1", "2")]
g34, gnd, v5 = pad_pos("U1", "5"), pad_pos("U1", "14"), pad_pos("U1", "19")
track([j1[0], (j1[0][0] + 2.0, j1[0][1] - 2.0), r1[0]], "/PH_OUT", 0.5)
track([r1[1], r2[0]], "/PH_ADC", 0.5)
track([r1[1], (r1[1][0], 38.0), (g34[0], 38.0), g34], "/PH_ADC", 0.5)
track([j1[2], (7.0, j1[2][1]), (7.0, 41.0), (v5[0], 41.0), v5], "+5V", 0.8)
# GND: pads join the pours on both faces; stitching vias tie the faces together.
for (x, y) in ((4.0, 26.0), (4.0, 34.0), (45.0, 38.0), (62.0, 38.5), (33.0, 4.5), (62.0, 4.5), (78.0, 22.0), (18.0, 41.0 + 1.7)):
    v = pcbnew.PCB_VIA(board)
    v.SetPosition(P(x, y))
    v.SetWidth(FromMM(0.8))
    v.SetDrill(FromMM(0.4))
    v.SetViaType(pcbnew.VIATYPE_THROUGH)
    v.SetLayerPair(pcbnew.F_Cu, pcbnew.B_Cu)
    v.SetNet(nets["GND"])
    board.Add(v)

# ---------- outline (rounded rectangle) ----------
def seg(a, b, layer=pcbnew.Edge_Cuts, w=0.1):
    s = pcbnew.PCB_SHAPE(board)
    s.SetShape(pcbnew.SHAPE_T_SEGMENT)
    s.SetStart(P(*a))
    s.SetEnd(P(*b))
    s.SetLayer(layer)
    s.SetWidth(FromMM(w))
    board.Add(s)


def arc(start, mid, end, layer=pcbnew.Edge_Cuts, w=0.1):
    s = pcbnew.PCB_SHAPE(board)
    s.SetShape(pcbnew.SHAPE_T_ARC)
    s.SetArcGeometry(P(*start), P(*mid), P(*end))
    s.SetLayer(layer)
    s.SetWidth(FromMM(w))
    board.Add(s)


k = RC * (1 - 0.70710678)
seg((RC, 0), (W - RC, 0)); arc((W - RC, 0), (W - k, k), (W, RC))
seg((W, RC), (W, H - RC)); arc((W, H - RC), (W - k, H - k), (W - RC, H))
seg((W - RC, H), (RC, H)); arc((RC, H), (k, H - k), (0, H - RC))
seg((0, H - RC), (0, RC)); arc((0, RC), (k, k), (RC, 0))

# ---------- copper pours and antenna keep-out ----------
def outline(zone, pts):
    o = zone.Outline()
    o.NewOutline()
    for (x, y) in pts:
        o.Append(FromMM(OX + x), FromMM(OY + y))


for layer in (pcbnew.F_Cu, pcbnew.B_Cu):
    z = pcbnew.ZONE(board)
    z.SetLayer(layer)
    z.SetNet(nets["GND"])
    z.SetLocalClearance(FromMM(0.4))
    z.SetMinThickness(FromMM(0.25))
    z.SetPadConnection(pcbnew.ZONE_CONNECTION_THERMAL)
    z.SetThermalReliefGap(FromMM(0.4))
    z.SetThermalReliefSpokeWidth(FromMM(0.5))
    z.SetIsFilled(False)
    z.SetZoneName("GND_" + ("TOP" if layer == pcbnew.F_Cu else "BOTTOM"))
    outline(z, [(0.3, 0.3), (W - 0.3, 0.3), (W - 0.3, H - 0.3), (0.3, H - 0.3)])
    board.Add(z)
keep = pcbnew.ZONE(board)
keep.SetIsRuleArea(True)
keep.SetDoNotAllowCopperPour(True)
keep.SetDoNotAllowTracks(False)
keep.SetDoNotAllowVias(True)
keep.SetDoNotAllowPads(False)
keep.SetDoNotAllowFootprints(False)
ls = pcbnew.LSET()
ls.AddLayer(pcbnew.F_Cu)
ls.AddLayer(pcbnew.B_Cu)
keep.SetLayerSet(ls)
keep.SetZoneName("SIN_COBRE_ANTENA")
outline(keep, [(19.5, 6.5), (28.5, 6.5), (28.5, 37.5), (19.5, 37.5)])
board.Add(keep)

# ---------- silkscreen ----------
def text(s, x, y, size=1.0, layer=pcbnew.F_SilkS, angle=0, just=0, thick=0.15):
    t = pcbnew.PCB_TEXT(board)
    t.SetText(s)
    t.SetPosition(P(x, y))
    t.SetLayer(layer)
    t.SetTextSize(VECTOR2I(FromMM(size), FromMM(size)))
    t.SetTextThickness(FromMM(thick))
    t.SetTextAngleDegrees(angle)
    t.SetHorizJustify(just)
    if layer == pcbnew.B_SilkS:
        t.SetMirrored(True)
    board.Add(t)


for (s, y) in (("PO", 10.0), ("GND", 12.54), ("5V", 15.08)):
    text(s, 2.9, y, 0.8, just=pcbnew.GR_TEXT_H_ALIGN_RIGHT, thick=0.12)
text("SENSOR", 4.5, 18.2, 0.8, thick=0.12)
text("PH-4502C", 4.5, 19.6, 0.8, thick=0.12)
text("Aqua Assistant pH", 40.0, 2.2, 1.2, thick=0.18)
text("v1.0", 56.0, 2.2, 0.9, thick=0.14)
text("Aqua Assistant AI - Medidor de pH v1.0", W / 2, H / 2 - 1.2, 1.4, pcbnew.B_SilkS, thick=0.2)
text("ESP32 + PH-4502C - 2026", W / 2, H / 2 + 1.2, 1.0, pcbnew.B_SilkS, thick=0.15)

# ---------- dimensions (comments layer) ----------
def dim(a, b, height, label_layer=pcbnew.Cmts_User):
    d = pcbnew.PCB_DIM_ALIGNED(board, pcbnew.PCB_DIM_ALIGNED_T)
    d.SetLayer(label_layer)
    d.SetStart(P(*a))
    d.SetEnd(P(*b))
    d.SetHeight(FromMM(height))
    d.SetUnitsMode(pcbnew.DIM_UNITS_MODE_MILLIMETRES)
    d.SetPrecision(pcbnew.DIM_PRECISION_X_XX if hasattr(pcbnew, "DIM_PRECISION_X_XX") else 2)
    d.SetLineThickness(FromMM(0.1))
    d.SetTextSize(VECTOR2I(FromMM(1.0), FromMM(1.0)))
    board.Add(d)


dim((0, 0), (W, 0), -4.0)
dim((0, H), (0, 0), -4.0)
dim((3.5, 40.5), (76.5, 40.5), 6.0)
dim((g34[0], 34.7), (g34[0], 9.3), -6.0)

filler = pcbnew.ZONE_FILLER(board)
filler.Fill(board.Zones())
pcb_file = os.path.join(OUT, PROJECT + ".kicad_pcb")
pcbnew.SaveBoard(pcb_file, board)
# Footprints keep the library 3D paths (.wrl, with the .step next to it in every KiCad install).
print("pcb ok", {r: [round(v, 2) for v in pad_pos(r, "1")] for r in ("J1", "R1", "R2", "U1")}, "R1.2", pad_pos("R1", "2"), "R2.2", pad_pos("R2", "2"))
