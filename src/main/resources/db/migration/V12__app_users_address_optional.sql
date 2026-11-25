-- Màn đăng ký không hỏi địa chỉ; người dùng bổ sung sau ở màn sửa hồ sơ.
ALTER TABLE app_users
    ALTER COLUMN address DROP NOT NULL;
