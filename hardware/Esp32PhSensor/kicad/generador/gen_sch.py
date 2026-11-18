"""Writes AquaAssistantPh.kicad_sch (KiCad 7) with standard symbols copied from the KiCad libraries."""
import json
import math
import os
import re
import sys
import uuid

sys.path.insert(0, os.path.dirname(__file__))
from design import PARTS, OFFBOARD, PROJECT, TITLE, REV, DATE, COMPANY, ESP_LEFT, ESP_RIGHT  # noqa: E402

OUT = sys.argv[1]
SYMDIR = "/usr/share/kicad/symbols"
ROOT = str(uuid.uuid4())
FONT = "(effects (font (size 1.27 1.27)))"


def u():
    return str(uuid.uuid4())


def extract(libfile, name):
    """Top-level (symbol "name" ...) block of a .kicad_sym file."""
    text = open(libfile).read()
    start = text.index(f'\n  (symbol "{name}"') + 1
    depth, i, in_str = 0, start, False
    while True:
        c = text[i]
        if c == '"' and text[i - 1] != "\\":
            in_str = not in_str
        elif not in_str:
            if c == "(":
                depth += 1
            elif c == ")":
                depth -= 1
                if depth == 0:
                    return text[start:i + 1]
        i += 1


LIBS = {
    "Device:R": (f"{SYMDIR}/Device.kicad_sym", "R"),
    "Connector:Conn_01x03_Socket": (f"{SYMDIR}/Connector.kicad_sym", "Conn_01x03_Socket"),
    "power:+5V": (f"{SYMDIR}/power.kicad_sym", "+5V"),
    "power:GND": (f"{SYMDIR}/power.kicad_sym", "GND"),
    "power:PWR_FLAG": (f"{SYMDIR}/power.kicad_sym", "PWR_FLAG"),
    "Mechanical:MountingHole": (f"{SYMDIR}/Mechanical.kicad_sym", "MountingHole"),
    "AquaAssistant:ESP32_DevKit_38P": (os.path.join(OUT, "AquaAssistant.kicad_sym"), "ESP32_DevKit_38P"),
    "AquaAssistant:PH-4502C": (os.path.join(OUT, "AquaAssistant.kicad_sym"), "PH-4502C"),
}
lib_blocks = []
pin_tables = {}
for lib_id, (f, name) in LIBS.items():
    block = extract(f, name)
    pins = {}
    for m in re.finditer(r'\(pin \w+ \w+ \(at ([-\d.]+) ([-\d.]+) [-\d.]+\).*?\(number "([^"]+)"', block, re.S):
        pins[m.group(3)] = (float(m.group(1)), float(m.group(2)))
    pin_tables[lib_id] = pins
    lib_blocks.append(block.replace(f'(symbol "{name}"', f'(symbol "{lib_id}"', 1).replace("\n", "\n  "))

items = []
sym_uuids = {}
pin_points = {}
pwr_count = [0]


def pin_xy(lib_id, num, at, rot=0, mirror_y=False):
    px, py = pin_tables[lib_id][num]
    if mirror_y:
        px = -px
    r = math.radians(rot)
    rx, ry = px * math.cos(r) - py * math.sin(r), px * math.sin(r) + py * math.cos(r)
    return (round(at[0] + rx, 2), round(at[1] - ry, 2))


def prop(name, value, at, hide=False, angle=0, justify=None):
    eff = "(effects (font (size 1.27 1.27))" + (f" (justify {justify})" if justify else "") + (" hide" if hide else "") + ")"
    return f'(property "{name}" "{value}" (at {at[0]} {at[1]} {angle}) {eff})'


def symbol(lib_id, ref, value, at, rot=0, footprint="", ref_at=None, val_at=None, in_bom=True, on_board=True, extra_props=(), ref_hide=False, val_angle=0, ref_angle=0, justify=None):
    sid = u()
    if not ref.startswith("#"):
        sym_uuids[ref] = sid
    pins = "\n    ".join(f'(pin "{n}" (uuid {u()}))' for n in pin_tables[lib_id])
    props = [prop("Reference", ref, ref_at or (at[0], at[1] - 5), hide=ref_hide, angle=ref_angle, justify=justify),
             prop("Value", value, val_at or (at[0], at[1] + 5), angle=val_angle, justify=justify),
             prop("Footprint", footprint, at, hide=True),
             prop("Datasheet", "~", at, hide=True)] + [prop(k, v, at, hide=True) for k, v in extra_props]
    items.append(f'''(symbol (lib_id "{lib_id}") (at {at[0]} {at[1]} {rot}) (unit 1)
    (in_bom {"yes" if in_bom else "no"}) (on_board {"yes" if on_board else "no"}) (dnp no)
    (uuid {sid})
    {chr(10).join("    " + p for p in props).strip()}
    {pins}
    (instances (project "{PROJECT}" (path "/{ROOT}" (reference "{ref}") (unit 1))))
  )''')
    for n in pin_tables[lib_id]:
        pin_points[(ref, n)] = pin_xy(lib_id, n, at, rot)
    return sid


def power(kind, at, rot=0, val_at=None, val_angle=0):
    pwr_count[0] += 1
    ref = f"#{'FLG' if kind == 'PWR_FLAG' else 'PWR'}{pwr_count[0]:02d}"
    lib = f"power:{kind}"
    if val_at is None:
        val_at = (at[0], at[1] + 3.81) if kind == "GND" else (at[0], at[1] - 3.81)
    symbol(lib, ref, kind, at, rot, ref_at=at, val_at=val_at, in_bom=False, ref_hide=True, val_angle=val_angle)


def wire(*pts):
    for a, b in zip(pts, pts[1:]):
        items.append(f"(wire (pts (xy {a[0]} {a[1]}) (xy {b[0]} {b[1]})) (stroke (width 0) (type default)) (uuid {u()}))")


def junction(p):
    items.append(f"(junction (at {p[0]} {p[1]}) (diameter 0) (color 0 0 0 0) (uuid {u()}))")


def no_connect(p):
    items.append(f"(no_connect (at {p[0]} {p[1]}) (uuid {u()}))")


def label(name, p, angle=0):
    just = "left bottom" if angle == 0 else "right bottom"
    items.append(f'(label "{name}" (at {p[0]} {p[1]} {angle}) (fields_autoplaced) (effects (font (size 1.27 1.27)) (justify {just})) (uuid {u()}))')


def note(text, p, size=1.27, bold=False):
    b = " bold" if bold else ""
    just = "left top" if "\n" in text else "left bottom"
    t = text.replace('"', '\\"').replace("\n", "\\n")
    items.append(f'(text "{t}" (at {p[0]} {p[1]} 0) (effects (font (size {size} {size}){b}) (justify {just})) (uuid {u()}))')


def box(a, b, dashed=True):
    items.append(f"(rectangle (start {a[0]} {a[1]}) (end {b[0]} {b[1]}) (stroke (width 0.254) (type {'dash' if dashed else 'default'})) (fill (type none)) (uuid {u()}))")


# ---------------- placement ----------------
# Off-board module U2 (PH-4502C).
U2 = (63.5, 63.5)
symbol("AquaAssistant:PH-4502C", "U2", "PH-4502C", U2, ref_at=(U2[0], U2[1] - 10.16), val_at=(U2[0], U2[1] + 10.16),
       on_board=False, extra_props=[("Descripcion", OFFBOARD["U2"][2])])
pv, ppo, pg = pin_points[("U2", "6")], pin_points[("U2", "3")], pin_points[("U2", "4")]
wire(pv, (pv[0] + 2.54, pv[1]), (pv[0] + 2.54, pv[1] - 5.08))
power("+5V", (pv[0] + 2.54, pv[1] - 5.08))
wire(ppo, (ppo[0] + 7.62, ppo[1]))
label("PH_OUT", (ppo[0] + 7.62, ppo[1]))
wire(pg, (pg[0] + 2.54, pg[1]), (pg[0] + 2.54, pg[1] + 5.08))
power("GND", (pg[0] + 2.54, pg[1] + 5.08))
for n in ("1", "2", "5"):
    no_connect(pin_points[("U2", n)])
box((38.1, 45.72), (96.52, 91.44))
note("FUERA DE LA PLACA: modulo PH-4502C + sonda E201-BNC", (39.37, 44.45), 1.27, True)
note("Se conecta a J1 con 3 cables Dupont:\nPo -> PO, G -> GND, V+ -> 5V.\nDo, To y el segundo G quedan libres.", (40.64, 80.01), 1.1)

# J1 sensor connector (pins on the left), below the divider.
J1 = (132.08, 86.36)
symbol("Connector:Conn_01x03_Socket", "J1", "SENSOR", J1, footprint=PARTS["J1"][0], ref_at=(J1[0], J1[1] - 6.35), val_at=(J1[0], J1[1] + 6.35))
j1, j2, j3 = (pin_points[("J1", n)] for n in ("1", "2", "3"))
wire(j1, (j1[0] - 5.08, j1[1]))
label("PH_OUT", (j1[0] - 5.08, j1[1]), 180)
wire(j2, (j2[0] - 7.62, j2[1]))
power("GND", (j2[0] - 7.62, j2[1]), 270, val_at=(j2[0] - 15.24, j2[1] + 0.635), val_angle=90)
wire(j3, (j3[0] - 15.24, j3[1]))
power("+5V", (j3[0] - 15.24, j3[1]), 90, val_at=(j3[0] - 22.86, j3[1] + 0.635), val_angle=90)
note("Pines: 1 PO, 2 GND, 3 5V", (J1[0] - 3.81, J1[1] + 10.16), 1.0)

# Divider R1/R2.
R1 = (142.24, 66.04)
symbol("Device:R", "R1", "20k", R1, 90, footprint=PARTS["R1"][0], ref_at=(R1[0], R1[1] - 4.445), val_at=(R1[0], R1[1] + 4.445),
       extra_props=[("Tolerancia", "1 %"), ("Potencia", "1/4 W")])
r1a, r1b = pin_points[("R1", "1")], pin_points[("R1", "2")]
wire(r1a, (r1a[0] - 7.62, r1a[1]))
label("PH_OUT", (r1a[0] - 7.62, r1a[1]), 180)
R2 = (156.21, 72.39)
symbol("Device:R", "R2", "20k", R2, 0, footprint=PARTS["R2"][0], ref_at=(R2[0] + 2.54, R2[1] - 1.27), val_at=(R2[0] + 2.54, R2[1] + 1.27),
       extra_props=[("Tolerancia", "1 %"), ("Potencia", "1/4 W")], justify="left")
r2a, r2b = pin_points[("R2", "1")], pin_points[("R2", "2")]
node = (r2a[0], r1b[1])
wire(r1b, node)
wire(node, r2a)
junction(node)
gnd_r2 = (r2b[0], r2b[1] + 5.08)
wire(r2b, gnd_r2)
power("GND", gnd_r2)
flag_tee = (r2b[0], r2b[1] + 2.54)
junction(flag_tee)
wire(flag_tee, (flag_tee[0] - 5.08, flag_tee[1]))
power("PWR_FLAG", (flag_tee[0] - 5.08, flag_tee[1]), val_at=(flag_tee[0] - 5.08, flag_tee[1] - 5.08))

# U1 ESP32 DevKit.
U1 = (195.58, 78.74)
symbol("AquaAssistant:ESP32_DevKit_38P", "U1", "ESP32_DevKit_38P", U1, footprint=PARTS["U1"][0],
       ref_at=(U1[0], U1[1] - 27.94), val_at=(U1[0], U1[1] + 27.94))
g34 = pin_points[("U1", "5")]
assert abs(g34[1] - node[1]) < 0.01, (g34, node)
wire(node, g34)
label("PH_ADC", (165.1, node[1]))
gpin = pin_points[("U1", "14")]
wire(gpin, (gpin[0] - 5.08, gpin[1]), (gpin[0] - 5.08, gpin[1] + 5.08))
power("GND", (gpin[0] - 5.08, gpin[1] + 5.08))
vpin = pin_points[("U1", "19")]
wire(vpin, (vpin[0] - 10.16, vpin[1]), (vpin[0] - 10.16, vpin[1] - 5.08))
power("+5V", (vpin[0] - 10.16, vpin[1] - 5.08))
for n in pin_tables["AquaAssistant:ESP32_DevKit_38P"]:
    if n not in ("5", "14", "19"):
        no_connect(pin_points[("U1", n)])

# Mounting holes.
for i, ref in enumerate(("H1", "H2", "H3", "H4")):
    at = (208.28 + i * 12.7, 124.46)
    symbol("Mechanical:MountingHole", ref, "M3", at, footprint=PARTS[ref][0], ref_at=(at[0] + 2.54, at[1] - 1.27), val_at=(at[0] + 2.54, at[1] + 1.27), justify="left")
note("Agujeros de montaje M3 (no electricos)", (205.74, 118.11), 1.0)

# Notes.
note("Conector del sensor y acondicionamiento de la senal", (101.6, 52.07), 1.27, True)
box((100.33, 53.34), (165.1, 99.06))
note("NOTAS", (25.4, 113.03), 1.524, True)
note("1. Divisor de tension: V(G34) = Po x R2 / (R1 + R2) = Po / 2. Con Po <= 5 V, G34 recibe <= 2,5 V (limite del ESP32 ~3,3 V).\n"
     "2. A pH 7 el PH-4502C entrega ~2,5 V (ajuste con su potenciometro) -> ~1,25 V en G34. La calibracion con buffers corrige el resto.\n"
     "3. G34 es entrada del ADC1: funciona con el Bluetooth activo. Firmware: hardware/Esp32PhSensor/Esp32PhSensor.ino.\n"
     "4. U1 se enchufa en dos zocalos hembra 1x19 separados 25,4 mm (verificar con la placa real antes de soldar).\n"
     "5. Los GND 20 y 26 del DevKit estan unidos internamente al 14; esta placa usa el pin 14.\n"
     "6. U2 (PH-4502C) no va en la placa: se conecta a J1 por cable. Alimentacion: USB del ESP32 (5 V).", (25.4, 115.57), 1.27)

sch = f'''(kicad_sch (version 20230121) (generator eeschema)
  (uuid {ROOT})
  (paper "A4")
  (title_block
    (title "{TITLE}")
    (date "{DATE}")
    (rev "{REV}")
    (company "{COMPANY}")
    (comment 1 "Esquematico electronico - simbolos IEC/IEEE (KiCad)")
    (comment 2 "Nets: PH_OUT (Po del sensor), PH_ADC (entrada G34), +5V, GND")
    (comment 3 "Archivo editable: KiCad 7 o superior")
  )
  (lib_symbols
  {chr(10).join("  " + b for b in lib_blocks)}
  )
  {chr(10).join("  " + it for it in items)}
  (sheet_instances (path "/" (page "1")))
)
'''
with open(os.path.join(OUT, PROJECT + ".kicad_sch"), "w") as f:
    f.write(sch)
json.dump(sym_uuids, open(os.path.join(OUT, ".symbol_uuids.json"), "w"))
print("sch ok", len(items), "items")
