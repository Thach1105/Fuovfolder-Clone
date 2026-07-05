package com.fuoverflow.auth.api;

import com.fuoverflow.auth.api.dto.*;
import com.fuoverflow.auth.application.AuthService;
import com.fuoverflow.auth.application.CompletePendingProfileService;
import com.fuoverflow.auth.application.CookieService;
import com.fuoverflow.auth.application.EmailVerificationService;
import com.fuoverflow.auth.application.PasswordResetService;
import com.fuoverflow.auth.application.SessionManagementService;
import com.fuoverflow.auth.application.SetPasswordService;
import com.fuoverflow.auth.config.AuthProperties;
import com.fuoverflow.auth.domain.ClientContext;
import com.fuoverflow.auth.persistence.UserSessionEntity;
import com.fuoverflow.auth.persistence.UserSessionRepository;
import com.fuoverflow.auth.support.EmailVerificationLinks;
import com.fuoverflow.auth.support.PasswordResetLinks;
import com.fuoverflow.common.config.CorsProperties;
import com.fuoverflow.common.security.RequirePermission;
import com.fuoverflow.common.web.ApiResponse;
import com.fuoverflow.user.api.dto.UserProfileResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Arrays;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;
    private final CompletePendingProfileService completePendingProfileService;
    private final EmailVerificationService emailVerificationService;
    private final PasswordResetService passwordResetService;
    private final SetPasswordService setPasswordService;
    private final CookieService cookieService;
    private final AuthProperties authProperties;
    private final CorsProperties corsProperties;
    private final SessionManagementService sessionManagementService;
    private final UserSessionRepository sessionRepository;

    public AuthController(
            AuthService authService,
            CompletePendingProfileService completePendingProfileService,
            EmailVerificationService emailVerificationService,
            PasswordResetService passwordResetService,
            SetPasswordService setPasswordService,
            CookieService cookieService,
            AuthProperties authProperties,
            CorsProperties corsProperties,
            SessionManagementService sessionManagementService,
            UserSessionRepository sessionRepository) {
        this.authService = authService;
        this.completePendingProfileService = completePendingProfileService;
        this.emailVerificationService = emailVerificationService;
        this.passwordResetService = passwordResetService;
        this.setPasswordService = setPasswordService;
        this.cookieService = cookieService;
        this.authProperties = authProperties;
        this.corsProperties = corsProperties;
        this.sessionManagementService = sessionManagementService;
        this.sessionRepository = sessionRepository;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.ok(authService.register(request));
    }

    @PostMapping("/complete-profile")
    public ApiResponse<UserProfileResponse> completeProfile(
            @Valid @RequestBody CompletePendingProfileRequest request,
            org.springframework.security.core.Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        return ApiResponse.ok(completePendingProfileService.complete(userId, request));
    }

    @GetMapping("/email/verify")
    public void verifyEmailRedirect(@RequestParam("token") String token, HttpServletResponse response) throws IOException {
        AuthProperties.EmailVerification config = authProperties.emailVerification();
        String link = EmailVerificationLinks.buildLink(
                config == null ? null : config.verificationUrlBase(),
                corsProperties.allowedOrigins(),
                token);
        response.sendRedirect(link);
    }

    @PostMapping("/email/verify")
    public ApiResponse<AuthenticatedUserResponse> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        return ApiResponse.ok(authService.userResponse(emailVerificationService.verify(request.token())));
    }

    @PostMapping("/email/resend")
    public ApiResponse<Void> resendVerificationEmail(@Valid @RequestBody ResendVerificationRequest request) {
        emailVerificationService.resend(request.email());
        return ApiResponse.ok(null);
    }

    @PostMapping("/password/forgot")
    public ApiResponse<ForgotPasswordResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        return ApiResponse.ok(passwordResetService.requestReset(request.email()));
    }

    @GetMapping("/password/reset")
    public void resetPasswordRedirect(@RequestParam("token") String token, HttpServletResponse response) throws IOException {
        AuthProperties.PasswordReset config = authProperties.passwordReset();
        String link = PasswordResetLinks.buildLink(
                config == null ? null : config.resetUrlBase(),
                corsProperties.allowedOrigins(),
                token);
        response.sendRedirect(link);
    }

    @PostMapping("/password/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request.token(), request.password());
    }

    @PostMapping("/password/set-request")
    public ApiResponse<Void> requestSetPassword(Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        setPasswordService.requestSetPassword(userId);
        return ApiResponse.ok(null);
    }

    @PostMapping("/password/set")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirmSetPassword(@Valid @RequestBody SetPasswordRequest request) {
        setPasswordService.confirmSetPassword(request.token(), request.password());
    }

    @PostMapping("/login")
    public ApiResponse<AuthTokenResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest,
                                                HttpServletResponse httpResponse) {
        AuthService.AuthTokenBundle bundle = authService.login(request, context(httpRequest));
        cookieService.writeTokenCookies(httpResponse, bundle.tokenPair());
        return ApiResponse.ok(bundle.response());
    }

    @PostMapping("/refresh")
    public ApiResponse<AuthTokenResponse> refresh(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        AuthService.AuthTokenBundle bundle = authService.refresh(refreshToken(httpRequest), context(httpRequest));
        cookieService.writeTokenCookies(httpResponse, bundle.tokenPair());
        return ApiResponse.ok(bundle.response());
    }

    @PostMapping("/token/generate")
    @RequirePermission("auth.token:generate")
    public ApiResponse<AuthTokenResponse> generate(@RequestParam UUID userId, HttpServletRequest httpRequest,
                                                   HttpServletResponse httpResponse) {
        AuthService.AuthTokenBundle bundle = authService.generateForUser(userId, context(httpRequest));
        cookieService.writeTokenCookies(httpResponse, bundle.tokenPair());
        return ApiResponse.ok(bundle.response());
    }

    @PostMapping("/introspect")
    @RequirePermission("auth.token:introspect")
    public ApiResponse<TokenIntrospectionResponse> introspect(@Valid @RequestBody TokenIntrospectionRequest request) {
        return ApiResponse.ok(authService.introspect(request));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        authService.logout(refreshToken(request));
        cookieService.clearTokenCookies(response);
    }

    @GetMapping("/sessions")
    public ApiResponse<SessionListResponse> listSessions(Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        UUID familyId = currentFamilyId(authentication);
        return ApiResponse.ok(sessionManagementService.listSessions(userId, familyId));
    }

    @DeleteMapping("/sessions/{familyId}")
    public ResponseEntity<Void> revokeSession(@PathVariable UUID familyId, Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        UUID currentFamily = currentFamilyId(authentication);
        sessionManagementService.revokeSession(userId, familyId, currentFamily);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/sessions")
    public ResponseEntity<Void> revokeOtherSessions(Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        UUID currentFamily = currentFamilyId(authentication);
        sessionManagementService.revokeOtherSessions(userId, currentFamily);
        return ResponseEntity.noContent().build();
    }

    private UUID currentFamilyId(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwt) {
            String sid = jwt.getToken().getClaimAsString("sid");
            if (sid != null) {
                try {
                    UUID sessionId = UUID.fromString(sid);
                    return sessionRepository.findByIdAndRevokedAtIsNull(sessionId)
                            .map(UserSessionEntity::getRefreshTokenFamilyId)
                            .orElse(null);
                } catch (IllegalArgumentException ignored) {}
            }
        }
        return null;
    }

    private ClientContext context(HttpServletRequest request) {
        return new ClientContext(request.getRemoteAddr(), request.getHeader("User-Agent"));
    }

    private String refreshToken(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        return Arrays.stream(request.getCookies())
                .filter(cookie -> authProperties.cookie().refreshName().equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }
}
