# Requisitos

Fuentes: el documento «Información complementaria del proyecto AquaSense AI» (requisitos originales y académicos) y el prompt maestro (arquitectura y restricciones). Estado verificado al día de esta versión.

## Requisitos funcionales

| ID | Requisito | Estado | Evidencia |
|---|---|---|---|
| RF-01 | Registro, inicio y cierre de sesión | Implementado | `AuthApiIntegrationTest` |
| RF-02 | Recordar sesión | Implementado | `rememberMeExtendsRefreshTokenLifetime` |
| RF-03 | Recuperar contraseña por código enviado al correo | **Parcial**: lógica probada; el envío SMTP no está verificado | `passwordResetFlowIsSingleUseAndInvalidatesSessions` |
| RF-04 | Perfiles de agua: potable, piscina, acuario, agricultura, general y personalizados, con rangos y fuentes | Implementado | `WaterProfileApiIntegrationTest` |
| RF-05 | Clasificación: ácido/neutro/alcalino y óptimo/precaución/crítico | Implementado | `WaterProfileDomainTest` |
| RF-06 | Aviso de que el pH no demuestra potabilidad | Implementado en clasificación, análisis, informes | pruebas de API |
| RF-07 | Registrar y consultar mediciones con usuario, dispositivo, perfil, origen y validación | Implementado | `MeasurementApiIntegrationTest` |
| RF-08 | Mediciones simuladas identificadas, nunca como reales | Implementado (origen fijado por el servidor) | `simulatedDeviceProducesSimulatedMeasurements…` |
| RF-09 | Historial con filtros y selección de periodo | Implementado | `filtersByPeriodAndSourceAndPaginates` |
| RF-10 | Gráficas solo bajo demanda | Implementado en el cliente (el servidor no genera gráficas salvo en informes HTML pedidos) | frontend |
| RF-11 | Detección de anomalías explicada | Implementado | `AnalysisEngineTest` |
| RF-12 | Tendencias y predicciones con incertidumbre y límites | Implementado | `AnalysisEngineTest` |
| RF-13 | Recomendaciones según perfil | Implementado (reglas) | `recommendationsNeverClaimWaterIsSafe` |
| RF-14 | Simulador de escenarios hipotéticos | Implementado | `scenarioSimulator…` |
| RF-15 | Informes consultables y exportables | Implementado en el servidor; **sin pantalla en el cliente todavía** | `ReportApiIntegrationTest` |
| RF-16 | Chat con asistente de IA | **Pendiente** (requiere clave de API) | — |
| RF-17 | IA generativa por API (informes redactados, explicaciones) | **Pendiente** | — |
| RF-18 | Freemium con límites reales en el servidor | Implementado | `FreemiumIntegrationTest` |
| RF-19 | Pagos reales y webhooks | **Pendiente** (sin credenciales de pasarela) | — |
| RF-20 | Roles y permisos | **Parcial**: `USER` y `ADMIN` (solo asigna planes) | `onlyAdminsCanAssignPremium…` |
| RF-21 | Conexión, calibración y lectura de medidor físico | **Pendiente** (sin hardware) | — |
| RF-22 | Notificaciones | **Parcial**: alertas internas consultables; sin avisos push ni correo | — |

## Requisitos no funcionales

| ID | Requisito | Estado |
|---|---|---|
| RNF-01 | Todo el código en Java (frontend y backend) | Cumplido |
| RNF-02 | Contraseñas con hash seguro (Argon2id) | Cumplido |
| RNF-03 | Cada usuario solo accede a sus datos | Cumplido y probado |
| RNF-04 | Sin secretos en el repositorio | Cumplido (`.env.example` sin valores reales; `JWT_SECRET` obligatorio) |
| RNF-05 | API REST versionada y documentada (OpenAPI) | Cumplido |
| RNF-06 | Persistencia real con migraciones | Cumplido |
| RNF-07 | Pruebas automatizadas | Cumplido: 84 ejecuciones backend, 18 cliente |
| RNF-08 | HTTPS en producción | Preparado (se termina en la plataforma); sin despliegue |
| RNF-09 | Rendimiento | **No medido** |
| RNF-10 | Accesibilidad | **No evaluada** formalmente |
| RNF-11 | Multiplataforma | Solo Linux verificado; ver el frontend |
| RNF-12 | Internacionalización | **Pendiente**: los textos están en español dentro del código |
