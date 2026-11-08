# Pruebas físicas pendientes

Estas pruebas necesitan objetos reales y se harán más adelante, con el dueño del proyecto. Hasta entonces, todo lo
relacionado con hardware y fotos está probado solo con simulaciones, y así se indica en la aplicación.

## 1. Medidor físico

- [ ] Conectar un medidor por USB o Bluetooth SPP (Atlas Scientific EZO-pH o el firmware ESP32 de `hardware/`).
- [ ] Verificar la detección del puerto, la conexión, la lectura en vivo (SSE) y la medición promediada.
- [ ] Calibrar con soluciones tampón de pH 4.00, 7.00 y 10.00 y anotar el error frente a cada una.
- [ ] Probar la desconexión del cable durante una lectura: el sistema debe pasar a "desconectado" sin guardar datos falsos.
- [ ] Confirmar que las lecturas se guardan con origen `REAL`.

## 2. Lectura por foto

- [ ] Fotografiar tiras reactivas reales con la carta de colores, con luz natural, luz cálida y poca luz.
- [ ] Fotografiar la pantalla de un medidor real.
- [ ] Comparar cada lectura de la IA con un medidor calibrado y registrar el error. Los márgenes actuales (±0.5 con carta,
      ±0.05 en pantalla) son supuestos del método que deben confirmarse o ajustarse.

## 3. Red neuronal (capa 7) con datos reales

- [ ] Reunir al menos 8 días de mediciones reales frecuentes (idealmente una por hora) del mismo cuerpo de agua.
- [ ] Revisar si la LSTM supera a las referencias simples con datos reales; hasta ahora solo se comprobó con un historial
      sintético marcado como simulado.

## 4. Precios de IA

- [ ] Confirmar los precios por millón de tokens en las páginas oficiales de Google y Groq y ajustar las variables `PRICE_*`.
