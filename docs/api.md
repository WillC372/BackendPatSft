# Referencia de la API

Base: `/api/v1`. Documentación interactiva generada con OpenAPI en `/swagger-ui.html` (JSON en `/v3/api-docs`). Salvo `auth/*` y `GET /actuator/health`, se exige `Authorization: Bearer <accessToken>`.

Formato de error: `{ "timestamp", "status", "code", "message", "details": [] }`.

## Autenticación — `/auth`

| Método | Ruta | Descripción | Errores típicos |
|---|---|---|---|
| POST | `/auth/register` | Crea cuenta (`displayName`, `email`, `username?`, `password`, `confirmPassword`) | 400 `VALIDATION_ERROR`, `PASSWORD_MISMATCH`; 409 `EMAIL_TAKEN`, `USERNAME_TAKEN` |
| POST | `/auth/login` | `identifier` (correo o usuario), `password`, `rememberMe` | 401 `INVALID_CREDENTIALS`; 423 `ACCOUNT_LOCKED` |
| POST | `/auth/refresh` | Rota el refresh token | 401 `INVALID_SESSION` |
| POST | `/auth/logout` | Revoca el refresh token | — |
| POST | `/auth/forgot-password` | Envía un código si el correo existe (misma respuesta siempre) | — |
| POST | `/auth/reset-password` | `code`, `newPassword`, `confirmPassword`; cierra todas las sesiones | 400 `INVALID_RESET_CODE` |

Contraseña: 8 a 72 caracteres con al menos una letra y un número.

## Usuario

`GET /users/me` — datos del usuario autenticado.

## Perfiles de agua — `/profiles`

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/profiles` | Perfiles del sistema y propios |
| GET | `/profiles/{id}` | Detalle |
| POST | `/profiles` | Crea un perfil personalizado (límite por plan) |
| POST | `/profiles/{id}/duplicate` | Copia un perfil como personalizado |
| PUT / DELETE | `/profiles/{id}` | Solo perfiles propios (404 `PROFILE_NOT_EDITABLE` si es del sistema) |
| GET | `/profiles/{id}/classify?ph=` | Clasificación, explicación y aviso |

## Dispositivos — `/devices`

`POST` (límite por plan; 409 `DEVICE_ALREADY_REGISTERED`), `GET`, `GET /{id}`, `DELETE /{id}` (409 `DEVICE_HAS_MEASUREMENTS`). El origen `REAL`/`SIMULATED` lo fija el servidor según `connectionType`.

## Mediciones — `/measurements`

| Método | Ruta | Descripción |
|---|---|---|
| POST | `/measurements` | Registra una lectura; 422 `MEASUREMENT_REJECTED` si la cadena la rechaza |
| GET | `/measurements` | Filtros `from`, `to`, `profileId`, `deviceId`, `source`; `page`, `size` (máx. 200) |
| GET | `/measurements/latest` | Última medición (204 si no hay) |
| GET | `/measurements/{id}` | Detalle |

## AquaSense Intelligence — `/analysis`

| Método | Ruta | Descripción |
|---|---|---|
| POST | `/analysis` | `profileId`, `from?`, `to?`, `includeSimulated?` → anomalías, tendencia, predicción, recomendaciones, limitaciones (402 si se agota la cuota) |
| GET | `/analysis/history` | Análisis anteriores |
| GET | `/analysis/{id}` | Informe de un análisis guardado |
| GET | `/analysis/alerts?onlyOpen=` | Alertas |
| POST | `/analysis/alerts/{id}/acknowledge` | Marca como atendida |
| POST | `/analysis/scenario` | `profileId` + `ph` o `delta` → comparación hipotética |

## Informes — `/reports`

`POST` (`profileId`, `from?`, `to?`, `format` = `MARKDOWN`/`HTML`, `includeChart?`, `includeSimulated?`), `GET`, `GET /{id}`, `GET /{id}/content?download=`. HTML y gráfica requieren Premium (402).

## Suscripciones — `/subscriptions`

`GET /subscriptions/me` — plan, límites y uso. `PUT /subscriptions/{userId}` — **solo ADMIN**: `{ "plan": "PREMIUM", "days": 30 }`. No existe endpoint de pago.

## Límites por plan

| | Gratuito | Premium |
|---|---|---|
| Dispositivos | 2 | 10 |
| Perfiles personalizados | 2 | 20 |
| Análisis por día | 3 | 50 |
| Periodo de análisis | 30 días | sin tope |
| Predicción | no | sí |
| Informes por mes | 3 | 100 |
| Informe HTML y gráfica | no | sí |
| Periodo de informe | 30 días | sin tope |
