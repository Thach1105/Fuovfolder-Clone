package com.fuoverflow.auth.api;

import com.fuoverflow.auth.api.dto.*;
import com.fuoverflow.auth.application.AuthService;
import com.fuoverflow.auth.application.PasswordResetService;
import com.fuoverflow.auth.application.CookieService;
import com.fuoverflow.auth.application.EmailVerificationService;
import com.fuoverflow.auth.domain.ClientContext;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;
    private final EmailVerificationService emailVerificationService;
    private final CookieService cookieService;
    private final PasswordResetService passwordResetService;

    public AuthController(AuthService authService, EmailVerificationService emailVerificationService, 
                          CookieService cookieService, PasswordResetService passwordResetService) {
        this.authService = authService;
        this.emailVerificationService = emailVerificationService;
        this.cookieService = cookieService;
        this.passwordResetService = passwordResetService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/email/verify")
    public AuthenticatedUserResponse verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        return authService.userResponse(emailVerificationService.verify(request.token()));
    }

    @PostMapping("/login")
    public AuthTokenResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest,
                                   HttpServletResponse httpResponse) {
        AuthService.AuthTokenBundle bundle = authService.login(request, context(httpRequest));
        cookieService.writeTokenCookies(httpResponse, bundle.tokenPair());
        return bundle.response();
    }

    @PostMapping("/refresh")
    public AuthTokenResponse refresh(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        AuthService.AuthTokenBundle bundle = authService.refresh(refreshToken(httpRequest), context(httpRequest));
        cookieService.writeTokenCookies(httpResponse, bundle.tokenPair());
        return bundle.response();
    }

    @PostMapping("/token/generate")
    public AuthTokenResponse generate(@RequestParam UUID userId, HttpServletRequest httpRequest,
                                      HttpServletResponse httpResponse) {
        AuthService.AuthTokenBundle bundle = authService.generateForUser(userId, context(httpRequest));
        cookieService.writeTokenCookies(httpResponse, bundle.tokenPair());
        return bundle.response();
    }

    @PostMapping("/introspect")
    public TokenIntrospectionResponse introspect(@Valid @RequestBody TokenIntrospectionRequest request) {
        return authService.introspect(request);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        authService.logout(refreshToken(request));
        cookieService.clearTokenCookies(response);
    }

    @PostMapping("/password/forgot")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordResetService.requestReset(request.email());
    }

    @PostMapping("/password/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request.token(), request.newPassword());
    }

    private ClientContext context(HttpServletRequest request) {
        return new ClientContext(request.getRemoteAddr(), request.getHeader("User-Agent"));
    }

    private String refreshToken(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        return Arrays.stream(request.getCookies())
                .filter(cookie -> "fuoverflow_rt".equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }
}
