# Orbis Backend

API REST para **Orbis** — sistema de tecnologia asistiva IoT para personas con discapacidad visual (bastón inteligente con IA).

> Este repositorio es el **backend** del proyecto Orbis.
> El frontend se encuentra en: [orbisFrontend](https://github.com/JuanAcostaACE/orbisFrontend)

---

## Descripcion

Orbis Backend es una API REST construida con Java 21 y Spring Boot 4.1.1. Recibe eventos del hardware ESP32 (bastón inteligente), los procesa usando Google Cloud Vision API para analizar imagenes, y los persiste en PostgreSQL. Expone endpoints REST consumidos por el frontend web.

---

## Stack

| Capa | Tecnologia |
|---|---|
| Lenguaje | Java 21 |
| Framework | Spring Boot 4.1.1 |
| Base de datos | PostgreSQL (produccion) / H2 en memoria (desarrollo local) |
| IA | Google Cloud Vision API |
| Deploy | Railway |
| Hardware | ESP32 (firmware en hardware/wokwi/) |

---

## Patrones de diseno implementados

| Patron | Descripcion |
|---|---|
| **Strategy** | Seleccion dinamica del modo de procesamiento (PASIVO/ACTIVO) |
| **Factory Method** | Creacion desacoplada de strategies |
| **Facade / Adapter** | Aislamiento de Google Cloud Vision |
| **Observer** | Notificacion de eventos sin acoplamiento |
| **State** | Gestion de estados del sistema (Inactivo → Pasivo → Activo) |
| **Command** | Encapsulamiento de operaciones (guardar evento, consultar IA) |

---

## Endpoints

| Metodo | URL | Descripcion |
|---|---|---|
| POST | /api/v1/eventos | Recibe evento del ESP32 |
| GET | /api/v1/eventos | Lista todos los eventos |
| GET | /api/v1/eventos/{id} | Obtiene evento por ID |
| GET | /api/v1/eventos/estado | Estado actual del sistema (State pattern) |
| GET | /health | Health check |

---

## Instalacion y ejecucion local

### Requisitos
- Java 21 instalado y configurado en JAVA_HOME
- (Opcional) Maven 3.x o usar mvnw incluido

### Pasos

```bash
# 1. Clonar el repositorio
git clone https://github.com/JuanAcostaACE/orbisBackend.git
cd orbisBackend

# 2. Ejecutar (usa H2 en memoria automaticamente, sin configuracion de DB)
.\mvnw.cmd spring-boot:run       # Windows
./mvnw spring-boot:run           # Linux/macOS

# 3. API disponible en:
# http://localhost:8080/api/v1/eventos
# http://localhost:8080/h2-console  (solo en modo local)
```

---

## Variables de entorno

### Desarrollo local (sin variables — usa H2 en memoria automaticamente)
No se requiere ninguna variable de entorno para correr en local.

### Produccion (Railway + PostgreSQL)

| Variable | Descripcion | Ejemplo |
|---|---|---|
| DB_URL | JDBC URL de PostgreSQL | jdbc:postgresql://host:5432/railway |
| DB_USERNAME | Usuario de la DB | postgres |
| DB_PASSWORD | Contrasena de la DB | 	u-password |
| SPRING_PROFILES_ACTIVE | Perfil Spring activo | prod |
| GOOGLE_APPLICATION_CREDENTIALS | Ruta al JSON de Service Account GCP | /app/gcp-key.json |
| PORT | Puerto HTTP (Railway lo inyecta automaticamente) | 8080 |

---

## Configuracion de base de datos

### Local (H2 - automatico)
Sin configuracion. El esquema se crea automaticamente al iniciar.

### Produccion (PostgreSQL en Railway)
1. Crear un proyecto en [Railway](https://railway.app)
2. Agregar servicio PostgreSQL
3. Configurar las variables de entorno listadas arriba
4. Ejecutar los scripts SQL en el orden:
   - src/main/resources/schema-postgresql.sql
   - src/main/resources/data-postgresql.sql

---

## Configuracion de Google Cloud Vision (solo modo ACTIVO)

1. Crear una cuenta de servicio en Google Cloud Console
2. Habilitar la API de Cloud Vision
3. Descargar el JSON de la cuenta de servicio
4. Configurar la variable GOOGLE_APPLICATION_CREDENTIALS con la ruta al JSON

---

## Despliegue en Railway

El archivo ailway.toml ya esta configurado. Solo necesitas:
1. Conectar este repositorio en Railway
2. Configurar las variables de entorno en el dashboard de Railway
3. Railway construye y despliega automaticamente con NIXPACKS

---

## Prueba rapida (sin hardware)

```bash
# Modo PASIVO — simula obstaculo detectado
curl -X POST http://localhost:8080/api/v1/eventos \
  -H "Content-Type: application/json" \
  -d '{"modo":"PASIVO","distanciaCm":28.5}'

# Modo ACTIVO — simula analisis de IA
curl -X POST http://localhost:8080/api/v1/eventos \
  -H "Content-Type: application/json" \
  -d '{"modo":"ACTIVO","distanciaCm":28.5}'

# Listar todos los eventos
curl http://localhost:8080/api/v1/eventos

# Estado del sistema
curl http://localhost:8080/api/v1/eventos/estado
```

---

## Estructura del proyecto

```
orbisBackend/
├── src/main/java/com/smartcane/api/
│   ├── controller/     EventoController, GlobalExceptionHandler, HealthController
│   ├── command/        Comando, ComandoGuardarEvento, ComandoConsultarIA, EjecutorComando
│   ├── config/         CorsConfig
│   ├── facade/         VisionFacade, VisionAdapter, VisionException
│   ├── factory/        CreadorStrategy, StrategyFactory
│   ├── model/          RegistroEvento, TipoObstaculo
│   ├── observer/       EventoObserver, EventoRegistradoEvent, LogEventoObserver
│   ├── repository/     RegistroEventoRepository, TipoObstaculoRepository
│   ├── service/        EventoService
│   ├── state/          EstadoSistema, EstadoInactivo, EstadoPasivo, EstadoActivo, ContextoSistema
│   └── strategy/       ModoProcesamientoStrategy, PasivoStrategy, ActivoStrategy
├── src/main/resources/
│   ├── application.yaml        Configuracion Spring Boot
│   ├── schema-postgresql.sql   DDL base de datos
│   └── data-postgresql.sql     Datos iniciales
├── hardware/wokwi/
│   ├── diagram.json    Circuito ESP32 simulado
│   ├── sketch.ino      Firmware ESP32
│   └── wokwi.toml      Librerias Wokwi
├── railway.toml        Configuracion de despliegue Railway
├── pom.xml
└── README.md
```

---

## Parte del proyecto Orbis

| Repositorio | Descripcion |
|---|---|
| [orbisBackend](https://github.com/JuanAcostaACE/orbisBackend) | Este repositorio — API REST Java/Spring Boot |
| [orbisFrontend](https://github.com/JuanAcostaACE/orbisFrontend) | Panel web (HTML/CSS/JS estatico) |