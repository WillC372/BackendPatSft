# API REST v1 · resumen

Base: `http://localhost:8080/api/v1`. Salvo las rutas de `auth` indicadas, todas requieren `Authorization: Bearer <token>`. La documentación interactiva completa está en `/swagger-ui.html`.

| Método | Ruta | Descripción |
|---|---|---|
| POST | `/auth/register` | Crear cuenta (pública) |
| POST | `/auth/login` | Iniciar sesión; `rememberMe` extiende la sesión a 30 días (pública) |
| POST | `/auth/logout` | Revocar todos los tokens del usuario |
| POST | `/auth/forgot-password` | Enviar enlace de restablecimiento (pública; no revela si el correo existe) |
| POST | `/auth/reset-password` | Cambiar contraseña con el token (pública) |
| GET | `/users/me` | Datos de la cuenta |
| GET / PUT | `/users/me/settings` | Tema, notificaciones, guardado automático, perfil activo |
| GET | `/users/me/activity` | Historial de operaciones |
| GET | `/dashboard` | Resumen para la pantalla de inicio |
| GET / POST | `/profiles` | Listar perfiles visibles / crear perfil personalizado |
| GET | `/profiles/active` | Perfil activo |
| PUT / DELETE | `/profiles/{id}` | Editar o borrar un perfil propio |
| POST | `/profiles/{id}/duplicate` | Duplicar (Prototype) |
| POST | `/profiles/{id}/activate` | Activar perfil |
| GET | `/measurements` | Historial paginado. Filtros: `from`, `to`, `source`, `status`, `page`, `size` |
| POST | `/measurements` | Registro manual (origen `MANUAL`) |
| GET | `/measurements/latest` | Última medición (204 si no hay) |
| GET | `/measurements/series` | Puntos para graficar (`days` o `from`/`to`) |
| GET / DELETE | `/measurements/{id}` | Detalle o borrado |
| GET | `/measurements/export?days=` | CSV |
| POST | `/vision/ph-reading` | Multipart `file` (JPEG/PNG, 10 MB) y `kind` (`TEST_STRIP` o `METER_DISPLAY`). Devuelve la lectura estimada, el margen, las opiniones y un `pendingId` |
| POST | `/vision/ph-reading/{pendingId}/save` | `{ ph, note }`: guarda el valor confirmado o corregido con origen `PHOTO` |
| GET | `/devices/scan` | Simulador y puertos serie detectados |
| GET | `/devices` | Dispositivos registrados |
| GET | `/devices/status` | Estado del medidor |
| POST | `/devices/connect` | `{ protocol: SIMULATOR \| AQUASENSE_ESP32 \| ATLAS_EZO, port, name }` |
| POST | `/devices/disconnect` | Desconectar |
| POST | `/devices/measure` | Medición estabilizada; devuelve `pendingId` si no hay guardado automático |
| POST | `/devices/readings/{pendingId}/save` | Guardar la lectura pendiente |
| POST | `/devices/calibrate` | `{ point: MID \| LOW \| HIGH, bufferValue }` |
| POST | `/devices/live/start` · `/devices/live/stop` | Modo en tiempo real |
| GET | `/devices/live` | Server-Sent Events: `reading`, `status`, `ping` |
| GET | `/alerts` | Alertas (`unreadOnly`, `limit`) |
| PATCH | `/alerts/{id}/read` · POST `/alerts/read-all` | Marcar como leídas |
| GET | `/intelligence/overview` | Resumen de IA del dashboard |
| GET | `/intelligence/anomalies?days=` | Anomalías del periodo |
| GET | `/intelligence/trends?days=` | Tendencia y comparación de periodos |
| GET | `/intelligence/forecast?horizon=` | Predicción con intervalo del 95 % |
| GET | `/intelligence/recommendations` | Recomendaciones según el perfil, con `sources` (citas literales) y `coverageNote` |
| POST | `/intelligence/scenario` | `{ hypotheticalPh }` |
| POST | `/intelligence/conclusion?days=` | Conclusión (IA o reglas) |
| POST | `/intelligence/chat` | `{ question, conversationId? }`. Requiere un proveedor de IA; devuelve `citations` verificadas, `conversationId`, `memoryTurns` y `omittedTurns` |
| GET · DELETE | `/intelligence/chat/conversations` · `/intelligence/chat/conversations/{id}` | Conversaciones guardadas (lista, detalle con citas, borrar una o todas) |
| POST | `/intelligence/scenario/explain` | `{ hypotheticalPh }`. Explicación del escenario (IA validada o reglas) |
| GET | `/intelligence/deep/forecast?includeSimulated=` | Red neuronal LSTM: datos, arquitectura, curva de aprendizaje, comparación con referencias, predicción a 12 h con intervalo y alerta anticipada |
| GET | `/intelligence/ai-usage?days=` · `/admin/ai-usage?days=` | Consumo y costos de IA (propio o global, máximo 90 días) |
| POST | `/intelligence/diagnosis?days=` | Diagnóstico estructurado (IA validada o reglas) |
| GET | `/intelligence/diagnosis/latest` · `/intelligence/diagnosis/history?limit=` | Último diagnóstico (204 si no hay) e historial |
| GET | `/intelligence/ml/insights?days=&includeSimulated=` | Modelo de aprendizaje automático: estado del entrenamiento, puntaje por medición, umbral, anomalías explicadas y patrón por hora |
| GET | `/intelligence/ai-status` | Cadena de proveedores, cuota y límites del plan |
| POST | `/intelligence/agent-reports` | `{ days, focus }`. Inicia el agente de informes; responde 202 con `jobId` |
| GET | `/intelligence/agent-reports/jobs/{jobId}` | Estado del trabajo: fase, pasos realizados y, al terminar, el informe |
| GET | `/intelligence/agent-reports` · `/intelligence/agent-reports/{id}` · `/{id}/text` · `/{id}/pdf` | Historial, informe guardado y descarga en texto o PDF |
| GET | `/reports?days=&includeAi=` · `/reports/text` · `/reports/pdf` | Informe por reglas en JSON, texto o PDF |
| GET | `/knowledge/sources` · `/knowledge/search?q=&profileType=&limit=` · `/knowledge/chunks/{id}` | Biblioteca normativa (ver `docs/RAG.md`) |
| POST | `/auth/verify-admin` | `{ token }`. Confirma el rol de administrador desde el enlace enviado al correo de `ADMIN_EMAIL` (público) |
| GET · POST | `/admin/mail` · `/admin/mail/test` | Estado del canal de correo y correo de prueba al propio administrador (rol `ADMIN`) |
| GET | `/payments/plan` | Plan actual, vencimiento, precio y datos de prueba del sandbox |
| POST | `/payments/checkout` | Crea un pago pendiente y devuelve el formulario firmado de PayU |
| POST | `/payments/verify` | `{ referenceCode }`. Consulta la orden en PayU y activa Premium si está aprobada |
| GET | `/payments` | Historial de pagos |
| POST | `/payments/payu/confirmation` | Confirmación de PayU (pública, firma validada, x-www-form-urlencoded) |
| GET | `/admin/summary` | Cifras del panel: usuarios, activos, Premium (pagado y asignado), administradores, cuentas desactivadas, mediciones y pagos (reales y de prueba por separado) (rol `ADMIN`) |
| GET | `/admin/users?q=&role=&plan=&status=&page=&size=` | Lista paginada (máximo 100 por página). `q` busca en nombre y correo; `status`: `ACTIVE` o `DISABLED` (rol `ADMIN`) |
| GET | `/admin/users/{id}` | Cuenta con estadísticas, pagos, actividad reciente y `guards`: el motivo por el que no se puede cambiar su rol o estado (rol `ADMIN`) |
| PUT | `/admin/users/{id}/plan` | `{ plan, days? }`. Premium con vencimiento en `days` días (1 a 730) o sin vencimiento; `FREE` lo quita (rol `ADMIN`) |
| PUT | `/admin/users/{id}/role` | `{ role }`. Cierra las sesiones del usuario. 409 si es la propia cuenta, el administrador de `ADMIN_EMAIL`, el último administrador activo o una cuenta desactivada que se quiere hacer administrador (rol `ADMIN`) |
| PUT | `/admin/users/{id}/status` | `{ enabled, reason? }`. Desactivar cierra sus sesiones y bloquea el inicio de sesión (403). 409 con las mismas protecciones del rol (rol `ADMIN`) |
| GET | `/admin/payments?limit=` · `/admin/activity?limit=` | Últimos pagos de todos los usuarios y acciones de administración (rol `ADMIN`) |

Errores: JSON `{ timestamp, status, error, message, fields }` con mensajes en español. Códigos: 400 validación, 401 sin sesión, 403 sin permiso, 404 no encontrado o ajeno, 409 conflicto de estado, 422 lectura rechazada, 429 límite alcanzado, 503 medidor o IA no disponibles.
