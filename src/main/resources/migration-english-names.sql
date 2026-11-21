-- ════════════════════════════════════════════════════════════════════
-- ORBIS — Migración: nombres de tablas, columnas y valores en inglés
-- Archivo: migration-english-names.sql
-- Cuándo ejecutar: UNA SOLA VEZ en Railway, ANTES de desplegar la versión
--                  del backend con nombres en inglés.
-- ════════════════════════════════════════════════════════════════════
--
-- CONTEXTO:
--   Las entidades JPA pasaron a nombres en inglés:
--     usuarios         → users          (nombre, correo, hash_contrasena, activo, fecha_creacion)
--     tipo_obstaculo   → obstacle_types (etiqueta, nivel_peligro)
--     registro_eventos → event_records  (modo, distancia_cm, imagen_url, etiquetasia,
--                                        tipo_obstaculo_id, usuario_id, fecha_hora)
--   y los valores de dominio:
--     modo:          PASIVO/ACTIVO   → PASSIVE/ACTIVE
--     nivel_peligro: ALTO/MEDIO/BAJO → HIGH/MEDIUM/LOW
--
--   Con ddl-auto=update, si el backend nuevo arranca ANTES de este script,
--   Hibernate crea tablas nuevas vacías y los datos quedan en las antiguas.
--   Por eso este script debe correr primero.
--
-- SEGURIDAD:
--   - Idempotente: cada paso comprueba si ya se aplicó.
--   - Nunca elimina filas; solo renombra y traduce valores.
--   - Una sola transacción: si algo falla, ROLLBACK completo.
--   - Si ya existe una tabla nueva (p. ej. Hibernate la creó vacía), el script
--     se detiene con un error en lugar de mezclar datos.
--
-- INSTRUCCIONES:
--   1. Hacer un backup (Railway Dashboard > Backups, o pg_dump).
--   2. Ejecutar: psql $DATABASE_URL < migration-english-names.sql
--   3. Desplegar el backend nuevo.
-- ════════════════════════════════════════════════════════════════════

BEGIN;

-- ── Paso 1: renombrar tablas ──────────────────────────────────────
DO $$
DECLARE
    pair TEXT[];
BEGIN
    FOREACH pair SLICE 1 IN ARRAY ARRAY[
        ['usuarios',         'users'],
        ['tipo_obstaculo',   'obstacle_types'],
        ['registro_eventos', 'event_records']
    ] LOOP
        IF to_regclass('public.' || pair[1]) IS NOT NULL THEN
            IF to_regclass('public.' || pair[2]) IS NOT NULL THEN
                RAISE EXCEPTION
                    'Existen a la vez % y %. Revisar a mano (¿arrancó el backend nuevo antes de migrar?).',
                    pair[1], pair[2];
            END IF;
            EXECUTE format('ALTER TABLE %I RENAME TO %I', pair[1], pair[2]);
            RAISE NOTICE 'Tabla % → %', pair[1], pair[2];
        ELSE
            RAISE NOTICE 'Tabla % no existe (ya migrada u omitida)', pair[1];
        END IF;
    END LOOP;
END $$;

-- ── Paso 2: quitar CHECK antiguos (bloquearían la traducción de valores) ──
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN
        SELECT c.conrelid::regclass AS tbl, c.conname
        FROM pg_constraint c
        WHERE c.contype = 'c'
          AND c.conrelid IN (to_regclass('public.event_records'), to_regclass('public.obstacle_types'))
          AND (pg_get_constraintdef(c.oid) LIKE '%PASIVO%' OR pg_get_constraintdef(c.oid) LIKE '%ALTO%')
    LOOP
        EXECUTE format('ALTER TABLE %s DROP CONSTRAINT %I', r.tbl, r.conname);
        RAISE NOTICE 'CHECK % eliminado de %', r.conname, r.tbl;
    END LOOP;
END $$;

-- ── Paso 3: renombrar columnas ────────────────────────────────────
DO $$
DECLARE
    col TEXT[];
BEGIN
    FOREACH col SLICE 1 IN ARRAY ARRAY[
        ['users',          'nombre',            'name'],
        ['users',          'correo',            'email'],
        ['users',          'hash_contrasena',   'password_hash'],
        ['users',          'activo',            'active'],
        ['users',          'fecha_creacion',    'created_at'],
        ['obstacle_types', 'etiqueta',          'label'],
        ['obstacle_types', 'nivel_peligro',     'danger_level'],
        ['event_records',  'modo',              'mode'],
        ['event_records',  'distancia_cm',      'distance_cm'],
        ['event_records',  'imagen_url',        'image_url'],
        ['event_records',  'etiquetasia',       'ai_labels'],
        ['event_records',  'etiquetas_ia',      'ai_labels'],
        ['event_records',  'tipo_obstaculo_id', 'obstacle_type_id'],
        ['event_records',  'usuario_id',        'user_id'],
        ['event_records',  'fecha_hora',        'created_at']
    ] LOOP
        IF EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = 'public' AND table_name = col[1] AND column_name = col[2])
           AND NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = 'public' AND table_name = col[1] AND column_name = col[3]) THEN
            EXECUTE format('ALTER TABLE %I RENAME COLUMN %I TO %I', col[1], col[2], col[3]);
            RAISE NOTICE 'Columna %.% → %', col[1], col[2], col[3];
        END IF;
    END LOOP;
END $$;

-- ── Paso 4: traducir valores de dominio ───────────────────────────
DO $$
BEGIN
    IF to_regclass('public.event_records') IS NOT NULL THEN
        UPDATE event_records SET mode = 'PASSIVE' WHERE mode = 'PASIVO';
        UPDATE event_records SET mode = 'ACTIVE'  WHERE mode = 'ACTIVO';
        UPDATE event_records SET ai_labels = 'NO_IMAGE' WHERE ai_labels = 'SIN_IMAGEN';
    END IF;
    IF to_regclass('public.obstacle_types') IS NOT NULL THEN
        UPDATE obstacle_types SET danger_level = 'HIGH'   WHERE danger_level = 'ALTO';
        UPDATE obstacle_types SET danger_level = 'MEDIUM' WHERE danger_level = 'MEDIO';
        UPDATE obstacle_types SET danger_level = 'LOW'    WHERE danger_level = 'BAJO';
    END IF;
END $$;

-- ── Paso 5: índices con nombres nuevos ────────────────────────────
ALTER INDEX IF EXISTS idx_registro_eventos_fecha      RENAME TO idx_event_records_created_at;
ALTER INDEX IF EXISTS idx_registro_evento_fecha       RENAME TO idx_event_records_created_at_2;
ALTER INDEX IF EXISTS idx_registro_eventos_modo       RENAME TO idx_event_records_mode;
ALTER INDEX IF EXISTS idx_registro_evento_usuario_id  RENAME TO idx_event_records_user_id;

-- ── Paso 6: secuencias de los id (los DEFAULT siguen apuntando a ellas tras renombrar) ──
ALTER SEQUENCE IF EXISTS usuarios_id_seq         RENAME TO users_id_seq;
ALTER SEQUENCE IF EXISTS tipo_obstaculo_id_seq   RENAME TO obstacle_types_id_seq;
ALTER SEQUENCE IF EXISTS registro_eventos_id_seq RENAME TO event_records_id_seq;

COMMIT;

-- ── Verificación final ────────────────────────────────────────────
SELECT mode, COUNT(*) AS total FROM event_records GROUP BY mode ORDER BY mode;
