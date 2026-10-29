-- ШАГ 6. Ручное назначение ролей и блокировка учетных записей.
-- Сначала зарегистрируйте учетную запись через POST /api/auth/register,
-- затем выполните один из запросов ниже от postgres в БД lab_db.

-- Повысить существующую учетную запись до администратора:
-- UPDATE app_accounts SET account_role = 'ADMIN', customer_id = NULL WHERE lower(email) = lower('admin@example.com');

-- Назначить сотрудника лаборатории:
-- UPDATE app_accounts SET account_role = 'LAB_TECHNICIAN', customer_id = NULL WHERE lower(email) = lower('lab@example.com');

-- Заблокировать учетную запись (она перестанет проходить аутентификацию):
-- UPDATE app_accounts SET status = 'LOCKED' WHERE lower(email) = lower('user@example.com');

-- Отключить учетную запись:
-- UPDATE app_accounts SET status = 'DISABLED' WHERE lower(email) = lower('user@example.com');

-- Разблокировать/включить учетную запись:
-- UPDATE app_accounts SET status = 'ACTIVE' WHERE lower(email) = lower('user@example.com');
