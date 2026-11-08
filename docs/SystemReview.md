# Revisión general del sistema (7 nov 2026)

Alcance: seguridad del backend y del frontend, robustez, recorrido automático de todas las pantallas y ortografía de los textos visibles.

## Hallazgos y correcciones

| # | Severidad | Hallazgo | Corrección | Prueba |
|---|---|---|---|---|
| 1 | **Alta** | Quien se registrara primero con el correo de `ADMIN_EMAIL` obtenía el rol de administrador sin demostrar que el correo era suyo | El rol se activa solo al abrir un enlace de un solo uso enviado a ese correo (vence en 24 h, guardado como hash). Al activarse se cierran las sesiones abiertas | `PasswordRecoveryEmailIntegrationTest.adminRoleRequiresConfirmationFromTheAdminMailbox`, `ApiFlowIntegrationTest.adminEndpointsRequireAdminRole` |
| 2 | Media | Al conectar un medidor, el nombre del puerto se abría tal cual: se podía intentar abrir cualquier archivo de dispositivo del servidor (por ejemplo `/dev/sda`) | Solo se aceptan puertos que el sistema detecta como puertos serie | `ApiFlowIntegrationTest.simulatorReadingsAreLabelledAndSavedOnRequest` |
| 3 | Media | Una URL mal formada (`/%E0%A4%A`) tumbaba el servidor del frontend | Manejo de errores en cada petición (400), solo GET y HEAD, archivos ocultos bloqueados y cabeceras `X-Content-Type-Options`, `X-Frame-Options` y `Referrer-Policy` | Verificado en vivo |
| 4 | Media | La lectura por foto decodificaba la imagen completa antes de revisar su tamaño: un PNG de pocos bytes que declare 30.000 × 30.000 píxeles podía agotar la memoria | Se leen las dimensiones de la cabecera antes de decodificar (máximo 50 megapíxeles y 12.000 px por lado) y se decodifica con submuestreo | `ImageSanitizerTest.rejectsDecompressionBombsBeforeDecodingPixels` |
| 5 | Baja | Al cambiar la contraseña, otros enlaces de recuperación pendientes de la misma cuenta seguían siendo válidos | Se invalidan todos al usar uno | `PasswordRecoveryEmailIntegrationTest.resetLinkArrivesByEmailAndWorksOnce` |
| 6 | Baja | Sin límite de conexiones en vivo (SSE) por usuario | Máximo 5 por usuario; al abrir una más se cierra la más antigua | Revisión de código |
| 7 | Baja | Swagger UI y `/v3/api-docs` siempre públicos | Se desactivan con `API_DOCS_ENABLED=false` (recomendado en producción) | Configuración |
| 8 | Texto | Unas 40 palabras sin tilde en mensajes visibles (por ejemplo "un ano de antiguedad", "por si solo", "hipotetico", "Evalua", "Infiltracion") | Corregidas en backend, frontend y en la descripción de dos perfiles del sistema (migración V8) | Recorrido de pantallas |

## Lo que se revisó y está bien

- **Acceso a datos ajenos:** cada recurso con id (mediciones, alertas, perfiles, diagnósticos, informes, conversaciones, lecturas pendientes del medidor y de fotos) comprueba que pertenezca al usuario; si no, responde 404.
- **Autenticación:**
  - Contraseñas con Argon2.
  - JWT revocables, y cambiar la contraseña cierra las sesiones.
  - Límite de intentos en registro, inicio de sesión, recuperación y confirmación de administrador.
  - Los tokens de recuperación se guardan como hash SHA-256.
- **Transmisión en vivo:** el token viaja en la cabecera, no en la URL.
- **Inyección de HTML (XSS):** el texto de la IA, las notas y los mensajes pasan por `esc()` antes de insertarse; las notificaciones también escapan.
- **Exportación CSV:** neutraliza fórmulas (`=`, `+`, `-`, `@`).
- **Fechas:** las mediciones con fecha futura o de más de un año se rechazan.
- **Interfaz:** se recorrieron 18 pantallas en escritorio y móvil, con tema claro y oscuro. No hubo errores de JavaScript ni respuestas 5xx, desbordes horizontales ni imágenes rotas.

## Recomendaciones para producción (sin cambio de código)

- **Tras un proxy inverso** (Nginx, Render, Railway…): configurar `server.forward-headers-strategy=native`. Si no, el límite de intentos ve la IP del proxy y no la del usuario.
- **Variables de producción:**
  - `API_DOCS_ENABLED=false`.
  - `MAIL_LOG_RESET_LINKS=false` (ya es el valor por defecto).
  - `JWT_SECRET` largo y aleatorio.
  - `CORS_ORIGINS` solo con el dominio real.
- **Frontend con HTTPS:** servirlo con HTTPS y una política CSP en el servidor web de producción. El servidor `Server.mjs` es solo para desarrollo.
- **Token de sesión:** se guarda en `localStorage`. Es habitual en aplicaciones de una sola página, pero depende de que no haya XSS. Mantener el escapado al agregar pantallas.
