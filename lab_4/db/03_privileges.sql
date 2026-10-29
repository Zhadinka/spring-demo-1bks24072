-- ШАГ 3. Права ограниченного пользователя приложения.
-- Выполнять от postgres в БД lab_db ПОСЛЕ 02_schema.sql:
--   psql -U postgres -d lab_db -f db/03_privileges.sql
-- Принцип наименьших привилегий: приложению нужны только DML-операции.


-- Схема public: посторонним нельзя создавать объекты, приложение только пользуется схемой.
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
GRANT USAGE ON SCHEMA public TO lab_readwrite;

-- Чтение и изменение данных. Нет TRUNCATE, REFERENCES, TRIGGER и любых DDL-прав.
-- UPDATE также нужен для SELECT ... FOR UPDATE (блокировки строк в бизнес-операциях).
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO lab_readwrite;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO lab_readwrite;

-- То же для таблиц, которые postgres создаст позже.
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO lab_readwrite;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO lab_readwrite;

-- Проверка (необязательно): права роли lab_app.
-- SELECT table_name, privilege_type FROM information_schema.role_table_grants
--  WHERE grantee = 'lab_readwrite' ORDER BY table_name, privilege_type;
