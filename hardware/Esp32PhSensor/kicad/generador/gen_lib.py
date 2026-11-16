"""Writes the project library: AquaSense.kicad_sym (ESP32 DevKit 38P, PH-4502C) and AquaSense.pretty (DevKit footprint)."""
import os
import sys
import uuid

sys.path.insert(0, os.path.dirname(__file__))
from design import ESP_LEFT, ESP_RIGHT  # noqa: E402

OUT = sys.argv[1]
FONT = "(effects (font (size 1.27 1.27)))"
HIDE = "(effects (font (size 1.27 1.27)) hide)"


def u():
    return str(uuid.uuid4())


def esp_type(name):
    if name in ("3V3", "5V"):
        return "power_out"
    if name == "GND":
        return "passive"
    if name in ("EN", "VP", "VN", "G34", "G35", "RXD"):
        return "input"
    if name == "TXD":
        return "output"
    return "bidirectional"


def esp32_symbol():
    pins = []
    for i, n in enumerate(ESP_LEFT):
        pins.append(f'(pin {esp_type(n)} line (at -15.24 {22.86 - i * 2.54:.2f} 0) (length 5.08) (name "{n}" {FONT}) (number "{i + 1}" {FONT}))')
    for i, n in enumerate(ESP_RIGHT):
        pins.append(f'(pin {esp_type(n)} line (at 15.24 {22.86 - i * 2.54:.2f} 180) (length 5.08) (name "{n}" {FONT}) (number "{i + 20}" {FONT}))')
    return f'''  (symbol "ESP32_DevKit_38P" (pin_names (offset 1.016)) (in_bom yes) (on_board yes)
    (property "Reference" "U" (at 0 27.94 0) {FONT})
    (property "Value" "ESP32_DevKit_38P" (at 0 -27.94 0) {FONT})
    (property "Footprint" "AquaSense:ESP32_DevKit_38P_Socket" (at 0 -30.48 0) {HIDE})
    (property "Datasheet" "https://www.espressif.com/sites/default/files/documentation/esp32_datasheet_en.pdf" (at 0 0 0) {HIDE})
    (property "ki_keywords" "ESP32 DevKit WROOM-32 38 pin USB-C" (at 0 0 0) {HIDE})
    (property "ki_description" "Placa de desarrollo ESP32 de 38 pines con USB-C (modulo ESP-32), filas separadas 25,4 mm" (at 0 0 0) {HIDE})
    (symbol "ESP32_DevKit_38P_0_1"
      (rectangle (start -10.16 25.4) (end 10.16 -25.4) (stroke (width 0.254) (type default)) (fill (type background)))
      (text "ESP32" (at 0 2.54 0) (effects (font (size 1.778 1.778) bold)))
      (text "DevKit 38P" (at 0 0 0) {FONT})
      (text "USB-C" (at 0 -22.86 0) (effects (font (size 1.016 1.016))))
    )
    (symbol "ESP32_DevKit_38P_1_1"
      {chr(10).join("      " + p for p in pins)}
    )
  )'''


def ph_module_symbol():
    right = [("power_in", "V+", "6", 2.54), ("output", "Po", "3", 0), ("passive", "G", "4", -2.54)]
    left = [("output", "To", "1", 2.54), ("output", "Do", "2", 0), ("passive", "G", "5", -2.54)]
    pins = [f'(pin {t} line (at 15.24 {y} 180) (length 5.08) (name "{n}" {FONT}) (number "{num}" {FONT}))' for t, n, num, y in right]
    pins += [f'(pin {t} line (at -15.24 {y} 0) (length 5.08) (name "{n}" {FONT}) (number "{num}" {FONT}))' for t, n, num, y in left]
    return f'''  (symbol "PH-4502C" (pin_names (offset 1.016)) (in_bom yes) (on_board no)
    (property "Reference" "U" (at 0 10.16 0) {FONT})
    (property "Value" "PH-4502C" (at 0 -10.16 0) {FONT})
    (property "Footprint" "" (at 0 0 0) {HIDE})
    (property "Datasheet" "" (at 0 0 0) {HIDE})
    (property "ki_keywords" "pH sensor module BNC E201" (at 0 0 0) {HIDE})
    (property "ki_description" "Modulo amplificador de pH PH-4502C (5 V) con conector BNC para sonda E201. Va fuera de la placa." (at 0 0 0) {HIDE})
    (symbol "PH-4502C_0_1"
      (rectangle (start -10.16 7.62) (end 10.16 -7.62) (stroke (width 0.254) (type default)) (fill (type background)))
      (circle (center 0 3.81) (radius 1.524) (stroke (width 0.254) (type default)) (fill (type none)))
      (circle (center 0 3.81) (radius 0.508) (stroke (width 0) (type default)) (fill (type outline)))
      (text "BNC" (at 0 6.35 0) (effects (font (size 1.016 1.016))))
      (text "Sonda E201" (at 0 -1.27 0) (effects (font (size 1.016 1.016))))
      (text "pH 0-14" (at 0 -3.81 0) (effects (font (size 1.016 1.016))))
    )
    (symbol "PH-4502C_1_1"
      {chr(10).join("      " + p for p in pins)}
    )
  )'''


def footprint():
    stroke = lambda w: f"(stroke (width {w}) (type solid))"  # noqa: E731
    items = []
    x0, x1, hw = -27.16, 27.24, 14.0
    # Silkscreen: DevKit outline, antenna zone, USB arrow, pin 1 mark and the three pins this board uses.
    for (a, b) in (((x0, -hw), (x1, -hw)), ((x1, -hw), (x1, hw)), ((x1, hw), (x0, hw)), ((x0, hw), (x0, -hw))):
        items.append(f'(fp_line (start {a[0]} {a[1]}) (end {b[0]} {b[1]}) {stroke(0.12)} (layer "F.SilkS"))')
    for k in range(6):
        xa = x0 + 0.6 + k * 1.0
        items.append(f'(fp_line (start {xa:.2f} -9) (end {xa + 4:.2f} -5) {stroke(0.1)} (layer "F.SilkS"))')
    items.append(f'(fp_text user "ANTENA" (at {x0 + 3.2:.2f} 0 90) (layer "F.SilkS") (effects (font (size 0.9 0.9) (thickness 0.13))))')
    items.append(f'(fp_text user "USB-C" (at {x1 - 3.2:.2f} 0 90) (layer "F.SilkS") (effects (font (size 0.9 0.9) (thickness 0.13))))')
    items.append(f'(fp_line (start {x1 - 1.6:.2f} -1.6) (end {x1 - 0.4:.2f} 0) {stroke(0.15)} (layer "F.SilkS"))')
    items.append(f'(fp_line (start {x1 - 0.4:.2f} 0) (end {x1 - 1.6:.2f} 1.6) {stroke(0.15)} (layer "F.SilkS"))')
    items.append(f'(fp_circle (center -22.86 15.0) (end -22.56 15.0) {stroke(0.3)} (fill solid) (layer "F.SilkS"))')
    for name, num in (("G34", 5), ("GND", 14), ("5V", 19)):
        x = -22.86 + (num - 1) * 2.54
        items.append(f'(fp_text user "{name}" (at {x:.2f} 10.9 90) (layer "F.SilkS") (effects (font (size 0.8 0.8) (thickness 0.12)) (justify left)))')
    # Fab and courtyard.
    items.append(f'(fp_rect (start {x0} {-hw}) (end {x1} {hw}) {stroke(0.1)} (fill none) (layer "F.Fab"))')
    items.append(f'(fp_rect (start {x0 - 0.5} {-hw - 0.5}) (end {x1 + 1.2} {hw + 0.5}) {stroke(0.05)} (fill none) (layer "F.CrtYd"))')
    pads = []
    for i, n in enumerate(ESP_LEFT):
        shape = "rect" if i == 0 else "circle"
        pads.append(f'(pad "{i + 1}" thru_hole {shape} (at {-22.86 + i * 2.54:.2f} 12.7) (size 1.7 1.7) (drill 1) (layers "*.Cu" "*.Mask") (pinfunction "{n}"))')
    for i, n in enumerate(ESP_RIGHT):
        pads.append(f'(pad "{i + 20}" thru_hole circle (at {-22.86 + i * 2.54:.2f} -12.7) (size 1.7 1.7) (drill 1) (layers "*.Cu" "*.Mask") (pinfunction "{n}"))')
    sock = "${KICAD7_3DMODEL_DIR}/Connector_PinSocket_2.54mm.3dshapes/PinSocket_1x19_P2.54mm_Vertical.wrl"
    models = [
        f'(model "{sock}" (offset (xyz -22.86 -12.7 0)) (scale (xyz 1 1 1)) (rotate (xyz 0 0 -90)))',
        f'(model "{sock}" (offset (xyz -22.86 12.7 0)) (scale (xyz 1 1 1)) (rotate (xyz 0 0 -90)))',
        '(model "${KIPRJMOD}/3dmodels/ESP32_DevKit_38P_USB-C.stpZ" (offset (xyz 0 0 9.54)) (scale (xyz 1 1 1)) (rotate (xyz 0 0 0)))',
    ]
    body = "\n  ".join(items + pads + models)
    return f'''(footprint "ESP32_DevKit_38P_Socket" (version 20221018) (generator pcbnew)
  (layer "F.Cu")
  (descr "ESP32 DevKit 38 pines (USB-C) enchufado en dos zocalos hembra 1x19, paso 2,54 mm, filas a 25,4 mm. Pines 1-19: 3V3...5V; 20-38: GND...CLK")
  (tags "ESP32 DevKit 38 socket")
  (attr through_hole)
  (fp_text reference "REF**" (at 0 -15.4) (layer "F.SilkS") (effects (font (size 1 1) (thickness 0.15))))
  (fp_text value "ESP32_DevKit_38P" (at 0 15.6) (layer "F.Fab") (effects (font (size 1 1) (thickness 0.15))))
  (fp_text user "${{REFERENCE}}" (at 0 0) (layer "F.Fab") (effects (font (size 1 1) (thickness 0.15))))
  {body}
)
'''


os.makedirs(os.path.join(OUT, "AquaSense.pretty"), exist_ok=True)
with open(os.path.join(OUT, "AquaSense.kicad_sym"), "w") as f:
    f.write(f"(kicad_symbol_lib (version 20220914) (generator kicad_symbol_editor)\n{esp32_symbol()}\n{ph_module_symbol()}\n)\n")
with open(os.path.join(OUT, "AquaSense.pretty", "ESP32_DevKit_38P_Socket.kicad_mod"), "w") as f:
    f.write(footprint())
with open(os.path.join(OUT, "sym-lib-table"), "w") as f:
    f.write('(sym_lib_table\n  (lib (name "AquaSense")(type "KiCad")(uri "${KIPRJMOD}/AquaSense.kicad_sym")(options "")(descr "Simbolos del proyecto"))\n)\n')
with open(os.path.join(OUT, "fp-lib-table"), "w") as f:
    f.write('(fp_lib_table\n  (lib (name "AquaSense")(type "KiCad")(uri "${KIPRJMOD}/AquaSense.pretty")(options "")(descr "Huellas del proyecto"))\n)\n')
print("lib ok")
