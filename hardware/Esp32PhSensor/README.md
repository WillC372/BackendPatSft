# Medidor de pH casero: ESP32 + PH-4502C

Firmware y planos para armar un medidor de pH económico que la app lee por USB (o Bluetooth) con el tipo de medidor **«Firmware AquaSense (ESP32)»**.

> Estado: el firmware se compiló y se probó en un computador con una capa que imita a Arduino (31 pruebas, `tools/FirmwareTest.cpp`) y se puede simular en Wokwi. **Todavía no se ha probado con el sensor físico.**

## Proyecto KiCad y documentación (cuatro vistas del mismo circuito)

El circuito también está como proyecto de **KiCad 7** en [`kicad/`](kicad/) (ábrelo con `AquaSensePh.kicad_pro`), con una PCB de 2 capas de 80 × 44 mm en la que el ESP32 se enchufa en dos zócalos 1×19. En [`documentacion/`](documentacion/) están las cuatro representaciones, exportadas desde ese proyecto, y el resumen en [`Documentacion-Medidor-pH.pdf`](documentacion/Documentacion-Medidor-pH.pdf):

| Vista | Archivos editables / exportables |
|---|---|
| 1. Esquemático | `kicad/AquaSensePh.kicad_sch` · PDF y SVG vectoriales · netlist `AquaSensePh.net` |
| 2. Vista 3D de la PCB | `AquaSensePh-STEP.zip` (STEP: FreeCAD, Fusion 360, SolidWorks) · `AquaSensePh.glb` (Blender, Visor 3D) · 4 renders |
| 3. Montaje en protoboard | `Protoboard.svg` (Inkscape, Illustrator) · `protoboard.json` (conexiones) · PNG |
| 4. Layout de PCB | `kicad/AquaSensePh.kicad_pcb` · hoja del layout (SVG/PNG) · PDF por capa · Gerber + taladros (`AquaSensePh-Gerber.zip`) · BOM |

- **Mismo circuito en todas:** [`Consistencia.md`](documentacion/Consistencia.md) compara red por red la netlist del esquemático, los pads de la PCB y las conexiones de la protoboard: coinciden. La vista 3D se genera desde el mismo archivo de PCB.
- **Reglas de diseño (DRC) de KiCad:** 0 errores, 0 pads sin conectar.
- Antes de mandar a fabricar la PCB, confirma con tu ESP32 que las filas de pines están a 25,4 mm (es lo que usa el diseño) y el orden de pines de tu PH-4502C.
- La carpeta [`planos/`](planos/) es la versión para baquelita perforada (sin fabricar PCB), con el mismo circuito.

## Piezas

| Ref. | Pieza | Cant. |
|---|---|---|
| U1 | ESP32 de 38 pines con USB-C (módulo ESP-32) | 1 |
| U2 | Módulo PH-4502C | 1 |
| — | Sonda de pH E201 con conector BNC | 1 |
| R1, R2 | Resistencia 20 kΩ, 1/4 W | 2 |
| J1 | Espadín hembra 1×19 | 2 |
| J2 | Espadín hembra 1×3 | 1 |
| — | Baquelita perforada de al menos 24×24 huecos | 1 |
| — | Cables Dupont macho-hembra | 3 |
| — | Sobres de calibración pH 6.86 y 4.00 (opcional 9.18) y agua destilada | — |

## Conexiones

| PH-4502C | Va a | Nota |
|---|---|---|
| V+ | 5V del ESP32 | Alimentación desde el USB |
| G | GND del ESP32 | Basta uno de los dos G |
| Po | R1 → G34 del ESP32, y R2 de G34 a GND | El divisor deja la señal a la mitad (máx. 2,5 V) |
| Do, To | sin conectar | |

Planos completos en [`planos/`](planos/): esquemático, cableado y baquelita (vista superior e inferior), también en un PDF para imprimir.

## Comandos (9600 baudios, una línea por comando)

| Comando | Respuesta |
|---|---|
| `PING` | `PONG AQUASENSE-PH-001` |
| `READ` | `PH:7.02;T:NA`, o `ERR:NO_SIGNAL` si no hay sensor, o `ERR:OUT_OF_RANGE` |
| `CAL:MID:6.86` | `OK` (valor entre 6.00 y 8.00) |
| `CAL:LOW:4.00` | `OK` (entre 2.00 y 5.50); `ERR:SAME_AS_MID` si la sonda está en el sobre equivocado |
| `CAL:HIGH:9.18` | `OK` (entre 8.50 y 12.00) |
| `CAL:CLEAR` | `OK` y vuelve a los valores de fábrica |
| `STATUS` | `MV:1251.3;MID:1300.0@6.86;LOW:1560.0@4.00;HIGH:-` |

Cada lectura toma 21 muestras y descarta las 5 más altas y las 5 más bajas. La calibración usa el valor escrito en el comando, así que sirven los sobres de 6.86 y 9.18. Se guarda en la memoria del ESP32 y no se pierde al desconectarlo.

## Cargar el firmware

1. Instala **Arduino IDE 2** y, en *Herramientas → Placa → Gestor de placas*, el paquete **esp32** de Espressif.
2. Abre `Esp32PhSensor.ino`. Placa: **ESP32 Dev Module**. Puerto: el que aparece al conectar el USB-C.
3. Si Windows no reconoce la placa, instala el controlador del chip USB que tenga impreso (CP210x, CH340 o CH9102).
4. Pulsa **Subir**. En el *Monitor serie* (9600 baudios, «Nueva línea») escribe `PING`: debe responder `PONG AQUASENSE-PH-001`.

## Calibrar

En la app: **Conexión → «Medidor conectado a este equipo»**, elige el tipo «Firmware AquaSense (ESP32)», pulsa **Conectar** y luego **Calibrar**. En la ventana, cambia «Valor del buffer» por el número de tu sobre:

1. Sonda en el sobre **6.86**, espera 1 minuto y calibra el punto **Medio** con el valor `6.86`.
2. Enjuaga la sonda con agua destilada y sécala con toques suaves.
3. Sonda en el sobre **4.00**, espera y calibra el punto **Bajo** con `4.00`.
4. Opcional: sobre **9.18**, punto **Alto** con `9.18`.

Antes de calibrar, gira la perilla «ajuste pH 7» del PH-4502C para que en el sobre 6.86 `STATUS` muestre cerca de 1250 mV.

## Simular en Wokwi (sin el sensor)

1. Abre https://wokwi.com/projects/new/esp32.
2. Reemplaza el contenido de `sketch.ino` por [`wokwi/sketch.ino`](wokwi/sketch.ino).
3. En la pestaña `diagram.json`, reemplaza todo por [`wokwi/diagram.json`](wokwi/diagram.json). Aparece un potenciómetro conectado a G34 que hace de sensor.
4. Pulsa ▶. En el monitor serie escribe `PING`, `READ`, `STATUS`. Gira el potenciómetro (clic y arrastrar) y vuelve a pedir `READ`.
5. Prueba la calibración: deja el potenciómetro en una posición, envía `CAL:MID:6.86`; súbelo un poco, envía `CAL:LOW:4.00`; ponlo en medio y pide `READ`.

Sin calibrar, el potenciómetro marca pH 7 cerca de un 38 % de su recorrido; fuera de aproximadamente 19 %–57 % responde `ERR:OUT_OF_RANGE`, porque esa parte equivale a pH por debajo de 0 o por encima de 14.

## Pruebas en el computador

```bash
g++ -std=c++17 -Wall -Wextra -I tools/shim -x c++ tools/FirmwareTest.cpp -o fwtest && ./fwtest
```

`tools/shim/` imita las funciones de Arduino que usa el firmware (lectura analógica, memoria y puerto serie). El flujo `.github/workflows/Firmware.yml` corre estas pruebas en cada cambio de esta carpeta.
