# PostgreSQL local para pruebas del backend

> **Estado de verificacion: NO VERIFICADO.** En el entorno donde se preparo esta guia
> no habia Docker, `psql` ni nada escuchando en el puerto 5432 (ver seccion 1).
> Ningun comando de este documento se ha ejecutado contra PostgreSQL. Los comandos
> son los previstos; hay que ejecutarlos y anotar el resultado.

Esta guia sirve para levantar un PostgreSQL 16 local y probar el backend con un motor real,
en lugar de H2. **Solo para una base local de pruebas.** No apunta a Railway ni a ninguna
base de produccion.

---

## 1. Estado de herramientas (comprobado)

| Herramienta | Resultado |
|---|---|
| `docker --version` | No encontrado |
| `docker compose version` | No encontrado |
| `psql --version` | No encontrado |
| Java | 17.0.12 instalado. El `pom.xml` y el CI usan **Java 21** |
| Maven | `mvn` no instalado; el proyecto trae `mvnw` / `mvnw.cmd` |
| Puerto 5432 | Sin servicio escuchando (`Test-NetConnection` = False) |

Con Docker o PostgreSQL nativo instalados, el resto de pasos es reproducible.

---

## 2. Requisitos

- Docker Desktop (recomendado), **o** PostgreSQL 16 nativo.
- JDK 21 (el proyecto compila con `java.version` 21; con JDK 17 el build fallara).
- Git Bash o PowerShell.

---

## 3. Configuracion

1. Copiar la plantilla y generar una contrasena local (no reutilizar ninguna de produccion):

   ```powershell
   Copy-Item .env.postgres.example .env.postgres
   python -c "import secrets; print(secrets.token_urlsafe(24))"
   ```

   Pegar el valor en `POSTGRES_PASSWORD` y `DB_PASSWORD` dentro de `.env.postgres`.
   El archivo esta en `.gitignore`; comprobarlo con `git check-ignore -v .env.postgres`.

2. Crear `docker-compose.yml` en la raiz (no esta en el repo; contenido previsto):

   ```yaml
   services:
     postgres:
       image: postgres:16.4
       env_file: .env.postgres
       ports:
         - "127.0.0.1:5432:5432"   # solo loopback
       volumes:
         - orbis_pg_data:/var/lib/postgresql/data
       healthcheck:
         test: ["CMD-SHELL", "pg_isready -U $$POSTGRES_USER -d $$POSTGRES_DB"]
         interval: 5s
         timeout: 3s
         retries: 20
   volumes:
     orbis_pg_data:
   ```

   Nota: la etiqueta `16.4` es una version concreta de la rama 16; confirmar que existe
   en Docker Hub antes de usarla.

---

## 4. Levantar PostgreSQL

**Docker Desktop:**

```powershell
docker compose --env-file .env.postgres up -d
docker compose ps            # esperar a estado "healthy"
```

**PostgreSQL nativo (alternativa):**

```powershell
# Crear rol y base locales (pedira la contrasena)
psql -U postgres -c "CREATE ROLE orbis_test LOGIN PASSWORD '<CONTRASENA_LOCAL>';"
psql -U postgres -c "CREATE DATABASE orbis_test OWNER orbis_test;"
```

---

## 5. Arrancar el backend contra PostgreSQL

El perfil `prod` fija `org.postgresql.Driver`, pero el perfil por defecto acepta
`DB_DRIVER`, asi que sirve para probar PostgreSQL sin tocar produccion:

```powershell
# Cargar variables del .env.postgres en la sesion (sin imprimirlas)
Get-Content .env.postgres | Where-Object { $_ -match '^[A-Z_]+=' } | ForEach-Object {
  $k, $v = $_ -split '=', 2; Set-Item -Path "env:$k" -Value $v }
$env:DB_URL = 'jdbc:postgresql://localhost:5432/orbis_test'
./mvnw spring-boot:run
```

**Advertencia importante:** el perfil por defecto usa `ddl-auto: create-drop`
(`application.yaml`). Eso **borra y recrea todas las tablas al arrancar y al parar**.
Es aceptable en `orbis_test`, pero nunca apuntar este perfil a una base con datos reales.
El perfil `prod` usa `ddl-auto: update`.

---

## 6. Comprobaciones a ejecutar (pendientes)

**(a) Arranque y creacion de tablas.** La aplicacion arranca sin error y aparece
`Started ...` en el log.

**(b) Tablas y columnas que crea Hibernate.**

```powershell
docker compose exec postgres psql -U orbis_test -d orbis_test -c "\dt"
docker compose exec postgres psql -U orbis_test -d orbis_test -c "\d usuarios"
docker compose exec postgres psql -U orbis_test -d orbis_test -c "\d registro_eventos"
docker compose exec postgres psql -U orbis_test -d orbis_test -c "\d tipo_obstaculo"
```

Esperado segun las entidades: `usuarios`, `registro_eventos`, `tipo_obstaculo`.
Confirmar en `\d registro_eventos` si la columna se llama `etiquetas_ia` (ver seccion 8).

**(c) Scripts SQL de `src/main/resources`.** Segun `application.yaml` **no hay**
`spring.sql.init` ni `schema-locations`, asi que ninguno de estos scripts se ejecuta al arrancar:
`schema-postgresql.sql`, `data-postgresql.sql`, `migration-fase2-auth.sql`.
Para comprobar si corren sin error, hay que ejecutarlos a mano **solo en `orbis_test`**
y solo despues de revisarlos (ver seccion 8).

**(d) Flujo de autenticacion con curl** (sustituir `<PASS>`; no imprimir contrasenas en el historial):

```powershell
$base = 'http://localhost:8080'
curl.exe -s -c cookies.txt -H "Content-Type: application/json" -d '{"nombre":"Prueba","correo":"prueba@local.test","contrasena":"<PASS>"}' "$base/auth/registro"
curl.exe -s -b cookies.txt -c cookies.txt -H "Content-Type: application/json" -d '{"correo":"prueba@local.test","contrasena":"<PASS>"}' "$base/auth/login"
curl.exe -s -b cookies.txt "$base/auth/me"
curl.exe -s -b cookies.txt -c cookies.txt -X POST "$base/auth/logout"
```

Nota: los campos JSON son los de `RegistroRequest` (`nombre`, `correo`, `contrasena`) y `LoginRequest`
(`correo`, `contrasena`), verificados en el codigo. `/auth/**` esta exento de CSRF (`SecurityConfig`).
Con `prod` la cookie es `Secure`, asi que con HTTP local no se reenvia; usar el perfil por defecto.

Anotar en esta seccion la fecha, la version de imagen, las tablas creadas y la salida de cada paso.

---

## 7. Parar y limpiar

```powershell
docker compose down          # conserva el volumen
docker compose down -v       # BORRA el volumen (solo datos de prueba)
```

---

## 8. Diferencias H2 vs PostgreSQL

Clave de verificacion: **[V-code]** comprobado leyendo el codigo o la configuracion;
**[V-run]** comprobado ejecutando; **[NV]** no verificado.

1. **Perfil por defecto: H2 y `create-drop`.** [V-code] `application.yaml` usa H2 en memoria
   por defecto y `ddl-auto: create-drop`. Con PostgreSQL via `DB_URL` el mismo perfil borra tablas.
2. **Driver.** [V-code] El perfil `prod` hardcodea `org.postgresql.Driver`; el por defecto
   acepta `DB_DRIVER`. [NV] Que el arranque funcione con esa combinacion.
3. **Sensibilidad a mayusculas.** [NV] PostgreSQL pliega identificadores sin comillas a minusculas
   y H2 a mayusculas. [V-code] Las entidades usan nombres en minusculas y snake_case
   (`usuarios`, `registro_eventos`, `tipo_obstaculo`), asi que Hibernate no depende de esto.
4. **Palabras reservadas.** [V-code] No hay nombres de entidad o columna que choquen
   (p. ej. `user`, `order`, `group`) en `model/`. Los SQL de `src/main/resources` tampoco los usan.
5. **Tipos.** [V-code] `activo` es `boolean`, `distanciaCm` es `Double`, `fechaHora` es `LocalDateTime`.
   [NV] Equivalencia exacta en PostgreSQL (`double precision`, `timestamp(6)`) vs H2.
6. **TEXT y VARCHAR.** [V-code] `imagenUrl` y `etiquetasIA` usan `columnDefinition = "TEXT"`.
   `nombre` tiene `length = 60` y `correo` `length = 120`; `modo` no tiene longitud (varchar 255 por defecto).
   El limite de 2000 caracteres de `etiquetas` esta solo en el DTO (`EventoRequest`), no en la BD.
   [NV] Como H2 y PostgreSQL aplican cada tipo en la practica.
7. **Identidad.** [V-code] Las entidades usan `GenerationType.IDENTITY`. El `schema-postgresql.sql`
   usa `BIGSERIAL`. [NV] DDL exacto que genera Hibernate en PostgreSQL.
8. **Nombre de columna `etiquetas_ia`.** [V-code] El campo `etiquetasIA` se traduce por convencion
   de Spring a `etiquetas_ia`. El `schema-postgresql.sql` define `etiquetasia`, asi que no coincide.
9. **Tabla `registro_evento` y `usuario` en la migracion.** [V-code] `migration-fase2-auth.sql` usa
   `registro_evento` y `usuario`; las entidades mapean `registro_eventos` y `usuarios`. Ejecutar la
   migracion fallaria en `REFERENCES usuario(id)` y en `UPDATE registro_evento`.
10. **Restricciones CHECK.** [V-code] `schema-postgresql.sql` define `CHECK` en `modo` y
    `nivel_peligro`. Las entidades no tienen `@Check`, asi que Hibernate no las crea.
11. **Default de `fecha_hora`.** [V-code] El schema usa `DEFAULT NOW()`; la entidad usa `@CreationTimestamp`.
12. **Datos semilla.** [V-code] `data-postgresql.sql` usa `ON CONFLICT (etiqueta)`, que funciona
    porque `TipoObstaculo.etiqueta` tiene `unique = true`. No se ejecuta al arrancar.
13. **Migracion destructiva.** [V-code] `migration-fase2-auth.sql` hace `UPDATE` sobre
    `registro_evento` y `ALTER ... SET NOT NULL`. Es una operacion sobre datos reales: no ejecutar
    en pruebas sin revisarla y sin copia de seguridad.
14. **Java.** [V-code] `pom.xml` y CI usan Java 21; el equipo local tiene 17. [NV] Fallo de build con 17.
15. **Cobertura de pruebas.** [V-code] Los tests y el CI usan H2 (`.github/workflows/ci.yml`,
    `src/test`). PostgreSQL no se prueba en CI.

---

## 9. Pendientes para el responsable

- Instalar Docker Desktop (o PostgreSQL 16 nativo) y confirmar la etiqueta de imagen.
- Instalar JDK 21 para compilar el proyecto.
- Ejecutar las secciones 5 y 6 y anotar resultados aqui.
- Decidir si la migracion `migration-fase2-auth.sql` se corrige antes de cualquier ejecucion.
