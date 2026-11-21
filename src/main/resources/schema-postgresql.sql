-- ══════════════════════════════════════════════════════
-- SmartCane AI — Script de inicialización PostgreSQL
-- Ejecutar en Railway/Supabase antes del primer deploy
-- (Hibernate con ddl-auto: update también crea las tablas,
--  pero este script siembra los datos iniciales)
--
-- Base de datos existente con nombres en español (registro_eventos,
-- tipo_obstaculo, usuarios): ejecutar antes migration-english-names.sql.
-- ══════════════════════════════════════════════════════

-- Tabla de tipos de obstáculo (catálogo)
CREATE TABLE IF NOT EXISTS obstacle_types (
    id           BIGSERIAL PRIMARY KEY,
    label        VARCHAR(100) NOT NULL UNIQUE,
    danger_level VARCHAR(10) CHECK (danger_level IN ('HIGH', 'MEDIUM', 'LOW'))
);

-- Tabla principal de eventos del SmartCane
CREATE TABLE IF NOT EXISTS event_records (
    id               BIGSERIAL PRIMARY KEY,
    mode             VARCHAR(20) NOT NULL CHECK (mode IN ('PASSIVE', 'ACTIVE')),
    distance_cm      DOUBLE PRECISION,
    image_url        TEXT,
    ai_labels        TEXT,
    obstacle_type_id BIGINT REFERENCES obstacle_types(id),
    created_at       TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Índice para consultas por fecha (el frontend muestra recientes primero)
CREATE INDEX IF NOT EXISTS idx_event_records_created_at ON event_records(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_event_records_mode       ON event_records(mode);
