package com.thanh.workhub.user;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;

import com.thanh.workhub.platform.storage.AvatarStorage;
import com.thanh.workhub.platform.storage.AvatarStorage.UploadedAvatar;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "app.jwt.secret-base64=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=")
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Testcontainers
class AvatarDatabaseTest {
    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    @Autowired com.thanh.workhub.user.internal.application.AvatarService avatars;
    @MockitoBean AvatarStorage storage;

    private MockMultipartFile image() throws Exception {
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", output);
        return new MockMultipartFile("file", "avatar.png", "image/png", output.toByteArray());
    }

    @Test void uploadsForJwtUserAndReplacesOldImageAfterCommit() throws Exception {
        jdbc.update("UPDATE app_users SET avatar_public_id = 'old-avatar' WHERE id = 1");
        given(storage.upload(any(byte[].class), eq(1L)))
                .willReturn(new UploadedAvatar("new-avatar", "https://example.com/avatar.png"));
        try {
            mvc.perform(multipart("/api/v1/users/me/avatar").file(image())
                            .param("userId", "999").with(jwt().jwt(j -> j.subject("1"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value(1))
                    .andExpect(jsonPath("$.data.avatarUrl").value("https://example.com/avatar.png"));
            assertThat(jdbc.queryForObject("SELECT avatar_public_id FROM app_users WHERE id = 1", String.class))
                    .isEqualTo("new-avatar");
            verify(storage).deleteQuietly("old-avatar");
            mvc.perform(get("/api/v1/auth/me").with(jwt().jwt(j -> j.subject("1")
                            .claim("email", "thanh.nguyen@example.com"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.avatarUrl").value("https://example.com/avatar.png"));
        } finally {
            jdbc.update("UPDATE app_users SET avatar_public_id = NULL, avatar_url = NULL WHERE id = 1");
        }
    }

    @Test void rejectsUnauthenticatedUpload() throws Exception {
        mvc.perform(multipart("/api/v1/users/me/avatar").file(image()))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(storage);
    }

    @Test void removesNewImageOnDatabaseRollback() throws Exception {
        given(storage.upload(any(byte[].class), eq(1L)))
                .willReturn(new UploadedAvatar("rolled-back-avatar", "https://example.com/rollback.png"));
        var file = image();
        new org.springframework.transaction.support.TransactionTemplate(transactions).executeWithoutResult(status -> {
            avatars.updateAvatar(1L, file);
            status.setRollbackOnly();
        });
        verify(storage).deleteQuietly("rolled-back-avatar");
        assertThat(jdbc.queryForObject("SELECT avatar_public_id FROM app_users WHERE id = 1", String.class))
                .isNull();
    }

    @Test void rejectsFakeImageAndEmptyFile() throws Exception {
        for (byte[] bytes : new byte[][] {new byte[0], "not an image".getBytes()}) {
            mvc.perform(multipart("/api/v1/users/me/avatar")
                            .file(new MockMultipartFile("file", "fake.png", "image/png", bytes))
                            .with(jwt().jwt(j -> j.subject("1"))))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(storage);
    }

    @Test void rejectsOversizedImage() throws Exception {
        mvc.perform(multipart("/api/v1/users/me/avatar")
                        .file(new MockMultipartFile("file", "large.png", "image/png", new byte[5 * 1024 * 1024 + 1]))
                        .with(jwt().jwt(j -> j.subject("1"))))
                .andExpect(status().isPayloadTooLarge());
        verifyNoInteractions(storage);
    }

    @Test void rejectsMissingUserBeforeUploading() throws Exception {
        mvc.perform(multipart("/api/v1/users/me/avatar").file(image())
                        .with(jwt().jwt(j -> j.subject("999"))))
                .andExpect(status().isNotFound());
        verifyNoInteractions(storage);
    }

    @Test void keepsExistingAvatarOnUploadFailure() throws Exception {
        given(storage.upload(any(byte[].class), eq(1L))).willThrow(
                new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_GATEWAY));
        mvc.perform(multipart("/api/v1/users/me/avatar").file(image())
                        .with(jwt().jwt(j -> j.subject("1"))))
                .andExpect(status().isBadGateway());
        verify(storage, never()).deleteQuietly(any());
    }
}
