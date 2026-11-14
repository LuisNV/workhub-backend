package com.thanh.workhub.user;

/** Dữ liệu tối thiểu module auth cần để xác thực, không lộ entity User ra ngoài module. */
public record UserCredentials(Long id, String email, String passwordHash, boolean emailVerified) {

    @Override
    public String toString() {
        return "UserCredentials[id=" + id + ", passwordHash=redacted]";
    }
}
