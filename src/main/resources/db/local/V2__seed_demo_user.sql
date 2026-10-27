INSERT INTO app_users (id, password_hash, full_name, email, phone, address)
VALUES (1, crypt('Admin@123456', gen_salt('bf', 12)), 'Nguyễn Văn Thành', 'thanh.nguyen@example.com', '090 123 4567', 'Hà Nội, Việt Nam');
ON CONFLICT (id) DO UPDATE
SET password_hash = EXCLUDED.password_hash;