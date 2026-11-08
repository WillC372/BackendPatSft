# Integración del medidor de pH

## Opciones evaluadas

| Opción | Cómo se conecta | Protocolo | Viabilidad |
|---|---|---|---|
| **ESP32 + sonda analógica** (p. ej. DFRobot Gravity pH V2) con el firmware incluido | USB serie o Bluetooth clásico SPP | Propio y abierto (ver abajo) | **Recomendada**: el protocolo lo controlamos nosotros |
| **Atlas Scientific EZO-pH** con placa USB-UART | USB serie | UART documentado por Atlas: `R`, `Cal,mid,7.00`, respuestas `*OK` | Viable; adaptador implementado |
| Medidores Bluetooth comerciales con app propia | BLE | Cerrado | Descartados mientras no publiquen su servicio BLE |

Los precios y la disponibilidad deben confirmarse con el proveedor al momento de la compra. No se verificaron en esta entrega.

## Protocolo del firmware AquaSense (ESP32)

9600 baudios, líneas terminadas en `\n`:

| Comando | Respuesta |
|---|---|
| `PING` | `PONG AQUASENSE-PH-001` |
| `READ` | `PH:7.12;T:NA` (o `T:24.5` si hay sensor de temperatura) |
| `CAL:MID:7.00` / `CAL:LOW:4.00` | `OK` o `ERR:motivo` |

Firmware de referencia: `hardware/esp32-ph-sensor/esp32-ph-sensor.ino`. Conexión sugerida: salida analógica de la placa de la sonda al GPIO34 del ESP32, alimentación de 3,3 V y GND común.

## Bluetooth

El backend usa jSerialComm. Un dispositivo Bluetooth clásico con perfil SPP, una vez emparejado en el sistema operativo, aparece como puerto serie (`COMx` en Windows, `/dev/rfcommX` en Linux, `/dev/tty.*` en macOS) y se conecta igual que uno USB. BLE (Bluetooth de baja energía) no está soportado desde el backend.

## Qué está probado y qué falta

| Elemento | Estado |
|---|---|
| Adaptadores Atlas EZO y firmware ESP32 | Probados con un enlace serie falso (`AdapterTest`) |
| Máquina de estados, reconexión tras fallo, lectura en vivo | Probados con el simulador y el enlace falso |
| Detección de puertos serie del sistema | Probada en Linux (detecta los puertos disponibles) |
| Lectura con un medidor físico real | **Pendiente: requiere hardware** |
| Calibración real (respuesta `*OK` del EZO, `OK` del ESP32) | **Pendiente: requiere hardware** |
| Compilación del firmware ESP32 | **Pendiente: no se compiló** |

## Simulador

`SimulatedPhMeter` genera lecturas alrededor de pH 7,15 con deriva y ruido. Todo lo que produce se guarda con origen `SIMULATED`, se muestra con la etiqueta "SIMULADO" y lo advierte la cadena de validación. No admite calibración.
