# Operacion y seguridad: CI bloqueante y token del firmware

Documento de operacion para el propietario. Cubre dos temas:

1. Que el despliegue a Railway quede bloqueado si fallan los tests.
2. El token de dispositivo (`APP_DEVICE_TOKEN`) que aparecio en `hardware/wokwi/sketch.ino`.

Nada de lo descrito aqui esta configurado todavia en Railway ni en GitHub. Cada accion marcada como
**Requiere aprobacion del propietario** debe hacerla una persona con acceso al dashboard.

---

## 1. CI que bloquea el despliegue

### Estado actual

- Workflow: `.github/workflows/ci.yml`, job `backend` ("Backend tests (Java 21 + H2)").
- Usa `actions/setup-java@v4` con Temurin 21 y cache de Maven.
- Ejecuta `./mvnw -B test` (sin `-Dmaven.test.skip`, sin override de release).
- Se ejecuta en `push` y `pull_request` contra `main` y `develop`.
- Antes de ejecutar el wrapper hace `chmod +x ./mvnw`: en git el archivo `mvnw` se guarda como `100644`
  (sin bit de ejecucion), y en Linux sin ese paso CI falla con "Permission denied".
  La correccion de fondo es `git update-index --chmod=+x mvnw` (cambio fuera de este documento; hacerlo aparte).

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

- El valor de prueba que estaba literal en `sketch.ino` (linea 78 en la version anterior) figura en los
  commits `812923d` (feat(fase2)) y `584f629` (feat(fase5)). Se busco con `git log --all -S` sobre el valor.
- Cualquiera con acceso de lectura al repositorio, o a su historial, puede ver ese valor.
- Ese valor coincide con el texto de desarrollo que el test de consistencia del firmware espera. Es un
  placeholder, pero si alguna vez se configuro igual en Railway, esta activo.

### Estado: activo o no

No se puede consultar Railway desde este entorno. El backend acepta cualquier valor igual al
`APP_DEVICE_TOKEN` configurado en Railway. Por tanto:

- Si la variable en Railway **sigue teniendo el valor de prueba** de `sketch.ino`, el token esta activo.
- Si la variable tiene otro valor, el token expuesto no sirve.
- **Requiere confirmacion del propietario** en Railway (Variables del servicio).

Tambien: en `application.yaml` el valor por defecto es vacio. Con `APP_DEVICE_TOKEN` vacio, el
`DeviceTokenFilter` deja pasar la peticion sin autenticarla (`deviceTokenBytes.length == 0`), y el README lo
describe igual. Un despliegue sin la variable no acepta el token expuesto, pero tampoco autentica dispositivos.

### Consecuencias si sigue activo

Quien tenga el valor puede enviar eventos a la API como dispositivo (`ROLE_DEVICE`): crear eventos falsos
asociados al correo del propietario, saturar el registro o provocar alertas falsas. No da acceso a sesiones
de usuario ni a la base de datos.

### Cambios hechos en el firmware (sin publicar un token nuevo)

- `sketch.ino` ya no contiene el token real en la configuracion. Lee `SECRET_DEVICE_TOKEN` de
  `secrets.h` si existe (`#if __has_include("secrets.h")`).
- Si `secrets.h` no existe, usa un placeholder de desarrollo que el backend rechaza cuando la variable de
  Railway tiene un valor real. Ese placeholder sigue apareciendo en el sketch; no es un secreto.
- `hardware/wokwi/.gitignore` excluye `secrets.h`. `hardware/wokwi/secrets.h.example` es la plantilla.
- Nada de esto se ha compilado para ESP32: no hay toolchain en este entorno. El cambio es de preprocesador
  y de una asignacion de constante; revisar al compilar en el IDE o en Wokwi.

### Rotacion: pasos para el propietario, en orden

**Requiere aprobacion y ejecucion por el propietario.** No pegar el valor nuevo en chats, issues ni commits.

1. Revisar en Railway la variable `APP_DEVICE_TOKEN`. Si su valor es el de prueba del sketch, rotar ahora.
2. Generar un valor nuevo solo en tu maquina, por ejemplo:

   ```
   python -c "import secrets; print(secrets.token_urlsafe(32))"
   ```

3. En Railway Dashboard > servicio orbisBackend > Variables, cambiar `APP_DEVICE_TOKEN` por el valor nuevo.
   Railway suele redesplegar al cambiar variables; confirmar que el despliegue termina bien.
4. En tu copia local del repositorio: `hardware/wokwi/secrets.h.example` -> `hardware/wokwi/secrets.h` y poner
   el mismo valor en `SECRET_DEVICE_TOKEN`. No copiar `secrets.h` fuera de tu maquina ni a un repositorio.
5. Cargar el firmware (Wokwi o hardware real) con `secrets.h` presente.
6. Verificar: una peticion de prueba con el valor antiguo debe ser rechazada (401/403); una con el valor nuevo
   debe ser aceptada. Hazlo desde tu maquina; no compartir el valor en la salida.
7. Revisar los logs del backend: el filtro registra "X-Device-Token invalido" (con IP y longitud, no el valor).
   Tras la rotacion, esos avisos deben corresponder solo a intentos con el valor antiguo.
8. Ejecutar `git status` antes de cualquier commit y confirmar que `secrets.h` no aparece.

### Reescribir el historial de git

Quitar el valor de los commits `812923d` y `584f629` requiere reescribir el historial compartido
(por ejemplo con `git filter-repo` y un force push). **Eso exige aprobacion explicita del propietario**,
porque cambia el historial compartido, invalida los clones existentes y debe coordinarse con cualquier otra
persona que tenga el repositorio. Tras la rotacion, el valor antiguo ya no tiene efecto, por lo que
reescribir el historial deja de ser urgente. Hasta entonces, el valor puede seguir leyendose en el historial.

---

## Lo que no se ha verificado

- La ejecucion de GitHub Actions (no es posible desde este entorno).
- La compilacion del firmware para ESP32.
- El valor actual de `APP_DEVICE_TOKEN` en Railway.
- El comportamiento de "Wait for CI" en la interfaz actual de Railway.
