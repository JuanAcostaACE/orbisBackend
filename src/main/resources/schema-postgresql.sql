-- ══════════════════════════════════════════════════════
-- SmartCane AI — Script de inicialización PostgreSQL
-- Ejecutar en Railway/Supabase antes del primer deploy
-- (Hibernate con ddl-auto: update también crea las tablas,
--  pero este script siembra los datos iniciales)
-- ══════════════════════════════════════════════════════

-- Tabla de tipos de obstáculo (catálogo)
CREATE TABLE IF NOT EXISTS tipo_obstaculo (
    id           BIGSERIAL PRIMARY KEY,
    etiqueta     VARCHAR(100) NOT NULL UNIQUE,
    nivel_peligro VARCHAR(10) CHECK (nivel_peligro IN ('ALTO', 'MEDIO', 'BAJO'))
);

-- Tabla principal de eventos del SmartCane
CREATE TABLE IF NOT EXISTS registro_eventos (
    id               BIGSERIAL PRIMARY KEY,
    modo             VARCHAR(20) NOT NULL CHECK (modo IN ('PASIVO', 'ACTIVO')),
    distancia_cm     DOUBLE PRECISION,
    imagen_url       TEXT,
    etiquetasia      TEXT,
    tipo_obstaculo_id BIGINT REFERENCES tipo_obstaculo(id),
    fecha_hora       TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Índice para consultas por fecha (el frontend muestra recientes primero)
CREATE INDEX IF NOT EXISTS idx_registro_eventos_fecha ON registro_eventos(fecha_hora DESC);
CREATE INDEX IF NOT EXISTS idx_registro_eventos_modo  ON registro_eventos(modo);
