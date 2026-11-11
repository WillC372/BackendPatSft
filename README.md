# AquaSense AI · Backend

API REST de **AquaSense AI**, un sistema de monitoreo de pH del agua. El backend centraliza toda la lógica: autenticación, perfiles de agua, mediciones, **lectura del medidor físico** (USB o Bluetooth SPP), alertas, informes y el módulo de inteligencia **AquaSense Intelligence**.

> Frontend: carpeta [`../Frontend`](../Frontend) (repositorio [FrontendPatSft](https://github.com/WillC372/FrontendPatSft)).

## Responsabilidades del servidor

- Registrar usuarios, iniciar y cerrar sesión, recuperar contraseñas (JWT + Argon2id).
- Guardar el **historial de sesiones**: cada inicio de sesión abre una sesión que agrupa todo lo que el usuario hace hasta cerrarla.
- Mantener las **cuentas de demostración** de los botones del inicio de sesión, una por rol (Usuario, Usuario Premium, Administrador).
- Importar **datos oficiales de pH** del Servicio Geológico de EE. UU. (USGS) cada 3 horas y guardarlos aparte de las mediciones de los usuarios.
- Guardar y consultar mediciones con su origen: `REAL` (medidor), `MANUAL` o `PHOTO` (foto o cámara leída por IA). El origen `SIMULATED` solo existe en las pruebas automáticas.
- Validar cada lectura (cadena de validación) y clasificarla según el perfil de agua activo.
- Hablar con el medidor por puerto serie: búsqueda, conexión, medición estabilizada, calibración y transmisión en vivo (Server-Sent Events).
- Detectar anomalías, analizar tendencias, predecir y recomendar (AquaSense Intelligence).
- Llamar a la IA generativa (Gemini y Groq; Claude como opción de pago) solo desde el servidor, con cuotas por plan y caché. El chat responde **en vivo** (Server-Sent Events).
- Generar informes del periodo en PDF (con gráficas y fuentes normativas) o texto, y exportar mediciones a CSV.

## Arquitectura

Monolito modular en capas (controlador → servicio → repositorio), organizado por módulo de negocio. Detalle y diagramas en [`docs/Architecture.md`](docs/Architecture.md). Catálogo de patrones de diseño con su función real y sus pruebas en [`docs/DesignPatterns.md`](docs/DesignPatterns.md).

```
src/main/java/com/aquasense/
├── AquaSenseApplication.java
├── config/         Seguridad, CORS, OpenAPI y propiedades tipadas
├── security/       JWT, filtro de autenticación, limitador de intentos
├── common/         Errores de negocio y manejo centralizado de excepciones
├── audit/          Historial de operaciones, cada una ligada a la sesión en que se hizo
├── user/           Usuarios, autenticación, sesiones (historial), cuentas de demostración, recuperación, preferencias
├── admin/          Administración de usuarios y planes: búsqueda, plan, rol, desactivación, pagos y auditoría
├── profile/        Perfiles de agua, rangos y clasificador de pH
├── measurement/    Mediciones, cadena de validación, filtros (Specification), iterador y observadores
├── device/         Hardware: adaptadores, simulador, fábrica, máquina de estados, comandos, SSE
├── alert/          Alertas generadas por anomalías
├── intelligence/   AquaSense Intelligence: estadística, reglas, diagnóstico IA, recomendaciones, escenarios, proveedores de IA
│   ├── agent/      Agente de informes: herramientas, ciclo, verificación de cifras, trabajos en segundo plano
│   └── ml/         Isolation Forest propio, variables, escalado robusto, modelo por usuario y regla de anomalías
├── vision/         Lectura de pH desde fotos: saneo de imagen, validación, segunda opinión
├── report/         Informes (Builder), exportación a texto y a PDF (report/pdf: PDFBox y gráficas Java2D)
├── reference/      Datos oficiales del USGS: cliente de su API (Adapter), importador programado y consulta
└── dashboard/      Consulta agregada para la pantalla de inicio
src/main/resources/
├── application.yml
└── db/migration/   Migraciones Flyway V1 a V13 (V11 historial de sesiones, V12 cuentas de demostración,
                    V13 datos oficiales)
hardware/Esp32PhSensor/    Firmware de referencia para ESP32 (no probado con hardware)
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
| Base de datos | PostgreSQL 16 con migraciones Flyway |
| Seguridad | JWT HS256 (jjwt 0.12.6), contraseñas con Argon2id (Bouncy Castle) |
| Hardware | jSerialComm 2.11.0 (USB serie y Bluetooth clásico SPP) |
| IA generativa | Google Gemini y Groq (planes gratuitos) por su API compatible con OpenAI, con `java.net.http`; Claude (SDK oficial de Anthropic 3.3.0) como opción de pago |
| Informes PDF | Apache PDFBox 3.0.3, gráficas con Java2D y fuente DejaVu Sans incluida (licencia libre en `resources/fonts/DejaVuLicense.txt`) |
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
   java -jar target/aquasense-backend-1.0.0.jar
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
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | Sí | Conexión a PostgreSQL |
| `JWT_SECRET` | Sí | Clave de firma de tokens, mínimo 32 caracteres. Sin ella el servidor no arranca |
| `PORT` | No | Puerto HTTP (8080) |
| `CORS_ORIGINS` | No | Orígenes permitidos para el frontend |
| `ADMIN_EMAIL` | No | Correo del administrador. Quien se registra o inicia sesión con él recibe un enlace en ese correo, y el rol `ADMIN` se activa **solo** cuando lo abre (así nadie puede registrarse antes con ese correo y quedarse con el rol). Requiere un canal de correo |
| `FRONTEND_ADMIN_VERIFY_URL` | No | Dirección de la pantalla que confirma el administrador (por defecto `http://localhost:5500/#/verify-admin`) |
| `API_DOCS_ENABLED` | No | `true` por defecto. Pon `false` en producción para no publicar Swagger UI ni `/v3/api-docs` |
| `MAILJET_API_KEY`, `MAILJET_SECRET_KEY`, `MAIL_FROM`, `MAIL_SENDER_NAME` | No | **Canal recomendado.** API de envío de Mailjet por HTTPS (plan gratuito de 200 correos al día; funciona aunque el puerto SMTP esté bloqueado). `MAIL_FROM` debe ser un remitente validado en Mailjet |
| `BREVO_API_KEY` | No | Alternativa: API de correo transaccional de Brevo |
| `MAIL_PROVIDER` | No | `auto` (Mailjet, Brevo o SMTP, el primero configurado), `mailjet`, `brevo`, `smtp` o `none` |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USER`, `MAIL_PASSWORD`, `MAIL_FROM` | No | SMTP para recuperar contraseña, alternativa a Brevo. Sin ningún canal de correo, la recuperación responde 503 ("no disponible") igual para cualquier correo, y el enlace nunca se escribe en el log |
| `MAIL_LOG_RESET_LINKS` | No | **Solo desarrollo** (por defecto `false`). Con `true` y sin SMTP, el enlace de recuperación se escribe en el log para probar el flujo; al iniciar se muestra una advertencia. Con SMTP configurado se ignora. **Nunca activarlo en producción**: quien lea los registros podría tomar cuentas |
| `FRONTEND_RESET_URL` | No | URL del frontend que recibe el token de restablecimiento |
| `GEMINI_API_KEY`, `GEMINI_MODEL`, `GEMINI_FALLBACK_MODEL` | No | Google Gemini, proveedor principal (por defecto `gemini-3.5-flash` y `gemini-3.5-flash-lite`) |
| `GROQ_API_KEY`, `GROQ_MODEL`, `GROQ_VISION_MODEL` | No | Groq, respaldo (texto `openai/gpt-oss-120b`; visión `qwen/qwen3.8-27b`) |
| `RATE_LIMIT_PER_MINUTE` | No | Intentos por minuto en registro, inicio de sesión y recuperación (10) |
| `ANTHROPIC_API_KEY`, `AI_MODEL`, `AI_EFFORT` | No | Claude, opción de pago (`claude-opus-5-5`) |
| `DEMO_ACCOUNTS_ENABLED`, `DEMO_ACCOUNTS_PASSWORD` | No | Cuentas de demostración del inicio de sesión (`true` y `AquaSense-Demo-2026`). La contraseña es pública a propósito: solo abre esas cuentas limitadas |
| `REFERENCE_DATA_ENABLED`, `REFERENCE_STATIONS` | No | Importación de datos oficiales del USGS (`true`) y estaciones destacadas, separadas por comas |
| `USGS_API_KEY` | No | Clave gratuita de api.data.gov: sube el límite de solicitudes al USGS (los datos son públicos sin ella) |
| `SIMULATOR_ENABLED` | No | Simulador de medidor (`false`); solo lo encienden las pruebas automáticas |
| `AI_DAILY_QUOTA_FREE`, `AI_DAILY_QUOTA_PREMIUM` | No | Consultas de IA por usuario y día (20 y 200) |

Sin ninguna clave de IA, el diagnóstico y la conclusión se calculan con reglas y el chat responde "no disponible".

## Documentación de la API

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- Resumen de endpoints: [`docs/Api.md`](docs/Api.md)

Todas las rutas están versionadas bajo `/api/v1`. Grupos: `auth`, `users/me` (incluye `users/me/sessions`), `profiles`, `measurements`, `devices`, `alerts`, `intelligence`, `reports`, `reference`, `dashboard`, `payments` y `admin`.

## Autenticación y autorización

- `POST /api/v1/auth/login` devuelve un JWT. Se envía en `Authorization: Bearer <token>`.
- Duración: 8 horas, o 30 días con "Recordarme".
- Cada token lleva su sesión (`sid`). Cerrar sesión termina esa sesión: su token deja de servir y la sesión queda en el historial. "Cerrar mis otras sesiones" (`POST /users/me/sessions/close-others`) cierra las de otros equipos. Cambiar la contraseña, el rol o desactivar la cuenta cierra todas.
- Contraseñas con Argon2id; tokens de recuperación de un solo uso, guardados como hash SHA-256 y con vencimiento de 30 minutos.
- Cada consulta filtra por el usuario autenticado; un usuario no puede leer ni borrar datos de otro (lo verifican las pruebas de integración).
- `/api/v1/admin/**` requiere el rol `ADMIN`. Los planes gratuito y Premium se aplican en el servidor: días de análisis histórico, horizonte de predicción y cuota diaria de IA.
- Límite de 10 intentos por minuto en registro, inicio de sesión y recuperación.

## AquaSense Intelligence

Primero calcula con estadística y reglas de dominio; la IA generativa solo redacta sobre resultados ya calculados.

| Función | Cómo funciona |
|---|---|
| Anomalías | Fuera de rango del perfil, cambio brusco (≥ 0,5 pH en una hora) y valor atípico (puntaje z robusto > 3,5 con al menos 10 mediciones previas) |
| Tendencias | Regresión lineal (pH por día), estadísticos y comparación con el periodo anterior. Mínimo 5 mediciones |
| Predicción | Regresión lineal con intervalo de predicción del 95 %. Requiere 10 mediciones y 2 días de historial; si no, responde "datos insuficientes" |
| Recomendaciones | Una estrategia por tipo de perfil (potable, piscina, acuario, riego, general), con los fragmentos normativos que la respaldan |
| Escenarios | Clasifica un pH hipotético y lo compara con el actual, sin guardarlo. **Explicación con IA** (`/scenario/explain`): el sistema calcula cuántas veces cambia la concentración de iones H+ y cuándo se llegaría a ese valor con la tendencia de 7 días; la IA redacta efectos por área, señales tempranas y prevención en JSON estricto, citando normas. Se verifica cada cifra contra los hechos, se descartan citas no entregadas y, si el pH hipotético es crítico, al menos un efecto queda como crítico. Sin IA se genera la misma estructura con reglas |
| **Diagnóstico IA** | El sistema calcula los hechos (mediciones, estadística, anomalías, tendencia, predicción, reglas del perfil). La IA razona sobre ellos y responde un **JSON con esquema estricto**: riesgo, hallazgos con evidencia, causas probables con probabilidad, plan de acción priorizado, datos faltantes, confianza y próxima medición. El `DiagnosisValidator` revisa la respuesta y aplica salvaguardas: limita la confianza según la cantidad y el tipo de datos, y no permite un riesgo menor al de la última medición. Si la respuesta no es válida, se usa `RuleBasedDiagnoser`, que produce la misma estructura. Cada diagnóstico se guarda en `ai_diagnoses` |
| Conclusión y chat | Mismo motor; el chat responde con los datos del usuario y cita las normas recuperadas como `[id]`. **Memoria:** cada conversación se guarda (`chat_conversations`, `chat_messages`) y la IA recibe los últimos 12 mensajes, hasta 9000 caracteres, como turnos reales; así entiende repreguntas como "¿y ese valor es grave?". Los datos del usuario se recalculan en cada turno y prevalecen sobre lo dicho antes. Hasta 50 conversaciones por usuario; se pueden borrar una a una o todas |
| **Consumo y costos** | Cada llamada registra proveedor, modelo, tokens, latencia, si vino de caché y su costo estimado con el precio vigente; los intentos fallidos de cada proveedor también se registran (Observer `AiCallListener`) y no cuentan para la cuota. Se guarda en una transacción independiente para no perder el registro si la operación falla. `/intelligence/ai-usage` resume por modelo, por función y por día; `/admin/ai-usage` da el total global y los usuarios con más consumo. Con `AI_BILLING=FREE` el cargo real es 0 y el costo estimado muestra cuánto costaría con facturación. **Los precios configurados vienen de agregadores de terceros y no se verificaron en las páginas oficiales** (la red del entorno no las alcanza); se ajustan con variables `PRICE_*` |
| **Respaldo normativo (RAG)** | 20 fragmentos **literales** de la EPA, la OMS, la CDC (MAHC 2024) y la FAO, con un resumen propio en español. Un índice BM25 local los recupera filtrando por tipo de perfil, y se citan en recomendaciones, diagnóstico, chat y agente (herramienta `search_guidelines`). El servidor descarta cualquier id que no haya entregado. Para acuarios no hay norma verificada y se muestra una nota en lugar de una cita. Detalle en [`docs/Rag.md`](docs/Rag.md) |

| **Lectura por foto** | El usuario fotografía una tira reactiva o la pantalla de un medidor sin conexión. La imagen se reduce a 1024 px y se recodifica (eso borra los metadatos, incluido el GPS); no se guarda. La IA con visión responde un JSON estricto: tipo de imagen, pH, margen, valores de la carta más parecidos, problemas de la foto y temperatura si se ve. El `PhotoReadingValidator` impone la precisión real del método: tira con carta ±0,5 y confianza máxima 0,7; sin carta ±1,0 y 0,35; pantalla ±0,05 y 0,9; cada problema de la foto resta 0,1. En las tiras se pide una **segunda opinión** a otro proveedor: si las dos lecturas difieren en más de 0,5, se amplía el margen y la confianza baja a 0,35. El usuario confirma o corrige el valor antes de guardarlo con origen `PHOTO` |

| **Agente de informes** | El usuario escribe un objetivo, por ejemplo "¿por qué bajó el pH esta semana?" o "informe para una inspección". El agente decide qué consultar con 7 herramientas de solo lectura: `get_water_profile`, `get_statistics`, `get_anomalies`, `get_measurements`, `get_forecast`, `get_alerts` y `get_latest_diagnosis`. Investiga en varios turnos y entrega el informe con `submit_report`: resumen, cifras clave, secciones con la gráfica que elige, recomendaciones y limitaciones. Las herramientas usan el usuario autenticado, nunca un dato del modelo. El ciclo tiene límites (10 turnos, 14 consultas y 2 correcciones). El validador **comprueba cada cifra de pH del texto contra los resultados de las herramientas** y marca las que no coinciden. Corre en segundo plano en un grupo de 2 hilos (Bulkhead), un trabajo por usuario a la vez; la interfaz consulta el avance y muestra cada paso. Si un proveedor falla, la investigación se reinicia con el siguiente; si todos fallan, se genera el informe por reglas. Cada informe se guarda en `agent_reports` con su traza y se puede descargar en PDF o en texto |

| **Aprendizaje automático local** | Un modelo **Isolation Forest** por usuario (Liu, Ting y Zhou, 2008), implementado en Java sin dependencias, en el paquete `intelligence/ml`. Se evaluó la biblioteca Smile, pero desde la versión 3 su licencia es GPL-3.0 y obligaría a distribuir todo el proyecto bajo GPL. El modelo aprende de las mediciones no simuladas de los últimos 90 días: mínimo 30, que cubran 3 días. Usa como variables el pH, el cambio frente a la medición anterior, la velocidad de cambio, la distancia a la mediana de las 10 previas, la hora del día (codificada como seno y coseno) y la temperatura si hay datos. Las variables se escalan con mediana y rango intercuartílico. El modelo tiene 100 árboles, muestras de hasta 256 y un umbral en el 3 % más inusual, nunca menor a 0,6. Cada anomalía se explica con las variables que más se alejan de lo típico. Como `MlAnomalyRule` se suma al detector, las **alertas, el diagnóstico y el agente** también lo usan. Isolation Forest no extrapola, así que una medición nueva se evalúa reentrenando con ella incluida (milisegundos). El modelo vive en memoria y se descarta cuando llega una medición (Observer) |

| **Red neuronal LSTM (capa 7)** | Predice el pH de las próximas 12 horas con una LSTM de una capa (16 unidades ocultas, 1612 parámetros) implementada en Java en el paquete `intelligence/deep`, sin dependencias. Se descartó DJL porque descarga motores nativos de PyTorch desde servidores externos y agrega cientos de MB. Las mediciones se convierten en una serie horaria (huecos de hasta 6 h interpolados) y en ventanas de 24 h de entrada. Los datos se dividen en orden cronológico: 70 % entrenamiento, 15 % validación con detención temprana y 15 % prueba, con 12 h de separación entre partes. En la prueba se compara con tres referencias: persistencia, tendencia lineal y estacional diaria. **La red solo se usa si reduce el error al menos un 5 %**; si no, se usa la mejor referencia y se dice. El intervalo es el percentil 90 del error en la prueba. Genera una alerta anticipada si se prevé salir del rango ideal, y la usan el agente (`get_neural_forecast`) y el diagnóstico. El gradiente se verifica numéricamente en las pruebas. Requiere unos 8 días de mediciones frecuentes; sin ellas no entrena y lo explica |

**Respuestas en vivo.** `POST /intelligence/chat/stream` responde con Server-Sent Events: fragmentos (`delta`) a medida que el modelo escribe, y al final la respuesta completa con citas verificadas (`done`) o un error. Para el chat se prueban primero los modelos rápidos: Gemini Flash-Lite y Groq gpt-oss-120b con razonamiento bajo. Un modelo se salta y se usa el siguiente si:

- no envía la respuesta HTTP en 8 s, o
- no escribe texto en 12 s.

Una respuesta que ya empezó se corta si pasan 20 s sin recibir nada. Un vigilante (`StreamWatchdog`) cierra la conexión porque el cliente HTTP de Java no tiene límite de lectura. Medido en producción: el texto empieza a aparecer en 1 a 3 s en la mayoría de las preguntas.

**Proveedores y respaldo.** `FallbackAiProvider` (Chain of Responsibility) intenta en orden Gemini 3.5 Flash, Gemini 3.5 Flash-Lite, Groq gpt-oss-120b y Claude. Pasa al siguiente si uno está saturado (503), sin cuota (429), rechaza la solicitud o devuelve un JSON inválido. Por delante está `AiProviderProxy`, que controla la cuota diaria por plan, la caché y el registro del consumo en `ai_usage`. Solo se envían valores de pH, fechas y datos del perfil; nunca nombres ni correos. Para fotos solo participan los proveedores con visión (`vision: true`): Gemini Flash, Gemini Flash-Lite y Groq Qwen 3.8. Gemini y Groq se usan con su endpoint compatible con OpenAI (`Authorization: Bearer`), por eso una sola clase (`OpenAiCompatibleProvider`) sirve para ambos.

**Retry.** Si un proveedor responde 429 o 503 indicando una espera de 20 segundos o menos (`Retry-After` o `x-ratelimit-reset-*` de Groq), se espera y se reintenta una vez. Si la espera es larga, por ejemplo con la cuota diaria agotada, se pasa de inmediato al siguiente proveedor.

**Verificado con llamadas reales (7 nov 2026).** Gemini 3.5 Flash generó un diagnóstico validado en unos 16 s. Con los modelos de Gemini forzados a fallar, la cadena respondió con Groq gpt-oss-120b. El chat respondió con Gemini usando las mediciones reales. Con imágenes sintéticas de prueba, la lectura por foto leyó una tira como 6.1 (Gemini 6.0 y Groq Qwen 6.2, coincidentes) y una pantalla como 7.42 a 24.6 °C, y rechazó una imagen sin relación con el agua. El agente de informes, con la cuota diaria de Gemini Flash agotada, reinició la investigación con Gemini Flash-Lite. Hizo entre 5 y 7 consultas según el objetivo, terminó en 4 a 7 segundos y todas sus cifras se verificaron (22/22 y 17/17). **Falta probarla con fotos reales de tiras**: la precisión con iluminación y cámaras reales no está medida. Los planes gratuitos tienen límites que cambian y que se consultan en AI Studio y en la consola de Groq.

## Historial de sesiones

Cada inicio de sesión (o registro) crea una fila en `user_sessions` con inicio, última actividad, vencimiento, si se pidió "Recordarme" y el navegador resumido ("Chrome en Windows"; no se guardan la IP ni el User-Agent completo). Cada acción del usuario (`audit_log`) se guarda con la sesión en que la hizo: mediciones, preguntas al asistente, descargas, cambios de perfil o de configuración, pagos. Al cerrar sesión, la sesión termina y su token deja de valer; al volver a entrar empieza otra. Las que vencen sin cerrar quedan como "Venció sin cerrar sesión".

- `GET /users/me/sessions`: lista con duración, estado y cuántas veces se hizo cada acción.
- `GET /users/me/sessions/{id}`: el detalle en orden.
- El administrador consulta las de cualquier cuenta (`/admin/users/{id}/sessions`).

## Cuentas de demostración

`DemoAccounts` crea al arrancar tres cuentas reales, una por rol del caso de estudio:

| Botón | Correo | Rol y plan |
|---|---|---|
| Usuario | `usuario@demo.aquasense` | Usuario, plan gratuito |
| Usuario Premium | `premium@demo.aquasense` | Usuario, Premium sin vencimiento |
| Administrador | `admin@demo.aquasense` | Administrador de solo lectura |

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

Dos informes se descargan en PDF desde la interfaz: el del agente de IA (botón "Descargar PDF" en el informe) y el informe del periodo por reglas (botón "Informe PDF" en el historial).

| Ruta | Contenido |
|---|---|
| `GET /api/v1/intelligence/agent-reports/{id}/pdf` | Objetivo, resumen, cifras clave, sello de cifras verificadas, secciones con la gráfica que eligió el agente, recomendaciones con prioridad, limitaciones, citas normativas literales y los pasos que siguió el agente |
| `GET /api/v1/reports/pdf?days=7&includeAi=false` | Resumen del periodo, gráfica de evolución, mediciones por estado, tendencia, anomalías marcadas, predicción con su intervalo, recomendaciones y conclusión |

- Las gráficas se dibujan en el servidor con los mismos datos guardados: franja del rango ideal del perfil, anomalías marcadas y banda de predicción. El eje de tiempo se ajusta al periodo (días, horas o segundos).
- La fuente va incrustada en el archivo, así las tildes y la "ñ" se ven igual en cualquier equipo.
- Si el periodo incluye lecturas del simulador, el PDF lo advierte en un recuadro "Datos simulados".
- Cada página lleva el aviso "Informe orientativo basado en el pH; no reemplaza un análisis de laboratorio" y su número.
- Un usuario solo puede descargar sus propios informes; el de otro usuario responde 404.

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
- **Al servidor**, cuando AquaSense corre en el mismo computador del medidor: interfaz `PhMeter` con los adaptadores `AquaSenseFirmwareAdapter` (firmware ESP32 incluido), `AtlasEzoPhAdapter` (Atlas Scientific EZO-pH por UART) y `TextMeterAdapter` (medidores de laboratorio que envían el valor como texto por USB o RS-232).
- **Por cámara**, para medidores sin cable de datos (ver `docs/Hardware.md`).

El simulador de medidor solo se usa en las pruebas automáticas (`SIMULATOR_ENABLED=false` por defecto): la app publicada no muestra lecturas falsas.

**Estado:** los protocolos están probados con un puerto falso en las pruebas automáticas y en el navegador; **no se han probado con un medidor físico**. Ver [`docs/Hardware.md`](docs/Hardware.md).

## Pruebas

```bash
mvn test
```

151 pruebas automatizadas. Cubren:

- Patrones de diseño, algoritmos de análisis, adaptadores de hardware y máquina de estados.
- El flujo completo de la API: registro, aislamiento entre usuarios, simulador, IA sin clave, informes, cierre de sesión y roles.
- El historial de sesiones y las cuentas de demostración con sus límites.
- La importación de datos oficiales con respuestas grabadas del USGS.
- Las respuestas en vivo: fragmentos, un modelo que tarda en empezar, uno que se queda callado y uno que se detiene a mitad.

Se ejecutan con H2 en modo PostgreSQL; para correrlas contra PostgreSQL real:

```bash
mvn test -Dspring.datasource.url=jdbc:postgresql://localhost:5432/aquasense_test \
  -Dspring.datasource.username=aquasense -Dspring.datasource.password=TU_CLAVE
```

Último resultado (11 nov 2026): 151/151 aprobadas. Incluyen el proveedor de IA contra un servidor HTTP local que imita la API, la cadena de respaldo, el validador de diagnósticos y el flujo completo con un proveedor saturado y otro que responde.

## Despliegue

Publicado el 8 nov 2026 en **Render** (backend con Docker, plan gratuito), **Neon** (PostgreSQL) y **Vercel** (frontend). La guía paso a paso está en [`docs/Deployment.md`](docs/Deployment.md).

| Parte | Dirección |
|---|---|
| Aplicación | https://frontendpatsft.vercel.app |
| API | https://aquasense-backend-willc372.onrender.com/api/v1 (salud: `/actuator/health`, documentación: `/swagger-ui.html`) |

Verificado en producción: el formulario de registro y el de medición de la página de Vercel llaman a la API de Render y guardan en Neon; CORS acepta solo el dominio de Vercel (otro origen recibe 403); el diagnóstico y el chat responden con Gemini y citas válidas; el informe PDF se descarga con sus fuentes; el correo está configurado.

- `render.yaml` crea el servicio con su verificación de salud (`/actuator/health`), genera `JWT_SECRET` y fija las direcciones del frontend. Las claves (base de datos, IA y correo) se escriben en el panel de Render, nunca en el repositorio.
- La base se configura con una sola variable, `DATABASE_URL`, con la cadena que entrega Neon (`postgresql://usuario:clave@host/db?sslmode=require`).
- El `Dockerfile` está ajustado a instancias de 512 MB. La imagen se construyó y se probó en un contenedor limitado a 512 MB: 335 MB de memoria después de generar PDF, el análisis con aprendizaje automático y la red LSTM.

Un backend en la nube no puede leer un medidor conectado a tu computador. Para lecturas físicas, el backend debe correr en el equipo donde está conectado el medidor.

## Solución de problemas

| Síntoma | Causa y solución |
|---|---|
| `JWT_SECRET must be set...` al arrancar | Define `JWT_SECRET` con 32 caracteres o más |
| `Connection refused` a PostgreSQL | Verifica que el servicio esté activo y los valores de `DB_*` |
| El frontend muestra "No se pudo conectar con el servidor" | Backend apagado, o el origen del frontend no está en `CORS_ORIGINS` |
| No aparece el puerto del medidor | Revisa el cable y el controlador USB. En Linux agrega tu usuario al grupo `dialout`. Para Bluetooth, empareja primero desde el sistema |
| "IA no configurada" | Faltan `GEMINI_API_KEY` y `GROQ_API_KEY`; el resto del análisis funciona sin ellas |
| Tras actualizar el servidor, todos deben volver a iniciar sesión | Los tokens emitidos antes del historial de sesiones no llevan sesión (`sid`) y ya no valen; ocurre una sola vez |
| "Datos oficiales" dice que la última actualización falló | El USGS no respondió o limitó las solicitudes; se muestran los datos anteriores y se reintenta en 3 horas. `USGS_API_KEY` sube el límite |
| Groq responde 403 "error code: 1010" | Cloudflare bloquea clientes sin User-Agent reconocible; el backend ya envía `AquaSense-AI/1.0` |
| El diagnóstico sale "con reglas" | Todos los proveedores fallaron o la respuesta no pasó la validación; el motivo aparece en la nota del diagnóstico |
| "La recuperación de contraseña no está disponible" | No hay canal de correo: define `MAILJET_API_KEY`, `MAILJET_SECRET_KEY` y `MAIL_FROM` (o Brevo o SMTP). Solo en desarrollo puedes usar `MAIL_LOG_RESET_LINKS=true` y copiar el enlace del log |

## Licencia

Proyecto académico de Patrones de Software. Sin licencia de uso definida por el autor.
