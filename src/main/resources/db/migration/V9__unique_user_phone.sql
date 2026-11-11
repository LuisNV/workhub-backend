ALTER TABLE app_users
ADD CONSTRAINT uk_app_users_phone UNIQUE (phone);
