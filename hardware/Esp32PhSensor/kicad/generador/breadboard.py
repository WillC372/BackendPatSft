"""Breadboard / wiring view of the same circuit, generated from an explicit model (components -> holes, wires), so its
connectivity can be computed and compared with the schematic and the PCB. Writes Protoboard.svg and protoboard.json."""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from design import ESP_LEFT, ESP_RIGHT, TITLE, REV, DATE  # noqa: E402

OUT = sys.argv[1]
P = 15.0          # px per 2.54 mm
ROWS = 32
COLS = "abcdefghij"
R0 = 8            # ESP32 pin 1 row

# ---------------- model ----------------
# Breadboard A: rails (+, -) on the left, then a..e, channel, f..j. Breadboard B: a..e, channel, f..j (no rails used).
# ESP32 bridges both: left pins in A column j, right pins in B column a (1.0" apart).
PLACE = {}  # (ref, pin) -> hole id
for i in range(19):
    PLACE[("U1", str(i + 1))] = f"A-j{R0 + i}"
    PLACE[("U1", str(i + 20))] = f"B-a{R0 + i}"
PLACE.update({("J1", "1"): "A-c2", ("J1", "2"): "A-c3", ("J1", "3"): "A-c4",
              ("R1", "1"): "A-d2", ("R1", "2"): "A-g2",
              ("R2", "1"): "A-i2", ("R2", "2"): "A-i6"})
WIRES = [  # name, from, to, color, net (for the legend only; connectivity is computed)
    ("W1", "A-h2", f"A-f{R0 + 4}", "#f08c00", "PH_ADC"),
    ("W2", "A-f6", "A-RM6", "#222222", "GND"),
    ("W3", "A-a3", "A-RM3", "#222222", "GND"),
    ("W4", "A-a4", "A-RP4", "#d62828", "+5V"),
    ("W5", f"A-f{R0 + 13}", f"A-RM{R0 + 13}", "#222222", "GND"),
    ("W6", f"A-f{R0 + 18}", f"A-RP{R0 + 18}", "#d62828", "+5V"),
]
CABLES = [  # Dupont cables from the off-board module U2 (female on U2, male into J1 socket)
    ("C1", ("U2", "3"), ("J1", "1"), "#f2c200", "Po -> PO"),
    ("C2", ("U2", "4"), ("J1", "2"), "#222222", "G -> GND"),
    ("C3", ("U2", "6"), ("J1", "3"), "#d62828", "V+ -> 5V"),
]


def node_of(hole):
    """Electrical node of a breadboard hole: 5-hole strip or full rail."""
    board, rest = hole.split("-")
    if rest.startswith("RP") or rest.startswith("RM"):
        return f"{board}-rail{rest[:2]}"
    col, row = rest[0], int(rest[1:])
    return f"{board}-strip{row}{'L' if col in 'abcde' else 'R'}"


# Union-find over nodes and component pins.
parent = {}


def find(x):
    parent.setdefault(x, x)
    while parent[x] != x:
        parent[x] = parent[parent[x]]
        x = parent[x]
    return x


def union(a, b):
    parent[find(a)] = find(b)


for pin, hole in PLACE.items():
    union(f"{pin[0]}.{pin[1]}", node_of(hole))
for _, a, b, _, _ in WIRES:
    union(node_of(a), node_of(b))
for _, a, b, _, _ in CABLES:
    union(f"{a[0]}.{a[1]}", f"{b[0]}.{b[1]}")
groups = {}
for key in list(parent):
    if "." in key:
        groups.setdefault(find(key), []).append(key)
nets = [sorted(g) for g in groups.values() if len(g) > 1]
json.dump({"place": {f"{k[0]}.{k[1]}": v for k, v in PLACE.items()}, "wires": WIRES, "cables": CABLES, "nets": nets},
          open(os.path.join(OUT, "protoboard.json"), "w"), indent=1)

# ---------------- drawing ----------------
W, H = 1600, 1130
AX = 560      # x of breadboard A column a
TOP = 170     # y of row 1
s = []


def esc(t):
    return str(t).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")


def text(x, y, t, size=12, weight=400, anchor="middle", fill="#111", extra=""):
    return f'<text x="{x:.1f}" y="{y:.1f}" font-family="Inter, DejaVu Sans, Arial" font-size="{size}" font-weight="{weight}" text-anchor="{anchor}" fill="{fill}" {extra}>{esc(t)}</text>'


def col_x(board, col):
    base = AX if board == "A" else AX + 10 * P + 0  # B column a is 10 holes (1.0") right of A column a? fixed below
    i = COLS.index(col)
    x = base + i * P + (3 * P - P if i >= 5 else 0)  # channel of 0.3" between e and f
    return x


A_J = col_x("A", "j")
B_A = A_J + 10 * P  # 1.0" between the two pin rows of the ESP32


def hole_xy(hole):
    board, rest = hole.split("-")
    if rest.startswith("RP") or rest.startswith("RM"):
        row = int(rest[2:])
        x = AX - (3.0 if rest.startswith("RP") else 2.0) * P - 0.6 * P
        return x, TOP + (row - 1) * P
    col, row = rest[0], int(rest[1:])
    if board == "A":
        x = col_x("A", col)
    else:
        i = COLS.index(col)
        x = B_A + i * P + (2 * P if i >= 5 else 0)
    return x, TOP + (row - 1) * P


def breadboard(board):
    xs = [hole_xy(f"{board}-{c}1")[0] for c in COLS]
    left = xs[0] - 1.4 * P - (3.6 * P if board == "A" else 0)
    right = xs[-1] + 1.4 * P
    top, bottom = TOP - 1.6 * P, TOP + (ROWS - 1) * P + 1.6 * P
    r = [f'<rect x="{left:.1f}" y="{top:.1f}" width="{right-left:.1f}" height="{bottom-top:.1f}" rx="10" fill="#f3f1ea" stroke="#c9c4b5" stroke-width="2"/>']
    mid = (xs[4] + xs[5]) / 2
    r.append(f'<rect x="{mid - 0.45*P:.1f}" y="{top + 6:.1f}" width="{0.9*P:.1f}" height="{bottom-top-12:.1f}" rx="3" fill="#dcd8cc"/>')
    for c, x in zip(COLS, xs):
        r.append(text(x, top + 0.95 * P, c, 10, 700, fill="#8a8370"))
        r.append(text(x, bottom - 0.45 * P, c, 10, 700, fill="#8a8370"))
    for row in range(1, ROWS + 1):
        y = TOP + (row - 1) * P
        if row == 1 or row % 5 == 0:
            r.append(text(xs[0] - 0.9 * P, y + 4, row, 9, 400, "end", "#8a8370"))
        for x in xs:
            r.append(f'<rect x="{x-3.2:.1f}" y="{y-3.2:.1f}" width="6.4" height="6.4" rx="1.2" fill="#3b3a36"/>')
    if board == "A":
        for kind, color, label in (("RP", "#d62828", "+"), ("RM", "#1f5fbf", "−")):
            x = hole_xy(f"A-{kind}1")[0]
            r.append(f'<line x1="{x - 0.55*P:.1f}" y1="{top + 10:.1f}" x2="{x - 0.55*P:.1f}" y2="{bottom - 10:.1f}" stroke="{color}" stroke-width="2"/>')
            r.append(text(x, top + 0.95 * P, label, 14, 700, fill=color))
            for row in range(1, ROWS + 1):
                if row % 6 == 0:
                    continue
                y = TOP + (row - 1) * P
                r.append(f'<rect x="{x-3.2:.1f}" y="{y-3.2:.1f}" width="6.4" height="6.4" rx="1.2" fill="#3b3a36"/>')
    r.append(text((left + right) / 2, bottom + 22, f"Protoboard {board}" + (" (rieles a la izquierda)" if board == "A" else " (sin rieles en el lado izquierdo)"), 13, 700))
    return r


s += [f'<rect width="{W}" height="{H}" fill="#ffffff"/>',
      f'<rect x="14" y="14" width="{W-28}" height="{H-28}" fill="none" stroke="#111" stroke-width="2"/>',
      text(40, 60, "Diagrama de montaje en protoboard (Breadboard / Wiring Diagram)", 28, 700, "start"),
      text(40, 90, "Mismo circuito que el esquemático y la PCB: mismas referencias (U1, U2, J1, R1, R2) y las mismas cuatro redes. Vista superior.", 15, 400, "start", "#444")]
s += breadboard("A") + breadboard("B")

# ESP32 bridging both breadboards.
ex0, ex1 = A_J - 0.9 * P, B_A + 0.9 * P
ey0, ey1 = TOP + (R0 - 1) * P - 2.6 * P, TOP + (R0 + 17) * P + 2.4 * P
s.append(f'<rect x="{ex0:.1f}" y="{ey0:.1f}" width="{ex1-ex0:.1f}" height="{ey1-ey0:.1f}" rx="8" fill="#1f2328" stroke="#000" stroke-width="2" opacity="0.97"/>')
s.append(f'<rect x="{ex0 + 3.0*P:.1f}" y="{ey0 - 0.6*P:.1f}" width="{ex1-ex0-6.0*P:.1f}" height="{6.4*P:.1f}" rx="4" fill="#2b3d8f" stroke="#1a2660"/>')
s.append(f'<rect x="{ex0 + 3.3*P:.1f}" y="{ey0 + 1.4*P:.1f}" width="{ex1-ex0-6.6*P:.1f}" height="{4.2*P:.1f}" rx="3" fill="#c9ccd1" stroke="#8a8f96"/>')
s.append(text((ex0 + ex1) / 2, ey0 + 3.3 * P, "ESP-32", 14, 700, fill="#333"))
s.append(text((ex0 + ex1) / 2, ey0 + 0.6 * P, "antena", 10, 400, fill="#c9d4ff"))
s.append(f'<rect x="{(ex0+ex1)/2 - 1.6*P:.1f}" y="{ey1 - 0.6*P:.1f}" width="{3.2*P:.1f}" height="{1.6*P:.1f}" rx="4" fill="#c7cbd1" stroke="#777"/>')
s.append(text((ex0 + ex1) / 2, ey1 + 1.9 * P, "USB-C → computador (Chrome o Edge)", 12, 700, "middle", "#333", 'stroke="#fff" stroke-width="4" paint-order="stroke"'))
s.append(text((ex0 + ex1) / 2, (ey0 + ey1) / 2, "U1", 22, 700, fill="#ffffff"))
s.append(text((ex0 + ex1) / 2, (ey0 + ey1) / 2 + 18, "DevKit 38P", 10, 400, fill="#cfd3da"))
for i, n in enumerate(ESP_LEFT):
    x, y = hole_xy(f"A-j{R0 + i}")
    hot = {"G34": "#f08c00", "GND": "#9aa0a6", "5V": "#ff6b6b"}.get(n)
    s.append(f'<circle cx="{x:.1f}" cy="{y:.1f}" r="4.6" fill="#d4af37" stroke="#7a6418"/>')
    s.append(text(x + 0.85 * P, y + 4, n, 10, 700 if hot else 400, "start", hot or "#c8ccd2"))
for i, n in enumerate(ESP_RIGHT):
    x, y = hole_xy(f"B-a{R0 + i}")
    s.append(f'<circle cx="{x:.1f}" cy="{y:.1f}" r="4.6" fill="#d4af37" stroke="#7a6418"/>')
    s.append(text(x - 0.85 * P, y + 4, n, 10, 400, "end", "#c8ccd2"))

# J1 female header (3 pins).
jx, jy1 = hole_xy("A-c2")
jy3 = hole_xy("A-c4")[1]
s.append(f'<rect x="{jx - 0.55*P:.1f}" y="{jy1 - 0.55*P:.1f}" width="{1.1*P:.1f}" height="{jy3 - jy1 + 1.1*P:.1f}" rx="2" fill="#111" />')
for r in (2, 3, 4):
    x, y = hole_xy(f"A-c{r}")
    s.append(f'<rect x="{x-2.6:.1f}" y="{y-2.6:.1f}" width="5.2" height="5.2" fill="#555"/>')
s.append(text(jx, jy1 - 0.9 * P, "J1", 13, 700))


def resistor(a, b, ref):
    (x1, y1), (x2, y2) = hole_xy(a), hole_xy(b)
    import math
    L = math.hypot(x2 - x1, y2 - y1)
    ang = math.degrees(math.atan2(y2 - y1, x2 - x1))
    body = min(3.4 * P, L - 0.8 * P)
    bands = ["#d62828", "#111", "#111", "#d62828", "#7a4a1d"]  # 20.0 k, 1 %: red black black red brown
    g = [f'<g transform="translate({x1:.1f},{y1:.1f}) rotate({ang:.1f})">',
         f'<line x1="0" y1="0" x2="{L:.1f}" y2="0" stroke="#9aa0a6" stroke-width="2.4"/>',
         f'<rect x="{(L-body)/2:.1f}" y="-6.5" width="{body:.1f}" height="13" rx="6" fill="#e8d3a6" stroke="#9c8250"/>']
    for k, c in enumerate(bands):
        bx = (L - body) / 2 + body * (0.18 + 0.15 * k) + (body * 0.08 if k == 4 else 0)
        g.append(f'<rect x="{bx:.1f}" y="-6.5" width="3.2" height="13" fill="{c}"/>')
    g.append("</g>")
    g.append(f'<circle cx="{x1:.1f}" cy="{y1:.1f}" r="2.6" fill="#9aa0a6"/><circle cx="{x2:.1f}" cy="{y2:.1f}" r="2.6" fill="#9aa0a6"/>')
    mx, my = (x1 + x2) / 2, (y1 + y2) / 2
    if abs(y2 - y1) > abs(x2 - x1):
        g.append(text(mx - 12, my + 4, ref + " 20 kΩ", 11, 700, "end", "#111", 'stroke="#fff" stroke-width="3" paint-order="stroke"'))
    else:
        g.append(text(mx, my - 11, ref + " 20 kΩ", 11, 700, "middle", "#111", 'stroke="#fff" stroke-width="3" paint-order="stroke"'))
    return g


def wire(a, b, color, name):
    (x1, y1), (x2, y2) = hole_xy(a), hole_xy(b)
    bend = 0.9 * P
    path = f"M {x1:.1f} {y1:.1f} C {x1:.1f} {y1 - bend:.1f}, {x2:.1f} {y2 - bend:.1f}, {x2:.1f} {y2:.1f}"
    return [f'<path d="{path}" fill="none" stroke="#000" stroke-opacity="0.35" stroke-width="7" stroke-linecap="round"/>',
            f'<path d="{path}" fill="none" stroke="{color}" stroke-width="4.6" stroke-linecap="round"/>',
            f'<circle cx="{x1:.1f}" cy="{y1:.1f}" r="3.4" fill="#c0c4c9" stroke="#555"/><circle cx="{x2:.1f}" cy="{y2:.1f}" r="3.4" fill="#c0c4c9" stroke="#555"/>',
            text((x1 + x2) / 2 + 12, (y1 + y2) / 2 - 4, name, 10, 700, "start", "#333", 'stroke="#fff" stroke-width="3" paint-order="stroke"')]


s += resistor("A-d2", "A-g2", "R1")
s += resistor("A-i2", "A-i6", "R2")
for name, a, b, color, _net in WIRES:
    s += wire(a, b, color, name)

# Off-board module U2 (PH-4502C) with probe and Dupont cables to J1.
mx, my, mw, mh = 70, 190, 270, 150
s.append(f'<rect x="{mx}" y="{my}" width="{mw}" height="{mh}" rx="10" fill="#1f5fbf" stroke="#0d3a7a" stroke-width="2"/>')
s.append(text(mx + mw / 2, my - 14, "U2 · Módulo PH-4502C (fuera de la placa)", 14, 700))
s.append(f'<circle cx="{mx + 4}" cy="{my + 75}" r="22" fill="#d9d9d9" stroke="#666" stroke-width="3"/><circle cx="{mx + 4}" cy="{my + 75}" r="6" fill="#888"/>')
s.append(text(mx + 4, my + 112, "BNC", 11, 700, "middle", "#fff", 'stroke="#1f5fbf" stroke-width="3" paint-order="stroke"'))
for k, lab in enumerate(("ajuste pH 7", "límite Do")):
    cx = mx + 90 + k * 70
    s.append(f'<rect x="{cx-17}" y="{my+22}" width="34" height="34" rx="3" fill="#2f7de1" stroke="#0d3a7a"/><circle cx="{cx}" cy="{my+39}" r="10" fill="#eee" stroke="#555"/>')
    s.append(text(cx, my + 72, lab, 10, 400, fill="#fff"))
pins = ["To", "Do", "Po", "G", "G", "V+"]
pin_xy = {}
for k, n in enumerate(pins):
    px, py = mx + mw - 22, my + 22 + k * 21
    pin_xy[str(k + 1)] = (px, py)
    s.append(f'<rect x="{px-6}" y="{py-6}" width="12" height="12" fill="#d4af37" stroke="#7a6418"/>')
    s.append(text(px - 12, py + 4, f"{n} ({k+1})", 10, 700, "end", "#fff"))
s.append(f'<path d="M {mx-18} {my+75} L {mx-50} {my+75} L {mx-50} {my+250}" fill="none" stroke="#333" stroke-width="6"/>')
s.append(f'<rect x="{mx-66}" y="{my+250}" width="32" height="120" rx="8" fill="#f2f2f2" stroke="#333" stroke-width="2"/>')
s.append(f'<path d="M {mx-62} {my+370} Q {mx-50} {my+398} {mx-38} {my+370}" fill="#cfe3ff" stroke="#333" stroke-width="2"/>')
s.append(text(mx - 20, my + 300, "Sonda pH E201", 12, 700, "start"))
s.append(text(mx - 20, my + 316, "(se enrosca al BNC)", 11, 400, "start", "#555"))
for name, (ref_a, pa), (ref_b, pb), color, label in CABLES:
    x1, y1 = pin_xy[pa]
    x2, y2 = hole_xy(PLACE[(ref_b, pb)])
    path = f"M {x1+6:.1f} {y1:.1f} C {x1+140:.1f} {y1:.1f}, {x2-160:.1f} {y2:.1f}, {x2-6:.1f} {y2:.1f}"
    s += [f'<path d="{path}" fill="none" stroke="#000" stroke-opacity="0.35" stroke-width="8" stroke-linecap="round"/>',
          f'<path d="{path}" fill="none" stroke="{color}" stroke-width="5.5" stroke-linecap="round"/>',
          f'<rect x="{x1+4}" y="{y1-6}" width="18" height="12" rx="2" fill="#111"/>']
s.append(text(150, 372, "Cables Dupont (hembra en U2, macho en J1):", 12, 700, "start"))
s.append(text(150, 390, "C1 amarillo: Po (3) → J1-1 (PO)", 12, 400, "start"))
s.append(text(150, 408, "C2 negro: G (4) → J1-2 (GND)", 12, 400, "start"))
s.append(text(150, 426, "C3 rojo: V+ (6) → J1-3 (5V)", 12, 400, "start"))

# Connection table (from the model).
tx, ty = 40, 640
rows = [("Ref.", "De", "A", "Red")]
for pin, hole in sorted(PLACE.items()):
    if pin[0] in ("J1", "R1", "R2") or (pin[0] == "U1" and pin[1] in ("5", "14", "19")):
        net = {"J1.1": "PH_OUT", "J1.2": "GND", "J1.3": "+5V", "R1.1": "PH_OUT", "R1.2": "PH_ADC", "R2.1": "PH_ADC", "R2.2": "GND",
               "U1.5": "PH_ADC", "U1.14": "GND", "U1.19": "+5V"}[f"{pin[0]}.{pin[1]}"]
        rows.append((pin[0], f"pin {pin[1]}" + (f" ({ESP_LEFT[int(pin[1]) - 1]})" if pin[0] == "U1" else ""), hole, net))
for name, a, b, _c, net in WIRES:
    rows.append((name, a, b, net))
s.append(text(tx, ty - 14, "Conexiones (A-c2 = protoboard A, columna c, fila 2)", 14, 700, "start"))
for k, row in enumerate(rows):
    y = ty + k * 19
    if k == 0:
        s.append(f'<rect x="{tx}" y="{y-14}" width="430" height="19" fill="#eef1f6"/>')
    for x, cell in zip((tx + 6, tx + 60, tx + 210, tx + 340), row):
        s.append(text(x, y, cell, 11.5, 700 if k == 0 else 400, "start"))
s.append(f'<rect x="{tx}" y="{ty-14}" width="430" height="{19*len(rows)}" fill="none" stroke="#999"/>')

# Legend and notes.
nx, ny = 1110, 200
notes = ["Cómo leerlo",
         "• Cada fila de 5 huecos (a–e o f–j) es un mismo nodo.",
         "• Los rieles + (rojo) y − (azul) recorren toda la protoboard A.",
         "• El ESP32 (U1) queda sobre las dos protoboards: pines 1–19",
         "  en la columna j de A y pines 20–38 en la columna a de B.",
         "• J1 es el mismo zócalo hembra 1×3 de la PCB.",
         "• RP / RM = riel + / riel − de la protoboard A.",
         "",
         "Redes (iguales a esquemático y PCB)",
         "• PH_OUT: U2-Po, J1-1, R1-1",
         "• PH_ADC: R1-2, R2-1, U1-5 (G34)",
         "• GND: U2-G, J1-2, R2-2, U1-14",
         "• +5V: U2-V+, J1-3, U1-19 (5V)",
         "",
         "Antes de conectar",
         "• Comprueba que la protoboard tenga los rieles continuos",
         "  (algunas los cortan a la mitad).",
         "• Mide con multímetro: entre + y − no debe haber continuidad.",
         "• R1 y R2 no tienen polaridad. Código de colores 1 %:",
         "  rojo-negro-negro-rojo-marrón = 20,0 kΩ."]
for k, t in enumerate(notes):
    bold = t in ("Cómo leerlo", "Redes (iguales a esquemático y PCB)", "Antes de conectar")
    s.append(text(nx, ny + k * 21, t, 13.5 if bold else 12.5, 700 if bold else 400, "start"))

# Title block.
bx, by = W - 520, H - 110
s += [f'<rect x="{bx}" y="{by}" width="504" height="94" fill="#fff" stroke="#111" stroke-width="2"/>',
      f'<line x1="{bx}" y1="{by+32}" x2="{bx+504}" y2="{by+32}" stroke="#111"/>',
      text(bx + 12, by + 22, TITLE, 13, 700, "start"),
      text(bx + 12, by + 54, "Plano: Montaje en protoboard (prototipo)", 13, 400, "start"),
      text(bx + 12, by + 80, f"Rev. {REV} · {DATE} · Escala: sin escala (paso 2,54 mm)", 12, 400, "start", "#444")]

svg = f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {W} {H}" width="{W}" height="{H}">\n' + "\n".join(s) + "\n</svg>\n"
open(os.path.join(OUT, "Protoboard.svg"), "w").write(svg)
print("breadboard ok:", len(nets), "nets")
for n in nets:
    print("  ", n)
