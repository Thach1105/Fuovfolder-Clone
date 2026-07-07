package com.fuoverflow.material.application;

import com.fuoverflow.common.config.UploadProperties;
import com.fuoverflow.common.exception.ForbiddenException;
import com.fuoverflow.common.security.UploadPermissionChecker;
import com.fuoverflow.common.storage.FileKind;
import com.fuoverflow.common.storage.ObjectStorage;
import com.fuoverflow.common.storage.StoredObject;
import com.fuoverflow.material.domain.UploadPurpose;
import com.fuoverflow.material.persistence.UploadedFileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UploadServiceTest {
    @Mock
    private UploadedFileRepository uploadedFileRepository;
    @Mock
    private ObjectStorage objectStorage;
    @Mock
    private UploadRateLimiter rateLimiter;
    @Mock
    private UploadPermissionChecker uploadPermissionChecker;

    private UploadService uploadService;

    @BeforeEach
    void setUp() {
        UploadProperties properties = new UploadProperties(
                5L * 1024 * 1024,
                20L * 1024 * 1024,
                50L * 1024 * 1024,
                30,
                null,
                null,
                null);
        uploadService = new UploadService(
                uploadedFileRepository,
                objectStorage,
                rateLimiter,
                properties,
                uploadPermissionChecker);
    }

    @Test
    void uploadRequiresPurposePermission() {
        UUID userId = UUID.randomUUID();
        when(uploadPermissionChecker.hasAnyPermission(userId, UploadPurpose.AVATAR.requiredPermissions()))
                .thenReturn(false);

        Authentication auth = userAuth(userId);
        assertThrows(ForbiddenException.class, () -> uploadService.upload(
                new MockMultipartFile("file", "avatar.png", "image/png", new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47}),
                UploadPurpose.AVATAR,
                userId,
                auth));
    }

    @Test
    void uploadStoresFileWhenPermitted() {
        UUID userId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "avatar.png",
                "image/png",
                new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A});
        when(uploadPermissionChecker.hasAnyPermission(eq(userId), any(Set.class))).thenReturn(true);
        when(objectStorage.storeFile(file, UploadPurpose.AVATAR.folder(), FileKind.IMAGE))
                .thenReturn(new StoredObject("avatars/key.png", "http://media/avatars/key.png"));
        when(uploadedFileRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = uploadService.upload(file, UploadPurpose.AVATAR, userId, userAuth(userId));

        assertEquals("avatars/key.png", response.objectKey());
        verify(rateLimiter).checkAllowed(userId, 30);
    }

    @Test
    void adminSkipsRateLimit() {
        UUID userId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile(
                "file", "avatar.png", "image/png",
                new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A});
        when(uploadPermissionChecker.hasAnyPermission(eq(userId), any(Set.class))).thenReturn(true);
        when(objectStorage.storeFile(file, UploadPurpose.AVATAR.folder(), FileKind.IMAGE))
                .thenReturn(new StoredObject("avatars/key.png", "http://media/avatars/key.png"));
        when(uploadedFileRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Authentication adminAuth = new UsernamePasswordAuthenticationToken(
                userId.toString(), null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        uploadService.upload(file, UploadPurpose.AVATAR, userId, adminAuth);

        org.mockito.Mockito.verifyNoInteractions(rateLimiter);
    }

    private static Authentication userAuth(UUID userId) {
        return new UsernamePasswordAuthenticationToken(
                userId.toString(), null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }
}
