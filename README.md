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
```

### Consola H2 (solo desarrollo explicito)

La consola H2 (`/h2-console`) esta **desactivada** en el perfil por defecto. Solo se activa
con el perfil `dev` y la variable `H2_CONSOLE_ENABLED=true`:

```bash
SPRING_PROFILES_ACTIVE=dev H2_CONSOLE_ENABLED=true ./mvnw spring-boot:run
# Consola: http://localhost:8080/h2-console
```

En `prod` la consola siempre esta desactivada. La base H2 en memoria sigue siendo el
almacenamiento local por defecto.

---

## Variables de entorno

### Desarrollo local (sin variables — usa H2 en memoria automaticamente)
No se requiere ninguna variable de entorno para correr en local.

### Limitador de peticiones (login y registro)

`POST /auth/login` y `POST /auth/registro` tienen un limite por IP con ventana fija
(en memoria). Al superarlo responde **429** con `{status, mensaje, timestamp}` y la cabecera `Retry-After`.

| Variable | Por defecto | Descripcion |
|---|---|---|
| APP_RATELIMIT_ENABLED | true | Activa o desactiva el limitador |
| APP_RATELIMIT_LOGIN_MAX | 10 | Intentos de login permitidos por ventana |
| APP_RATELIMIT_LOGIN_VENTANA | 60 | Duracion de la ventana de login (segundos) |
| APP_RATELIMIT_REGISTRO_MAX | 10 | Registros permitidos por ventana |
| APP_RATELIMIT_REGISTRO_VENTANA | 3600 | Duracion de la ventana de registro (segundos) |
| APP_RATELIMIT_CUENTA_MAX | 5 | Intentos fallidos de login por cuenta (correo normalizado) antes de bloquearla |
| APP_RATELIMIT_CUENTA_VENTANA | 900 | Ventana del bloqueo por cuenta (segundos). La cuenta queda bloqueada hasta que caduque |

#### Cliente real detras del proxy (X-Real-IP)

Detras de un proxy la conexion directa llega desde el proxy, no desde el cliente. Tomcat
(`RemoteIpValve`, bloque `server.tomcat.remoteip` en `application.yaml`) toma el cliente real
de la cabecera `X-Real-IP` **solo** si la conexion directa viene de un proxy de confianza.
`X-Forwarded-For` **no** se usa: un cliente puede falsificarla sin un proxy de confianza.
Una `X-Real-IP` enviada por un par no confiable se ignora.

| Variable | Por defecto | Descripcion |
|---|---|---|
| APP_TRUSTED_PROXIES | loopback y rangos privados (127.x, ::1, 10.x, 172.16-31.x, 192.168.x, IPv6 ULA fc00::/7) | Expresion regular con las IP de conexion de confianza. No usar llaves en el valor. Vacia = no se confia en ningun proxy |

**Verificacion pendiente (operador):** desde el repositorio no se pudo confirmar la IP desde la
que Railway conecta con el contenedor. Railway no publica rangos fijos para su proxy, y hay
reportes de IPs en 100.64.0.0/10 (espacio CGNAT) que NO estan en el valor por defecto. Si la IP
del proxy no entra en el patron, `X-Real-IP` se ignora y el limite vuelve a ser global (el
comportamiento anterior, sin riesgo de suplantacion). Para confirmarlo tras desplegar: enviar
`X-Real-IP: 203.0.113.99` a `POST /auth/login` con credenciales erroneas repetidas y revisar en
los logs la IP que aparece en el aviso "Limite de peticiones alcanzado". Si aparece la IP del
proxy y no 203.0.113.99, anadir esa IP con `APP_TRUSTED_PROXIES`.

Ademas, el trafico por la red privada de Railway (`*.railway.internal`) no pasa por el edge, asi
que cualquier servicio dentro del patron de confianza podria fijar `X-Real-IP`. Confiar en el
patron por defecto solo es seguro si este backend es el unico servicio de su entorno con acceso
a la red privada.

#### Limite por cuenta (login)

Ademas del limite por IP, `POST /auth/login` cuenta los intentos fallidos por correo normalizado
(minusculas y sin espacios). Al alcanzar `APP_RATELIMIT_CUENTA_MAX` fallos dentro de
`APP_RATELIMIT_CUENTA_VENTANA` segundos, la cuenta responde **429** con el mismo cuerpo que el
limite por IP, aunque se envie la contrasena correcta. La comprobacion se hace antes de buscar el
usuario, asi que una cuenta bloqueada y una inexistente responden igual. Un login correcto borra
el contador. Una IP no bloquea a otras cuentas; una cuenta bloqueada lo esta desde cualquier IP.

Efecto colateral conocido: quien conoce un correo puede bloquear temporalmente el acceso de esa
cuenta enviando intentos erroneos (denegacion de servicio por cuenta).

#### Limitaciones y riesgo abierto (instancia unica)

- **Estos contadores funcionan para UNA sola instancia del backend.** Los contadores de IP y de
  cuenta viven en memoria del proceso. Si el backend corre en mas de una instancia, cada una cuenta
  por separado y los limites se multiplican por el numero de instancias.
- Si el backend pasa a varias instancias, los contadores deben moverse a un almacen compartido
  (por ejemplo PostgreSQL existente). Esa decision requiere aprobacion del propietario; no se
  anadio Redis ni otra dependencia de infraestructura.
- Sin reinicios persistentes: al reiniciar el proceso los contadores se pierden.

### Autenticacion de dispositivo (ESP32)

| Variable | Descripcion |
|---|---|
| APP_DEVICE_TOKEN | Token que el ESP32 envia en `X-Device-Token`. |
| APP_DEVICE_OWNER_EMAIL | Correo del usuario propietario de los eventos del dispositivo. Obligatorio si `APP_DEVICE_TOKEN` esta definido; debe tener formato de correo valido y pertenecer a un usuario registrado. |

**Regla de arranque:** si `APP_DEVICE_TOKEN` esta definido, `APP_DEVICE_OWNER_EMAIL` tambien
debe estar definido y tener formato de correo valido. Si falta o es invalido, la aplicacion
no arranca y el mensaje de error nombra la variable (nunca muestra valores).

**Comportamiento si `APP_DEVICE_TOKEN` esta vacio o no definido:** la autenticacion de
dispositivo queda deshabilitada. El arranque no falla; si `APP_DEVICE_OWNER_EMAIL` esta
definido, se registra un aviso (WARN) porque no tiene efecto. El filtro no autentica ninguna
peticion con `X-Device-Token`. Sin token valido, `POST /api/v1/eventos` solo acepta una
sesion de usuario; el ESP32 recibira 401. Verificar que la variable este definida en cada entorno.

**Propietario no registrado:** si el correo de `APP_DEVICE_OWNER_EMAIL` no corresponde a un
usuario registrado, una peticion del dispositivo responde 404 con mensaje generico y se
registra un error en log (sin el correo ni el token).

### Produccion (Railway + PostgreSQL)

| Variable | Descripcion | Ejemplo |
|---|---|---|
| DB_URL | JDBC URL de PostgreSQL | jdbc:postgresql://host:5432/railway |
| DB_USERNAME | Usuario de la DB | postgres |
| DB_PASSWORD | Contrasena de la DB | 	u-password |
| SPRING_PROFILES_ACTIVE | Perfil Spring activo | prod |
| GOOGLE_APPLICATION_CREDENTIALS | Ruta al JSON de Service Account GCP | /app/gcp-key.json |
| PORT | Puerto HTTP (Railway lo inyecta automaticamente) | 8080 |
| APP_CORS_ORIGIN | URL exacta del frontend (sin `/` final) | https://orbis.vercel.app |
| APP_DEVICE_TOKEN | Token del ESP32 (32+ caracteres aleatorios) | — |
| APP_DEVICE_OWNER_EMAIL | Correo del dueño del dispositivo | correo@ejemplo.com |
| SESSION_COOKIE_SECURE | Cookie solo por HTTPS (en `prod` ya es `true`) | true |
| SHOW_SQL | Imprimir SQL en el log (solo depuración) | false |

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

El archivo 
ailway.toml ya esta configurado. Solo necesitas:
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

## Seguridad

- Sesión por cookie HttpOnly `ORBIS_SID` (solo cookie, nunca en la URL) y CSRF con `XSRF-TOKEN`.
- Contraseñas con BCrypt (costo 12, máximo 72 bytes).
- CORS con orígenes, métodos y cabeceras explícitos.
- Cabeceras HSTS, nosniff, Referrer-Policy, Permissions-Policy y CSP.
- Errores en JSON sin trazas internas (400/401/403/404/405/500).
- CI con permisos mínimos, Dependabot y CodeQL.

Detalles y cómo reportar vulnerabilidades: [SECURITY.md](SECURITY.md).

---

## Parte del proyecto Orbis

| Repositorio | Descripcion |
|---|---|
| [orbisBackend](https://github.com/JuanAcostaACE/orbisBackend) | Este repositorio — API REST Java/Spring Boot |
| [orbisFrontend](https://github.com/JuanAcostaACE/orbisFrontend) | Panel web (HTML/CSS/JS estatico) |
