# Plan y resultados de pruebas

## Estrategia

- **Unitarias**: dominio puro (clasificación, motor estadístico, cadena de validación, renderizadores).
- **Integración con PostgreSQL real**: API completa con `MockMvc` sobre una base de pruebas; incluyen autenticación, aislamiento entre usuarios y límites de plan.
- **Patrones**: cada patrón de la matriz tiene al menos una prueba que lo ejercita (ver [patrones.md](patrones.md)).
- **Dispositivos**: la capa de dispositivos y su simulador se prueban en el cliente; el simulador está identificado como tal.

No se usó Testcontainers porque el entorno de desarrollo no tenía un daemon de Docker; las pruebas usan un PostgreSQL local (variables `TEST_DB_*`).

## Ejecución

```bash
mvn test
```

## Resultados verificados

Última ejecución completa: **84 pruebas, 0 fallos, 0 errores, 0 omitidas** (69 métodos; los parametrizados se expanden).

| Clase | Pruebas | Qué cubre |
|---|---|---|
| `UserLockoutTest` | 2 | Bloqueo por intentos fallidos |
| `JwtServiceTest` | 3 | Firma, manipulación, secreto débil |
| `AuthApiIntegrationTest` | 7 | Registro, Argon2id, login, refresh rotatorio, logout, recordarme, recuperación, bloqueo |
| `WaterProfileDomainTest` | 23 | Rangos, acidez, Specification, Prototype |
| `WaterProfileApiIntegrationTest` | 6 | Perfiles del sistema, personalizados, aislamiento |
| `MeasurementValidationChainTest` | 6 | Cadena de validación |
| `MeasurementApiIntegrationTest` | 8 | Origen real/simulado, rechazo, sospechosas, evento, filtros, aislamiento |
| `AnalysisEngineTest` | 10 | Anomalías (caso 7,1 → 5,8), tendencia, predicción, estrategias, datos insuficientes |
| `IntelligenceApiIntegrationTest` | 5 | Análisis, alertas sin duplicados, escenarios, aislamiento |
| `FreemiumIntegrationTest` | 6 | Límites de dispositivos, perfiles, análisis; Premium; vencimiento; solo ADMIN |
| `ReportRenderingTest` | 4 | Markdown, escape HTML (XSS), SVG, factoría |
| `ReportApiIntegrationTest` | 4 | Informe gratuito y Premium, cuotas, aislamiento |

## Casos de prueba destacados (aceptación)

| Caso | Resultado esperado | Verificado |
|---|---|---|
| Serie con pH habitual 7,1 que cae a 5,8 | Anomalía crítica explicada, sin afirmar riesgo sanitario | Sí |
| Menos de 3 mediciones | "Datos insuficientes", sin predicción inventada | Sí |
| Serie sin patrón lineal | No se genera predicción | Sí |
| pH 15 | Rechazo 422 y no se guarda | Sí |
| Dispositivo simulador | Medición siempre `SIMULATED` | Sí |
| Usuario B pide datos de A | 404 | Sí |
| Tercer dispositivo en plan gratuito | 402 `PLAN_LIMIT_REACHED` | Sí |
| Nombre con `<script>` en informe HTML | Escapado, sin etiqueta script | Sí |

## Pruebas pendientes o no realizadas

- Envío real de correo (SMTP).
- Rendimiento y carga.
- Comunicación con un medidor físico.
- Pruebas automáticas de la interfaz gráfica.
- Despliegue y comprobaciones en la nube.
