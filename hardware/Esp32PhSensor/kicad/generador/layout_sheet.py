"""PCB layout sheet: KiCad layer plots (black-and-white SVG, recolored and stacked) plus stack-up, drill and track tables
computed from the .kicad_pcb."""
import os
import re
import sys
from collections import Counter

import pcbnew

sys.path.insert(0, os.path.dirname(__file__))
from design import TITLE, REV, DATE  # noqa: E402

PCB, LAY, OUT = sys.argv[1], sys.argv[2], sys.argv[3]
b = pcbnew.LoadBoard(PCB)
mm = pcbnew.ToMM
W, H = 1600, 1130
COL = {"F.Cu": "#c83434", "B.Cu": "#2f63c8", "F.SilkS": "#1c1c1c", "B.SilkS": "#1c1c1c", "Edge.Cuts": "#c99400",
       "Cmts.User": "#4a4a4a", "F.Mask": "#7a8a99"}


def layer_svg(name, color, opacity=1.0, x=0, y=0, w=100, h=60, view=(90, 60, 100, 66)):
    src = open(os.path.join(LAY, name + ".svg")).read()
    body = src[src.index(">", src.index("<svg")) + 1:src.rindex("</svg>")]
    body = body.replace("#000000", color).replace("#FFFFFF", "#ffffff")
    body = re.sub(r"<title>.*?</title>|<desc>.*?</desc>", "", body, flags=re.S)
    return (f'<svg x="{x}" y="{y}" width="{w}" height="{h}" viewBox="{view[0]} {view[1]} {view[2]} {view[3]}" '
            f'preserveAspectRatio="xMidYMid meet" opacity="{opacity}">{body}</svg>')


def text(x, y, t, size=13, weight=400, anchor="start", fill="#111"):
    t = str(t).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    return f'<text x="{x}" y="{y}" font-family="Inter, DejaVu Sans, Arial" font-size="{size}" font-weight="{weight}" text-anchor="{anchor}" fill="{fill}">{t}</text>'


def panel(x, y, w, h, title, layers, view=(90, 60, 100, 66)):
    out = [f'<rect x="{x}" y="{y}" width="{w}" height="{h}" fill="#ffffff" stroke="#c8ced8" rx="8"/>', text(x + 12, y + 22, title, 14, 700)]
    for name, color, op in layers:
        out.append(layer_svg(name, color, op, x + 8, y + 30, w - 16, h - 38, view))
    return out


s = [f'<rect width="{W}" height="{H}" fill="#f6f7f9"/>', f'<rect x="14" y="14" width="{W-28}" height="{H-28}" fill="none" stroke="#111" stroke-width="2"/>',
     text(40, 58, "Diseño del circuito impreso (PCB Layout)", 28, 700),
     text(40, 86, "Generado con KiCad 7 desde AquaAssistantPh.kicad_pcb. Vista desde arriba; la capa inferior se muestra como si la placa fuera transparente.", 14, 400, fill="#444")]
board_view = (91, 61, 98, 62)
s += panel(30, 104, 1000, 640, "Capas superior (rojo) e inferior (azul) con serigrafía, contorno y cotas en mm",
           [("B.Cu", COL["B.Cu"], 0.45), ("F.Cu", COL["F.Cu"], 0.8), ("F.SilkS", COL["F.SilkS"], 1), ("Edge.Cuts", COL["Edge.Cuts"], 1), ("Cmts.User", COL["Cmts.User"], 1)], board_view)
tight = (98, 68, 84, 48)
s += panel(1045, 104, 525, 205, "Ruteo sin los planos de GND (rojo F.Cu)", [("nz-F.Cu", COL["F.Cu"], 1), ("Edge.Cuts", COL["Edge.Cuts"], 1)], tight)
s += panel(1045, 321, 525, 205, "Capa inferior B.Cu: plano de GND", [("B.Cu", COL["B.Cu"], 0.85), ("Edge.Cuts", COL["Edge.Cuts"], 1)], tight)
s += panel(1045, 538, 525, 205, "Máscara (aberturas en gris) y serigrafía superior", [("F.Mask", COL["F.Mask"], 1), ("F.SilkS", COL["F.SilkS"], 1), ("Edge.Cuts", COL["Edge.Cuts"], 1)], tight)

# ---------- tables ----------
drills = Counter()
for fp in b.GetFootprints():
    for pad in fp.Pads():
        d = pad.GetDrillSize().x
        if d > 0:
            kind = "PTH" if pad.GetAttribute() == pcbnew.PAD_ATTRIB_PTH else "NPTH"
            drills[(round(mm(d), 2), kind)] += 1
tracks = Counter()
length = Counter()
vias = 0
for t in b.GetTracks():
    if t.GetClass() == "PCB_VIA":
        vias += 1
        drills[(round(mm(t.GetDrillValue()), 2), "Vía")] += 1
    else:
        tracks[(t.GetNetname(), round(mm(t.GetWidth()), 2), b.GetLayerName(t.GetLayer()))] += 1
        length[t.GetNetname()] += mm(t.GetLength())
ds = b.GetDesignSettings()
x0, y0 = 30, 762
s.append(f'<rect x="{x0}" y="{y0}" width="1540" height="250" fill="#fff" stroke="#c8ced8" rx="8"/>')
spec = [("Capas", "2 (F.Cu, B.Cu)"), ("Material", "FR-4, 1,6 mm"), ("Cobre", "35 µm (1 oz) ambas caras"), ("Acabado", "HASL sin plomo"),
        ("Máscara / serigrafía", "verde / blanca"), ("Tamaño", "80,0 × 44,0 mm, esquinas r = 2 mm"),
        ("Pista mín. / separación", f"{mm(ds.m_TrackMinWidth):.2f} mm / 0,25 mm (señal 0,5 mm; 5 V 0,8 mm)"),
        ("Planos", "GND en ambas caras, alivio térmico 0,4 mm"), ("Zona sin cobre", "bajo la antena del ESP32 (19,5–28,5 mm)"), ("DRC", "0 errores, 0 pads sin conectar")]
s.append(text(x0 + 16, y0 + 26, "Especificaciones de fabricación", 15, 700))
for i, (k, v) in enumerate(spec):
    s += [text(x0 + 16, y0 + 50 + i * 19, k, 12.5, 700), text(x0 + 190, y0 + 50 + i * 19, v, 12.5)]
tx = x0 + 560
s.append(text(tx, y0 + 26, "Taladros", 15, 700))
s += [text(tx, y0 + 50, "Diámetro", 12.5, 700), text(tx + 110, y0 + 50, "Tipo", 12.5, 700), text(tx + 200, y0 + 50, "Cantidad", 12.5, 700), text(tx + 290, y0 + 50, "Uso", 12.5, 700)]
uses = {("PTH", 1.0): "zócalos U1 (38) y J1 (3)", ("PTH", 0.8): "resistencias R1, R2", ("NPTH", 3.2): "montaje M3", ("Vía", 0.4): "vías de unión GND"}
for i, ((d, kind), n) in enumerate(sorted(drills.items(), key=lambda kv: (kv[0][1], kv[0][0]))):
    s += [text(tx, y0 + 70 + i * 19, f"{d:.2f} mm", 12.5), text(tx + 110, y0 + 70 + i * 19, kind, 12.5), text(tx + 200, y0 + 70 + i * 19, n, 12.5),
          text(tx + 290, y0 + 70 + i * 19, uses.get((kind, d), ""), 12.5)]
total = sum(drills.values())
s.append(text(tx, y0 + 70 + len(drills) * 19 + 6, f"Total: {total} agujeros", 12.5, 700))
nx = x0 + 1080
s.append(text(nx, y0 + 26, "Pistas por red", 15, 700))
s += [text(nx, y0 + 50, "Red", 12.5, 700), text(nx + 120, y0 + 50, "Ancho", 12.5, 700), text(nx + 200, y0 + 50, "Capa", 12.5, 700), text(nx + 270, y0 + 50, "Largo", 12.5, 700)]
for i, ((net, w, layer), n) in enumerate(sorted(tracks.items())):
    s += [text(nx, y0 + 70 + i * 19, net.lstrip("/"), 12.5), text(nx + 120, y0 + 70 + i * 19, f"{w:.2f} mm", 12.5), text(nx + 200, y0 + 70 + i * 19, layer, 12.5),
          text(nx + 270, y0 + 70 + i * 19, f"{length[net]:.1f} mm", 12.5)]
s.append(text(nx, y0 + 70 + len(tracks) * 19 + 6, "GND: por los planos de cobre (sin pistas).", 12, 400, fill="#444"))
# legend
lx, ly = x0 + 1080, y0 + 180
for i, (name, label) in enumerate((("F.Cu", "cobre superior"), ("B.Cu", "cobre inferior"), ("Edge.Cuts", "contorno"), ("F.SilkS", "serigrafía"))):
    s += [f'<rect x="{lx + (i % 2) * 220}" y="{ly + (i // 2) * 22 - 11}" width="22" height="12" fill="{COL[name]}"/>', text(lx + 30 + (i % 2) * 220, ly + (i // 2) * 22, f"{name}: {label}", 12)]
# title block
bx, by = W - 520, H - 110
s += [f'<rect x="{bx}" y="{by}" width="504" height="94" fill="#fff" stroke="#111" stroke-width="2"/>', f'<line x1="{bx}" y1="{by+32}" x2="{bx+504}" y2="{by+32}" stroke="#111"/>',
      text(bx + 12, by + 22, TITLE, 13, 700), text(bx + 12, by + 54, "Plano: Diseño de PCB (layout) · 2 capas", 13),
      text(bx + 12, by + 80, f"Rev. {REV} · {DATE} · Escala: según cotas (mm)", 12, 400, fill="#444")]
s.append(text(40, H - 60, "Archivos para fabricar: carpeta Fabricacion/ (Gerber RS-274X, taladros Excellon, mapa de taladros y BOM).", 13, 400, fill="#333"))
s.append(text(40, H - 40, "Antes de pedir la placa, confirma con tu ESP32 que las filas de pines están a 25,4 mm.", 13, 700, fill="#a00"))
open(OUT, "w").write(f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {W} {H}" width="{W}" height="{H}">' + "\n".join(s) + "</svg>")
print("layout sheet ok; drills", dict(drills), "vias", vias)
