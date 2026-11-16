"""Compares the connectivity of the three editable sources: schematic netlist (kicad-cli), PCB pads (pcbnew) and the
breadboard model. Writes Consistencia.md and exits with 1 if anything differs."""
import json
import os
import re
import sys

import pcbnew

sys.path.insert(0, os.path.dirname(__file__))
from design import NETS, PARTS  # noqa: E402

NETFILE, PCB, BBJSON, OUT = sys.argv[1:5]

# 1. Schematic netlist (KiCad S-expression).
txt = open(NETFILE).read()
sch_nets = {}
for m in re.finditer(r'\(net \(code "\d+"\) \(name "([^"]+)"\)(.*?)\)\s*(?=\(net |\)\s*\)\s*$)', txt, re.S):
    name, body = m.group(1), m.group(2)
    nodes = sorted(f"{r}.{p}" for r, p in re.findall(r'\(node \(ref "([^"]+)"\) \(pin "([^"]+)"\)', body))
    if not name.startswith("unconnected-"):
        sch_nets[name] = nodes
sch_refs = sorted(set(re.findall(r'\(comp \(ref "([^"]+)"\)', txt)))

# 2. PCB pads.
board = pcbnew.LoadBoard(PCB)
pcb_nets = {}
pcb_refs = []
for fp in board.GetFootprints():
    pcb_refs.append(fp.GetReference())
    for pad in fp.Pads():
        n = pad.GetNetname()
        if n and not n.startswith("unconnected-"):
            pcb_nets.setdefault(n, []).append(f"{fp.GetReference()}.{pad.GetNumber()}")
pcb_nets = {k: sorted(v) for k, v in pcb_nets.items()}
pcb_refs = sorted(pcb_refs)

# 3. Breadboard model: nets are anonymous groups; name them by matching the reference nets.
bb = json.load(open(BBJSON))
ref_sets = {name: sorted(f"{r}.{p}" for r, p in nodes) for name, nodes in NETS.items()}
bb_nets = {}
for group in bb["nets"]:
    match = [n for n, nodes in ref_sets.items() if set(nodes) == set(group)]
    bb_nets[match[0] if match else "SIN_NOMBRE:" + ",".join(group)] = sorted(group)

problems = []
lines = ["# Verificación de consistencia entre las cuatro representaciones", "",
         "Generado automáticamente por `kicad/check.py` a partir de los archivos editables.", ""]
lines += ["| Red | Referencia (design.py) | Esquemático (netlist KiCad) | PCB (pads) | Protoboard |", "|---|---|---|---|---|"]
for name, nodes in ref_sets.items():
    on_board = [n for n in nodes if not n.startswith("U2.")]
    s_ok = sch_nets.get(name) == nodes
    p_ok = pcb_nets.get(name) == on_board
    b_ok = bb_nets.get(name) == nodes
    if not (s_ok and p_ok and b_ok):
        problems.append(name)
    fmt = lambda ok, v: ("✔ " if ok else "✘ ") + (", ".join(v) if v else "—")  # noqa: E731
    lines.append(f"| `{name}` | {', '.join(nodes)} | {fmt(s_ok, sch_nets.get(name))} | {fmt(p_ok, pcb_nets.get(name))} | {fmt(b_ok, bb_nets.get(name))} |")
extra = (set(sch_nets) - set(ref_sets)) | (set(pcb_nets) - set(ref_sets)) | {k for k in bb_nets if k.startswith("SIN_NOMBRE")}
if extra:
    problems.append("redes de más: " + ", ".join(sorted(extra)))
lines += ["", "La PCB no incluye U2 (módulo PH-4502C): va fuera de la placa y se conecta a J1 por cable; por eso sus pines no",
          "aparecen en la columna PCB. La protoboard sí lo incluye, con los mismos cables Dupont.", ""]
board_parts = sorted(PARTS)
lines += ["## Componentes", "",
          f"- Esquemático: {', '.join(sch_refs)}",
          f"- PCB: {', '.join(pcb_refs)}",
          f"- Esperados en la PCB: {', '.join(board_parts)} (U2 queda fuera de la placa)", ""]
if sorted(r for r in sch_refs if r != "U2") != board_parts or pcb_refs != board_parts:
    problems.append("componentes distintos")
lines.append("**Resultado: " + ("TODO COINCIDE**" if not problems else "DIFERENCIAS: " + "; ".join(problems) + "**"))
open(OUT, "w").write("\n".join(lines) + "\n")
print("\n".join(lines))
sys.exit(1 if problems else 0)
