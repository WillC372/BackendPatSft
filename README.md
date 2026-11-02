# AquaSense AI — Backend

API REST de **AquaSense AI**, un sistema de monitoreo, almacenamiento y análisis del pH del agua (agua potable, piscinas, acuarios, riego y uso general). Este repositorio contiene el servidor; la aplicación de escritorio está en el repositorio [FrontendPatSft](https://github.com/WillC372/FrontendPatSft).

Proyecto de la asignatura **Patrones de Software**. Todo el código es Java.

> **Aviso sobre el pH.** Una medición de pH, por sí sola, no demuestra que el agua sea potable ni segura ni que esté libre de contaminantes. La API incluye este aviso en sus respuestas de clasificación, análisis e informes.

## Estado real del proyecto

| Área | Estado |
|---|---|
| Registro, login, "recordarme", recuperación de contraseña | Implementado y probado. El envío de correo (SMTP) **no está verificado con un servidor real**. |
| Perfiles de agua con rangos y fuentes, perfiles personalizados | Implementado y probado |
| Dispositivos y mediciones (validación, clasificación, historial con filtros) | Implementado y probado |
| AquaSense Intelligence: anomalías, tendencias, predicción con incertidumbre, recomendaciones, escenarios, alertas | Implementado y probado (estadística propia, sin API de IA) |
| Informes (Markdown y HTML con gráfica SVG), consulta y descarga | Implementado y probado |
| Freemium: límites por plan aplicados en el servidor | Implementado y probado |
| **Pagos reales** (Wompi, Mercado Pago, Stripe) | **No implementado.** Premium lo asigna un administrador. |
| **IA generativa** (chat, redacción de informes) mediante API externa | **No implementado** (requiere clave del proveedor). |
| Conectores de medidor físico (Bluetooth/USB) | **No implementado**; ver el repositorio del frontend. |
| Despliegue público | **No realizado.** Hay guía y `Dockerfile` sin probar; ver [docs/despliegue.md](docs/despliegue.md). |

Las limitaciones completas están en [docs/limitaciones.md](docs/limitaciones.md).

## Responsabilidades del servidor

Centraliza la autenticación, la persistencia en PostgreSQL, las reglas de negocio (clasificación por perfil, validación de mediciones), el análisis, los informes y el control de límites por plan. El cliente nunca accede a la base de datos.

## Arquitectura

Monolito modular con arquitectura en capas por módulo (`api` → `service` → `repository` → `domain`). Detalle y diagramas en [docs/arquitectura.md](docs/arquitectura.md).

```
src/main/java/com/aquasense
├── identity       registro, login, JWT, recuperación, roles
├── waterprofile   perfiles de agua y clasificación
├── device         dispositivos del usuario
├── measurement    mediciones y cadena de validación
├── intelligence   análisis, alertas, escenarios
├── reporting      informes (Builder, Composite, Visitor)
├── billing        planes, límites, suscripciones
├── shared         errores comunes y Specification
└── config         seguridad y OpenAPI
src/main/resources/db/migration   V1…V6 (Flyway)
```

## Tecnologías

Java 21 · Spring Boot 3.5.0 · Spring Security · Spring Data JPA · PostgreSQL (probado con la versión 16) · Flyway · jjwt 0.12.6 · BouncyCastle (Argon2id) · springdoc-openapi 2.8.8 · JUnit 5 / Mockito / MockMvc · Maven.

## Requisitos previos

- JDK 21
- Maven 3.9+
- PostgreSQL 14 o superior (probado con 16)

## Instalación y configuración

1. Cree la base de datos y el usuario:

   ```sql
   CREATE USER aquasense WITH PASSWORD 'cambie-esta-clave';
   CREATE DATABASE aquasense OWNER aquasense;
   ```

2. Defina las variables de entorno (hay un ejemplo sin secretos en `.env.example`):

   | Variable | Obligatoria | Descripción |
   |---|---|---|
   | `JWT_SECRET` | Sí | Secreto de firma de tokens, mínimo 32 bytes. Si falta o es corto, el servidor no arranca. Genere uno con `openssl rand -base64 48`. |
   | `DB_URL` | No | Por defecto `jdbc:postgresql://localhost:5432/aquasense` |
   | `DB_USERNAME` | No | Por defecto `aquasense` |
   | `DB_PASSWORD` | Sí | Clave de la base de datos |
   | `PORT` | No | Por defecto `8080` |
   | `ADMIN_EMAIL` | No | Correo de un usuario **ya registrado** al que se concede el rol `ADMIN` al arrancar |
   | `MAIL_ENABLED`, `MAIL_FROM`, `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD` | No | Envío de códigos de recuperación. Con `MAIL_ENABLED=false` (por defecto) no se envía correo y el servidor lo registra. |

   Nunca incluya secretos en el repositorio.

3. Las migraciones se aplican solas al arrancar (Flyway).

## Ejecución local

```bash
export DB_PASSWORD=cambie-esta-clave
export JWT_SECRET=$(openssl rand -base64 48)
mvn spring-boot:run
```

O con el JAR:

```bash
mvn -DskipTests package
java -jar target/aquasense-backend-0.1.0-SNAPSHOT.jar
```

- Salud: `GET /actuator/health`
- Documentación OpenAPI: `/swagger-ui.html` y `/v3/api-docs`

## API

Versionada bajo `/api/v1`. Todos los endpoints, salvo `auth/*`, exigen `Authorization: Bearer <token>`. Referencia completa en [docs/api.md](docs/api.md).

| Prefijo | Función |
|---|---|
| `/api/v1/auth` | registro, login, refresh, logout, recuperación |
| `/api/v1/users` | usuario actual |
| `/api/v1/profiles` | perfiles de agua y clasificación |
| `/api/v1/devices` | dispositivos |
| `/api/v1/measurements` | mediciones e historial |
| `/api/v1/analysis` | análisis, alertas, escenarios |
| `/api/v1/reports` | informes |
| `/api/v1/subscriptions` | plan, límites y uso |

Los errores tienen un formato único: `{ timestamp, status, code, message, details[] }`. Un límite de plan responde `402` con `code = PLAN_LIMIT_REACHED`.

## Autenticación y autorización

- Contraseñas con **Argon2id**; nunca en texto plano ni cifrado reversible.
- Token de acceso JWT de 15 minutos y token de refresco opaco, con rotación, guardado como hash SHA-256 (7 días; 30 con "recordarme").
- Bloqueo de la cuenta durante 15 minutos tras 5 intentos fallidos.
- Código de recuperación de 10 caracteres, de un solo uso, válido 30 minutos, guardado como hash; al restablecer se cierran todas las sesiones.
- Cada usuario accede solo a sus datos (verificado por pruebas). Rol `ADMIN` solo para asignar planes.

## Integración con inteligencia artificial

El análisis actual usa **estadística propia** (sin llamadas a servicios externos): detección de anomalías, regresión lineal, predicción con intervalo del 95 %, reglas de recomendación y simulador de escenarios. La integración con una API de IA generativa (chat y redacción) **no está implementada**; ver [docs/limitaciones.md](docs/limitaciones.md).

## Integración con dispositivos

Los medidores se conectan al **cliente** (Bluetooth/USB están junto al usuario), que envía cada lectura a `POST /api/v1/measurements`. El servidor decide si una medición es `REAL` o `SIMULATED` según el tipo del dispositivo registrado; el cliente no puede declararla. Detalles en el repositorio del frontend.

## Pruebas

Requieren una base PostgreSQL de pruebas. Por defecto usan `aquasense_test` con el usuario `aquasense` (ver `src/test/resources/application-test.yml`, valores solo de desarrollo); se sobrescriben con `TEST_DB_URL`, `TEST_DB_USERNAME` y `TEST_DB_PASSWORD`.

```bash
mvn test
```

Último resultado verificado: **84 ejecuciones, 0 fallos** (69 métodos de prueba; los parametrizados se expanden). Plan y resultados en [docs/pruebas.md](docs/pruebas.md).

## Despliegue

No se ha desplegado todavía. [docs/despliegue.md](docs/despliegue.md) describe qué hace falta y los pasos previstos.

## Solución de problemas

| Síntoma | Causa y solución |
|---|---|
| `JWT_SECRET debe definirse con al menos 32 bytes` | Falta la variable o es corta. |
| `Connection refused` a PostgreSQL | El servidor de base de datos no está en marcha o `DB_URL` es incorrecta. |
| `/actuator/health` en `DOWN` | Revise la conexión a la base de datos. El correo no afecta la salud cuando `MAIL_ENABLED=false`. |
| `Schema-validation` al arrancar | La base tiene un esquema distinto de las migraciones; use una base vacía. |
| `402 PLAN_LIMIT_REACHED` | Límite del plan gratuito alcanzado (no es un error del servidor). |

## Documentación

[Requisitos](docs/requisitos.md) · [Arquitectura](docs/arquitectura.md) · [Patrones de diseño](docs/patrones.md) · [Modelo de datos](docs/modelo-datos.md) · [API](docs/api.md) · [Pruebas](docs/pruebas.md) · [Despliegue](docs/despliegue.md) · [Limitaciones](docs/limitaciones.md)

## Licencia

Sin licencia definida (proyecto académico). Pendiente de que el autor elija una.
