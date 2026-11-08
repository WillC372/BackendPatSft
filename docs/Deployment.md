# Despliegue en internet

AquaSense se publica en tres servicios gratuitos que se comunican por la API REST:

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
| Frontend | Vercel | `https://frontendpatsft.vercel.app` |
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
3. Abre `https://frontendpatsft.vercel.app`, crea una cuenta y registra una medición.

Si Vercel asigna otra dirección, hay que cambiarla en `CORS_ORIGINS`, `FRONTEND_RESET_URL`, `FRONTEND_ADMIN_VERIFY_URL` y `PAYMENTS_RESPONSE_URL` en Render.

## Límites de los planes gratuitos

- **Render** apaga el backend tras 15 minutos sin uso. La primera visita después tarda alrededor de un minuto; la interfaz avisa que el servidor se está activando.
- La instancia gratuita tiene 512 MB de memoria. El contenedor se probó con ese límite: unos 335 MB después de generar PDF, el análisis con aprendizaje automático y el entrenamiento de la red LSTM.
- **Neon** suspende la base tras unos minutos sin uso y la reactiva en la siguiente consulta.
- No hay medidor físico conectado a un servidor en la nube: en el despliegue se usan el simulador, el registro manual y la lectura por foto.

## Variables que fija `render.yaml`

| Variable | Para qué |
|---|---|
| `FORWARD_HEADERS_STRATEGY=native` | Detrás del proxy de Render, el límite de intentos se aplica por cliente y no a todos juntos |
| `DB_POOL_SIZE=5` | Pocas conexiones para la base gratuita |
| `CORS_ORIGINS` | Solo el dominio de Vercel puede llamar a la API desde el navegador |
| `FRONTEND_RESET_URL`, `FRONTEND_ADMIN_VERIFY_URL` | Enlaces de los correos |
| `PAYMENTS_RESPONSE_URL`, `PAYMENTS_CONFIRMATION_URL` | Retorno y confirmación de PayU |
