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

## Correos de los botones de acceso

Los botones Usuario, Premium y Administrador usan cuentas compartidas. Para que sus correos lleguen (alarmas, avisos y recuperación de contraseña), usan buzones reales elegidos por el dueño: `candow372@gmail.com` (Usuario), `guifig1324@gmail.com` (Premium) y `adminaqua16@gmail.com` (Administrador), en `application.yml`. Se pueden cambiar en Render con `DEMO_USER_EMAIL`, `DEMO_PREMIUM_EMAIL` y `DEMO_ADMIN_EMAIL`. Al cambiarlos, cada cuenta conserva sus lugares, mediciones e historial. No uses el correo de una cuenta real de la app: el botón no podría entrar y los intentos fallidos bloquearían esa cuenta.

## Correos en la bandeja principal (dominio propio)

Hoy los correos salen por Mailjet con un remitente `@gmail.com`. Gmail desconfía de un correo que dice venir de `@gmail.com` pero no salió de los servidores de Google, y lo manda a spam. La solución es enviar desde un **dominio propio autenticado**:

1. **Compra un dominio** (unos 10 USD al año en Namecheap, Porkbun o Cloudflare), por ejemplo `aquaassistant.co`.
2. En **Mailjet → Account settings → Sender domains & addresses → Add domain**, escribe el dominio. Mailjet muestra los registros que hay que copiar.
3. En el **DNS del dominio** (en la página donde lo compraste) crea estos registros TXT:
   - **SPF**, en el nombre `@` (la raíz): `v=spf1 include:spf.mailjet.com ~all`. Si ya hay un registro `v=spf1`, agrega `include:spf.mailjet.com` al existente; no puede haber dos.
   - **DKIM**, en el nombre `mailjet._domainkey`: el valor largo que muestra Mailjet (empieza por `k=rsa; p=`).
   - **DMARC**, en el nombre `_dmarc`: `v=DMARC1; p=none; rua=mailto:tu-correo@gmail.com`.
   - El registro de **verificación** que pide Mailjet, si lo pide.
4. Espera a que Mailjet marque **SPF y DKIM: OK** (unos minutos, a veces hasta un día).
5. En Render, servicio del backend → **Environment**, cambia `MAIL_FROM` a una dirección del dominio, por ejemplo `alertas@aquaassistant.co`, y deja `MAIL_SENDER_NAME=Aqua Assistant AI`.
6. Prueba: entra como administrador, abre **Configuración** y pulsa **Enviarme un correo de prueba**. Si el primer correo llega a spam, márcalo como «No es spam»: con el dominio autenticado, Gmail aprende rápido.

Los correos ya van en texto plano y HTML, con el nombre del remitente. Lo que faltaba era autenticar el remitente.

## Alarma por SMS con Twilio

Cuando el pH se altera, la alarma llega también como mensaje de texto al celular que la persona guarda en **Configuración → Notificaciones → Alarma por SMS** (solo la alarma, como máximo un SMS por hora, porque cada SMS cuesta). Para encenderla:

1. Crea una cuenta en [twilio.com](https://www.twilio.com/try-twilio) y verifica tu correo y tu celular.
2. En la consola, **Phone Numbers → Buy a number** (en la cuenta de prueba es gratis): elige uno con la capacidad *SMS*.
3. En **Messaging → Settings → Geo permissions**, activa **Colombia** (y cualquier otro país al que vayas a enviar).
4. Con la cuenta de prueba, solo llegan SMS a números verificados: agrégalos en **Phone Numbers → Verified Caller IDs** (el tuyo ya lo está). Twilio antepone «Sent from your Twilio trial account». Para enviar a cualquiera, actualiza la cuenta (pago por mensaje).
5. En Render, servicio del backend → **Environment**, agrega `TWILIO_ACCOUNT_SID` y `TWILIO_AUTH_TOKEN` (en la página principal de la consola de Twilio) y `TWILIO_FROM_NUMBER` (el número comprado, con `+`). Render reinicia el backend.
6. En la app, guarda tu celular y pulsa **Enviar SMS de prueba**. Si no llega, la app muestra el motivo que dio Twilio.

## Límites de los planes gratuitos

- **Render** apaga el backend tras 15 minutos sin uso (se deja dormir a propósito, ver abajo). La primera visita después tarda alrededor de un minuto; la interfaz avisa que el servidor se está activando.
- La instancia gratuita tiene 512 MB de memoria. El contenedor se probó con ese límite: unos 335 MB después de generar PDF, el análisis con aprendizaje automático y el entrenamiento de la red LSTM.
- **Neon** suspende la base tras unos minutos sin uso y la reactiva en la siguiente consulta.
- No hay medidor físico conectado a un servidor en la nube: en el despliegue se usan el simulador, el registro manual y la lectura por foto.

## El backend se duerme cuando nadie lo usa

Render apaga una instancia gratuita tras 15 minutos sin peticiones y da **750 horas gratis al mes por cuenta**, compartidas por el backend y el servicio de IA. Un backend despierto todo el mes usaría unas 730, así que **se deja dormir** (desde el 19 nov 2026):

- **Primera visita:** tarda alrededor de un minuto; la app muestra «Activando el servidor...» y continúa sola. Mientras alguien usa la app (o el medidor envía una medición cada 10 s) no se duerme.
- **Despertador (`.github/workflows/WakeUp.yml`), con dos modos según la variable `KEEP_AWAKE_MODE` de GitHub (*Settings → Secrets and variables → Actions → Variables*):**
  - `always` (lo que hace si la variable no existe; activo desde el 20 nov 2026, a pedido del dueño): una ejecución despierta al backend y al servicio de IA cada 4 minutos durante unas 5 horas y media y lanza la siguiente, así que no se duermen y responden al instante. Gasta horas gratis todo el tiempo: los dos juntos usan unas 48 por día, así que las 750 del mes no alcanzan para el mes entero.
  - `3h`: el comportamiento anterior. Cada 3 horas los despierta una vez unos 15 minutos (al despertar se envían los recordatorios vencidos, se importan los datos del USGS y se revisan los pagos pendientes) y el resto del tiempo duermen.
  - GitHub ejecuta los flujos programados cuando puede (a veces tarde). En modo `always` el horario solo vuelve a arrancar la cadena si se cortó. Se puede lanzar a mano con **Actions → Despertar el backend y la IA → Run workflow**. Para otras direcciones existen `KEEP_AWAKE_URL` (backend) y `KEEP_AWAKE_AI_URL` (IA).
- **Alarma por alteración del pH:** la medición que la provoca despierta al backend, así que la alarma y su aviso salen igual.
- **Consumo:** en modo `3h`, unas 2 horas al día por servicio más el uso real; en modo `always`, 24 horas al día por servicio.

**Para tenerlo siempre despierto** (por ejemplo, el día de una presentación): en Render, *Environment* del backend, agrega `KEEP_AWAKE=true` y guarda. El propio servidor pide `/actuator/health/liveness` a su dirección pública cada 10 minutos (`config/KeepAwake.java`). Si además creaste un monitor en UptimeRobot, mientras esté activo también lo mantiene despierto: páusalo para que vuelva a dormirse. Quita la variable después para no gastar las horas del mes.

## Variables que fija `render.yaml`

| Variable | Para qué |
|---|---|
| `FORWARD_HEADERS_STRATEGY=native` | Detrás del proxy de Render, el límite de intentos se aplica por cliente y no a todos juntos |
| `DB_POOL_SIZE=5` | Pocas conexiones para la base gratuita |
| `CORS_ORIGINS` | Solo el dominio de Vercel puede llamar a la API desde el navegador |
| `FRONTEND_RESET_URL`, `FRONTEND_ADMIN_VERIFY_URL` | Enlaces de los correos |
| `PAYMENTS_RESPONSE_URL`, `PAYMENTS_CONFIRMATION_URL` | Retorno y confirmación de PayU |
