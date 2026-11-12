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

---

## 10. Resultados de verificacion (2026-11-11)

Entorno: PostgreSQL **16.15** nativo (servicio `postgresql-16`, Windows), base `orbis_test`, rol `orbis_test`,
escuchando en `localhost:5432`. JDK 21.0.13 (`JAVA_HOME` de Microsoft). Perfil por defecto con
`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `DB_DRIVER=org.postgresql.Driver` cargados desde `.env.postgres`
(sin imprimir valores). Commit base: `73b9b45`. Sin Docker; no se uso `docker-compose.yml`.

| # | Paso | Resultado |
|---|---|---|
| 1 | Conectividad y tablas previas | **PASS**. `SELECT version()` = PostgreSQL 16.15 (Visual C++ build 1944, 64 bits). `orbis_test` sin tablas antes de arrancar. |
| 2 | Arranque y `/health` | **PASS**. Log: `HikariPool-1 - Added connection`, `Database driver: PostgreSQL JDBC Driver`, `Database dialect: PostgreSQLDialect`, `Started ApiApplication in 14.161 seconds`. `GET /health` = `{"status":"UP"}`. |
| 2b | DDL al arrancar | **PASS** (confirma la advertencia). El log muestra `drop table if exists registro_eventos/tipo_obstaculo/usuarios cascade` seguido de los `create table`. Es `create-drop`: en el arranque borra y recrea. |
| 3 | Tablas y columnas tras arrancar | **PASS** (ver tabla de abajo). Tablas: `usuarios`, `registro_eventos`, `tipo_obstaculo`. No hay otras. |
| 3b | Columna `etiquetasIA` | **FAIL** en la nota 8 de este documento. La columna real es `etiquetasia` (sin guion bajo), no `etiquetas_ia`. El DDL de Hibernate es `etiquetasia TEXT`. |
| 4 | Scripts SQL ejecutados al arrancar | **PASS**. Ninguno. El log no contiene lineas de `schema-`, `data-` ni `migration-`, y el perfil por defecto no define `spring.sql.init`. |
| 5 | Flujo de autenticacion | **PASS**. `POST /auth/registro` = 201. `POST /auth/login` = 200 con cookie `ORBIS_SID`. `GET /auth/me` = 200. `POST /auth/logout` = 200. `GET /auth/me` = 401. En `usuarios` hay exactamente 1 fila para el correo de prueba, `activo = t`, hash con prefijo `$2a$` (BCrypt). |
| 6 | Flujo de dispositivo | **Parcial**. `APP_DEVICE_TOKEN` y `APP_DEVICE_OWNER_EMAIL` no estan en `.env.postgres`, asi que el flujo de token esta deshabilitado y **no se ha verificado el camino con token (NO VERIFICADO)**. Con sesion: `POST /api/v1/eventos` PASIVO = 201 (`id` 1, `etiquetasIA` null). `POST` ACTIVO con `etiquetasIA` null = 201 (`id` 2, `etiquetasIA` `SIN_IMAGEN`). Las filas de `registro_eventos` coinciden con las respuestas: `modo`, `distancia_cm = 45`, `etiquetasia`, `usuario_id` apuntando al usuario de la sesion. Se uso un segundo usuario de prueba, porque la contrasena del primero no se conservo. |
| 7 | Revision de `migration-fase2-auth.sql` (solo lectura) | **FAIL** (estatico, no ejecutado). Ver discrepancias abajo. |
| 8 | Parada y tablas tras parar | **Parcial**. La parada fue forzada (`Stop-Process -Force`), porque el proceso no admite cierre limpio (no hay actuator). Las tres tablas **siguen existiendo** tras la parada: con parada forzada no se ejecutan los hooks de cierre de Spring. El drop por `create-drop` al parar **NO VERIFICADO** con cierre limpio; el drop al arrancar **si** se verifico (paso 2b). |

### Tablas y columnas observadas (tras el arranque)

- `usuarios`: `id` bigint PK identity; `nombre` varchar(60) not null; `correo` varchar(120) not null unique; `hash_contrasena` varchar(255) not null; `activo` boolean not null; `fecha_creacion` timestamp not null.
- `registro_eventos`: `id` bigint PK identity; `modo` varchar(255) not null; `distancia_cm` double precision; `imagen_url` text; `etiquetasia` text; `tipo_obstaculo_id` bigint FK a `tipo_obstaculo`; `usuario_id` bigint FK a `usuarios`; `fecha_hora` timestamp not null.
- `tipo_obstaculo`: `id` bigint PK identity; `etiqueta` varchar(255) not null unique; `nivel_peligro` varchar(255).

No hay restricciones CHECK (esperado, nota 10).

### Discrepancias de `migration-fase2-auth.sql` (paso 7)

Nombres de tabla incorrectos. Las tablas reales son `registro_eventos` y `usuarios`:

- Linea 35: `table_name = 'registro_evento'`.
- Linea 38: `ALTER TABLE registro_evento ... REFERENCES usuario(id)`. Dos errores en la misma linea: `registro_evento` y `usuario`.
- Lineas 46-47: `ON registro_evento(usuario_id)`.
- Lineas 49-50: `ON registro_evento(fecha_hora DESC)`.
- Linea 63: `FROM registro_evento`.
- Linea 74: `FROM usuario`.
- Linea 89: `UPDATE registro_evento`.
- Linea 97: `FROM registro_evento`.
- Linea 104: `FROM registro_evento`.
- Linea 108: `table_name = 'registro_evento'`.
- Linea 112: `ALTER TABLE registro_evento`.
- Linea 131: `FROM registro_evento r`.
- Linea 132: `LEFT JOIN usuario u`.

Las columnas `usuario_id`, `fecha_hora`, `correo` y `id` existen con esos nombres. Aviso adicional: la consulta de la linea 35 y la de la linea 108 filtran solo por `table_name` sin `table_schema`. Conviene corregirlas antes de cualquier ejecucion. La migracion **no se ha ejecutado**.

### No verificado

- Perfil `prod` con PostgreSQL real (`ddl-auto: update`). Requiere un arranque con `SPRING_PROFILES_ACTIVE=prod`.
- Camino de dispositivo con `APP_DEVICE_TOKEN`.
- Drop de `create-drop` al parar con cierre limpio.
- Ejecucion de `schema-postgresql.sql`, `data-postgresql.sql` y `migration-fase2-auth.sql`.
- Docker (no se usó).
