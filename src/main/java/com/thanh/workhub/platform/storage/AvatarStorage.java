package com.thanh.workhub.platform.storage;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.cloudinary.Cloudinary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import com.thanh.workhub.platform.web.ApiException;
import com.thanh.workhub.platform.web.ErrorCode;

@Service
public class AvatarStorage {
    private static final Logger log = LoggerFactory.getLogger(AvatarStorage.class);
    private final ObjectProvider<Cloudinary> cloudinary;

    public AvatarStorage(ObjectProvider<Cloudinary> cloudinary) {
        this.cloudinary = cloudinary;
    }

    public record UploadedAvatar(String publicId, String url) {}

    public UploadedAvatar upload(byte[] bytes, Long userId) {
        var client = cloudinary.getIfAvailable();
        if (client == null) {
            throw new ApiException(ErrorCode.STORAGE_DISABLED);
        }
        String publicId = "workhub/avatars/" + userId + "/" + UUID.randomUUID();
        try {
            var result = client.uploader().upload(bytes, Map.of(
                    "public_id", publicId, "resource_type", "image", "overwrite", false,
                    "allowed_formats", List.of("jpg", "jpeg", "png")));
            return new UploadedAvatar((String) result.get("public_id"), (String) result.get("secure_url"));
        } catch (IOException | RuntimeException exception) {
            // Không trả thông tin xác thực hoặc lỗi nội bộ của nhà cung cấp cho client.
            throw new ApiException(ErrorCode.STORAGE_UPLOAD_FAILED);
        }
    }

    public void deleteQuietly(String publicId) {
        if (publicId == null) return;
        try {
            var client = cloudinary.getIfAvailable();
            if (client != null) client.uploader().destroy(publicId, Map.of("invalidate", true));
        } catch (IOException | RuntimeException exception) {
            log.warn("Không thể xóa avatar Cloudinary; cần thử dọn dẹp lại");
        }
    }
}
