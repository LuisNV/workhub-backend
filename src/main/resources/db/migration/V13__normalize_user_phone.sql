-- Chuẩn hóa số điện thoại về dạng chỉ có chữ số ("090 123 4567" -> "0901234567"),
-- cùng định dạng mà đăng ký / sửa hồ sơ / đăng nhập dùng, để unique constraint chặn được trùng thật sự.
UPDATE app_users
SET phone = regexp_replace(phone, '\s', '', 'g')
WHERE phone ~ '\s';
