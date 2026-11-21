-- ════════════════════════════════════════════════════════════════════
-- ORBIS — Migración Fase 2: autenticación y eventos huérfanos
-- Archivo: migration-phase2-auth.sql
-- Cuándo ejecutar: UNA SOLA VEZ en Railway, antes de activar autenticación
-- ════════════════════════════════════════════════════════════════════
--
-- CONTEXTO:
--   Antes de la Fase 2, la tabla event_records no tenía la columna
--   user_id (propietario). Hibernate con ddl-auto=update la añade
--   automáticamente como NULL (nullable) para no romper los registros
--   existentes. Este script los asigna a un usuario admin de forma segura.
--
-- SEGURIDAD:
--   - Todas las operaciones son idempotentes (pueden correr más de una vez)
--   - Nunca elimina registros; solo asigna propietario
--   - Verifica que el usuario de destino exista antes de asignar
--   - Usa una transacción explícita; si algo falla, hace ROLLBACK completo
--
-- INSTRUCCIONES:
--   1. Configurar ADMIN_EMAIL abajo con el correo de un usuario ya registrado
--   2. Ejecutar en Railway: Railway Dashboard > Connect > Query
--      o via psql: psql $DATABASE_URL < migration-phase2-auth.sql
--   3. Verificar con: SELECT COUNT(*) FROM event_records WHERE user_id IS NULL;
--      (debe devolver 0 después de correr el script)
-- ════════════════════════════════════════════════════════════════════

BEGIN;

-- ── Paso 0: Verificar que la columna existe ──────────────────────
-- (Si Hibernate aún no corrió, crear la columna manualmente)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'event_records'
          AND column_name = 'user_id'
    ) THEN
        ALTER TABLE event_records ADD COLUMN user_id BIGINT REFERENCES users(id);
        RAISE NOTICE 'Columna user_id creada en event_records';
    ELSE
        RAISE NOTICE 'Columna user_id ya existe (ok)';
    END IF;
END $$;

-- ── Paso 1: Agregar índice de propietario si no existe ────────────
CREATE INDEX IF NOT EXISTS idx_event_records_user_id
    ON event_records(user_id);

CREATE INDEX IF NOT EXISTS idx_event_records_created_at
    ON event_records(created_at DESC);

-- ── Paso 2: Asignar eventos huérfanos al usuario admin ────────────
-- EDITAR: reemplazar 'admin@orbis.com' con el correo real del admin
DO $$
DECLARE
    v_admin_email  TEXT    := 'admin@orbis.com';  -- ← CAMBIAR AQUÍ
    v_admin_id     BIGINT;
    v_count_before  BIGINT;
    v_count_assigned   BIGINT;
BEGIN
    -- Contar huérfanos antes
    SELECT COUNT(*) INTO v_count_before
    FROM event_records WHERE user_id IS NULL;

    RAISE NOTICE '--- Eventos sin propietario (user_id IS NULL): %', v_count_before;

    IF v_count_before = 0 THEN
        RAISE NOTICE 'No hay eventos huérfanos. Nada que migrar.';
        RETURN;
    END IF;

    -- Buscar usuario admin
    SELECT id INTO v_admin_id
    FROM users
    WHERE email = v_admin_email
    LIMIT 1;

    IF v_admin_id IS NULL THEN
        RAISE EXCEPTION
            'Usuario % no encontrado en la tabla users. '
            'Regístralo primero via POST /auth/register y luego ejecuta este script.',
            v_admin_email;
    END IF;

    RAISE NOTICE 'Asignando % eventos al usuario % (id=%)',
        v_count_before, v_admin_email, v_admin_id;

    -- Asignación idempotente (WHERE user_id IS NULL evita re-asignar)
    UPDATE event_records
    SET user_id = v_admin_id
    WHERE user_id IS NULL;

    GET DIAGNOSTICS v_count_assigned = ROW_COUNT;

    RAISE NOTICE 'Eventos migrados: %', v_count_assigned;
    RAISE NOTICE '--- Verificación post-migración: eventos sin propietario restantes: %',
        (SELECT COUNT(*) FROM event_records WHERE user_id IS NULL);
END $$;

-- ── Paso 3: Agregar constraint NOT NULL si todos están asignados ──
-- (Opcional y seguro: solo aplica si no quedan huérfanos)
DO $$
BEGIN
    IF (SELECT COUNT(*) FROM event_records WHERE user_id IS NULL) = 0 THEN
        -- Verificar si la restricción ya existe
        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = 'public'
              AND table_name = 'event_records'
              AND column_name = 'user_id'
              AND is_nullable = 'NO'
        ) THEN
            ALTER TABLE event_records
                ALTER COLUMN user_id SET NOT NULL;
            RAISE NOTICE 'Restricción NOT NULL aplicada en user_id';
        ELSE
            RAISE NOTICE 'Restricción NOT NULL ya estaba aplicada (ok)';
        END IF;
    ELSE
        RAISE WARNING 'Aún hay eventos sin propietario. NOT NULL no aplicado.';
    END IF;
END $$;

COMMIT;

-- ── Verificación final ────────────────────────────────────────────
SELECT
    u.email                         AS owner,
    COUNT(r.id)                      AS total_events,
    MIN(r.created_at)                AS first_event,
    MAX(r.created_at)                AS last_event
FROM event_records r
LEFT JOIN users u ON r.user_id = u.id
GROUP BY u.email
ORDER BY total_events DESC;
