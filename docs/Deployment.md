# Despliegue en internet

> **Separación en servicios (19 nov 2026).** La IA (los paquetes `intelligence`, `knowledge`, `vision` y `report`, y la traducción de la interfaz) se movió al servicio [AIPatSft](https://github.com/WillC372/AIPatSft), y el esquema de la base de datos al repositorio [DBPatSft](https://github.com/WillC372/DBPatSft). Lo que este documento dice de esos paquetes ahora vale para AIPatSft.

## Servicio de IA y base de datos

Desde la separación, la aplicación son tres despliegues más la base de datos:

1. **Base de datos (DBPatSft).** En el repositorio, *Settings → Secrets and variables → Actions → New repository secret*: `DATABASE_URL` con la misma cadena de Neon que usa el backend. El flujo *Migraciones* prueba las migraciones en una base vacía y las aplica a Neon en cada cambio de `main` (también se corre a mano desde *Actions*). Las migraciones V1 a V24 ya estaban aplicadas en Neon cuando se separó la base de datos; las nuevas (desde V25) las aplica este flujo.
2. **Servicio de IA (AIPatSft).** En Render, *New → Blueprint* y elegir el repositorio. Pide `DATABASE_URL` (la misma), `JWT_SECRET` (copiarla del backend: *Environment* del servicio del backend), `INTERNAL_API_KEY` (una clave nueva de 16 caracteres o más, por ejemplo la salida de `openssl rand -hex 32`) y las claves `GEMINI_API_KEY` y `GROQ_API_KEY` (las que tenía el backend). No se mantiene despierto: el plan gratuito tiene 750 horas al mes para todos los servicios y el backend las usa.
3. **Backend.** En *Environment* agregar `INTERNAL_API_KEY` con el mismo valor del paso 2 y `AI_SERVICE_URL=https://aquasense-ai-willc372.onrender.com`. Las claves de Gemini y Groq ya no hacen falta aquí.
4. **Frontend.** No necesita cambios: `Config.js` ya apunta al servicio de IA para todo lo de Aqua.

Aqua Assistant se publica en tres servicios gratuitos que se comunican por la API REST:

```mermaid
flowchart LR
    U[Navegador] -->|página| V[Vercel<br/>FrontendPatSft]
    U -->|/api/v1 con JWT| R[Render<br/>BackendPatSft · Docker]
    R -->|JDBC con SSL| N[(Neon<br/>PostgreSQL)]
    R --> IA[Gemini · Groq]
    R --> M[Mailjet]
    R --> P[PayU sandbox]
```

| Parte | Servicio | Dirección |
|---|---|---|
| Frontend | Vercel | `https://aquaassistantai.vercel.app` (publicado el 8 nov 2026) |
| Backend | Render (plan gratuito, Docker) | `https://aquasense-backend-willc372.onrender.com` |
| Base de datos | Neon (PostgreSQL gratuito, no vence) | la entrega Neon |

El frontend llama al backend publicado cuando corre fuera de `localhost` (ver `Config.js` del frontend). El backend solo acepta peticiones del dominio de Vercel (`CORS_ORIGINS`).

## 1. Base de datos en Neon

1. Entra a https://neon.tech y crea una cuenta (puedes usar tu cuenta de GitHub).
2. Crea un proyecto: nombre `aquasense`, versión de PostgreSQL 16, la región más cercana a Render (por ejemplo *US West (Oregon)*).
3. En **Connect** copia la cadena de conexión. Tiene esta forma:
   `postgresql://usuario:clave@ep-xxxx.us-west-2.aws.neon.tech/neondb?sslmode=require`
4. Guárdala para el paso 2. Es un secreto: no la compartas ni la subas al repositorio.

Las tablas se crean solas: al arrancar, el backend ejecuta las migraciones de Flyway.

## 2. Backend en Render

1. Entra a https://render.com y crea una cuenta con GitHub.
2. **New → Blueprint** y elige el repositorio **BackendPatSft**. Render lee `render.yaml`.
3. Render pide los valores marcados como secretos:

   | Variable | Valor |
   |---|---|
   | `DATABASE_URL` | La cadena de Neon del paso 1 |
   | `GEMINI_API_KEY` | Tu clave de Google AI Studio |
   | `GROQ_API_KEY` | Tu clave de Groq |
   | `MAILJET_API_KEY` y `MAILJET_SECRET_KEY` | Las llaves de Mailjet |
   | `MAIL_FROM` | El remitente validado en Mailjet |
   | `ADMIN_EMAIL` | El correo del administrador principal |

   `JWT_SECRET` lo genera Render. Las direcciones del frontend y de PayU ya vienen en el archivo.
4. **Apply**. La primera construcción tarda entre 5 y 10 minutos. Termina cuando el servicio aparece como *Live*.
5. Comprueba `https://aquasense-backend-willc372.onrender.com/actuator/health`: debe responder `{"status":"UP"}`.

Si Render asigna otra dirección (por ejemplo, porque el nombre ya existía), hay que cambiarla en `Config.js` del frontend y en `PAYMENTS_CONFIRMATION_URL`.

## 3. Frontend en Vercel

1. Entra a https://vercel.com con tu cuenta de GitHub.
2. Si el proyecto **frontendpatsft** ya existe, cada `git push` lo publica solo. Si no: **Add New → Project**, elige **FrontendPatSft**, deja *Framework Preset* en **Other**, sin comando de construcción, y pulsa **Deploy**.
3. Abre `https://aquaassistantai.vercel.app`, crea una cuenta y registra una medición.

Si Vercel asigna otra dirección, hay que cambiarla en `CORS_ORIGINS`, `FRONTEND_RESET_URL`, `FRONTEND_ADMIN_VERIFY_URL` y `PAYMENTS_RESPONSE_URL` en Render.

## Alarmas por WhatsApp (WhatsApp Cloud API de Meta)

Cuando el pH se altera, además del aviso en el celular y del correo, la app manda un WhatsApp al número que cada persona guarda en **Configuración → Notificaciones**. Sin las variables de abajo, WhatsApp queda apagado y la app lo dice; no se finge ningún envío.

1. En [developers.facebook.com](https://developers.facebook.com) crea una app de tipo **Empresa** y agrégale el producto **WhatsApp**. Meta da un número de prueba gratis.
2. En **WhatsApp → Configuración de la API** copia el **Identificador del número de teléfono** (`WHATSAPP_PHONE_NUMBER_ID`) y genera un **token de acceso** (`WHATSAPP_TOKEN`). El token temporal dura 24 horas; para uno permanente, crea un **usuario del sistema** en el Business Manager con permiso `whatsapp_business_messaging`.
3. En **Para (destinatarios)** agrega y verifica los celulares que recibirán avisos (con el número de prueba, hasta 5).
4. En **Administrador de WhatsApp → Plantillas de mensajes** crea una plantilla con estos datos:
   - Nombre: `alerta_ph`. Categoría: **Utilidad**. Idioma: **Español** (`es`).
   - Cuerpo: `⚠️ {{1}}: {{2}} Abre Aqua Assistant AI para ver qué hacer.`
   - Ejemplos para la revisión: `{{1}}` = «Alteración del pH en el agua» y `{{2}}` = «El pH subió de 7.30 a 12.67 en 5 minutos. Es peligroso».
5. Cuando Meta la apruebe, que suele tardar de minutos a horas, pon en Render (backend → **Environment**) las variables `WHATSAPP_TOKEN` y `WHATSAPP_PHONE_NUMBER_ID`. Opcionales: `WHATSAPP_TEMPLATE` (por defecto `alerta_ph`) y `WHATSAPP_TEMPLATE_LANG` (por defecto `es`).
6. En la app: **Configuración → Notificaciones**, escribe tu celular y pulsa «Enviar WhatsApp de prueba».

## Correos de los botones de acceso

Los botones Usuario, Premium y Administrador usan cuentas compartidas. Para que sus correos lleguen (alarmas, avisos y recuperación de contraseña), usan buzones reales elegidos por el dueño: `candow372@gmail.com` (Usuario), `guifig1324@gmail.com` (Premium) y `adminaqua16@gmail.com` (Administrador), en `application.yml`. Se pueden cambiar en Render con `DEMO_USER_EMAIL`, `DEMO_PREMIUM_EMAIL` y `DEMO_ADMIN_EMAIL`. Al cambiarlos, cada cuenta conserva sus lugares, mediciones e historial. No uses el correo de una cuenta real de la app: el botón no podría entrar y los intentos fallidos bloquearían esa cuenta.

## Límites de los planes gratuitos

- **Render** apaga el backend tras 15 minutos sin uso (se deja dormir a propósito, ver abajo). La primera visita después tarda alrededor de un minuto; la interfaz avisa que el servidor se está activando.
- La instancia gratuita tiene 512 MB de memoria. El contenedor se probó con ese límite: unos 335 MB después de generar PDF, el análisis con aprendizaje automático y el entrenamiento de la red LSTM.
- **Neon** suspende la base tras unos minutos sin uso y la reactiva en la siguiente consulta.
- No hay medidor físico conectado a un servidor en la nube: en el despliegue se usan el simulador, el registro manual y la lectura por foto.

## El backend se duerme cuando nadie lo usa

Render apaga una instancia gratuita tras 15 minutos sin peticiones y da **750 horas gratis al mes por cuenta**, compartidas por el backend y el servicio de IA. Un backend despierto todo el mes usaría unas 730, así que **se deja dormir** (desde el 19 nov 2026):

- **Primera visita:** tarda alrededor de un minuto; la app muestra «Activando el servidor...» y continúa sola. Mientras alguien usa la app (o el medidor envía una medición cada 10 s) no se duerme.
- **Recordatorios, datos del USGS y pagos:** `.github/workflows/WakeUp.yml` lo despierta cada 3 horas unos 15 minutos; al despertar envía los recordatorios vencidos, importa los datos oficiales del USGS y revisa los pagos pendientes. Por eso un recordatorio puede llegar hasta unas 3 horas tarde si nadie usa la app. GitHub ejecuta los flujos programados cuando puede (a veces tarde) y los desactiva en un repositorio público tras 60 días sin actividad; se reactivan en **Actions → Despertar el backend → Enable workflow** y se puede lanzar a mano con **Run workflow**. Para otra dirección, crea la variable `KEEP_AWAKE_URL` en *Settings → Secrets and variables → Actions → Variables*.
- **Alarma por alteración del pH:** la medición que la provoca despierta al backend, así que la alarma y su aviso salen igual.
- **Consumo:** unas 2 horas al día (≈ 60 al mes) más el uso real, en vez de ≈ 730.

**Para tenerlo siempre despierto** (por ejemplo, el día de una presentación): en Render, *Environment* del backend, agrega `KEEP_AWAKE=true` y guarda. El propio servidor pide `/actuator/health/liveness` a su dirección pública cada 10 minutos (`config/KeepAwake.java`). Si además creaste un monitor en UptimeRobot, mientras esté activo también lo mantiene despierto: páusalo para que vuelva a dormirse. Quita la variable después para no gastar las horas del mes.

## Variables que fija `render.yaml`

| Variable | Para qué |
|---|---|
| `FORWARD_HEADERS_STRATEGY=native` | Detrás del proxy de Render, el límite de intentos se aplica por cliente y no a todos juntos |
| `DB_POOL_SIZE=5` | Pocas conexiones para la base gratuita |
| `CORS_ORIGINS` | Solo el dominio de Vercel puede llamar a la API desde el navegador |
| `FRONTEND_RESET_URL`, `FRONTEND_ADMIN_VERIFY_URL` | Enlaces de los correos |
| `PAYMENTS_RESPONSE_URL`, `PAYMENTS_CONFIRMATION_URL` | Retorno y confirmación de PayU |
