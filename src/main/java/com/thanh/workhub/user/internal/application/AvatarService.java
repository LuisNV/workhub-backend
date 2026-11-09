package com.thanh.workhub.user.internal.application;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Set;
import javax.imageio.ImageIO;

import com.thanh.workhub.platform.storage.AvatarStorage;
import com.thanh.workhub.user.internal.api.dto.UserProfileResponse;
import com.thanh.workhub.user.internal.persistence.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AvatarService {
    private final UserRepository users;
    private final AvatarStorage storage;
    private final UserService profiles;

    public AvatarService(UserRepository users, AvatarStorage storage, UserService profiles) {
        this.users = users;
        this.storage = storage;
        this.profiles = profiles;
    }

    @Transactional
    public UserProfileResponse updateAvatar(Long userId, MultipartFile file) {
        byte[] bytes = validate(file);
        var user = users.findForAvatarUpdate(userId).orElseThrow(() -> new UserNotFoundException(userId));
        String oldPublicId = user.getAvatarPublicId();
        var uploaded = storage.upload(bytes, userId);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED) storage.deleteQuietly(oldPublicId);
                else if (status == STATUS_ROLLED_BACK) storage.deleteQuietly(uploaded.publicId());
            }
        });
        user.updateAvatar(uploaded.publicId(), uploaded.url());
        users.saveAndFlush(user);
        return profiles.getProfile(userId);
    }

    private byte[] validate(MultipartFile file) {
        if (file.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ảnh không được rỗng");
        if (file.getSize() > 5L * 1024 * 1024) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Ảnh tối đa 5 MB");
        }
        try {
            byte[] bytes = file.getBytes();
            try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers = ImageIO.getImageReaders(input);
                if (!readers.hasNext()) throw invalidImage();
                var reader = readers.next();
                try {
                    reader.setInput(input);
                    if (!Set.of("JPEG", "PNG").contains(reader.getFormatName().toUpperCase(java.util.Locale.ROOT))) {
                        throw invalidImage();
                    }
                    if ((long) reader.getWidth(0) * reader.getHeight(0) > 20_000_000L) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ảnh tối đa 20 megapixel");
                    }
                    if (reader.read(0) == null) throw invalidImage();
                } finally {
                    reader.dispose();
                }
            }
            return bytes;
        } catch (IOException exception) {
            throw invalidImage();
        }
    }

    private ResponseStatusException invalidImage() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chỉ chấp nhận ảnh JPEG hoặc PNG hợp lệ");
    }
}
