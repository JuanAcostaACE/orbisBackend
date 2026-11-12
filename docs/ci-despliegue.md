# CI como requisito de despliegue

Este documento describe cómo hacer que el job `tests-backend` (workflow `.github/workflows/ci.yml`) sea un requisito real antes de desplegar el backend en Railway.

Estado: **preparado, no activado**. El workflow está en el repositorio, pero la protección de rama en GitHub y la espera de CI en Railway deben configurarse a mano por el propietario. Nada de esto se ha aplicado todavía.

---

## Qué hace el workflow

- Se ejecuta en `push` y `pull_request` para las ramas `main` y `fase1-seguridad-bugs`.
- Job `tests-backend` (nombre visible `tests-backend`) en `ubuntu-latest`, con timeout de 20 minutos.
- JDK 21 Temurin, caché de Maven, y `./mvnw -B test` sin flags de salto de tests.
- Permisos mínimos (`contents: read`). No usa secretos ni despliega nada.
- Si fallan los tests, sube los reportes de Surefire como artefacto.

---

## (a) Hacer el job obligatorio en GitHub

El nombre que hay que seleccionar es exactamente `tests-backend`. GitHub solo lo ofrece en el selector después de que el job se haya ejecutado al menos una vez en el repositorio, así que hay que hacer primero un push a `fase1-seguridad-bugs` (o abrir un PR) con el workflow ya subido.

1. En GitHub, abrir el repositorio y entrar en **Settings > Rules > Rulesets**.
2. Pulsar **New ruleset > New branch ruleset**.
3. Nombre: por ejemplo `proteger-main`. **Enforcement status**: `Active`.
4. En **Target branches**, pulsar **Add target > Include default branch** (o incluir explícitamente `main`).
5. Activar **Require a pull request before merging**. El número de aprobaciones requeridas puede ser 0 si trabajas en solitario; si quieres exigir revisión, pon 1.
6. Activar **Require status checks to pass**:
   - Activar **Require branches to be up to date before merging** (recomendado).
   - Pulsar **Add checks** y seleccionar `tests-backend`.
7. Activar **Block force pushes** (recomendado).
8. En **Bypass list**, no añadir a nadie si quieres que se aplique también a administradores. Si la lista está vacía, la regla vale para todos, incluido el propietario.
9. Pulsar **Create**.

Verificación: en un PR hacia `main`, el panel de merge debe mostrar "Required: tests-backend" como pendiente hasta que pase, y el botón de merge debe quedar bloqueado si falla.

Si ya existe una protección clásica de rama (**Settings > Branches**) sobre `main`, la regla equivalente es **Require status checks to pass before merging** con `tests-backend`. No conviene tener las dos a la vez sin necesidad.

---

## (b) Esperar a CI en Railway

**Importante:** no se ha podido confirmar la etiqueta exacta de esta opción en la interfaz actual de Railway. Hay que verificarla en el panel.

1. En Railway, abrir el proyecto y el servicio `orbisBackend`.
2. Ir a **Settings** del servicio y buscar la sección de despliegue relacionada con el repositorio de GitHub (source / GitHub repo).
3. Buscar una opción llamada **"Wait for CI"** (o una etiqueta equivalente que indique esperar a los checks de GitHub antes de desplegar) y activarla.
4. Confirmar que el servicio está conectado a la rama `main` como rama de despliegue.
5. Si la interfaz no muestra esa opción, no asumir que existe: el gate en Railway no está activo y solo se protege el merge en GitHub (punto a).

---

## (c) Cambio recomendado en `railway.toml` (requiere aprobación del propietario)

**No aplicar sin aprobación.** Este documento solo describe el cambio; el archivo `railway.toml` no se ha modificado.

Línea actual:

```
buildCommand = "chmod +x ./mvnw && ./mvnw -Dmaven.test.skip=true -B clean install"
```

Cambio propuesto (quitar `-Dmaven.test.skip=true`):

```
buildCommand = "chmod +x ./mvnw && ./mvnw -B clean install"
```

Motivo: con `-Dmaven.test.skip=true` el build de Railway no compila ni ejecuta tests, así que un build puede generar un JAR desplegable aunque los tests fallen. Si el gate de CI no se aplica (por ejemplo, un despliegue manual o "Wait for CI" desactivado), el build de Railway no tiene ninguna comprobación. Quitar el flag hace que Railway también ejecute los tests antes de desplegar.

Coste: el build de Railway tarda más, y los tests deben pasar también en su entorno (no tiene `DB_URL`, así que usan H2).

---

## (d) Cómo comprobar que el gate funciona

Hacer esta prueba en una rama desechable, **que no debe fusionarse nunca con `main`**.

1. Crear una rama desechable desde `fase1-seguridad-bugs`, por ejemplo `prueba-gate-ci`.
2. Añadir un test deliberadamente roto en `src/test/` (por ejemplo `assertTrue(false)`). Esto implica tocar `src/`, así que hacerlo solo cuando no haya otro agente editando esa carpeta, y en la rama desechable.
3. Hacer push y abrir un PR de `prueba-gate-ci` hacia `main`.
4. Comprobar que:
   - el check `tests-backend` aparece en rojo;
   - el botón de merge está bloqueado por la regla de rama;
   - en Railway no aparece ningún despliegue nuevo para ese commit (la pestaña **Deployments** no debe mostrar uno, o debe quedar esperando/cancelado según la opción de espera).
5. Cerrar el PR sin fusionar y borrar la rama `prueba-gate-ci`.

Para comprobar también el caso de éxito: un PR normal con tests en verde debe permitir el merge, y Railway debe desplegar solo después de que `tests-backend` pase.

---

## (e) Qué NO protege esta configuración

- **Despliegues manuales.** Un `railway up`, un redeploy desde el panel o un rollback a un deployment anterior no pasan por CI.
- **Pushes directos a `main`** si la regla de rama no está activa, o si el propietario está en la bypass list. Un push directo al commit de `main` puede saltarse el PR y el check.
- **Dependencia de Railway.** El gate depende de que Railway respete el check de GitHub ("Wait for CI"). Si la opción no existe, cambia de nombre o está desactivada, Railway puede desplegar sin esperar. El `buildCommand` sin `-Dmaven.test.skip` (punto c) es una segunda barrera solo si se aprueba.
- **Ramas distintas de `main`.** Si Railway despliega también otras ramas, esas no están cubiertas por la regla.
- **Tests que no cubren el problema.** El gate solo ejecuta `./mvnw -B test`: no hace análisis estático, no escanea dependencias ni comprueba secretos.
- **Secretos de producción.** El workflow usa H2 y no prueba la configuración real de PostgreSQL de Railway.
- **Commits ya desplegados.** La regla solo afecta a merges futuros.
