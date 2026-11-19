# Aqua Assistant AI · Backend

API REST principal de **Aqua Assistant AI**, un sistema de monitoreo de pH del agua: autenticación, perfiles de agua, mediciones, **lectura del medidor físico** (USB o Bluetooth SPP), lugares, cuidado del agua, alertas y avisos, pagos y datos oficiales.

El proyecto está en cuatro repositorios:

| Repositorio | Qué es | Dónde corre |
|---|---|---|
| [FrontendPatSft](https://github.com/WillC372/FrontendPatSft) | La aplicación web | Vercel |
| **BackendPatSft** (este) | API principal | Render |
| [AIPatSft](https://github.com/WillC372/AIPatSft) | Servicio de IA: Aqua (chat), diagnóstico, anomalías, predicción, LSTM, Isolation Forest, simulador, agente de informes, informes PDF, lectura de fotos, biblioteca normativa y traducciones | Render |
| [DBPatSft](https://github.com/WillC372/DBPatSft) | Esquema y migraciones de la base de datos | Neon (PostgreSQL) |

Los dos servicios usan la misma base de datos y la misma sesión: el servicio de IA acepta el token que entrega este backend (misma `JWT_SECRET`) y comprueba en la base que la sesión siga abierta. Entre ellos hablan por dos rutas internas protegidas con la clave compartida `INTERNAL_API_KEY`: el servicio de IA envía aquí las mediciones leídas de una foto (para que se validen y generen alertas como cualquier otra) y este backend le pide la traducción de los correos y avisos que todavía no estén en la tabla compartida.

> Frontend: carpeta [`../Frontend`](../Frontend) (repositorio [FrontendPatSft](https://github.com/WillC372/FrontendPatSft)).
>
> Requerimientos funcionales y no funcionales (historias de usuario con criterios de aceptación y MockUps): [FrontendPatSft/docs/Requerimientos.md](https://github.com/WillC372/FrontendPatSft/blob/main/docs/Requerimientos.md).

## Responsabilidades del servidor

- Registrar usuarios, iniciar y cerrar sesión, recuperar contraseñas (JWT + Argon2id).
- Guardar la **foto de perfil**: se recorta en cuadrado, se reduce a 256 px y se vuelve a codificar (se borran los metadatos, como la ubicación). Solo la ve su dueño.
- Guardar el **historial de sesiones**: cada inicio de sesión abre una sesión que agrupa todo lo que el usuario hace hasta cerrarla.
- Mantener las **cuentas de demostración** de los botones del inicio de sesión, Usuario Premium y Administrador.
- Importar **datos oficiales de pH** del Servicio Geológico de EE. UU. (USGS) cada 3 horas y guardarlos aparte de las mediciones de los usuarios.
- Guardar y consultar mediciones con su origen: `REAL` (medidor), `MANUAL` o `PHOTO` (foto leída por el servicio de IA, que la envía a `POST /api/v1/internal/measurements`). El origen `SIMULATED` solo existe en las pruebas automáticas.
- Validar cada lectura (cadena de validación) y clasificarla según el perfil de agua activo.
- Hablar con el medidor por puerto serie: búsqueda, conexión, medición estabilizada, calibración y transmisión en vivo (Server-Sent Events).
- Generar **alertas** al guardar cada medición con reglas fijas: fuera del rango del perfil, **alteración del pH** y valor atípico (`alert/analysis`). El Isolation Forest y el resto de análisis están en el servicio de IA.
- **Alteración del pH** (`PhAlterationRule`): si el pH cambia 0,5 o más frente a cualquier medición de la última hora (también con un medidor que mide cada 10 s y un cambio que crece en varios minutos), o si entra en nivel crítico, se crea la alerta «Alteración del pH en el agua» (`kind` = `PH_ALTERATION`). El mensaje dice si es peligroso según los rangos del perfil (crítico = peligroso) y qué hacer según el tipo de agua (piscina: salir del agua; acuario: los peces corren peligro; agua potable: no tomarla; riego: no regar). El aviso al celular o computador va como urgente (`Urgency: high`), queda en pantalla y vibra; la app hace sonar la alarma y Aqua lo dice en voz alta. La misma alerta no se repite en el mismo lugar durante 30 minutos (salvo que se vuelva peligrosa).
- Separar las mediciones por **lugar** (piscina, acuario, tanque...) y **compartir** un lugar con un código (rol solo ver o ver y medir).
- Guardar **otros parámetros** (cloro libre, alcalinidad, dureza cálcica, ácido cianúrico), **tratamientos**, **recordatorios** e **historial de calibraciones**.
- Enviar **avisos fuera de la app**: Web Push (cifrado RFC 8291 y VAPID RFC 8292 con el JDK, sin dependencias; las claves se crean solas en `app_keys`) y correo, para alertas y recordatorios vencidos.
- Enviar los correos y los avisos en el idioma de cada persona (`i18n/`): las traducciones están en la tabla compartida `ui_translations`, que llena el servicio de IA; lo que falta se le pide a él y, si no responde a tiempo (duerme cuando nadie lo usa), ese texto sale en español.
- Exportar mediciones a CSV. Los informes en PDF los genera el servicio de IA.

## Arquitectura

Monolito modular en capas (controlador → servicio → repositorio), organizado por módulo de negocio. Detalle y diagramas en [`docs/Architecture.md`](docs/Architecture.md). Catálogo de patrones de diseño con su función real y sus pruebas en [`docs/DesignPatterns.md`](docs/DesignPatterns.md).

```
src/main/java/com/aquaassistant/
├── AquaAssistantApplication.java
├── config/         Seguridad, CORS, OpenAPI y propiedades tipadas
├── security/       JWT, filtro de autenticación, limitador de intentos
├── common/         Errores de negocio y manejo centralizado de excepciones
├── audit/          Historial de operaciones, cada una ligada a la sesión en que se hizo
├── user/           Usuarios, autenticación, sesiones (historial), cuentas de demostración, recuperación, preferencias
├── admin/          Administración de usuarios y planes: búsqueda, plan, rol, desactivación, pagos y auditoría
├── profile/        Perfiles de agua, rangos y clasificador de pH
├── measurement/    Mediciones, cadena de validación, filtros (Specification), iterador y observadores
├── device/         Hardware: adaptadores, simulador, fábrica, máquina de estados, comandos, SSE
├── alert/          Alertas: reglas fijas al guardar cada medición (alert/analysis) y avisos
├── i18n/           Idioma de cada persona y traducción de correos y avisos (tabla compartida + servicio de IA)
├── reference/      Datos oficiales del USGS: cliente de su API (Adapter), importador programado y consulta
└── dashboard/      Consulta agregada para la pantalla de inicio
src/main/resources/
└── application.yml
src/test/resources/db/migration/   Copia de las migraciones de DBPatSft para las pruebas (scripts/sync-migrations.sh)
hardware/Esp32PhSensor/    Medidor casero ESP32 + PH-4502C: firmware, pruebas, proyecto KiCad (esquemático, PCB, 3D), protoboard y Wokwi
docs/                        Documentación técnica
```

## Convenciones de código

- **Inglés:** nombres de clases, métodos y variables, comentarios y Javadoc, mensajes del log, claves de los datos que recibe la IA y las instrucciones (prompts) de cada capa de IA. Los prompts piden de forma explícita que la IA responda en español.
- **Español:** solo lo que ve el usuario: mensajes de error de la API, textos de informes, PDF y correos, etiquetas y resúmenes de las normas.
- **Nombres de archivo en inglés y en PascalCase**, como `MeasurementService.java`, `docs/DesignPatterns.md` o `knowledge/Guidelines.json`. Las migraciones conservan el prefijo que exige Flyway (`V1__CreateSchema.sql`).
- **Excepciones por convención de las herramientas:** `README.md`, `pom.xml`, `application.yml`, `Dockerfile`, `docker-compose.yml`, `.gitignore` y `.env.example`. Las carpetas de paquetes Java van en minúsculas, como exige Java.

Una base de datos creada antes del cambio de nombre de las migraciones necesita `flyway repair` (o actualizar `flyway_schema_history`) para registrar los nombres y sumas de verificación nuevos; una base nueva no necesita nada.

## Tecnologías

| Componente | Tecnología |
|---|---|
| Lenguaje | Java 21 |
| Framework | Spring Boot 3.4.5 (Web, Data JPA, Security, Validation, Mail, Actuator) |
| Base de datos | PostgreSQL 16 (Neon); el esquema lo aplica el repositorio DBPatSft con Flyway |
| Seguridad | JWT HS256 (jjwt 0.12.6), contraseñas con Argon2id (Bouncy Castle) |
| Hardware | jSerialComm 2.11.0 (USB serie y Bluetooth clásico SPP) |
| Documentación de la API | springdoc-openapi 2.8.6 (Swagger UI) |
| Pruebas | JUnit 5, Mockito, AssertJ, Awaitility, MockMvc, H2 (modo PostgreSQL) |

## Requisitos previos

- JDK 21 y Maven 3.9 o superior.
- PostgreSQL 16 (o Docker para levantarlo).
- Opcional: clave de la API de Anthropic, servidor SMTP y un medidor compatible (ver [`docs/Hardware.md`](docs/Hardware.md)).

## Instalación y ejecución local

1. Crea la base de datos:
   ```bash
   sudo -u postgres psql -c "CREATE USER aquasense WITH PASSWORD 'cambia-esta-clave';"
   sudo -u postgres psql -c "CREATE DATABASE aquasense OWNER aquasense;"
   ```
2. Copia `.env.example` como `.env` y completa al menos `DB_PASSWORD` y `JWT_SECRET` (32 caracteres o más).
3. Exporta las variables y arranca:
   ```bash
   set -a && source .env && set +a
   mvn spring-boot:run
   ```
   o compila el JAR:
   ```bash
   mvn -DskipTests package
   java -jar target/aqua-assistant-backend-1.0.0.jar
   ```
4. Comprueba: `http://localhost:8080/actuator/health` debe responder `{"status":"UP"}`.

Flyway crea las tablas y los cinco perfiles del sistema en el primer arranque; no hay que ejecutar SQL a mano.

### Con Docker

```bash
cp .env.example .env    # completa DB_PASSWORD y JWT_SECRET
docker compose --env-file .env up --build
```

> En Docker el backend no ve los puertos serie del equipo anfitrión. Para usar un medidor real, ejecuta el JAR directamente en el equipo donde está conectado el medidor, o comparte el dispositivo con el contenedor (`devices:` en Compose, solo en Linux).

## Variables de entorno

| Variable | Obligatoria | Descripción |
|---|---|---|
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | Sí | Conexión a PostgreSQL (o `DATABASE_URL` con la cadena de Neon) |
| `AI_SERVICE_URL` | No | Dirección del servicio de IA, para traducir correos y avisos (`http://localhost:8081`) |
| `INTERNAL_API_KEY` | No | Clave que comparten este backend y el servicio de IA para sus llamadas internas (16 caracteres o más, la misma en los dos). Sin ella no se guardan las lecturas por foto |
| `FLYWAY_ENABLED` | No | `false`: el esquema lo aplica DBPatSft. Ponlo en `true` solo para crear una base local vacía con la copia de las pruebas |
| `JWT_SECRET` | Sí | Clave de firma de tokens, mínimo 32 caracteres. Sin ella el servidor no arranca |
| `PORT` | No | Puerto HTTP (8080) |
| `CORS_ORIGINS` | No | Orígenes permitidos para el frontend |
| `ADMIN_EMAIL` | No | Correo del administrador. Quien se registra o inicia sesión con él recibe un enlace en ese correo, y el rol `ADMIN` se activa **solo** cuando lo abre (así nadie puede registrarse antes con ese correo y quedarse con el rol). Requiere un canal de correo |
| `FRONTEND_ADMIN_VERIFY_URL` | No | Dirección de la pantalla que confirma el administrador (por defecto `http://localhost:5500/#/verify-admin`) |
| `API_DOCS_ENABLED` | No | `false` por defecto: Swagger UI y `/v3/api-docs` no se publican, porque el mapa completo de la API ayuda a un atacante. Ponlo en `true` solo en desarrollo |
| `CLIENT_IP_HEADER` | No | Cabecera con la dirección real del cliente que pone el proxy de delante (`CF-Connecting-IP` por defecto, la de Cloudflare en Render). Vacía: la dirección de la conexión. Fuera de Cloudflare, ponla vacía o con la cabecera de tu proxy |
| `MAILJET_API_KEY`, `MAILJET_SECRET_KEY`, `MAIL_FROM`, `MAIL_SENDER_NAME` | No | **Canal recomendado.** API de envío de Mailjet por HTTPS (plan gratuito de 200 correos al día; funciona aunque el puerto SMTP esté bloqueado). `MAIL_FROM` debe ser un remitente validado en Mailjet |
| `BREVO_API_KEY` | No | Alternativa: API de correo transaccional de Brevo |
| `MAIL_PROVIDER` | No | `auto` (Mailjet, Brevo o SMTP, el primero configurado), `mailjet`, `brevo`, `smtp` o `none` |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USER`, `MAIL_PASSWORD`, `MAIL_FROM` | No | SMTP para recuperar contraseña, alternativa a Brevo. Sin ningún canal de correo, la recuperación responde 503 ("no disponible") igual para cualquier correo, y el enlace nunca se escribe en el log |
| `MAIL_LOG_RESET_LINKS` | No | **Solo desarrollo** (por defecto `false`). Con `true` y sin SMTP, el enlace de recuperación se escribe en el log para probar el flujo; al iniciar se muestra una advertencia. Con SMTP configurado se ignora. **Nunca activarlo en producción**: quien lea los registros podría tomar cuentas |
| `FRONTEND_RESET_URL` | No | URL del frontend que recibe el token de restablecimiento |
| `RATE_LIMIT_PER_MINUTE` | No | Intentos por minuto en registro, inicio de sesión y recuperación (10) |
| `DEMO_ACCOUNTS_ENABLED`, `DEMO_ACCOUNTS_PASSWORD` | No | Cuentas de demostración del inicio de sesión (`true` y `AquaAssistant-Demo-2026`). La contraseña es pública a propósito: solo abre esas cuentas limitadas |
| `REFERENCE_DATA_ENABLED`, `REFERENCE_STATIONS` | No | Importación de datos oficiales del USGS (`true`) y estaciones destacadas, separadas por comas |
| `USGS_API_KEY` | No | Clave gratuita de api.data.gov: sube el límite de solicitudes al USGS (los datos son públicos sin ella) |
| `SIMULATOR_ENABLED` | No | Simulador de medidor (`false`); solo lo encienden las pruebas automáticas |

Las claves de IA (Gemini, Groq) se configuran en el servicio de IA, no aquí.

## Documentación de la API

- Swagger UI: `http://localhost:8080/swagger-ui.html` (con `API_DOCS_ENABLED=true`; apagado por defecto)
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- Resumen de endpoints: [`docs/Api.md`](docs/Api.md)

Todas las rutas están versionadas bajo `/api/v1`. Grupos: `auth`, `users/me` (incluye `users/me/sessions`), `profiles`, `measurements`, `places`, `treatments`, `reminders`, `calibrations`, `devices`, `alerts`, `push`, `reference`, `dashboard`, `payments` y `admin`. Las rutas `intelligence`, `knowledge`, `vision`, `reports` e `i18n` las atiende el servicio de IA.

## Autenticación y autorización

- `POST /api/v1/auth/login` devuelve un JWT. Se envía en `Authorization: Bearer <token>`.
- Duración: 8 horas, o 30 días con "Recordarme".
- `GET /auth/me` devuelve la cuenta del token y su sesión actual (inicio, vencimiento, estado). `GET /users/me` sigue devolviendo solo la cuenta.
- Cada token lleva su sesión (`sid`). Cerrar sesión termina esa sesión: su token deja de servir y la sesión queda en el historial. "Cerrar mis otras sesiones" (`POST /users/me/sessions/close-others`) cierra las de otros equipos. Cambiar la contraseña, el rol o desactivar la cuenta cierra todas.
- Contraseñas con Argon2id; tokens de recuperación de un solo uso, guardados como hash SHA-256 y con vencimiento de 30 minutos.
- Cada consulta filtra por el usuario autenticado; un usuario no puede leer ni borrar datos de otro (lo verifican las pruebas de integración).
- `/api/v1/admin/**` requiere el rol `ADMIN`. Los planes gratuito y Premium se aplican en el servidor: días de análisis histórico, horizonte de predicción y cuota diaria de IA.
- Límite de 10 intentos por minuto por dirección en registro, inicio de sesión y recuperación, con la dirección real del cliente (detrás de Cloudflare, `CF-Connecting-IP`). Además, una cuenta se bloquea 15 minutos tras 5 inicios de sesión fallidos, venga de donde venga; los intentos correctos no cuentan.

## La IA de Aqua

Está en su propio servicio, [AIPatSft](https://github.com/WillC372/AIPatSft): chat con memoria y citas, diagnóstico, anomalías, tendencias y predicción, recomendaciones con respaldo normativo, simulador de escenarios, Isolation Forest y red LSTM por lugar, agente de informes, informes en PDF, lectura de pH por foto, biblioteca normativa, consumo y costos, y las traducciones de la interfaz. Su README explica cada función.

## Historial de sesiones

Cada inicio de sesión (o registro) crea una fila en `user_sessions` con inicio, última actividad, vencimiento, si se pidió "Recordarme" y el navegador resumido ("Chrome en Windows"; no se guardan la IP ni el User-Agent completo). Cada acción del usuario (`audit_log`) se guarda con la sesión en que la hizo: mediciones, preguntas al asistente, descargas, cambios de perfil o de configuración, pagos. Al cerrar sesión, la sesión termina y su token deja de valer; al volver a entrar empieza otra. Las que vencen sin cerrar quedan como "Venció sin cerrar sesión".

- `GET /users/me/sessions`: lista con duración, estado y cuántas veces se hizo cada acción.
- `GET /users/me/sessions/{id}`: el detalle en orden.
- El administrador consulta las de cualquier cuenta (`/admin/users/{id}/sessions`).

## Cuentas de demostración

`DemoAccounts` crea al arrancar dos cuentas reales:

| Botón | Correo | Rol y plan |
|---|---|---|
| Usuario Premium | `premium@demo.aquaassistant` | Usuario, Premium sin vencimiento |
| Administrador | `admin@demo.aquaassistant` | Administrador de solo lectura |

La cuenta gratuita de demostración (`usuario@demo.aquaassistant`) se retiró del inicio de sesión el 15 nov 2026. Como su contraseña era pública, al arrancar se desactiva; sus datos se conservan. Para probar el plan gratuito, crea una cuenta propia.

`GET /auth/demo-accounts` las entrega al frontend con la contraseña (pública a propósito). Como cualquiera puede usarlas, tienen límites que aplica el servidor:

- No se pueden eliminar, recuperar la contraseña ni pagar.
- Los administradores reales no pueden cambiarlas, para que los botones sigan funcionando.
- El administrador de demostración no puede cambiar planes, roles ni estados, borrar cuentas ni enviar correos.
- Ese administrador ve enmascarados el nombre y el correo de las cuentas reales y no puede abrir su detalle ni sus sesiones.
- El "último administrador activo" se cuenta sin él.

## Datos oficiales (USGS)

`ReferenceDataImporter` descarga de la API pública **Water Data OGC** del USGS (`api.waterdata.usgs.gov/ogcapi/v0`, parámetro `00400`, pH):

- Al arrancar y cada 3 horas, el último valor de cada estación que reportó pH en las últimas 24 horas (unas 470 estaciones de ríos y arroyos).
- La serie de ocho estaciones destacadas en distintos estados.
- El nombre de las estaciones con el pH más bajo y más alto.

Las lecturas se guardan 30 días en `reference_stations` y `reference_readings`, aparte de las mediciones de los usuarios. Se conserva el estado de revisión del USGS (los valores recientes son provisionales). Cada importación queda en `reference_imports`.

Si el USGS rechaza una solicitud por su límite (429) o falla un momento, se espera y se repite. Si una estación no se puede actualizar, las demás sí. `GET /reference/summary` y `GET /reference/stations/{id}/readings` alimentan la pantalla "Datos oficiales". Las pruebas usan respuestas grabadas de la API real.

## Administración

La pantalla "Administración" (solo rol `ADMIN`) muestra las cifras del sistema y permite gestionar cada cuenta.

- **Usuarios:** búsqueda por nombre o correo y filtros por rol, plan y estado, con paginación.
- **Plan:** dar Premium por 30, 90 o 365 días, o sin vencimiento, y volver a Gratuito.
- **Rol:** hacer administrador o quitar el rol. Las sesiones abiertas del usuario se cierran para que el cambio rija de inmediato.
- **Eliminar:** borrar para siempre una cuenta con todos sus datos, escribiendo su correo para confirmar. Cada usuario también puede eliminar su propia cuenta desde Configuración, con su contraseña. No se elimina la propia cuenta desde Administración, el administrador principal, el último administrador activo ni una cuenta con pagos reales aprobados.
- **Estado:** desactivar una cuenta con un motivo opcional, o reactivarla. Una cuenta desactivada no inicia sesión (403), sus tokens dejan de valer y no recibe enlaces de recuperación. Sus datos se conservan.
- **Pagos y actividad:** últimos pagos de todos los usuarios (los de prueba se distinguen de los reales) y registro de las acciones de administración.

Reglas que aplica el servidor, aunque la interfaz ya desactive el botón:

- Un administrador no cambia su propio rol ni desactiva su propia cuenta.
- El administrador de `ADMIN_EMAIL` no pierde el rol ni se desactiva desde la pantalla.
- Siempre queda al menos un administrador activo.
- Una cuenta desactivada no recibe el rol de administrador.

Cada cambio queda dos veces en la auditoría: como acción del administrador y en el historial del usuario. Si hay correo configurado, el usuario recibe un aviso; el correo sale después de guardar el cambio, así que un fallo del correo no lo deshace.

## Informes en PDF

Los genera el servicio de IA ([AIPatSft](https://github.com/WillC372/AIPatSft#informes-en-pdf)).

## Pagos

Premium cuesta COP 49.900 por 30 días, con pago por periodo en PayU Latam y sin cobros automáticos. Por defecto se usa el sandbox de PayU con sus credenciales públicas de prueba: no se mueve dinero real y no hace falta registrar un comercio. El servidor activa Premium solo después de consultar la orden en PayU y comprobar estado, cuenta, valor y moneda. Lo verifiqué en vivo con un pago aprobado y otro rechazado. Los detalles, los datos de prueba y los pasos para producción están en [`docs/Payments.md`](docs/Payments.md).

## Correo

La recuperación de contraseña envía un correo con un botón y un enlace de un solo uso que vence en 30 minutos. El canal recomendado es la API de Mailjet por HTTPS (`MailjetMailChannel`); Brevo y SMTP son alternativas (patrón Strategy). Sin canal configurado la recuperación responde que no está disponible y el enlace nunca se escribe en el log, salvo en modo desarrollo (`MAIL_LOG_RESET_LINKS=true`). Los administradores ven el estado del canal y pueden enviarse un correo de prueba desde Configuración.

**Verificado con envío real (7 nov 2026):** Mailjet aceptó el correo de recuperación y lo reportó como enviado (`sent`), sin ningún enlace en el log. El enlace del correo apunta a `FRONTEND_RESET_URL`; en desarrollo es `localhost`, así que solo se abre en el mismo equipo donde corre el frontend.

## Pendientes

Las pruebas físicas con un medidor real están pendientes. Los detalles están en [`docs/PendingWork.md`](docs/PendingWork.md). La última revisión general del sistema está en [`docs/SystemReview.md`](docs/SystemReview.md).

## Integración con dispositivos

Un medidor se conecta de tres formas:

- **Al computador del usuario, desde la app publicada (Web Serial).** En Chrome o Edge, la página abre el puerto USB del medidor (o un Bluetooth emparejado como puerto serie), lee y calibra, y envía cada valor a `POST /devices/browser-readings`, que lo guarda como medición `REAL`. Es la forma de usar un medidor con el backend en la nube, que no puede ver los USB del usuario.
- **Al servidor**, cuando Aqua Assistant corre en el mismo computador del medidor: interfaz `PhMeter` con los adaptadores `AquaAssistantFirmwareAdapter` (firmware ESP32 incluido), `AtlasEzoPhAdapter` (Atlas Scientific EZO-pH por UART) y `TextMeterAdapter` (medidores de laboratorio que envían el valor como texto por USB o RS-232).
- **Por cámara**, para medidores sin cable de datos (ver `docs/Hardware.md`).

**Medidor casero.** [`hardware/Esp32PhSensor/`](hardware/Esp32PhSensor/) tiene todo para armar uno con un ESP32 y el módulo PH-4502C: el firmware (calibración de 1 a 3 puntos con el valor real de cada sobre, por ejemplo 6.86), el esquemático, el cableado y el plano de la baquelita, y el proyecto para simularlo en Wokwi. El firmware se compiló y se probó en el computador (31 pruebas, también en GitHub Actions); falta probarlo con el sensor físico.

El simulador de medidor solo se usa en las pruebas automáticas (`SIMULATOR_ENABLED=false` por defecto): la app publicada no muestra lecturas falsas.

**Estado:** los protocolos están probados con un puerto falso en las pruebas automáticas y en el navegador; **no se han probado con un medidor físico**. Ver [`docs/Hardware.md`](docs/Hardware.md).

## Pruebas

```bash
mvn test
```

111 pruebas automatizadas (las de la IA están en AIPatSft). Cubren:

- Patrones de diseño, algoritmos de análisis, adaptadores de hardware y máquina de estados.
- El flujo completo de la API: registro, aislamiento entre usuarios, simulador, cierre de sesión y roles.
- El historial de sesiones y las cuentas de demostración con sus límites.
- La importación de datos oficiales con respuestas grabadas del USGS.
- La alarma por alteración del pH: caída en una piscina (peligroso, salir del agua), cambio que crece con un medidor cada 10 s, consejo por tipo de agua, entrada en nivel crítico, y una sola alerta para varias lecturas seguidas.
- Las rutas internas con la clave compartida y la traducción de correos: primero la tabla compartida, luego el servicio de IA, y en español si no responde.

Se ejecutan con H2 en modo PostgreSQL y una copia de las migraciones de DBPatSft (`scripts/sync-migrations.sh ../DBPatSft` la actualiza); para correrlas contra PostgreSQL real:

```bash
mvn test -Dspring.datasource.url=jdbc:postgresql://localhost:5432/aquasense_test \
  -Dspring.datasource.username=aquasense -Dspring.datasource.password=TU_CLAVE
```

GitHub Actions las corre en cada cambio de `main` (`.github/workflows/Tests.yml`). Último resultado (19 nov 2026): 111/111 aprobadas, en local y en GitHub Actions.

## Despliegue

Publicado en **Render** (backend y servicio de IA con Docker, plan gratuito), **Neon** (PostgreSQL, esquema aplicado por DBPatSft) y **Vercel** (frontend). La guía paso a paso está en [`docs/Deployment.md`](docs/Deployment.md).

| Parte | Dirección |
|---|---|
| Aplicación | https://aquaassistantai.vercel.app |
| API | https://aquasense-backend-willc372.onrender.com/api/v1 (salud: `/actuator/health`; para pings que la mantengan despierta: `/actuator/health/liveness`) |
| Servicio de IA | https://aquasense-ai-willc372.onrender.com/api/v1 ([AIPatSft](https://github.com/WillC372/AIPatSft)) |

Verificado en producción: el formulario de registro y el de medición de la página de Vercel llaman a la API de Render y guardan en Neon; CORS acepta solo el dominio de Vercel (otro origen recibe 403); el correo está configurado.

- `render.yaml` crea el servicio con su verificación de salud (`/actuator/health`), genera `JWT_SECRET` (cópiala al servicio de IA) y fija las direcciones del frontend y del servicio de IA. Las claves (base de datos, `INTERNAL_API_KEY` y correo) se escriben en el panel de Render, nunca en el repositorio.
- La base se configura con una sola variable, `DATABASE_URL`, con la cadena que entrega Neon (`postgresql://usuario:clave@host/db?sslmode=require`).
- **Mantenerla despierta:** Render apaga la instancia gratuita tras 15 minutos sin uso. Un monitor de UptimeRobot (cada 5 minutos) consulta `/actuator/health/liveness`, que no toca la base de datos. El flujo `.github/workflows/KeepAwake.yml` hace lo mismo, pero GitHub lo ejecuta solo cada pocas horas, así que por sí solo no evita que se apague. Pasos en [`docs/Deployment.md`](docs/Deployment.md#mantener-la-api-despierta).
- **Memoria que se limpia sola:** los intentos de inicio de sesión viejos se borran solos. En la base, los enlaces de recuperación y verificación usados o vencidos hace más de un día se borran cada 6 horas.
- El `Dockerfile` está ajustado a instancias de 512 MB. Los informes PDF y los modelos de IA, lo que más memoria usaba, ahora corren en el servicio de IA.

Un backend en la nube no puede leer un medidor conectado a tu computador. Para lecturas físicas, el backend debe correr en el equipo donde está conectado el medidor.

## Solución de problemas

| Síntoma | Causa y solución |
|---|---|
| La primera visita tarda alrededor de un minuto | Render apagó la instancia por inactividad. Configura el monitor de UptimeRobot ([pasos](docs/Deployment.md#mantener-la-api-despierta)) y revisa que el flujo *Keep the API awake* esté activo en GitHub |
| `JWT_SECRET must be set...` al arrancar | Define `JWT_SECRET` con 32 caracteres o más |
| `Connection refused` a PostgreSQL | Verifica que el servicio esté activo y los valores de `DB_*` |
| El frontend muestra "No se pudo conectar con el servidor" | Backend apagado, o el origen del frontend no está en `CORS_ORIGINS` |
| No aparece el puerto del medidor | Revisa el cable y el controlador USB. En Linux agrega tu usuario al grupo `dialout`. Para Bluetooth, empareja primero desde el sistema |
| Una foto se lee pero no se guarda | `INTERNAL_API_KEY` falta o no es la misma en el backend y en el servicio de IA |
| Tras actualizar el servidor, todos deben volver a iniciar sesión | Los tokens emitidos antes del historial de sesiones no llevan sesión (`sid`) y ya no valen; ocurre una sola vez |
| "Datos oficiales" dice que la última actualización falló | El USGS no respondió o limitó las solicitudes; se muestran los datos anteriores y se reintenta en 3 horas. `USGS_API_KEY` sube el límite |
| "La recuperación de contraseña no está disponible" | No hay canal de correo: define `MAILJET_API_KEY`, `MAILJET_SECRET_KEY` y `MAIL_FROM` (o Brevo o SMTP). Solo en desarrollo puedes usar `MAIL_LOG_RESET_LINKS=true` y copiar el enlace del log |

## Licencia

Proyecto académico de Patrones de Software. Sin licencia de uso definida por el autor.
