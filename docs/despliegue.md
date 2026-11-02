# Guía de despliegue

> **Estado: no desplegado.** No existe una URL pública del backend. Esta guía describe lo necesario; el `Dockerfile` incluido **no se ha probado** porque el entorno de desarrollo no tenía un daemon de Docker.

## Qué hace falta

1. Un servicio que ejecute Java 21 o contenedores (por ejemplo Render, Railway o Fly.io) y una base **PostgreSQL gestionada**. La cuenta y su facturación las crea el propietario del proyecto.
2. Variables de entorno en el servicio (nunca en el repositorio): `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, y opcionalmente `ADMIN_EMAIL` y las de SMTP.
3. HTTPS: lo proporciona la plataforma. El servidor respeta las cabeceras `X-Forwarded-*`.

## Pasos con contenedor

```bash
docker build -t aquasense-backend .
docker run -p 8080:8080 \
  -e DB_URL=jdbc:postgresql://HOST:5432/aquasense \
  -e DB_USERNAME=aquasense -e DB_PASSWORD=... \
  -e JWT_SECRET=... aquasense-backend
```

## Comprobaciones tras el despliegue

- `GET /actuator/health` devuelve `{"status":"UP"}` (Flyway aplica las migraciones al arrancar).
- `/swagger-ui.html` carga la documentación.
- Registrar un usuario y consultar `GET /api/v1/users/me`.
- Configurar la URL pública en el cliente con la variable `AQUASENSE_API_URL`.

## Producción: pendientes recomendados

Limitar la documentación OpenAPI si se desea, definir CORS si se agrega un cliente web, configurar copias de seguridad de la base de datos, y añadir limitación de tasa por IP.
