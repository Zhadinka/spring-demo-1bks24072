-- ШАГ 1. Роли и база данных.
-- Выполнять от суперпользователя (postgres), подключившись к БД "postgres".
--   psql -U postgres -f db/01_roles_and_database.sql
-- В pgAdmin: оператор CREATE DATABASE выполните отдельно от остальных
-- (он не может работать внутри транзакции, а Query Tool шлёт скрипт целиком).


-- Групповая роль: хранит набор прав приложения, войти под ней нельзя.
CREATE ROLE lab_readwrite NOLOGIN;

-- Пользователь приложения. Он не владелец таблиц, не суперпользователь,
-- не может создавать/удалять таблицы и другие БД. Он только читает и меняет данные.
-- Необходимо заменить пароль и положите тот же в .env (DB_PASSWORD).
CREATE ROLE lab_app LOGIN PASSWORD 'change_me'
    NOSUPERUSER NOCREATEDB NOCREATEROLE;
GRANT lab_readwrite TO lab_app;   -- lab_app наследует права группы lab_readwrite

CREATE DATABASE lab_db ENCODING 'UTF8';

-- Подключаться к БД могут только явно допущенные роли.
REVOKE ALL ON DATABASE lab_db FROM PUBLIC;
GRANT CONNECT ON DATABASE lab_db TO lab_app;
