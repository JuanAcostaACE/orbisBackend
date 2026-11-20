# CI como requisito de despliegue

Este documento describe cómo hacer que el job `tests-backend` (workflow "CI — Backend", archivo `.github/workflows/ci.yml`) sea un requisito real antes de desplegar el backend en Railway.

Estado: **preparado, no activado**. El workflow está en el repositorio, pero la regla de rama en GitHub, la espera de CI en Railway y la desactivación de los deploys de PR deben configurarse a mano por el propietario. Nada de esto se ha aplicado todavía. El archivo `railway.toml` no se ha modificado.

---

## Qué hace el workflow

- Nombre del workflow: `CI — Backend`. Se ejecuta en `push` y `pull_request` para las ramas `main` y `fase1-seguridad-bugs`.
- Job `tests-backend` (nombre visible `tests-backend`) en `ubuntu-latest`, con timeout de 20 minutos.
- JDK 21 Temurin, caché de Maven, y `./mvnw -B test` sin flags de salto de tests.
- Permisos mínimos (`contents: read`). No usa secretos ni despliega nada.
- Si fallan los tests, sube los reportes de Surefire como artefacto.

---

## Verificación local

- Build local con tests: `./mvnw -B clean install`, con **144 tests en verde**.
- El workflow usa `./mvnw -B test`, sin flags de salto de tests. Spring Boot usa H2 cuando `DB_URL` no está definida, por lo que el job no necesita secretos ni base de datos.

---

## 1. Verificación de origen de Railway

En Railway, el servicio `orbisBackend` debe desplegar desde la rama **`main`**.

1. Abrir el proyecto en Railway y entrar en el servicio `orbisBackend`.
2. Ir a **Settings** y localizar la sección de origen del código (Source / conexión con el repositorio de GitHub). El nombre exacto de la sección y del campo de rama hay que confirmarlo en la UI actual.
3. Comprobar que la rama configurada es `main`.

Criterio: el campo de rama muestra exactamente `main`.

Si muestra otra rama (por ejemplo `fase1-seguridad-bugs`), cambiarla a `main` antes de activar cualquier otro punto de este documento. Mientras la rama sea otra, Railway desplegará commits que no han pasado por la regla de `main`.

**Aviso:** cambiar la rama de origen a `main` puede disparar un despliegue inmediato de `main`. Seguir el orden de la sección 4: primero activar **"Wait for CI"**, después cambiar o confirmar la rama, y solo entonces verificar.

**Esto no se puede verificar desde este repositorio.** La rama de despliegue vive en la configuración de Railway, no en el código.

---

## 2. Desactivar deploys automáticos de PR

Railway puede crear un despliegue para cada pull request. Eso permitiría desplegar un build que todavía no ha pasado CI. Para este servicio esa opción debe estar **desactivada**.

1. En Railway, abrir el proyecto y el entorno (normalmente `production`).
2. Buscar la opción de deploys de pull requests (PR Deploys o similar; etiqueta a confirmar en la UI actual), normalmente en la configuración del entorno o del servicio.
3. Desactivarla para `orbisBackend`.

Criterio: con un PR abierto contra `main`, Railway no crea ningún despliegue asociado a ese PR. Esto se comprueba en la pestaña **Deployments** tras la prueba del punto 5.

---

## Prerrequisito: ci.yml debe estar en main

`main` todavía contiene la versión antigua de `ci.yml`, cuyos jobs y disparadores no incluyen `tests-backend`. Mientras esa versión siga en `main`, el check `tests-backend` no aparecerá en el selector de checks del ruleset (sección 3) y no habrá nada que Railway pueda esperar (sección 4).

- La fusión del `ci.yml` nuevo (`fase1-seguridad-bugs` hacia `main`) la decide y aprueba el propietario, mediante un PR revisado antes de fusionar.
- **No está hecha.** Este documento no da por hecho que la fusión haya ocurrido.
- Hasta que el propietario apruebe y se fusione, no ejecutar las secciones 3 y 5.

---

## 3. Ruleset de GitHub para `main`

Pasos exactos (los nombres de menús pueden variar; confirmar en la UI actual):

1. En GitHub, abrir el repositorio y entrar en **Settings > Rules > Rulesets**.
2. Pulsar **New ruleset > New branch ruleset**.
3. Nombre: por ejemplo `proteger-main`. **Enforcement status**: `Active`.
4. En **Target branches**, pulsar **Add target > Include default branch** o incluir explícitamente `main`.
5. Activar **Require a pull request before merging**. El número de aprobaciones puede ser 0 si se trabaja en solitario; poner 1 si se quiere exigir revisión.
6. Activar **Require status checks to pass**:
   - Activar **Require branches to be up to date before merging** (recomendado).
   - Pulsar **Add checks** y seleccionar `tests-backend`.
7. Activar **Block force pushes** (recomendado).
8. Activar **Block deletions** para que `main` no pueda borrarse.
9. En **Bypass list**, no añadir a nadie. Con la lista vacía la regla vale para todos, incluido el propietario.
10. Pulsar **Create**.

**Nota:** `tests-backend` solo aparece en el selector de checks después de que el workflow se haya ejecutado al menos una vez. Si no aparece, consultar la sección «Prerrequisito: ci.yml debe estar en main» y volver a esta pantalla cuando se cumpla.

Verificación: en un PR hacia `main`, el panel de merge debe mostrar `tests-backend` como requisito pendiente hasta que pase, y el botón de merge debe quedar bloqueado si falla.

Si ya existe una protección clásica (**Settings > Branches**) sobre `main`, la regla equivalente es **Require status checks to pass before merging** con `tests-backend`. No conviene tener las dos a la vez sin necesidad.

---

## 4. Procedimiento para comprobar que Railway espera a CI

El objetivo es que Railway no despliegue un commit hasta que `tests-backend` esté en verde.

**Orden obligatorio (confirmar los nombres de menú en la UI actual):**
1. Activar primero **"Wait for CI"**.
2. Después cambiar o confirmar la rama de origen (sección 1). Cambiar la rama a `main` puede disparar un despliegue inmediato de `main`.
3. Solo entonces, verificar el procedimiento de esta sección.

1. En Railway, en el servicio `orbisBackend`, activar la opción **"Wait for CI"** (etiqueta a confirmar en la UI actual) para la rama `main`.
2. Si la interfaz no muestra esa opción, no asumir que existe: el gate en Railway **no está activo** y solo queda protegido el merge en GitHub (punto 3).
3. Abrir un PR hacia `main` con un cambio que pase los tests (por ejemplo, un cambio de documentación).
4. Esperar a que `tests-backend` termine en verde.
5. Hacer el merge y comprobar en Railway, en la pestaña **Deployments**, la hora de inicio del despliegue.

Criterio: la hora de inicio del despliegue es **posterior** a la hora de finalización del check `tests-backend` en GitHub (pestaña Checks del PR o de la ejecución del workflow). Si el despliegue empieza antes de que el check termine, el gate no funciona.

---

## 5. Prueba controlada con PR fallido

Hacer esta prueba en una rama desechable. **La rama no debe fusionarse nunca con `main`.**

1. Crear una rama desechable **desde `main`**, por ejemplo `prueba-gate-ci`. No crearla desde `fase1-seguridad-bugs`.
2. En esa rama, añadir un test deliberadamente roto en `src/test/` (por ejemplo `assertTrue(false)`). Hacerlo solo cuando ningún otro agente o persona esté editando `src/`.
3. Hacer push de `prueba-gate-ci` y abrir un PR hacia `main`.
4. Comprobar que:
   - el check `tests-backend` aparece en rojo;
   - el botón de merge está bloqueado por el ruleset (punto 3);
   - Railway no crea ningún despliegue para ese PR ni para ese commit (pestaña **Deployments** sin entradas nuevas; si la opción de espera está activa, no debe iniciarse ningún despliegue).
5. Cerrar el PR **sin fusionar**.
6. Borrar la rama `prueba-gate-ci` en GitHub.

**Evidencia a guardar:**
- Captura de la pestaña Checks del PR con `tests-backend` en rojo.
- Captura del panel de merge con el botón bloqueado y el requisito `tests-backend` visible.
- Captura de la lista de **Deployments** de Railway antes y después de la prueba, en la que se vea que no hay despliegue nuevo para el commit del PR.
- Fecha y hora de cada captura, y el enlace al PR cerrado.

### Caso de éxito (obligatorio para el criterio e)

Este caso es obligatorio: sin él, el criterio (e) de la sección 6 no puede verificarse. Un PR normal con tests en verde debe permitir el merge, y Railway debe desplegar ese mismo commit solo después de que `tests-backend` termine en verde (procedimiento del punto 4).

---

## 6. Criterios de éxito

El gate funciona si y solo si se cumplen **todos** estos puntos:

- [ ] **(a)** El ruleset bloquea el merge de un PR rojo hacia `main`. Verificado: [ ]
- [ ] **(b)** Railway no despliega commits cuyo check `tests-backend` no esté en verde. Verificado: [ ]
- [ ] **(c)** Los deploys automáticos de PR están desactivados para `orbisBackend`. Verificado: [ ]
- [ ] **(d)** La rama de origen del servicio en Railway es `main`. Verificado: [ ]
- [ ] **(e)** El despliegue tras un merge en verde se inicia después de que el check `tests-backend` termine. Verificado: [ ]

Hasta que los cinco puntos estén marcados, el gate se considera **no verificado**.

---

## 7. Qué NO protege esta configuración

- **Redeploys manuales.** Un redeploy desde el panel de Railway, un `railway up` o un rollback a un deployment anterior no pasan por CI.
- **Pushes directos a `main`** si la regla de rama no está activa, o si alguien está en la bypass list. Un push directo puede saltarse el PR y el check.
- **Cambios en las variables de Railway.** Modificar variables (por ejemplo `DB_URL` o `SPRING_PROFILES_ACTIVE`) no pasa por CI y puede alterar el comportamiento en producción.
- **Dependencia de la opción "Wait for CI".** Si la opción no existe, cambia de nombre o está desactivada, Railway puede desplegar sin esperar. En ese caso solo queda protegido el merge en GitHub.
- **El flag de saltar tests en `railway.toml`.** Mientras el `buildCommand` contenga `-Dmaven.test.skip=true`, el build de Railway no ejecuta tests, aunque el gate de CI esté activo. Ver la sección siguiente.
- **Ramas distintas de `main`.** Si Railway despliega también otras ramas, esas no están cubiertas por la regla.
- **Tests que no cubren el problema.** El gate solo ejecuta `./mvnw -B test`: no hace análisis estático, no escanea dependencias ni comprueba secretos.
- **Secretos y configuración real de producción.** El workflow usa H2 y no prueba la configuración real de PostgreSQL de Railway.
- **Commits ya desplegados.** La regla solo afecta a merges futuros.

---

## Cambio propuesto en `railway.toml` (requiere aprobación del propietario)

**No aplicar sin aprobación.** Este documento solo describe el cambio; el archivo `railway.toml` no se ha modificado.

Línea actual:

```
buildCommand = "chmod +x ./mvnw && ./mvnw -Dmaven.test.skip=true -B clean install"
```

Cambio propuesto (quitar `-Dmaven.test.skip=true`):

```
buildCommand = "chmod +x ./mvnw && ./mvnw -B clean install"
```

Motivo: con `-Dmaven.test.skip=true` el build de Railway no compila ni ejecuta tests, así que puede generar un JAR desplegable aunque los tests fallen. Si el gate de CI no se aplica (por ejemplo, un despliegue manual o "Wait for CI" desactivado), el build de Railway no tiene ninguna comprobación. Quitar el flag hace que Railway también ejecute los tests antes de desplegar.

Coste: el build de Railway tarda más, y los tests deben pasar también en su entorno.

**Checklist antes de quitar el flag (no verificado; comprobar en Railway):**
- [ ] Revisar si las variables del servicio (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `SPRING_PROFILES_ACTIVE`) están expuestas al paso de build.
- [ ] Si `SPRING_PROFILES_ACTIVE=prod` y `DB_URL` son visibles durante el build, los tests pueden usar el perfil de producción: fallarían o tocarían la base de datos. En ese caso **no quitar el flag** hasta que los tests se configuren para ejecutarse con el perfil por defecto durante el build.
