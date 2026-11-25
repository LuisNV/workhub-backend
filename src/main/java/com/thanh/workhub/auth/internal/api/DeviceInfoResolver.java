package com.thanh.workhub.auth.internal.api;

import com.thanh.workhub.auth.internal.domain.DeviceInfo;

import jakarta.servlet.http.HttpServletRequest;

/** Ghép thông tin thiết bị app gửi lên với User-Agent và IP của request. */
final class DeviceInfoResolver {
    private DeviceInfoResolver() {
    }

    static DeviceInfo from(HttpServletRequest request, String deviceId, String deviceName, String platform) {
        // Chạy sau reverse proxy thì cần bật server.forward-headers-strategy để getRemoteAddr() là IP thật.
        return new DeviceInfo(deviceId, deviceName, platform, request.getHeader("User-Agent"), request.getRemoteAddr());
    }
}
