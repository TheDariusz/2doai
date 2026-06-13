-- Seed the 11 FR-007 life domains in canonical order. Codes are stable
-- UPPER_SNAKE values mirrored by the LifeDomain enum (CategorySyncCheck asserts
-- they agree at boot). Immutable versioned migration — the list is fixed in MVP.
-- Pure INSERT — backward-compatible (expand-only).
INSERT INTO category (code, name_pl, display_order) VALUES
    ('HEALTH',        'Zdrowie',                                              1),
    ('FINANCE',       'Finanse',                                              2),
    ('CAREER',        'Kariera i rozwój zawodowy',                            3),
    ('EDUCATION',     'Edukacja i rozwój osobisty',                           4),
    ('RELATIONSHIPS', 'Relacje',                                              5),
    ('HOME',          'Dom i otoczenie',                                      6),
    ('LEISURE',       'Czas wolny i hobby',                                   7),
    ('ADMIN',         'Sprawy formalne i administracyjne',                    8),
    ('SAFETY',        'Bezpieczeństwo i przygotowanie na sytuacje awaryjne',  9),
    ('TRANSPORT',     'Transport i mobilność',                               10),
    ('INNER_GROWTH',  'Rozwój wewnętrzny / wartości',                        11);
