# Integración del medidor de pH

## Medidor conectado al computador del usuario (app publicada)

El backend publicado corre en la nube y no puede abrir un puerto USB del computador del usuario. Por eso la pantalla **Conexión → Medidor conectado a este equipo** usa la API Web Serial del navegador (Chrome o Edge en computador):

1. El usuario elige el tipo de medidor y la velocidad, y pulsa **Conectar medidor**; el navegador muestra la lista de puertos para que elija el del medidor.
2. La página habla el protocolo del medidor: firmware AquaSense (`PING`, `READ`, `CAL:...`), Atlas EZO-pH (`i`, `R`, `Cal,...`) o un medidor de laboratorio que envía texto (`pH 7.02  25.0 °C`).
3. Cada lectura se envía a `POST /devices/browser-readings` y se guarda como medición real, con el medidor registrado en "Dispositivos registrados".

Un medidor Bluetooth clásico emparejado en el sistema operativo aparece como puerto serie y se conecta igual. La pantalla muestra las últimas líneas que envía el medidor, útil para revisar el formato de un medidor nuevo.

## Opciones evaluadas

| Opción | Cómo se conecta | Protocolo | Viabilidad |
|---|---|---|---|
| **ESP32 + sonda analógica** (p. ej. DFRobot Gravity pH V2) con el firmware incluido | USB serie o Bluetooth clásico SPP | Propio y abierto (ver abajo) | **Recomendada**: el protocolo lo controlamos nosotros |
| **Atlas Scientific EZO-pH** con placa USB-UART | USB serie | UART documentado por Atlas: `R`, `Cal,mid,7.00`, respuestas `*OK` | Viable; adaptador implementado |
| Medidor de laboratorio o de mesa con salida USB o RS-232 | USB serie (con adaptador USB-serie si es RS-232) | Texto: envía una línea con el pH al pulsar "Print"/"Send" o periódicamente | Viable: `TEXT_METER` en el servidor y en el navegador |
| Medidores Bluetooth comerciales con app propia | BLE | Cerrado | Descartados mientras no publiquen su servicio BLE |

Los precios y la disponibilidad deben confirmarse con el proveedor al momento de la compra. No se verificaron en esta entrega.

## Protocolo del firmware AquaSense (ESP32)

9600 baudios, líneas terminadas en `\n`:

| Comando | Respuesta |
|---|---|
| `PING` | `PONG AQUASENSE-PH-001` |
| `READ` | `PH:7.12;T:NA` (o `T:24.5` si hay sensor de temperatura) |
| `CAL:MID:7.00` / `CAL:LOW:4.00` | `OK` o `ERR:motivo` |

Firmware de referencia: `hardware/Esp32PhSensor/Esp32PhSensor.ino`. Conexión sugerida: salida analógica de la placa de la sonda al GPIO34 del ESP32, alimentación de 3,3 V y GND común.

## Bluetooth

El backend usa jSerialComm. Un dispositivo Bluetooth clásico con perfil SPP, una vez emparejado en el sistema operativo, aparece como puerto serie (`COMx` en Windows, `/dev/rfcommX` en Linux, `/dev/tty.*` en macOS) y se conecta igual que uno USB. BLE (Bluetooth de baja energía) no está soportado desde el backend.

## Medidores sin cable de datos: lectura por cámara

Para un medidor de pH que solo tiene pantalla (de laboratorio, de bolsillo o tipo lapicero), la pantalla **Conexión → Medidor por cámara** del frontend lo usa como un medidor conectado:

1. La cámara (del computador o del celular) apunta a la pantalla del medidor, dentro del recuadro guía.
2. El navegador compara cada 1,5 s una miniatura en escala de grises del recuadro. Cuando el número cambia y la imagen queda estable, envía **un solo cuadro** a `POST /vision/ph-reading` con `kind=METER_DISPLAY`. También se puede leer a mano ("Leer ahora") o cada 1, 5 o 15 minutos.
3. El backend lo lee con IA de visión (el mismo flujo y las mismas salvaguardas de la lectura por foto: margen ±0,05 y confianza máxima 0,9 para pantallas).
4. Si la lectura es confiable (80 % o más), se guarda con origen `PHOTO` y la nota "Lectura por cámara". Si no, se muestra sin guardar.

Cada lectura usa una consulta de la cuota diaria de IA (20 en el plan gratuito, 200 en Premium); por eso la imagen se compara en el equipo del usuario y solo se consulta cuando el número cambia. Una sesión de cámara admite como máximo 40 lecturas. Probado con una cámara simulada que muestra 7.42 y luego 6.95: dos consultas a la IA, dos mediciones guardadas, ninguna mientras la pantalla no cambió.

## Qué está probado y qué falta

| Elemento | Estado |
|---|---|
| Adaptadores Atlas EZO y firmware ESP32 | Probados con un enlace serie falso (`AdapterTest`) |
| Máquina de estados, reconexión tras fallo, lectura en vivo | Probados con el simulador y el enlace falso |
| Detección de puertos serie del sistema | Probada en Linux (detecta los puertos disponibles) |
| Lectura por cámara de la pantalla de un medidor | Probada con una cámara simulada y un proveedor de IA local; falta con un medidor real |
| Lectura con un medidor físico real | **Pendiente: requiere hardware** |
| Calibración real (respuesta `*OK` del EZO, `OK` del ESP32) | **Pendiente: requiere hardware** |
| Compilación del firmware ESP32 | **Pendiente: no se compiló** |

## Simulador

`SimulatedPhMeter` genera lecturas alrededor de pH 7,15 con deriva y ruido. Todo lo que produce se guarda con origen `SIMULATED`, se muestra con la etiqueta "SIMULADO" y lo advierte la cadena de validación. No admite calibración.
