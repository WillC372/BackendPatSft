# Verificación de consistencia entre las cuatro representaciones

Generado automáticamente por `kicad/check.py` a partir de los archivos editables.

| Red | Referencia (design.py) | Esquemático (netlist KiCad) | PCB (pads) | Protoboard |
|---|---|---|---|---|
| `/PH_OUT` | J1.1, R1.1, U2.3 | ✔ J1.1, R1.1, U2.3 | ✔ J1.1, R1.1 | ✔ J1.1, R1.1, U2.3 |
| `/PH_ADC` | R1.2, R2.1, U1.5 | ✔ R1.2, R2.1, U1.5 | ✔ R1.2, R2.1, U1.5 | ✔ R1.2, R2.1, U1.5 |
| `GND` | J1.2, R2.2, U1.14, U2.4 | ✔ J1.2, R2.2, U1.14, U2.4 | ✔ J1.2, R2.2, U1.14 | ✔ J1.2, R2.2, U1.14, U2.4 |
| `+5V` | J1.3, U1.19, U2.6 | ✔ J1.3, U1.19, U2.6 | ✔ J1.3, U1.19 | ✔ J1.3, U1.19, U2.6 |

La PCB no incluye U2 (módulo PH-4502C): va fuera de la placa y se conecta a J1 por cable; por eso sus pines no
aparecen en la columna PCB. La protoboard sí lo incluye, con los mismos cables Dupont.

## Componentes

- Esquemático: H1, H2, H3, H4, J1, R1, R2, U1, U2
- PCB: H1, H2, H3, H4, J1, R1, R2, U1
- Esperados en la PCB: H1, H2, H3, H4, J1, R1, R2, U1 (U2 queda fuera de la placa)

**Resultado: TODO COINCIDE**
