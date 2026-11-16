# Generador del proyecto KiCad y de la documentación

Scripts con los que se generaron el proyecto KiCad (`../`) y la carpeta `../../documentacion/`. Todas las vistas salen
de una sola descripción del circuito (`design.py`), y `check.py` comprueba que coinciden.

| Script | Qué hace |
|---|---|
| `design.py` | Componentes, pines y redes del circuito (fuente única) |
| `gen_lib.py` | Biblioteca del proyecto: símbolos ESP32 DevKit 38P y PH-4502C, huella con dos zócalos 1×19 |
| `gen_sch.py` | Esquemático `AquaSensePh.kicad_sch` con símbolos de las bibliotecas oficiales de KiCad |
| `gen_pcb.py` | PCB `AquaSensePh.kicad_pcb` con la API de KiCad: huellas, pistas, planos GND, zona sin cobre, serigrafía y cotas |
| `devkit3d.py` | Modelo 3D del ESP32 (CadQuery/OpenCascade) con los modelos oficiales de KiCad (WROOM-32, USB-C, …) |
| `breadboard.py` | Diagrama de protoboard (SVG) y su modelo de conexiones (`protoboard.json`) |
| `check.py` | Compara la netlist del esquemático, los pads de la PCB y las conexiones de la protoboard |
| `pcb2json.py`, `pcb3d.html`, `render-pcb.js` | Vista 3D: posiciones y modelos tomados del `.kicad_pcb`, capas de KiCad pintadas sobre la placa |
| `layout_sheet.py` | Hoja del layout con las capas exportadas por KiCad y tablas de taladros y pistas |
| `gen_doc.py`, `html2pdf.js`, `svg2png.js` | Documento PDF y PNG |

Requisitos: KiCad 7 (`kicad-cli` y el módulo de Python `pcbnew`), Node 18+ con `playwright`, `three@0.169.0` y
`occt-import-js@0.0.22`, y CadQuery solo para rehacer el modelo 3D del ESP32. Orden: `gen_lib.py`, `gen_sch.py`,
`gen_pcb.py` (cada uno recibe la carpeta del proyecto), luego las exportaciones con `kicad-cli` y `check.py`.

Para editar el diseño no hace falta nada de esto: abre `../AquaSensePh.kicad_pro` en KiCad 7 o superior.
