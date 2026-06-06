package com.fuoverflow.auth.api;

import com.fuoverflow.auth.api.dto.*;
import com.fuoverflow.auth.application.AuthService;
import com.fuoverflow.auth.application.CookieService;
import com.fuoverflow.auth.application.EmailVerificationService;
import com.fuoverflow.auth.domain.ClientContext;
import com.fuoverflow.common.security.RequirePermission;
import com.fuoverflow.common.web.ApiResponse;
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

    public AuthController(AuthService authService, EmailVerificationService emailVerificationService, CookieService cookieService) {
        this.authService = authService;
        this.emailVerificationService = emailVerificationService;
        this.cookieService = cookieService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.ok(authService.register(request));
    }

    @PostMapping("/email/verify")
    public ApiResponse<AuthenticatedUserResponse> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        return ApiResponse.ok(authService.userResponse(emailVerificationService.verify(request.token())));
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
    public ApiResponse<TokenIntrospectionResponse> introspect(@Valid @RequestBody TokenIntrospectionRequest request) {
        return ApiResponse.ok(authService.introspect(request));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        authService.logout(refreshToken(request));
        cookieService.clearTokenCookies(response);
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
