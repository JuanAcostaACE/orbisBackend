-- ══════════════════════════════════════════════════════
-- SmartCane AI — Datos iniciales (seed)
-- Se ejecuta automáticamente si se configura en application.yaml
-- Para Railway: ejecutar manualmente una sola vez
-- ══════════════════════════════════════════════════════

-- Catálogo inicial de tipos de obstáculo
-- (Las etiquetas coinciden con las que puede retornar Google Vision)
INSERT INTO obstacle_types (label, danger_level) VALUES
    ('Stairs',      'HIGH'),
    ('Step',        'HIGH'),
    ('Curb',        'HIGH'),
    ('Door',        'MEDIUM'),
    ('Wall',        'MEDIUM'),
    ('Pillar',      'MEDIUM'),
    ('Chair',       'LOW'),
    ('Table',       'LOW'),
    ('Person',      'LOW'),
    ('Bicycle',     'LOW'),
    ('Car',         'HIGH'),
    ('Motorcycle',  'HIGH')
ON CONFLICT (label) DO NOTHING;
