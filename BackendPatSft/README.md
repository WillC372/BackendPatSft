# PoolGuard — Backend (Java Puro)

Servidor backend para el sistema de monitoreo inteligente de calidad de agua en piscinas de uso mixto (adultos y niños), desarrollado para el caso de estudio universitario de **Patrones de Software**.

> **Repositorio del Frontend:** [FrontendPatSft](https://github.com/WillC372/FrontendPatSft)

---

## 🏛️ Arquitectura y Reglas Técnicas

1. **Lenguaje:** 100% Java estándar (JDK 8 o superior, sin frameworks pesados ni dependencias externas de terceros).
2. **Nomenclatura:** Todo el código fuente, clases, interfaces, métodos y variables están estrictamente en **Inglés** (`WaterQualityStateMachine`, `BluetoothSimulator`, `AlertFactory`, etc.).
3. **Seguridad y Encriptación:** Las contraseñas **nunca** se guardan en texto plano; están cifradas con **PBKDF2 con HMAC-SHA256** y sal criptográfica aleatoria de 16 bytes (65.536 iteraciones).
4. **Control de Acceso basado en Roles (RBAC):** Diferenciación entre usuarios `ADMIN` y `OPERATOR`.
5. **Persistencia Reactiva:** Persistencia atómica transaccional mediante el patrón `Unit of Work` sobre almacenamiento JSON embebido y soporte SQL relacional.

---

## 🧩 Patrones de Diseño Implementados (GoF)

| Patrón | Paquete / Clases Clave | Descripción y Responsabilidad |
| :--- | :--- | :--- |
| **State Pattern** | `com.poolguard.patterns.state` (`WaterStateMachine`, `OptimalState`, `CautionState`, `CriticalState`) | Modela el ciclo de vida de la calidad del agua. Según el estado actual, altera dinámicamente el comportamiento del sistema, la severidad de las alertas y las restricciones de uso de la piscina. |
| **Observer Pattern** | `com.poolguard.patterns.observer` (`SensorSubject`, `SensorObserver`, `BluetoothSimulator`) | Desacopla la recepción de telemetría del sensor físico/simulador de la lógica de procesamiento. `MonitoringService` observa al sujeto sin acoplamiento directo. Incluye escenarios de prueba en vivo. |
| **Mediator / Event Bus** | `com.poolguard.patterns.mediator` (`EventBus`, `Event`, `EventType`) | Centraliza y desacopla la comunicación multidireccional entre el módulo Bluetooth, el motor analítico de pH, el motor de IA predictiva y la interfaz. |
| **Factory Method / Abstract Factory** | `com.poolguard.patterns.factory` (`AlertFactory`, `DatabaseConnectionFactory`) | `AlertFactory` crea dinámicamente los tipos de alertas (`PushAlert`, `VoiceAlert`, `VisualAlert`). `DatabaseConnectionFactory` abstrae la creación de motores de almacenamiento (JSON embebido / MySQL). |
| **Singleton Pattern** | `com.poolguard.patterns.singleton` (`BluetoothConnectionManager`), `com.poolguard.auth.SessionManager` | Garantiza una única instancia global del gestor de conexión Bluetooth y del administrador de sesiones activas del sistema. |
| **Command Pattern** | `com.poolguard.patterns.command` (`Command`, `CommandInvoker`, `UpdateThresholdCommand`, `RegisterMaintenanceCommand`) | Encapsula cada acción sensible del usuario en objetos ejecutables con bitácora de auditoría inalterable (`AuditService`). |
| **Repository + Unit of Work** | `com.poolguard.patterns.repository` (`Repository<T>`, `UnitOfWork`) | Abstrae el acceso a datos y maneja transacciones locales atómicas con capacidad de funcionamiento offline y sincronización automática. |

---

## 🧠 Módulos Avanzados

1. **Motor Analítico de pH (`AnalyticsEngine`):** Evalúa flujos de datos y pendientes a corto plazo para detectar caídas anómalas de pH antes de alcanzar niveles críticos (prevención de incidentes por orina/fluidos).
2. **IA Predictiva de Carga Orgánica (`PredictiveEngine`):** Evalúa carga de bañistas, cloro libre y pH para calcular probabilísticamente el riesgo y tiempo estimado de colapso sanitario.
3. **Asistente Inteligente de Dosificación (`DosageAssistant`):** Calcula la cantidad exacta de químicos correctivos (soda cáustica, ácido clorhídrico, hipoclorito de sodio) en gramos o mililitros según el volumen de la piscina ($m^3$).
4. **Reporte Sanitario Exportable (`ReportGenerator`):** Genera reportes técnicos de cumplimiento sanitario en texto estructurado.

---

## 📡 API REST Propia (Endpoints)

| Método | Endpoint | Descripción | Acceso |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/login` | Autenticación con verificación de hash PBKDF2 | Público |
| `POST` | `/api/register` | Registro de nuevos operarios | Público |
| `POST` | `/api/logout` | Invalidación de sesión del token | Autenticado |
| `GET` | `/api/status` | Estado de la piscina, última lectura y estado Bluetooth | Autenticado |
| `GET` | `/api/readings?hours=24` | Historial de telemetría de las últimas 24 horas | Autenticado |
| `GET` | `/api/prediction` | Análisis de riesgo y colapso sanitario por IA | Autenticado |
| `GET` | `/api/dosage` | Recomendaciones químicas automáticas | Autenticado |
| `GET` | `/api/alerts` | Historial de alertas emitidas | Autenticado |
| `POST` | `/api/thresholds` | Modificación de umbrales de seguridad (Command) | Admin |
| `POST` | `/api/maintenance` | Registro de aplicación de químicos (Command) | Autenticado |
| `GET` | `/api/audit` | Bitácora inalterable de auditoría | Admin |
| `GET` | `/api/report` | Descarga de reporte sanitario | Autenticado |
| `GET` | `/api/bluetooth/status` | Estado actual de conexión BLE y simulador | Autenticado |
| `GET` | `/api/bluetooth/scan` | Escaneo de medidores físicos BLE en el rango | Autenticado |
| `POST` | `/api/bluetooth/simulator/start` | Inicio del flujo de telemetría del simulador | Autenticado |
| `POST` | `/api/bluetooth/simulator/stop` | Detención del flujo y desconexión de sensor | Autenticado |
| `POST` | `/api/bluetooth/simulator/scenario`| Aplicación de escenarios de prueba o valores manuales | Autenticado |

---

## ⚙️ Compilación y Ejecución

### En Windows (1 Clic)
Simplemente ejecuta el script:
```bat
iniciar_servidor.bat
```

### Manual por Terminal
```powershell
# 1. Compilar todas las clases
$files = (Get-ChildItem -Path backend\src, database -Filter *.java -Recurse).FullName; javac -encoding UTF-8 -d backend/out $files

# 2. Iniciar el servidor en el puerto 8080
java -cp backend/out com.poolguard.Main 8080
```

---

## 👥 Cuentas Preconfiguradas

* **Administrador:** `admin` / `admin123` (Rol: `ADMIN`)
* **Operario:** `operario` / `operario123` (Rol: `OPERATOR`)
