package com.fuoverflow.auth.application;

import com.fuoverflow.auth.domain.ClientContext;
import com.fuoverflow.auth.domain.TokenPair;
import com.fuoverflow.auth.persistence.UserSessionRepository;
import com.fuoverflow.common.exception.ForbiddenException;
import com.fuoverflow.user.api.dto.AuthUserView;
import com.fuoverflow.user.application.UserLookupService;
import com.fuoverflow.user.domain.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OAuthSessionIssuerTest {
    @Mock private UserLookupService users;
    @Mock private JwtService jwt;
    @Mock private UserSessionRepository sessions;
    @Mock private CookieService cookies;

    private OAuthSessionIssuer issuer;

    @BeforeEach
    void setUp() {
        issuer = new OAuthSessionIssuer(users, jwt, sessions, cookies);
    }

    @Test
    void issue_activeUser_returnsTokenBundle() {
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();
        AuthUserView user = new AuthUserView(
                userId,
                "user@example.com",
                "username",
                "hash",
                "Display Name",
                UserStatus.ACTIVE,
                List.of("USER"),
                1L,
                List.of(),
                false,
                true,
                now,
                now,
                null
        );
        when(users.findAuthUserById(userId)).thenReturn(Optional.of(user));

        TokenPair pair = new TokenPair("access", "refresh", "refreshHash",
                UUID.randomUUID(), UUID.randomUUID(), now,
                now.plusSeconds(600), now.plusSeconds(2592000));
        when(jwt.generate(any(), any(), any())).thenReturn(pair);

        ClientContext context = new ClientContext("127.0.0.1", "Test Agent");
        AuthService.AuthTokenBundle result = issuer.issue(userId, context);

        assertThat(result).isNotNull();
        assertThat(result.tokenPair()).isEqualTo(pair);
        verify(sessions).save(any());
    }

    @Test
    void issue_pendingProfileUser_returnsTokenBundle() {
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();
        AuthUserView user = new AuthUserView(
                userId,
                "user@example.com",
                "username",
                null,
                "Display Name",
                UserStatus.PENDING_PROFILE,
                List.of("USER"),
                1L,
                List.of(),
                false,
                true,
                now,
                null,
                null
        );
        when(users.findAuthUserById(userId)).thenReturn(Optional.of(user));

        TokenPair pair = new TokenPair("access", "refresh", "refreshHash",
                UUID.randomUUID(), UUID.randomUUID(), now,
                now.plusSeconds(600), now.plusSeconds(2592000));
        when(jwt.generate(any(), any(), any())).thenReturn(pair);

        ClientContext context = new ClientContext("127.0.0.1", "Test Agent");
        AuthService.AuthTokenBundle result = issuer.issue(userId, context);

        assertThat(result).isNotNull();
        verify(sessions).save(any());
    }

    @Test
    void issue_disabledUser_throwsForbidden() {
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();
        AuthUserView user = new AuthUserView(
                userId,
                "user@example.com",
                "username",
                "hash",
                "Display Name",
                UserStatus.DISABLED,
                List.of("USER"),
                1L,
                List.of(),
                false,
                true,
                now,
                now,
                null
        );
        when(users.findAuthUserById(userId)).thenReturn(Optional.of(user));

        ClientContext context = new ClientContext("127.0.0.1", "Test Agent");

        assertThatThrownBy(() -> issuer.issue(userId, context))
                .isInstanceOf(ForbiddenException.class)
                .satisfies(e -> {
                    ForbiddenException ex = (ForbiddenException) e;
                    assertThat(ex.code()).isEqualTo("USER_DISABLED");
                    assertThat(ex.getMessage()).contains("User cannot authenticate");
                });
    }
}
