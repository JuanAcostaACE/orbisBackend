# Operacion y seguridad: CI bloqueante, token del firmware y limites de peticiones

Documento de operacion para el propietario. Cubre cuatro temas:

1. Que el despliegue a Railway quede bloqueado si fallan los tests.
2. El token de dispositivo (`APP_DEVICE_TOKEN`) que aparecio en `hardware/wokwi/sketch.ino`: procedimiento de rotacion.
3. Como lee el firmware el token (`secrets.h`).
4. El limitador de peticiones en memoria y sus limitaciones.

Nada de lo descrito aqui esta configurado todavia en Railway ni en GitHub. Cada accion marcada como
**Requiere aprobacion del propietario** debe hacerla una persona con acceso al dashboard.

Este documento no contiene valores reales de ningun secreto. Si un valor aparece aqui, es un error: avisar y
retirarlo.

---

## 1. CI que bloquea el despliegue

### Estado actual

- Workflow: `.github/workflows/ci.yml`, job `backend` ("Backend tests (Java 21 + H2)").
- Usa `actions/setup-java@v4` con Temurin 21 y cache de Maven.
- Ejecuta `./mvnw -B test` (sin `-Dmaven.test.skip`, sin override de release).
- Se ejecuta en `push` y `pull_request` contra `main` y `develop`.
- `mvnw` esta registrado como `100755` en el indice de git (commit `chore(ci): mvnw ejecutable en el indice`),
  asi que el workflow ya no hace `chmod`: el indice es la fuente de verdad.

**Problema:** `railway.toml` construye con `./mvnw -Dmaven.test.skip=true -B clean install`.
Eso significa que Railway puede desplegar un commit con tests rotos, aunque el job de CI falle.
CI por si sola no bloquea nada: solo avisa.

### Opcion (a): "Wait for CI" en Railway (checks del integrador de GitHub)

Railway, cuando el servicio esta conectado a GitHub, puede esperar a que los checks de GitHub del commit
pasen antes de desplegar.

- Quien lo activa: el propietario, en Railway Dashboard > servicio orbisBackend > Settings (opcion de
  esperar a CI / "Wait for CI" segun la version de la interfaz).
- Ventajas: no hay que mantener un segundo pipeline de despliegue ni guardar secretos de Railway en GitHub.
  Railway sigue siendo quien despliega.
- Desventajas: depende de que el check de GitHub se reporte siempre para el commit. Si el workflow no se
  ejecuta (por ejemplo por filtros de ruta) o el check no existe, el comportamiento debe verificarse en el
  dashboard; no se puede asumir. Ademas, la proteccion solo es tan buena como el propio check.

### Opcion (b): desplegar desde GitHub Actions solo despues de los tests

Un job `deploy` con `needs: backend`, que solo corre en `main`, y que despliega con el CLI de Railway
usando un token guardado como secreto de GitHub.

- Ventajas: el despliegue depende directamente del job de tests; control total del orden.
- Desventajas: hay que crear y rotar un token de Railway como secreto de GitHub; hay que desactivar el
  despliegue automatico desde GitHub en Railway para no desplegar dos veces; es mas codigo que mantener.

### Recomendacion

**Opcion (a), junto con el cambio de `buildCommand` de abajo.** Es la opcion con menos piezas que mantener.
Con el build sin `-Dmaven.test.skip`, Railway tambien falla su propio build si los tests fallan, de modo que
hay dos barreras independientes (el check de CI y el build).

**Requiere aprobacion del propietario:** el propietario debe:

1. Activar la espera a CI para el servicio en Railway Dashboard y confirmar que el check
   "Backend tests (Java 21 + H2)" aparece en los commits de `main`.
2. Opcional pero recomendado: en GitHub > Settings > Branches, proteger `main` y marcar ese check como requerido
   para hacer merge. (Esto protege los merges; no sustituye al bloqueo de despliegue.)
3. Aprobar cambiar `railway.toml`. **No se ha modificado.** El `buildCommand` recomendado es:

   ```
   chmod +x ./mvnw && ./mvnw -B clean install
   ```

   Antes de aplicarlo, comprobar que la suite pasa en el build de Railway (sin `DB_URL`, los tests usan H2)
   y que el tiempo de build es aceptable.

### Verificacion

- El YAML de `ci.yml` se valido con PyYAML (carga sin errores). **GitHub Actions no se ha ejecutado**: no hay
  forma de correr el workflow desde este entorno. La primera ejecucion real ocurrira al hacer push o PR.

---

## 2. Token de dispositivo expuesto en el firmware

### Que es

`APP_DEVICE_TOKEN` (Railway) es el secreto que el ESP32 envia en la cabecera `X-Device-Token`.

- Backend: `src/main/java/com/orbis/api/config/DeviceTokenFilter.java` compara la cabecera con el valor de
  `app.device.token` (en `application.yaml`: `${APP_DEVICE_TOKEN:}`) usando `MessageDigest.isEqual`
  (comparacion en tiempo constante). Si coincide, la peticion se autentica con `ROLE_DEVICE`.
- Firmware: `hardware/wokwi/sketch.ino` envia la cabecera en la peticion HTTP de eventos.
- Efecto: los eventos enviados por el dispositivo se asocian al correo de `APP_DEVICE_OWNER_EMAIL`.

### Exposicion

- El valor que estaba literal en `sketch.ino` en la version antigua figura en los commits `812923d`
  (feat(fase2)) y `584f629` (feat(fase5)), ambos alcanzables desde `main`. Detalle completo, con hashes, en
  `docs/historial-token.md`.
- **El mismo valor sigue en la rama `fase1-seguridad-bugs`:** es el fallback que `sketch.ino` usa cuando no
  existe `secrets.h` (ver "Como lee el firmware `secrets.h`"). Por tanto no es solo un valor del historial:
  esta en el arbol actual del repositorio, y cualquiera con acceso de lectura lo ve.
- Consecuencia: si alguna vez se configuro ese valor en Railway, el token esta activo. Si Railway tiene otro
  valor, el token expuesto no sirve, pero el fallback del firmware sigue siendo un valor conocido.

### Estado: activo o no

No se puede consultar Railway desde este entorno. El backend acepta cualquier valor igual al
`APP_DEVICE_TOKEN` configurado en Railway. Por tanto:

- Si la variable en Railway **tiene el valor de prueba** (el mismo del fallback de `sketch.ino`), el token esta activo.
- Si la variable tiene otro valor, el token expuesto no sirve.
- **Requiere confirmacion del propietario** en Railway (Variables del servicio). El paso 1 del runbook
  explica como comprobarlo sin copiar el valor.

Tambien: en `application.yaml` el valor por defecto es vacio. Con `APP_DEVICE_TOKEN` vacio, el
`DeviceTokenFilter` deja pasar la peticion sin autenticarla (`deviceTokenBytes.length == 0`), y el README lo
describe igual. Un despliegue sin la variable no acepta el token expuesto, pero tampoco autentica dispositivos.

### Consecuencias si sigue activo

Quien tenga el valor puede enviar eventos a la API como dispositivo (`ROLE_DEVICE`): crear eventos falsos
asociados al correo del propietario, saturar el registro o provocar alertas falsas. No da acceso a sesiones
de usuario ni a la base de datos.

### Cambios hechos en el firmware (sin publicar un token nuevo)

- `sketch.ino` ya no contiene un token real en la configuracion. Lee `SECRET_DEVICE_TOKEN` de
  `secrets.h` si existe (`#if __has_include("secrets.h")`).
- Si `secrets.h` no existe, usa un fallback de desarrollo. **Ese fallback tiene el mismo valor que el token
  expuesto.** Si Railway tuviera ese valor, el backend lo aceptaria. Por eso el fallback debe retirarse del
  sketch (cambio pendiente, ver "Pendiente" al final de esta seccion).
- `hardware/wokwi/.gitignore` excluye `secrets.h`. `hardware/wokwi/secrets.h.example` es la plantilla.
- Nada de esto se ha compilado para ESP32: no hay toolchain en este entorno. El cambio es de preprocesador
  y de una asignacion de constante; revisar al compilar en el IDE o en Wokwi.

### Como lee el firmware `secrets.h`

Fichero: `hardware/wokwi/sketch.ino` (no modificado en esta documentacion). Lectura del fichero:

1. Lineas 60-67: `#if __has_include("secrets.h")`. El preprocesador comprueba si existe `secrets.h` en la
   carpeta del sketch (`hardware/wokwi/`). Si existe, la linea `#include "secrets.h"` define
   `SECRET_DEVICE_TOKEN`. Si no existe, la rama `#else` define `SECRET_DEVICE_TOKEN` con el fallback.
2. Linea 87, dentro del namespace `Cfg`: `const char* DEVICE_TOKEN = SECRET_DEVICE_TOKEN;`. Es el unico punto
   donde se usa la constante.
3. Linea 407: `http.addHeader("X-Device-Token", Cfg::DEVICE_TOKEN);`. El valor se envia en cada peticion de
   evento y **nunca** se imprime por Serial (el propio comentario del sketch lo indica).

Comportamiento si falta `secrets.h`:

- **No hay error de compilacion.** `__has_include` es silencioso: el build termina bien usando el fallback.
  Es el fallo mas facil de pasar por alto.
- El dispositivo envia el valor del fallback. Con el valor correcto de Railway, el backend responde 401 (o 403)
  a los eventos y el dispositivo deja de registrarlos. Sin fuga, pero sin servicio.
- Si Railway tuviera el valor del fallback, el backend lo aceptaria. Por eso la rotacion es obligatoria y el
  fallback debe retirarse.
- En Wokwi, `secrets.h` no viene con el proyecto. Si se sube un proyecto compartido con `secrets.h` dentro, el
  valor queda publico. No subir `secrets.h` a proyectos compartidos (**no verificado** como se comporta el
  editor de Wokwi con ficheros ocultos; comprobar antes de compartir).

**Pendiente (fuera del alcance de esta documentacion):** retirar el fallback con valor real de `sketch.ino` y
sustituirlo por un `#error` si falta `secrets.h`, para que el build falle en vez de usar un valor conocido.
Requiere cambio de codigo y aprobacion.

### Runbook de rotacion (orden obligatorio)

El token expuesto se considera **comprometido**. Ningun valor real debe quedar en ficheros versionados,
en chats, en issues ni en la salida de comandos. Todos los pasos los ejecuta el propietario, en este orden.
No avanzar al paso siguiente si el anterior falla.

**Variables de shell** (cargarlas desde el gestor de contrasenas; nunca escribir los valores en un fichero):

- `BACKEND_URL`: URL base del backend, sin barra final.
- `OLD_TOKEN`: el valor comprometido.
- `NEW_TOKEN`: el valor nuevo generado en el paso 2.

Para que no queden en el historial de la shell, usar `read -rs OLD_TOKEN && export OLD_TOKEN` (bash) o
equivalente, y pegar el valor desde el gestor.

**1. Confirmar el valor actual de `APP_DEVICE_TOKEN` en Railway, sin copiarlo.**

- En Railway Dashboard > servicio orbisBackend > Variables, comprobar que `APP_DEVICE_TOKEN` existe en el
  entorno de produccion. Railway muestra el valor enmascarado: no copiarlo ni pegarlo en ningun chat.
- Para saber si coincide con el valor historico, ejecutar la prueba del paso 5 con `$OLD_TOKEN` **antes de
  cambiar nada**:
  - `401` o `403`: el valor de Railway no es el historico (o la variable esta vacia). La rotacion sigue siendo
    necesaria para cerrar la exposicion.
  - `201`: el valor de Railway **es** el historico y esta activo. Rotar cuanto antes. Ese `201` ha creado un
    evento de prueba: borrarlo desde la aplicacion.
- Esta comprobacion no copia el valor de Railway a ningun sitio.

**2. Generar el token nuevo en tu maquina, sin imprimirlo en chats.**

```
export NEW_TOKEN="$(python -c "import secrets; print(secrets.token_urlsafe(32))")"
```

- Guardar el valor en el gestor de contrasenas. Si se necesita copiarlo a Railway y al `secrets.h`, hacerlo en
  el terminal local.
- Para el firmware: copiar `hardware/wokwi/secrets.h.example` a `hardware/wokwi/secrets.h` y poner el valor en
  `SECRET_DEVICE_TOKEN`. Este fichero **nunca** se commitea (lo excluye `hardware/wokwi/.gitignore`).
  Comprobar antes de cualquier commit:

  ```
  git check-ignore -v hardware/wokwi/secrets.h
  git status --short
  ```

  `secrets.h` no debe aparecer en `git status`.

**3. Configurar Railway (owner).** En Railway Dashboard > servicio orbisBackend > Variables, en el entorno de
produccion:

- `APP_DEVICE_TOKEN` = el valor de `$NEW_TOKEN`.
- `APP_DEVICE_OWNER_EMAIL` = el correo del propietario. Confirmar que esta definida en el **mismo entorno**.
  Sin ella, los eventos del dispositivo no se asocian a ninguna cuenta. El correo no es secreto, pero no lo
  pegues en issues publicos.

**4. Redespliegue y espera.** Al cambiar variables, Railway redespliega el servicio. Esperar a que el
despliegue nuevo (con marca de tiempo posterior al paso 3) figure como activo y que los logs muestren el
arranque de la aplicacion. No ejecutar las pruebas del paso 5 contra un despliegue anterior.

**5. Verificar: el token antiguo se rechaza y el nuevo se acepta.** Exportar `BACKEND_URL` antes.

Token antiguo (debe devolver `401` o `403`, y no crear evento):

```
curl -s -o /dev/null -w "%{http_code}\n" -X POST "$BACKEND_URL/api/v1/events" \
  -H "Content-Type: application/json" \
  -H "X-Device-Token: $OLD_TOKEN" \
  -d '{"mode":"PASSIVE","distanceCm":50}'
```

Token nuevo (debe devolver `201`):

```
curl -s -o /dev/null -w "%{http_code}\n" -X POST "$BACKEND_URL/api/v1/events" \
  -H "Content-Type: application/json" \
  -H "X-Device-Token: $NEW_TOKEN" \
  -d '{"mode":"PASSIVE","distanceCm":50}'
```

- Si el token antiguo devuelve `201`: sigue activo. Parar, revisar `APP_DEVICE_TOKEN` en Railway y repetir el
  paso 4 antes de seguir.
- Si el token nuevo devuelve `401`/`403`: el valor de Railway no coincide con `$NEW_TOKEN` o el despliegue no
  ha terminado.
- Si devuelve `400`: el cuerpo no cumple `EventRequest` (`mode` debe ser `PASSIVE` o `ACTIVE`; `distanceCm`
  entre 0 y 500). La autenticacion si funciona.
- Si devuelve `500`: revisar los logs de Railway antes de seguir.
- El `201` crea un evento real asociado al correo de `APP_DEVICE_OWNER_EMAIL`. Borrarlo desde la aplicacion si
  no se quiere conservar.
- Un `401` del token antiguo no prueba por si solo que el dispositivo funcione. Un `401` tambien aparece si
  la variable esta vacia. Por eso el `201` del token nuevo es la prueba que cuenta.

**6. Reflashear el ESP32** (Wokwi o hardware real) con el `secrets.h` nuevo presente en `hardware/wokwi/`.
Comprobar que llegan eventos: deben aparecer en la aplicacion eventos con marca de tiempo posterior al
reflasheo. Por Serial solo se ven codigos de respuesta; el valor del token nunca se imprime.

**7. Revisar los logs de Railway.** `DeviceTokenFilter` registra un aviso `X-Device-Token invalido recibido
desde IP: ...` con la longitud de la cabecera, nunca el valor.

- Tras el reflasheo no deben aparecer avisos nuevos desde la IP del dispositivo.
- Los avisos que aparezcan desde IPs que no son las del dispositivo indican que alguien sigue usando el valor
  antiguo: investigarlos antes de dar la rotacion por cerrada. La IP que ve el backend depende del proxy: ver
  `docs/proxy-railway.md`.

**8. Registrar la rotacion.** Anotar la fecha y el responsable en la tabla de abajo (sin valores). Hacerlo
solo cuando los pasos 5 a 7 estan completos.

**9. Historial de git: solo despues de 1 a 8, y solo con aprobacion explicita.** La reescritura del historial
no forma parte de la rotacion. Ver `docs/historial-token.md` para las opciones. Por defecto no se hace.

#### Rollback: si el dispositivo deja de enviar tras la rotacion

1. Lo mas probable: el ESP32 sigue con el valor antiguo o con el fallback. Comprobar que `secrets.h` contiene
   `$NEW_TOKEN` y reflashear (paso 6). Repetir el `201` del paso 5 desde el portatil para separar el problema
   del firmware del problema del backend.
2. Si el `201` del token nuevo falla: el problema esta en Railway. Volver a fijar `APP_DEVICE_TOKEN` al valor
   nuevo (no al antiguo) y esperar al redespliegue.
3. Solo si el propietario decide por escrito que la exposicion del valor antiguo es aceptable durante una
   ventana concreta: restaurar temporalmente el valor antiguo en Railway. Anotar la decision, la fecha de
   inicio y la fecha de fin en la tabla de registro. Rotar de nuevo en cuanto el firmware funcione. No dejar el
   valor antiguo activo de forma indefinida.
4. No usar una variable vacia como solucion: el backend deja de autenticar al dispositivo y el dispositivo
   deja de registrar eventos.

#### Registro de rotaciones

| Fecha (AAAA-MM-DD) | Responsable | Paso 5: antiguo / nuevo | Paso 6: eventos llegan | Notas (sin valores) |
|---|---|---|---|---|
| (pendiente) | (pendiente) | (pendiente) | (pendiente) | Rotacion no ejecutada todavia |

### Reescribir el historial de git

Quitar el valor de los commits `812923d` y `584f629` requiere reescribir el historial compartido
(por ejemplo con `git filter-repo` y un force push). **Eso exige aprobacion explicita del propietario**,
porque cambia el historial compartido, invalida los clones existentes y debe coordinarse con cualquier otra
persona que tenga el repositorio. Tras la rotacion, el valor antiguo ya no tiene efecto, por lo que
reescribir el historial deja de ser urgente. Hasta entonces, el valor puede seguir leyendose en el historial.

Analisis completo, hashes y opciones en `docs/historial-token.md`. Recomendacion: rotar y verificar, sin
reescribir el historial, salvo que el propietario decida lo contrario con un plan coordinado.

Puntos a tener en cuenta si se decide reescribir:

- Reescribir invalida todos los clones existentes: cada persona debe volver a clonar o hacer reset al nuevo
  historial. Los forks y las ramas abiertas quedan desalineados.
- Requiere un force push coordinado a las ramas afectadas, con un momento acordado y las protecciones de rama
  revisadas (un force push puede estar bloqueado en `main`).
- Tambien hay que purgar caches de CI y cualquier copia del historial fuera de GitHub.
- **Esta fase no incluye la reescritura del historial ni el force push.** Queda fuera de alcance hasta que el
  propietario lo apruebe por escrito.

---

## 3. Rate limit en memoria

El limitador de peticiones vive en la memoria del proceso del backend. Esta seccion describe lo que hace hoy,
con que valores y que pasa cuando el proceso se reinicia o se escala.

### Limites configurados

Valores por defecto leidos de `src/main/resources/application.yaml` (bloque `app.ratelimit`). Cada valor puede
cambiarse con la variable de entorno indicada.

| Limite | Ambito | Valor por defecto | Variables | Donde se aplica |
|---|---|---|---|---|
| Login por IP | `POST /auth/login`, por IP | 10 peticiones por 60 s | `APP_RATELIMIT_LOGIN_MAX`, `APP_RATELIMIT_LOGIN_WINDOW` | `RateLimitFilter` |
| Registro por IP | `POST /auth/register`, por IP | 10 peticiones por 3600 s | `APP_RATELIMIT_REGISTER_MAX`, `APP_RATELIMIT_REGISTER_WINDOW` | `RateLimitFilter` |
| Login por cuenta | fallos de `POST /auth/login` por correo normalizado | 5 fallos por 900 s | `APP_RATELIMIT_ACCOUNT_MAX`, `APP_RATELIMIT_ACCOUNT_WINDOW` | `AccountLockoutService`, llamado desde `AuthController` |
| Interruptor general | solo el limite por IP | activado | `APP_RATELIMIT_ENABLED` | `RateLimitFilter` |

Notas:

- El limite por IP responde `429` con cabecera `Retry-After`. El limite por cuenta responde `429` con el mismo
  formato de cuerpo.
- El limite por cuenta **no** lo gestiona `RateLimitFilter`. Se comprueba en `AuthController` antes de buscar
  el usuario. Segun el codigo leido, `APP_RATELIMIT_ENABLED` no lo desactiva.
- El limite por IP cuenta **cada** peticion `POST` a esas rutas, correcta o no, dentro de la ventana.
- La IP que se usa es la que resuelve Tomcat (`RemoteIpValve`). Detras de Railway, eso depende de la
  configuracion de proxy de confianza: ver `docs/proxy-railway.md`. Si esa IP es la del proxy, todos los clientes
  comparten un unico contador.

### Que pasa si el proceso se reinicia

- Los contadores viven en `ConcurrentHashMap` del proceso. Un reinicio, un redespliegue o un fallo los borra.
- Tras un reinicio, cada IP y cada cuenta empieza con la ventana a cero. Una cuenta bloqueada queda
  desbloqueada.
- Un atacante puede aprovechar reinicios provocados (por ejemplo, redespliegues) para reiniciar sus intentos.
  Con un redespliegue cada pocos minutos, el limite por IP deja de ser efectivo.
- La limpieza de entradas caducadas solo se dispara cuando hay mas de 5.000 entradas. Con mucho trafico,
  el mapa crece hasta entonces. No se ha medido el consumo de memoria.

### Una sola instancia: condicion de validez

**El limite solo es valido con una unica instancia del backend.** Con varias instancias:

- Cada instancia cuenta por separado. Con `N` instancias y balanceo, un atacante puede hacer hasta
  `N` veces el maximo por IP y por cuenta.
- El bloqueo por cuenta se vuelve inconsistente: un intento puede caer en una instancia que no conoce los
  fallos anteriores.

Railway, con el plan y la configuracion actuales, debe tratarse como una sola instancia **solo si** el
propietario lo ha confirmado en el dashboard (numero de replicas). Esta documentacion no lo ha verificado.

### Si se necesita escalar: almacen compartido

Cualquier despliegue con varias instancias necesita un almacen compartido para los contadores antes de subir
el numero de replicas. **No implementar sin presentar costes y aprobacion.** Alternativas:

| Alternativa | Ventajas | Inconvenientes y coste | Notas |
|---|---|---|---|
| Tabla PostgreSQL con caducidad | Sin servicio nuevo: la base de datos de produccion ya es PostgreSQL. | Una escritura por intento de login. Postgres no caduca filas: hace falta un job de limpieza. Carga extra en la base de datos bajo ataque. Implementacion moderada. | Requiere `INSERT ... ON CONFLICT` atomico. |
| Redis | Caducidad nativa y operacion atomica de contador. Respuesta rapida. | Un servicio mas que pagar y operar. Hay que decidir si, si Redis cae, se deja pasar o se bloquea (fail-open o fail-closed). Mas configuracion. | Coste mensual: verificar en el precio actual de Railway (no consultado). |
| Limitacion en el borde (CDN o proxy) | Bloquea antes de llegar a la aplicacion. Sin codigo. | Depende de como el proveedor detecte la IP del cliente, con el mismo problema de confianza de cabeceras. No ve el correo de la cuenta, asi que no sustituye el limite por cuenta. Coste segun el plan. | El limite por cuenta debe seguir en la aplicacion o en la base de datos. |

Recomendacion para cuando haga falta: tabla PostgreSQL para el limite por IP y por cuenta, por ser la opcion
sin servicio nuevo. Redis solo si el trafico lo justifica. Cualquiera de las tres requiere coste y
aprobacion antes de implementarse.

---

## Lo que no se ha verificado

- La ejecucion de GitHub Actions (no es posible desde este entorno).
- La compilacion del firmware para ESP32 y el comportamiento de `__has_include` en el compilador de Wokwi.
- El valor actual de `APP_DEVICE_TOKEN` en Railway.
- El numero de replicas actual del servicio en Railway.
- El comportamiento de "Wait for CI" en la interfaz actual de Railway.
- Los comandos `curl` de rotacion (pasos 5 y 6): solo se han revisado en texto, no ejecutados contra el backend.
- Las IPs que ve el backend detras del proxy de Railway (ver `docs/proxy-railway.md`).
- La prueba automatica `HardwareWokwiConsistencyTest` comprueba el sketch y el `.gitignore` de forma estatica;
  no sustituye a compilar el firmware.
