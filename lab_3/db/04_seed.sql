-- ШАГ 4. Тестовые данные.
-- Выполнять от postgres в БД lab_db после 03_privileges.sql:
--   psql -U postgres -d lab_db -f db/04_seed.sql
-- Скрипт можно запускать повторно: он очищает таблицы и сбрасывает счётчики id.
-- Данные согласованы с правилами предметной области (статусы, диапазоны, разброс).


TRUNCATE measurements, analyses, samples, analysis_methods, users RESTART IDENTITY CASCADE;

INSERT INTO users (name, email) VALUES
    ('Migel Ivanovka',   'migel.ivanovka@gmail.com'),     -- id 1
    ('Anna Smirnova', 'anna.smirnova@gmail.com'),   -- id 2
    ('Egor Rudin',   'egor.rudin@gmail.com');     -- id 3 (без проб: можно удалять)

INSERT INTO analysis_methods (name, allowed_material, min_value, max_value, max_spread) VALUES
    ('Glucose',       'blood', 0,  100, 4),   -- id 1
    ('Hemoglobin',    'blood', 50, 200, 5),   -- id 2
    ('Total protein', 'urine', 0,  20,  1);   -- id 3

INSERT INTO samples (material, customer_id, received_at, status) VALUES
    ('blood', 1, now() - interval '3 days', 'IN_PROGRESS'),  -- id 1: два исследования, одно ещё идёт
    ('blood', 2, now() - interval '5 days', 'COMPLETED'),    -- id 2: всё завершено
    ('urine', 2, now() - interval '1 day',  'RECEIVED'),     -- id 3: исследований нет
    ('blood', 1, now() - interval '2 hours','RECEIVED');     -- id 4: исследований нет

INSERT INTO analyses (sample_id, method_id, status, average_value) VALUES
    (1, 1, 'IN_PROGRESS', 5.25),   -- id 1: среднее по подтверждённым 5.0 и 5.5
    (1, 2, 'IN_PROGRESS', NULL),   -- id 2: измерений ещё нет
    (2, 1, 'COMPLETED',   4.9);    -- id 3: среднее по 4.8 и 5.0

INSERT INTO measurements (analysis_id, measured_value, measured_at, confirmed) VALUES
    (1, 5.0,  now() - interval '2 days',  TRUE),    -- id 1
    (1, 5.5,  now() - interval '2 days',  TRUE),    -- id 2
    (1, 12.0, now() - interval '1 day',   FALSE),   -- id 3: выброс, подтвердить нельзя (разброс > 4)
    (3, 4.8,  now() - interval '4 days',  TRUE),    -- id 4
    (3, 5.0,  now() - interval '4 days',  TRUE);    -- id 5
