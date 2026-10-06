-- ══════════════════════════════════════════════════════
-- SmartCane AI — Datos iniciales (seed)
-- Se ejecuta automáticamente si se configura en application.yaml
-- Para Railway: ejecutar manualmente una sola vez
-- ══════════════════════════════════════════════════════

-- Catálogo inicial de tipos de obstáculo
-- (Las etiquetas coinciden con las que puede retornar Google Vision)
INSERT INTO tipo_obstaculo (etiqueta, nivel_peligro) VALUES
    ('Stairs',      'ALTO'),
    ('Step',        'ALTO'),
    ('Curb',        'ALTO'),
    ('Door',        'MEDIO'),
    ('Wall',        'MEDIO'),
    ('Pillar',      'MEDIO'),
    ('Chair',       'BAJO'),
    ('Table',       'BAJO'),
    ('Person',      'BAJO'),
    ('Bicycle',     'BAJO'),
    ('Car',         'ALTO'),
    ('Motorcycle',  'ALTO')
ON CONFLICT (etiqueta) DO NOTHING;
