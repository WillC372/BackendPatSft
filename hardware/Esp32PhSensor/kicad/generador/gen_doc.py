"""Builds Documentacion.html (then printed to PDF) with the four representations and the verification results."""
import base64
import csv
import os
import re
import sys

sys.path.insert(0, os.path.dirname(__file__))
from design import NETS, TITLE, REV, DATE  # noqa: E402

D = sys.argv[1]


def svg_inline(path, crop=None):
    s = open(path, encoding="utf-8").read()
    s = s[s.index("<svg"):]
    s = re.sub(r'\swidth="[^"]*"', "", s, count=1)
    s = re.sub(r'\sheight="[^"]*"', "", s, count=1)
    if crop:
        s = re.sub(r'viewBox="[^"]*"', f'viewBox="{crop}"', s, count=1)
    return s.replace("<svg", '<svg class="fit" preserveAspectRatio="xMidYMid meet"', 1)


def img(path):
    return "data:image/png;base64," + base64.b64encode(open(path, "rb").read()).decode()


net_rows = "".join(f"<tr><td><code>{n.lstrip('/')}</code></td><td>{', '.join(f'{r}-{p}' for r, p in nodes)}</td></tr>" for n, nodes in NETS.items())
bom = list(csv.reader(open(os.path.join(D, "4-PCB/Fabricacion/BOM.csv"), encoding="utf-8")))
bom_rows = "".join("<tr>" + "".join(f"<td>{c}</td>" for c in r[:3] + [r[4], r[5]]) + "</tr>" for r in bom[1:])
cons = open(os.path.join(D, "Consistencia.md"), encoding="utf-8").read()
cons_rows = ""
for line in cons.splitlines():
    if line.startswith("| `"):
        cells = [c.strip() for c in line.strip("|").split("|")]
        cons_rows += "<tr>" + "".join(f"<td>{c.replace('`', '')}</td>" for c in cells) + "</tr>"
gerbers = sorted(os.listdir(os.path.join(D, "4-PCB/Fabricacion/Gerber")))
drc = open(os.path.join(D, "4-PCB/DRC.rpt")).read()
counts = [int(n) for n in re.findall(r"Found (\d+) ", drc)]
drc_line = f"{counts[0]} violaciones de reglas, {counts[1]} pads sin conectar, {counts[2]} errores de huella"

pages = []
pages.append(f"""
<section class="page cover">
  <div class="cover-text">
    <p class="kicker">AquaSense AI · Documentación de hardware · Rev. {REV} · {DATE}</p>
    <h1>Medidor de pH: ESP32 + PH-4502C</h1>
    <p class="lead">Cuatro representaciones del mismo circuito, generadas desde un único proyecto editable de KiCad 7.</p>
    <ol class="toc">
      <li><b>Esquemático electrónico</b> — símbolos normalizados, redes y notas de diseño.</li>
      <li><b>Vista tridimensional de la PCB</b> — modelos 3D reales sobre la placa, con sus capas.</li>
      <li><b>Diagrama de montaje en protoboard</b> — cables, terminales y huecos.</li>
      <li><b>Diseño del circuito impreso</b> — capas, pistas, pads, vías, taladros y cotas.</li>
      <li>Fabricación, lista de materiales y verificación de consistencia.</li>
    </ol>
    <h3>Redes del circuito</h3>
    <table class="t"><tr><th>Red</th><th>Pines</th></tr>{net_rows}</table>
    <p class="ok">✔ Las cuatro redes coinciden en esquemático, PCB y protoboard (verificación automática). ✔ DRC: {drc_line}.</p>
  </div>
  <img class="cover-img" src="{img(os.path.join(D, '2-Vista3D/3D-isometrica.png'))}">
</section>""")
pages.append(f"""<section class="page"><h2>1 · Esquemático electrónico</h2>
  <div class="frame">{svg_inline(os.path.join(D, '1-Esquematico/AquaSensePh.svg'))}</div>
  <p class="cap">Archivo editable: <code>kicad/AquaSensePh.kicad_sch</code>. Exportado de KiCad: PDF y SVG vectoriales, netlist <code>AquaSensePh.net</code>.</p></section>""")
v = os.path.join(D, "2-Vista3D")
pages.append(f"""<section class="page"><h2>2 · Vista tridimensional de la PCB</h2>
  <div class="grid4">
    <figure><img src="{img(os.path.join(v, '3D-isometrica.png'))}"><figcaption>Montada: el ESP32 enchufado en los zócalos (altura total 17,6 mm sobre la cara inferior).</figcaption></figure>
    <figure><img src="{img(os.path.join(v, '3D-sin-ESP32.png'))}"><figcaption>Sin el ESP32: zócalos 1×19, J1, R1, R2, serigrafía y zona sin cobre bajo la antena.</figcaption></figure>
    <figure><img src="{img(os.path.join(v, '3D-superior.png'))}"><figcaption>Vista superior ortográfica: 80 × 44 mm, agujeros M3 a 3,5 mm de los bordes.</figcaption></figure>
    <figure><img src="{img(os.path.join(v, '3D-inferior.png'))}"><figcaption>Cara inferior (vista desde abajo): plano de GND y serigrafía inferior.</figcaption></figure>
  </div>
  <p class="cap">Modelos 3D de las bibliotecas oficiales de KiCad (zócalos, resistencias, módulo ESP32-WROOM-32, USB-C…) colocados con las posiciones del archivo de PCB. Archivos: <code>AquaSensePh-STEP.zip</code> (STEP, editable en FreeCAD/Fusion 360/SolidWorks) y <code>AquaSensePh.glb</code> (Blender, Visor 3D de Windows).</p></section>""")
pages.append(f"""<section class="page"><h2>3 · Diagrama de montaje en protoboard</h2>
  <div class="frame">{svg_inline(os.path.join(D, '3-Protoboard/Protoboard.svg'))}</div>
  <p class="cap">Archivo editable: <code>Protoboard.svg</code> (Inkscape, Illustrator). Las conexiones se calculan desde <code>protoboard.json</code>.</p></section>""")
pages.append(f"""<section class="page"><h2>4 · Diseño del circuito impreso</h2>
  <div class="frame">{svg_inline(os.path.join(D, '4-PCB/PCB-Layout.svg'))}</div>
  <p class="cap">Archivo editable: <code>kicad/AquaSensePh.kicad_pcb</code>. Capas en PDF: <code>Capa-F.Cu.pdf</code>, <code>Capa-B.Cu.pdf</code>, <code>Capa-F.SilkS.pdf</code>, <code>Capa-F.Mask.pdf</code>.</p></section>""")
pages.append(f"""<section class="page"><h2>5 · Fabricación, materiales y verificación</h2>
  <div class="two">
    <div>
      <h3>Lista de materiales (BOM)</h3>
      <table class="t small"><tr><th>Referencias</th><th>Cant.</th><th>Valor</th><th>Descripción</th><th>En placa</th></tr>{bom_rows}</table>
      <h3>Archivos de fabricación (Gerber RS-274X + Excellon)</h3>
      <p class="small">{' · '.join(gerbers)}</p>
      <p class="small">Para pedir la placa (JLCPCB, PCBWay u otro): sube <code>AquaSensePh-Gerber.zip</code>; 2 capas, 1,6 mm, 80 × 44 mm.</p>
    </div>
    <div>
      <h3>Verificación de consistencia</h3>
      <table class="t small"><tr><th>Red</th><th>Referencia</th><th>Esquemático</th><th>PCB</th><th>Protoboard</th></tr>{cons_rows}</table>
      <p class="small">La PCB no incluye U2 (módulo PH-4502C): va fuera de la placa y se conecta a J1 por cable. La vista 3D se genera desde el mismo archivo de PCB.</p>
      <h3>Pendiente de comprobar con el hardware</h3>
      <ul class="small">
        <li>Distancia real entre filas de pines del ESP32 (diseñado a 25,4 mm).</li>
        <li>Orden de pines del módulo PH-4502C (To, Do, Po, G, G, V+) según su serigrafía.</li>
        <li>El firmware y el circuito aún no se han probado con el sensor físico.</li>
      </ul>
    </div>
  </div></section>""")

html = f"""<!doctype html><html lang="es"><head><meta charset="utf-8"><title>{TITLE}</title><style>
@page {{ size: A4 landscape; margin: 8mm; }}
body {{ margin: 0; font-family: Inter, 'DejaVu Sans', Arial, sans-serif; color: #111; }}
.page {{ width: 281mm; height: 192mm; page-break-after: always; display: flex; flex-direction: column; overflow: hidden; }}
.page:last-child {{ page-break-after: auto; }}
h1 {{ font-size: 30pt; margin: 4mm 0 2mm; }} h2 {{ font-size: 15pt; margin: 0 0 2mm; }} h3 {{ font-size: 11pt; margin: 4mm 0 1.5mm; }}
.kicker {{ color: #2b5a8a; font-weight: 700; font-size: 10pt; margin: 0; }} .lead {{ font-size: 12pt; color: #333; }}
.cover {{ flex-direction: row; gap: 8mm; align-items: center; }} .cover-text {{ flex: 1; }} .cover-img {{ width: 128mm; border-radius: 4mm; }}
.toc li {{ margin: 1.2mm 0; font-size: 10.5pt; }} .ok {{ color: #1b7a3a; font-weight: 700; font-size: 10pt; }}
.frame {{ flex: 1; min-height: 0; display: flex; }} svg.fit {{ width: 100%; height: 100%; }}
.cap {{ font-size: 8.5pt; color: #444; margin: 1.5mm 0 0; }} code {{ font-size: 8pt; background: #eef1f6; padding: 0 1mm; border-radius: 1mm; }}
.grid4 {{ flex: 1; display: grid; grid-template-columns: 1fr 1fr; gap: 3mm; min-height: 0; }}
figure {{ margin: 0; display: flex; flex-direction: column; min-height: 0; }} figure img {{ width: 100%; flex: 1; min-height: 0; object-fit: contain; border: 0.3mm solid #dde2ea; border-radius: 2mm; }}
figcaption {{ font-size: 8.5pt; color: #333; margin-top: 1mm; }}
.t {{ border-collapse: collapse; width: 100%; font-size: 9pt; }} .t th, .t td {{ border: 0.2mm solid #c8ced8; padding: 1mm 1.5mm; text-align: left; vertical-align: top; }}
.t th {{ background: #eef1f6; }} .small {{ font-size: 8.5pt; }} .two {{ display: grid; grid-template-columns: 1fr 1fr; gap: 6mm; }}
</style></head><body>{''.join(pages)}</body></html>"""
open(os.path.join(D, "Documentacion.html"), "w", encoding="utf-8").write(html)
print("html ok")
