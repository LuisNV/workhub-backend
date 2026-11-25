package com.thanh.workhub.auth.internal.domain;

/**
 * Thông tin thiết bị lúc đăng nhập.
 * <ul>
 *   <li>{@code deviceId}: ID ổn định do app tự sinh, dùng để nhận ra cùng một máy (có thể null).</li>
 *   <li>{@code deviceName}/{@code platform}: chỉ để hiển thị, người dùng đổi tên máy cũng không sao.</li>
 *   <li>{@code userAgent}/{@code ipAddress}: lấy từ request.</li>
 * </ul>
 * Giá trị quá dài được cắt cho vừa cột.
 */
public record DeviceInfo(String deviceId, String deviceName, String platform, String userAgent, String ipAddress) {

    public static final DeviceInfo UNKNOWN = new DeviceInfo(null, null, null, null, null);

    public DeviceInfo {
        deviceId = clean(deviceId, 64);
        deviceName = clean(deviceName, 100);
        platform = clean(platform, 50);
        userAgent = clean(userAgent, 512);
        ipAddress = clean(ipAddress, 64);
    }

    private static String clean(String value, int max) {
        if (value == null) return null;
        String stripped = value.strip();
        if (stripped.isEmpty()) return null;
        return stripped.length() <= max ? stripped : stripped.substring(0, max);
    }
}
