# Historial de git: token de dispositivo

Documento de decision. Analiza si conviene quitar el valor del token de dispositivo del historial de git.
Relacionado con `docs/operacion-seguridad.md` (rotacion, seccion 2).

Ningun valor del token aparece en este documento. Los hashes son de commits, no secretos.

**Estado: no se ha reescrito ni se ha modificado el historial.** Todos los comandos usados son de lectura
(`git log`, `git show`, `git grep`, `git branch`, `git remote`, `git merge-base`, `git rev-list`).

---

## 1. Hechos verificados (solo lectura)

Fecha del analisis: 2026-11-11. Rama de trabajo: `fase1-seguridad-bugs`, HEAD `ea4615d` en ese momento.

### Ramas y remotos

- Ramas locales: `main` (cabeza `584f629`) y `fase1-seguridad-bugs`.
- Ramas remotas conocidas localmente: `origin/main` y `origin/HEAD` (apunta a `origin/main`).
- No hay referencia `origin/fase1-seguridad-bugs` en el clon local. Eso no prueba que la rama no exista en
  GitHub: las referencias remotas locales pueden estar desactualizadas, y no se ha hecho `git fetch`.
- Remoto configurado: `origin` (GitHub), para lectura y escritura.

### Ancestros de `main`

| Commit | Mensaje (resumen) | Ancestro de `main` |
|---|---|---|
| `812923d` | feat(fase2): paginacion, device token ESP32, firmware no-bloqueante | Si |
| `899775e` | feat(fase2.5+3): DeviceTokenFilter timing-safe... | Si |
| `584f629` | feat(fase5): tests unitarios... (cabeza de `main`) | Si (es la cabeza) |
| `bb2faf5` | test(hardware): consistencia del firmware sin exigir el token en el repo | No (solo en `fase1-seguridad-bugs`) |
| `dd10e25` | ci/firmware: CI con chmod de mvnw, token fuera del sketch... | No (solo en `fase1-seguridad-bugs`) |

### Commits cuyo diff anade o quita el valor

Busqueda con `git log --all -G` usando el valor literal del fallback (no se imprime aqui):

- `812923d` y `584f629`: en `main`. Anaden el valor en `hardware/wokwi/sketch.ino`.
- `bb2faf5` y `dd10e25`: solo en `fase1-seguridad-bugs`. Quitan una ocurrencia (en el test Java y en el sketch).

Con `git log --all -S` (cambio en el numero de ocurrencias) salen los mismos commits, sin `dd10e25`.

### Commits cuyo arbol contiene el valor

Revisados los 28 commits alcanzables desde cualquier referencia (`git rev-list --all`). El valor aparece en
el arbol de **15 commits**:

- En `main` (3): `812923d`, `899775e`, `584f629`.
- Solo en `fase1-seguridad-bugs` (12): `c61a1e7`, `ea4615d`, `f3edf0b`, `bb2faf5`, `fdee800`, `0ca3f58`,
  `7d2ed37`, `15c07f6`, `f86e0fe`, `dd10e25`, `e6b2754`, `fcd764d`.

### Por que el valor sigue en la rama actual

En `ea4615d` (HEAD) el valor aparece una vez en `hardware/wokwi/sketch.ino`: es el fallback `#define` que se
usa cuando no existe `secrets.h`. El valor del fallback es **el mismo** que el del historial. Por tanto:

- Quitar el valor del historial no basta si el fallback sigue en el sketch: cada commit futuro lo volveria a
  incluir.
- Cualquier merge de `fase1-seguridad-bugs` a `main` llevaria el valor a la cabeza de `main`.

Esto es un hallazgo nuevo respecto a la version anterior de `operacion-seguridad.md`, que lo describia como
un placeholder inocuo. Ya no es inocuo si Railway lo tuviera configurado.

---

## 2. Que significa "exposicion" aqui

- Quien haya clonado el repositorio antes de cualquier reescritura tiene el valor en su copia local.
- GitHub conserva los objetos de los commits mientras existan referencias o hasta que se recolecten. Forks,
  vistas de commits por hash, diffs de PRs antiguos y caches de CI pueden seguir mostrando el valor.
- El valor solo da acceso como dispositivo (`ROLE_DEVICE`): puede enviar eventos asociados al correo del
  propietario. No da acceso a sesiones ni a la base de datos.
- **Si Railway no tiene ese valor, no hay acceso posible con el.** Si lo tiene, hay que rotar. La rotacion es
  lo que cierra la exposicion, no la reescritura.

---

## 3. Opciones

### Opcion (a): rotar y dejar el historial

- Se rota `APP_DEVICE_TOKEN` (ver runbook en `docs/operacion-seguridad.md`).
- Lo que sigue expuesto: el valor antiguo en el historial de todos los clones, forks y en GitHub. Un atacante
  con ese valor no puede usarlo, porque el backend ya no lo acepta, **siempre que** la verificacion del paso 5
  devuelva `401` o `403`.
- Lo que no protege: si alguien conserva el valor y Railway vuelve a tenerlo, el valor vuelve a funcionar.
  Por eso la verificacion no es opcional.
- Coste: ninguno en el historial. No rompe nada.
- Riesgo residual: bajo, mientras Railway no use el valor historico. Hay que retirar el fallback del sketch
  para que el valor no siga apareciendo en commits nuevos.

### Opcion (b): reescribir el historial y hacer force push

Herramientas: `git filter-repo` (recomendada) o BFG Repo-Cleaner. Reemplazaria el valor por un texto neutro en
todos los commits, incluidos `812923d`, `584f629` y los de `fase1-seguridad-bugs`.

Implicaciones:

- **Todos los hashes cambian** desde el primer commit afectado hasta la cabeza de cada rama. Las referencias a
  hashes en issues, PRs, documentos y en este repositorio dejan de ser validas.
- **Todos los clones y forks deben volver a clonarse** o hacer reset a la nueva historia. Un `git pull`
  normal falla o duplica commits.
- **Los PRs abiertos se rompen**: su base cambia y los diffs pierden contexto. Hay que reabrirlos o rebasarlos.
- **Las protecciones de rama pueden bloquear el force push** en `main`. Hay que desactivarlas temporalmente
  y volver a activarlas, con registro de quien lo hizo.
- **Otras personas o agentes con trabajo local** (en este mismo repositorio hay cambios sin commit de otros
  agentes) perderian su punto de partida. Hay que coordinar un momento en que nadie trabaje en el repositorio.
- GitHub puede seguir mostrando objetos antiguos por hash durante un tiempo, hasta que se recolecten. Limpiarlos
  del todo puede requerir soporte de GitHub (**no verificado** desde este entorno).
- Hay que purgar caches de CI y cualquier copia del historial fuera de GitHub.
- La reescritura no sustituye a la rotacion: si el valor aun esta activo en Railway, sigue funcionando.

**Requiere aprobacion explicita y por escrito del propietario, y un plan coordinado** que incluya: ventana de
tiempo, lista de ramas a reescribir, lista de PRs a reabrir, aviso a todos los clones y comprobacion posterior
con `git log --all -S` (el valor no debe aparecer).

### Opcion (c): borrar el repositorio y recrearlo

- Pierde issues, PRs, estrellas, configuracion de CI, protecciones de rama y permisos. El historial anterior
  sigue existiendo en cualquier clon y fork.
- No cierra la exposicion. **No recomendada.**

---

## 4. Recomendacion

**Opcion (a), con dos condiciones:**

1. Ejecutar la rotacion completa del runbook y confirmar que el valor antiguo devuelve `401` o `403` en
   produccion (paso 5). Sin esa confirmacion, la rotacion no esta cerrada.
2. Retirar el fallback con el valor real de `hardware/wokwi/sketch.ino` y sustituirlo por un error de
   compilacion si falta `secrets.h`. Asi el valor no vuelve a entrar en commits nuevos ni llega a `main` por
   merge. Es un cambio de codigo: requiere aprobacion.

**La opcion (b) no se recomienda como primer paso.** Solo tiene sentido si el propietario decide que el
historial debe quedar limpio por politica, y aun asi requiere aprobacion explicita y un plan coordinado. No se
ha ejecutado ningun comando de reescritura, ni se ejecutara sin esa aprobacion.

---

## 5. Pendiente y no verificado

- Si `fase1-seguridad-bugs` existe en GitHub y en que estado: no se ha consultado el remoto (no hay `git fetch`).
- Si Railway tiene hoy el valor historico: no se ha consultado Railway desde este entorno.
- Si GitHub conserva los objetos de los commits afectados tras una reescritura: no verificado.
- La lista de 15 commits con el valor en el arbol sale de la busqueda local en este clon. Otros clones o
  refs no descargadas podrian contener mas.
