package com.fuoverflow.user.application;

import com.fuoverflow.common.exception.ConflictException;
import com.fuoverflow.user.api.dto.AuthUserView;
import com.fuoverflow.user.domain.UserStatus;
import com.fuoverflow.user.persistence.UserEntity;
import com.fuoverflow.user.persistence.UserMapper;
import com.fuoverflow.user.persistence.UserRepository;
import com.fuoverflow.user.validation.EmailNormalizer;
import com.fuoverflow.user.validation.UsernameNormalizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserRegistrationServiceTest {

    @Mock
    private UserRepository repository;

    @Mock
    private UserMapper mapper;

    @Mock
    private EmailNormalizer emailNormalizer;

    @Mock
    private UsernameNormalizer usernameNormalizer;

    @Mock
    private RoleAssignmentService roleAssignments;

    private UserRegistrationService service;

    @BeforeEach
    void setUp() {
        service = new UserRegistrationService(
            repository, mapper, emailNormalizer, usernameNormalizer, roleAssignments
        );
    }

    @Test
    void register_passwordFlow_shouldUsePendingFactory() {
        // Given
        RegisterUserCommand command = new RegisterUserCommand(
            "user@example.com", "testuser", "hashed_password", "Test User", "HCM"
        );

        when(emailNormalizer.normalize("user@example.com")).thenReturn("user@example.com");
        when(usernameNormalizer.normalize("testuser")).thenReturn("testuser");
        when(repository.existsByNormalizedEmailAndDeletedAtIsNull("user@example.com")).thenReturn(false);
        when(repository.existsByUsernameNormalizedAndDeletedAtIsNull("testuser")).thenReturn(false);

        UserEntity mockEntity = mock(UserEntity.class);
        when(mockEntity.getId()).thenReturn(UUID.randomUUID());
        when(repository.save(any(UserEntity.class))).thenReturn(mockEntity);

        AuthUserView mockView = mock(AuthUserView.class);
        when(mapper.toAuthUser(mockEntity)).thenReturn(mockView);

        // When
        service.register(command);

        // Then
        ArgumentCaptor<UserEntity> entityCaptor = ArgumentCaptor.forClass(UserEntity.class);
        verify(repository).save(entityCaptor.capture());

        UserEntity savedEntity = entityCaptor.getValue();
        assertThat(savedEntity.getStatus()).isEqualTo(UserStatus.PENDING_EMAIL_VERIFICATION);
        assertThat(savedEntity.isEmailVerified()).isFalse();
    }

    @Test
    void register_oauthFlow_shouldUseOauthUserFactory() {
        // Given
        RegisterUserCommand command = new RegisterUserCommand(
            "oauth@gmail.com", "oauthuser", "", "OAuth User", "HN",
            true, UserStatus.ACTIVE
        );

        when(emailNormalizer.normalize("oauth@gmail.com")).thenReturn("oauth@gmail.com");
        when(usernameNormalizer.normalize("oauthuser")).thenReturn("oauthuser");
        when(repository.existsByNormalizedEmailAndDeletedAtIsNull("oauth@gmail.com")).thenReturn(false);
        when(repository.existsByUsernameNormalizedAndDeletedAtIsNull("oauthuser")).thenReturn(false);

        UserEntity mockEntity = mock(UserEntity.class);
        when(mockEntity.getId()).thenReturn(UUID.randomUUID());
        when(repository.save(any(UserEntity.class))).thenReturn(mockEntity);

        AuthUserView mockView = mock(AuthUserView.class);
        when(mapper.toAuthUser(mockEntity)).thenReturn(mockView);

        // When
        service.register(command);

        // Then
        ArgumentCaptor<UserEntity> entityCaptor = ArgumentCaptor.forClass(UserEntity.class);
        verify(repository).save(entityCaptor.capture());

        UserEntity savedEntity = entityCaptor.getValue();
        assertThat(savedEntity.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(savedEntity.isEmailVerified()).isTrue();
    }

    @Test
    void register_shouldThrowConflictException_whenEmailAlreadyExists() {
        // Given
        RegisterUserCommand command = new RegisterUserCommand(
            "existing@example.com", "newuser", "hashed_password", "New User", "HCM"
        );

        when(emailNormalizer.normalize("existing@example.com")).thenReturn("existing@example.com");
        when(usernameNormalizer.normalize("newuser")).thenReturn("newuser");
        when(repository.existsByNormalizedEmailAndDeletedAtIsNull("existing@example.com")).thenReturn(true);

        // When/Then
        assertThatThrownBy(() -> service.register(command))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("Email is already used");
    }

    @Test
    void register_shouldThrowConflictException_whenUsernameAlreadyExists() {
        // Given
        RegisterUserCommand command = new RegisterUserCommand(
            "new@example.com", "existinguser", "hashed_password", "New User", "HCM"
        );

        when(emailNormalizer.normalize("new@example.com")).thenReturn("new@example.com");
        when(usernameNormalizer.normalize("existinguser")).thenReturn("existinguser");
        when(repository.existsByNormalizedEmailAndDeletedAtIsNull("new@example.com")).thenReturn(false);
        when(repository.existsByUsernameNormalizedAndDeletedAtIsNull("existinguser")).thenReturn(true);

        // When/Then
        assertThatThrownBy(() -> service.register(command))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("Username is already used");
    }

    @Test
    void register_shouldAssignDefaultUserRole() {
        // Given
        RegisterUserCommand command = new RegisterUserCommand(
            "user@example.com", "testuser", "hashed_password", "Test User", "HCM"
        );

        when(emailNormalizer.normalize("user@example.com")).thenReturn("user@example.com");
        when(usernameNormalizer.normalize("testuser")).thenReturn("testuser");
        when(repository.existsByNormalizedEmailAndDeletedAtIsNull("user@example.com")).thenReturn(false);
        when(repository.existsByUsernameNormalizedAndDeletedAtIsNull("testuser")).thenReturn(false);

        UUID userId = UUID.randomUUID();
        UserEntity mockEntity = mock(UserEntity.class);
        when(mockEntity.getId()).thenReturn(userId);
        when(repository.save(any(UserEntity.class))).thenReturn(mockEntity);

        AuthUserView mockView = mock(AuthUserView.class);
        when(mapper.toAuthUser(mockEntity)).thenReturn(mockView);

        // When
        service.register(command);

        // Then
        verify(roleAssignments).ensureDefaultUserRole(userId);
    }

    @Test
    void register_oauthFlowWithUnverifiedEmail_shouldCreateCorrectEntity() {
        // Given
        RegisterUserCommand command = new RegisterUserCommand(
            "unverified.oauth@example.com", "unverifiedoauth", "",
            "Unverified OAuth", null, false, UserStatus.ACTIVE
        );

        when(emailNormalizer.normalize("unverified.oauth@example.com")).thenReturn("unverified.oauth@example.com");
        when(usernameNormalizer.normalize("unverifiedoauth")).thenReturn("unverifiedoauth");
        when(repository.existsByNormalizedEmailAndDeletedAtIsNull("unverified.oauth@example.com")).thenReturn(false);
        when(repository.existsByUsernameNormalizedAndDeletedAtIsNull("unverifiedoauth")).thenReturn(false);

        UserEntity mockEntity = mock(UserEntity.class);
        when(mockEntity.getId()).thenReturn(UUID.randomUUID());
        when(repository.save(any(UserEntity.class))).thenReturn(mockEntity);

        AuthUserView mockView = mock(AuthUserView.class);
        when(mapper.toAuthUser(mockEntity)).thenReturn(mockView);

        // When
        service.register(command);

        // Then
        ArgumentCaptor<UserEntity> entityCaptor = ArgumentCaptor.forClass(UserEntity.class);
        verify(repository).save(entityCaptor.capture());

        UserEntity savedEntity = entityCaptor.getValue();
        assertThat(savedEntity.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(savedEntity.isEmailVerified()).isFalse();
    }
}
