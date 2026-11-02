# Limitaciones conocidas y trabajo pendiente

Este documento enumera, sin atenuar, lo que **no** está hecho o **no** se ha verificado.

## Funcionalidad no implementada

- **Pagos reales.** No hay integración con Wompi, Mercado Pago ni Stripe, ni webhooks, ni renovaciones. El plan Premium lo asigna un administrador. No se simula ningún cobro.
- **IA generativa.** No hay llamada a un proveedor externo: ni chatbot, ni informes redactados por modelo. La capa de abstracción de proveedor, el Circuit Breaker, los reintentos y el control de costos **no existen** todavía. Todo análisis actual es estadística y reglas propias.
- **Conectores de medidor físico.** No hay Bluetooth ni USB. Tampoco calibración.
- **Notificaciones** push o por correo de alertas.
- **Tablas** de pagos, preferencias y auditoría (ver [modelo-datos.md](modelo-datos.md)).
- **Limitación de tasa por IP** y verificación del correo al registrarse.
- **Internacionalización**: solo español.

## No verificado

- Envío de correo con un servidor SMTP real.
- Despliegue, HTTPS real y el `Dockerfile`.
- Rendimiento con grandes volúmenes (el análisis toma como máximo las 1000 mediciones más recientes).
- Accesibilidad.

## Decisiones de diseño que conviene conocer

- Las predicciones son una regresión lineal: no modelan estacionalidad ni intervenciones. Se muestran con su intervalo del 95 % y sus limitaciones.
- Los umbrales de precaución de los perfiles los definió AquaSense; las fuentes (OMS, EPA, CDC, FAO) respaldan el rango recomendado, no la banda de precaución.
- Las mediciones recibidas con más de 24 h de retraso se guardan como "sospechosas".
- Un usuario puede quedar sin acceso si olvida su contraseña y el correo no está configurado: no hay recuperación manual.
