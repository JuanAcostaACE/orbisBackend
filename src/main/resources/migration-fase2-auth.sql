-- ════════════════════════════════════════════════════════════════════
-- ORBIS — Migración Fase 2: autenticación y eventos huérfanos
-- Archivo: migration-fase2-auth.sql
-- Cuándo ejecutar: UNA SOLA VEZ en Railway, antes de activar autenticación
-- ════════════════════════════════════════════════════════════════════
--
-- CONTEXTO:
--   Antes de la Fase 2, la tabla registro_eventos no tenía la columna
--   usuario_id (propietario). Hibernate con ddl-auto=update la añade
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
--      o via psql: psql $DATABASE_URL < migration-fase2-auth.sql
--   3. Verificar con: SELECT COUNT(*) FROM registro_eventos WHERE usuario_id IS NULL;
--      (debe devolver 0 después de correr el script)
-- ════════════════════════════════════════════════════════════════════

BEGIN;

-- ── Paso 0: Verificar que la columna existe ──────────────────────
-- (Si Hibernate aún no corrió, crear la columna manualmente)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'registro_evento'
          AND column_name = 'usuario_id'
    ) THEN
        ALTER TABLE registro_evento ADD COLUMN usuario_id BIGINT REFERENCES usuario(id);
        RAISE NOTICE 'Columna usuario_id creada en registro_evento';
    ELSE
        RAISE NOTICE 'Columna usuario_id ya existe (ok)';
    END IF;
END $$;

-- ── Paso 1: Agregar índice de propietario si no existe ────────────
CREATE INDEX IF NOT EXISTS idx_registro_evento_usuario_id
    ON registro_evento(usuario_id);

CREATE INDEX IF NOT EXISTS idx_registro_evento_fecha
    ON registro_evento(fecha_hora DESC);

-- ── Paso 2: Asignar eventos huérfanos al usuario admin ────────────
-- EDITAR: reemplazar 'admin@orbis.com' con el correo real del admin
DO $$
DECLARE
    v_admin_email  TEXT    := 'admin@orbis.com';  -- ← CAMBIAR AQUÍ
    v_admin_id     BIGINT;
    v_count_antes  BIGINT;
    v_count_asig   BIGINT;
BEGIN
    -- Contar huérfanos antes
    SELECT COUNT(*) INTO v_count_antes
    FROM registro_evento WHERE usuario_id IS NULL;

    RAISE NOTICE '--- Eventos sin propietario (usuario_id IS NULL): %', v_count_antes;

    IF v_count_antes = 0 THEN
        RAISE NOTICE 'No hay eventos huérfanos. Nada que migrar.';
        RETURN;
    END IF;

    -- Buscar usuario admin
    SELECT id INTO v_admin_id
    FROM usuario
    WHERE correo = v_admin_email
    LIMIT 1;

    IF v_admin_id IS NULL THEN
        RAISE EXCEPTION
            'Usuario % no encontrado en la tabla usuario. '
            'Regístralo primero via POST /auth/registro y luego ejecuta este script.',
            v_admin_email;
    END IF;

    RAISE NOTICE 'Asignando % eventos al usuario % (id=%)',
        v_count_antes, v_admin_email, v_admin_id;

    -- Asignación idempotente (WHERE usuario_id IS NULL evita re-asignar)
    UPDATE registro_evento
    SET usuario_id = v_admin_id
    WHERE usuario_id IS NULL;

    GET DIAGNOSTICS v_count_asig = ROW_COUNT;

    RAISE NOTICE 'Eventos migrados: %', v_count_asig;
    RAISE NOTICE '--- Verificación post-migración: eventos sin propietario restantes: %',
        (SELECT COUNT(*) FROM registro_evento WHERE usuario_id IS NULL);
END $$;

-- ── Paso 3: Agregar constraint NOT NULL si todos están asignados ──
-- (Opcional y seguro: solo aplica si no quedan huérfanos)
DO $$
BEGIN
    IF (SELECT COUNT(*) FROM registro_evento WHERE usuario_id IS NULL) = 0 THEN
        -- Verificar si la restricción ya existe
        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_name = 'registro_evento'
              AND column_name = 'usuario_id'
              AND is_nullable = 'NO'
        ) THEN
            ALTER TABLE registro_evento
                ALTER COLUMN usuario_id SET NOT NULL;
            RAISE NOTICE 'Restricción NOT NULL aplicada en usuario_id';
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
    u.correo                         AS propietario,
    COUNT(r.id)                      AS total_eventos,
    MIN(r.fecha_hora)                AS primer_evento,
    MAX(r.fecha_hora)                AS ultimo_evento
FROM registro_evento r
LEFT JOIN usuario u ON r.usuario_id = u.id
GROUP BY u.correo
ORDER BY total_eventos DESC;
