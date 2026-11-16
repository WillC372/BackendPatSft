# Builds a colored STEP model of the 38-pin ESP32 DevKit (USB-C) in the frame of the KiCad footprint:
# x along the board (USB towards +x, antenna towards -x), y up (= -footprint y), z up; devkit PCB bottom at z = 0.
# The male headers hang below (z < 0) so the model drops into two 1x19 sockets.
import sys
import math
import cadquery as cq
from OCP.STEPCAFControl import STEPCAFControl_Reader, STEPCAFControl_Writer
from OCP.TDocStd import TDocStd_Document
from OCP.XCAFApp import XCAFApp_Application
from OCP.XCAFDoc import XCAFDoc_DocumentTool, XCAFDoc_ColorSurf, XCAFDoc_ColorGen, XCAFDoc_ColorTool
from OCP.TCollection import TCollection_ExtendedString
from OCP.TDF import TDF_LabelSequence
from OCP.TopLoc import TopLoc_Location
from OCP.gp import gp_Trsf, gp_Ax1, gp_Pnt, gp_Dir, gp_Vec
from OCP.Quantity import Quantity_Color, Quantity_TOC_RGB
from OCP.IFSelect import IFSelect_RetDone
from OCP.STEPControl import STEPControl_AsIs
from OCP.TDataStd import TDataStd_Name

M, OUT = sys.argv[1], sys.argv[2]
app = XCAFApp_Application.GetApplication_s()
doc = TDocStd_Document(TCollection_ExtendedString("MDTV-XCAF"))
app.InitDocument(doc)
shapes = XCAFDoc_DocumentTool.ShapeTool_s(doc.Main())
colors = XCAFDoc_DocumentTool.ColorTool_s(doc.Main())


from OCP.TopExp import TopExp_Explorer
from OCP.TopAbs import TopAbs_FACE, TopAbs_SOLID
from OCP.TopoDS import TopoDS, TopoDS_Compound, TopoDS_Shell
from OCP.BRep import BRep_Builder
from OCP.BRepBuilderAPI import BRepBuilderAPI_Transform


def faces_by_color(path):
    """Reads a STEP and returns {(r,g,b): [faces]} with the color of each face (face, solid or part color)."""
    d = TDocStd_Document(TCollection_ExtendedString("MDTV-XCAF"))
    app.InitDocument(d)
    r = STEPCAFControl_Reader()
    r.SetColorMode(True)
    assert r.ReadFile(path) == IFSelect_RetDone, path
    r.Transfer(d)
    st = XCAFDoc_DocumentTool.ShapeTool_s(d.Main())
    ct = XCAFDoc_DocumentTool.ColorTool_s(d.Main())
    free = TDF_LabelSequence()
    st.GetFreeShapes(free)
    out = {}
    col = Quantity_Color()

    def color_of(shape):
        for kind in (XCAFDoc_ColorSurf, XCAFDoc_ColorGen):
            if ct.GetColor(shape, kind, col):
                return (round(col.Red(), 3), round(col.Green(), 3), round(col.Blue(), 3))
        return None

    for i in range(free.Length()):
        top = st.GetShape_s(free.Value(i + 1))
        base = color_of(top)
        # Colors stored on the parts of an assembly: walk the labels.
        labels = TDF_LabelSequence()
        st.GetShapes(labels)
        label_colors = []
        for j in range(labels.Length()):
            lab = labels.Value(j + 1)
            c = None
            for kind in (XCAFDoc_ColorSurf, XCAFDoc_ColorGen):
                if XCAFDoc_ColorTool.GetColor_s(lab, kind, col):
                    c = (round(col.Red(), 3), round(col.Green(), 3), round(col.Blue(), 3))
                    break
            if c:
                label_colors.append((st.GetShape_s(lab), c))
        sx = TopExp_Explorer(top, TopAbs_SOLID)
        solids = []
        while sx.More():
            solids.append(sx.Current())
            sx.Next()
        targets = solids or [top]
        for so in targets:
            sc = color_of(so)
            if sc is None:
                for shp, c in label_colors:
                    if shp.IsSame(so) or shp.IsPartner(so):
                        sc = c
                        break
            if sc is not None:
                out.setdefault(sc, []).append(so)
                continue
            # Solid with colored faces (for example the WROOM module): one shell per color.
            groups = {}
            fx = TopExp_Explorer(so, TopAbs_FACE)
            while fx.More():
                f = fx.Current()
                c = color_of(f)
                if c is None:
                    for shp, lc in label_colors:
                        if shp.IsPartner(f):
                            c = lc
                            break
                groups.setdefault(c or base or (0.7, 0.7, 0.7), []).append(f)
                fx.Next()
            for c, faces in groups.items():
                shell = TopoDS_Shell()
                bld = BRep_Builder()
                bld.MakeShell(shell)
                for f in faces:
                    bld.Add(shell, f)
                out.setdefault(c, []).append(shell)
    return out


def trsf(rx=0, rz=0, t=(0, 0, 0)):
    a = gp_Trsf(); a.SetRotation(gp_Ax1(gp_Pnt(0, 0, 0), gp_Dir(1, 0, 0)), math.radians(rx))
    b = gp_Trsf(); b.SetRotation(gp_Ax1(gp_Pnt(0, 0, 0), gp_Dir(0, 0, 1)), math.radians(rz))
    c = gp_Trsf(); c.SetTranslation(gp_Vec(*t))
    return c.Multiplied(b.Multiplied(a))  # first rotate about X, then about Z, then move


def rgb(r, g, b):
    return Quantity_Color(r, g, b, Quantity_TOC_RGB)


asm = shapes.NewShape()
TDataStd_Name.Set_s(asm, TCollection_ExtendedString("ESP32_DevKit_38P_USB-C"))
parts = []


def place(path, name, rx=0, rz=0, t=(0, 0, 0)):
    tr = trsf(rx, rz, t)
    k = 0
    for c, solids in faces_by_color(path).items():
        for so in solids:
            moved = BRepBuilderAPI_Transform(so, tr, True).Shape()
            solid(moved, f"{name}-{k}", rgb(*c))
            k += 1


def solid(shape, name, color):
    lab = shapes.AddShape(shape, False)
    colors.SetColor(lab, color, XCAFDoc_ColorSurf)
    colors.SetColor(lab, color, XCAFDoc_ColorGen)

    TDataStd_Name.Set_s(lab, TCollection_ExtendedString(name))
    comp = shapes.AddComponent(asm, lab, TopLoc_Location())
    TDataStd_Name.Set_s(comp, TCollection_ExtendedString(name))


X0, X1, HALF_W, T = -27.16, 27.24, 14.0, 1.6
PITCH, ROW = 2.54, 12.7
# Black FR4 with the plated holes of the headers.
board = cq.Workplane("XY").box(X1 - X0, 2 * HALF_W, T, centered=(False, True, False)).translate((X0, 0, 0)).edges("|Z").fillet(0.8)
for r in (-ROW, ROW):
    for i in range(19):
        board = board.cut(cq.Workplane("XY").circle(0.5).extrude(T).translate((-22.86 + i * PITCH, r, 0)))
board = board.cut(cq.Workplane("XY").circle(1.4).extrude(T).translate((X0 + 2.0, HALF_W - 2.0, 0)))
board = board.cut(cq.Workplane("XY").circle(1.4).extrude(T).translate((X0 + 2.0, -HALF_W + 2.0, 0)))
board = board.cut(cq.Workplane("XY").circle(1.4).extrude(T).translate((X1 - 2.0, HALF_W - 2.0, 0)))
board = board.cut(cq.Workplane("XY").circle(1.4).extrude(T).translate((X1 - 2.0, -HALF_W + 2.0, 0)))
solid(board.val().wrapped, "PCB", rgb(0.04, 0.045, 0.05))

# Gold pads around the header holes (top side).
pads = cq.Workplane("XY")
for r in (-ROW, ROW):
    for i in range(19):
        pads = pads.union(cq.Workplane("XY").circle(0.85).circle(0.5).extrude(0.04).translate((-22.86 + i * PITCH, r, T)))
solid(pads.val().wrapped, "Pads", rgb(0.85, 0.68, 0.25))

# White pin labels as printed on the user's board (rows: footprint y=+12.7 -> model y=-12.7).
LEFT = ["3V3", "EN", "VP", "VN", "G34", "G35", "G32", "G33", "G25", "G26", "G27", "G14", "G12", "GND", "G13", "SD2", "SD3", "CMD", "5V"]
RIGHT = ["GND", "G23", "G22", "TXD", "RXD", "G21", "GND", "G19", "G18", "G5", "G17", "G16", "G4", "G0", "G2", "G15", "SD1", "SD0", "CLK"]
silk = None
for names, y, sign in ((LEFT, -ROW + 2.3, 1), (RIGHT, ROW - 2.3, -1)):
    for i, nm in enumerate(names):
        t = (cq.Workplane("XY").text(nm, 1.05, 0.03, font="DejaVu Sans", kind="bold", halign="left" if sign > 0 else "right", valign="center")
             .rotate((0, 0, 0), (0, 0, 1), 90).translate((-22.86 + i * PITCH, y, T)))
        silk = t if silk is None else silk.union(t)
for txt, pos, size, rot in (("USB-C", (21.0, 0, T), 1.4, 90), ("EN", (20.3, 6.4, T), 1.0, 0), ("BOOT", (20.3, -6.4, T), 1.0, 0)):
    silk = silk.union(cq.Workplane("XY").text(txt, size, 0.03, font="DejaVu Sans", kind="bold", halign="center", valign="center")
                      .rotate((0, 0, 0), (0, 0, 1), rot).translate(pos))
solid(silk.val().wrapped, "Silkscreen", rgb(0.95, 0.95, 0.95))

# Real KiCad models.
place(f"{M}/RF_Module.3dshapes/ESP32-WROOM-32.step", "ESP32-WROOM-32", rz=90, t=(X0 + 15.74, 0, T))
place(f"{M}/Connector_USB.3dshapes/USB_C_Receptacle_GCT_USB4105-xx-A_16P_TopMnt_Horizontal.step", "USB-C", rz=90, t=(X1 + 0.6 - 3.68, 0, T))
place(f"{M}/Package_TO_SOT_SMD.3dshapes/SOT-223.step", "AMS1117-3.3", rz=90, t=(13.5, 6.0, T))
place(f"{M}/Package_DFN_QFN.3dshapes/QFN-24-1EP_4x4mm_P0.5mm_EP2.6x2.6mm.step", "USB-UART", rz=45, t=(10.0, -5.2, T))
place(f"{M}/Button_Switch_SMD.3dshapes/SW_SPST_TL3342.step", "EN", t=(23.2, 7.6, T))
place(f"{M}/Button_Switch_SMD.3dshapes/SW_SPST_TL3342.step", "BOOT", t=(23.2, -7.6, T))
for i, (x, y) in enumerate(((5.0, 9.0), (5.0, -9.4))):
    place(f"{M}/LED_SMD.3dshapes/LED_0603_1608Metric.step", f"LED{i+1}", t=(x, y, T))
for i, (x, y, r) in enumerate(((1.5, 4.0, 90), (1.5, 1.5, 90), (1.5, -1.0, 90), (1.5, -3.5, 90), (6.0, 2.0, 0), (17.5, -2.5, 0))):
    place(f"{M}/Resistor_SMD.3dshapes/R_0603_1608Metric.step", f"R{i+1}", rz=r, t=(x, y, T))
for i, (x, y) in enumerate(((17.5, 1.0), (9.5, 9.5), (17.5, -6.0))):
    place(f"{M}/Capacitor_SMD.3dshapes/C_0805_2012Metric.step", f"C{i+1}", rz=90, t=(x, y, T))
# Male headers under the board: flipped about X (plastic below, long pins down), then turned to run along +x.
for y in (-ROW, ROW):
    place(f"{M}/Connector_PinHeader_2.54mm.3dshapes/PinHeader_1x19_P2.54mm_Vertical.step", "Header", rx=180, rz=-90, t=(-22.86, y, 0))

shapes.UpdateAssemblies()
w = STEPCAFControl_Writer()
w.SetColorMode(True)
w.SetNameMode(True)
w.Transfer(doc, STEPControl_AsIs)
assert w.Write(OUT) == IFSelect_RetDone
print("ok", OUT)
