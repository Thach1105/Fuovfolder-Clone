# Error Response Standardization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add support contact info to 5xx responses, add `TooManyRequestsException` (429), and fix `OAuthEmailNotVerifiedException` to return a proper 4xx instead of 500.

**Architecture:** Add `SupportProperties` to bind env vars; inject into `GlobalExceptionHandler`; add `TooManyRequestsException` as a new `ApiException` subclass; fix `OAuthEmailNotVerifiedException` to extend `ForbiddenException`.

**Tech Stack:** Spring Boot 3.5.x, `@ConfigurationProperties`, Jackson, Jakarta Validation.

## Global Constraints

- Java 21, Spring Boot 3.5.x
- No new dependencies
- All error responses use existing `ApiResponse.failure()` shape
- Stack traces must never leak to client
- `common` module must not depend on `auth` module

---

### Task 1: `SupportProperties` + `TooManyRequestsException`

**Files:**
- Create: `backend/common/src/main/java/com/fuoverflow/common/config/SupportProperties.java`
- Create: `backend/common/src/main/java/com/fuoverflow/common/exception/TooManyRequestsException.java`
- Modify: `backend/app/src/main/resources/application.yml`

**Interfaces:**
- Produces: `SupportProperties` record with `.email()` and `.phone()` — used by Task 2
- Produces: `TooManyRequestsException(String code, String message)` — used by Plan 3 (resend rate limiter)

- [ ] **Step 1: Write failing test for `TooManyRequestsException`**

Create `backend/common/src/test/java/com/fuoverflow/common/exception/TooManyRequestsExceptionTest.java`:

```java
package com.fuoverflow.common.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class TooManyRequestsExceptionTest {

    @Test
    void shouldReturn429Status() {
        var ex = new TooManyRequestsException("RESEND_TOO_SOON", "Please wait 60 seconds.");
        assertThat(ex.status()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(ex.code()).isEqualTo("RESEND_TOO_SOON");
        assertThat(ex.getMessage()).isEqualTo("Please wait 60 seconds.");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd backend && mvn -q test -pl common -Dtest=TooManyRequestsExceptionTest
```

Expected: FAIL — `TooManyRequestsException` does not exist yet.

- [ ] **Step 3: Create `TooManyRequestsException`**

Create `backend/common/src/main/java/com/fuoverflow/common/exception/TooManyRequestsException.java`:

```java
package com.fuoverflow.common.exception;

import org.springframework.http.HttpStatus;

public class TooManyRequestsException extends ApiException {
    public TooManyRequestsException(String code, String message) {
        super(code, message, HttpStatus.TOO_MANY_REQUESTS);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
cd backend && mvn -q test -pl common -Dtest=TooManyRequestsExceptionTest
```

Expected: PASS.

- [ ] **Step 5: Create `SupportProperties`**

Create `backend/common/src/main/java/com/fuoverflow/common/config/SupportProperties.java`:

```java
package com.fuoverflow.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.support")
public record SupportProperties(String email, String phone) {
    public SupportProperties {
        email = email != null ? email : "";
        phone = phone != null ? phone : "";
    }
}
```

- [ ] **Step 6: Add `app.support` config to `application.yml`**

In `backend/app/src/main/resources/application.yml`, add at the end of the file:

```yaml
app:
  support:
    email: ${SUPPORT_EMAIL:}
    phone: ${SUPPORT_PHONE:}
```

- [ ] **Step 7: Commit**

```bash
cd backend && git add common/src app/src/main/resources/application.yml
git commit -m "feat(common): add TooManyRequestsException and SupportProperties"
```

---

### Task 2: Update `GlobalExceptionHandler` with support contact message

**Files:**
- Modify: `backend/common/src/main/java/com/fuoverflow/common/web/GlobalExceptionHandler.java`

**Interfaces:**
- Consumes: `SupportProperties` from Task 1 — `.email()`, `.phone()`

- [ ] **Step 1: Write failing test**

Create `backend/common/src/test/java/com/fuoverflow/common/web/GlobalExceptionHandlerTest.java`:

```java
package com.fuoverflow.common.web;

import com.fuoverflow.common.config.SupportProperties;
import com.fuoverflow.common.exception.TooManyRequestsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler(new SupportProperties("support@fuoverflow.com", "0900-000-000"));
    }

    @Test
    void shouldIncludeSupportContactIn5xxMessage() {
        var request = new MockHttpServletRequest();
        var response = handler.handleUnexpected(new RuntimeException("boom"), request);
        assertThat(response.getBody().message())
                .contains("support@fuoverflow.com")
                .contains("0900-000-000");
        assertThat(response.getStatusCode().value()).isEqualTo(500);
    }

    @Test
    void shouldReturn429ForTooManyRequestsException() {
        var request = new MockHttpServletRequest();
        var ex = new TooManyRequestsException("RESEND_TOO_SOON", "Vui lòng chờ 60 giây.");
        var response = handler.handleApiException(ex, request);
        assertThat(response.getStatusCode().value()).isEqualTo(429);
        assertThat(response.getBody().code()).isEqualTo("RESEND_TOO_SOON");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd backend && mvn -q test -pl common -Dtest=GlobalExceptionHandlerTest
```

Expected: FAIL — constructor signature mismatch (no `SupportProperties` arg yet).

- [ ] **Step 3: Update `GlobalExceptionHandler`**

Replace `backend/common/src/main/java/com/fuoverflow/common/web/GlobalExceptionHandler.java`:

```java
package com.fuoverflow.common.web;

import com.fuoverflow.common.config.SupportProperties;
import com.fuoverflow.common.exception.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final SupportProperties support;

    public GlobalExceptionHandler(SupportProperties support) {
        this.support = support;
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleApiException(ApiException exception, HttpServletRequest request) {
        return ResponseEntity.status(exception.status())
                .body(error(exception.code(), exception.getMessage(), request, null));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        List<ApiResponse.ErrorDetail.FieldError> fields = exception.getBindingResult().getFieldErrors().stream()
                .map(this::toFieldError)
                .toList();
        return ResponseEntity.badRequest()
                .body(error("VALIDATION_ERROR", "Request validation failed", request, fields));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException exception, HttpServletRequest request) {
        return ResponseEntity.badRequest()
                .body(error("VALIDATION_ERROR", "Request validation failed", request, null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception exception, HttpServletRequest request) {
        log.error("Unhandled exception: method={}, path={}, requestId={}",
                request.getMethod(), request.getRequestURI(), request.getHeader("X-Request-Id"), exception);
        String message = String.format(
                "Đã xảy ra lỗi hệ thống. Vui lòng liên hệ hỗ trợ qua email %s hoặc số điện thoại %s.",
                support.email(), support.phone());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(error("INTERNAL_ERROR", message, request, null));
    }

    private ApiResponse.ErrorDetail.FieldError toFieldError(FieldError error) {
        return new ApiResponse.ErrorDetail.FieldError(error.getField(), error.getDefaultMessage());
    }

    private ApiResponse<Void> error(String code, String message, HttpServletRequest request,
                                    List<ApiResponse.ErrorDetail.FieldError> fields) {
        ApiResponse.ErrorDetail detail = new ApiResponse.ErrorDetail(request.getHeader("X-Request-Id"), fields);
        return ApiResponse.failure(code, message, detail);
    }
}
```

- [ ] **Step 4: Enable `SupportProperties` in common module**

In `backend/common/src/main/java/com/fuoverflow/common/config/` find the existing `@Configuration` class (e.g. `WebCorsConfig` or similar). Check which class uses `@EnableConfigurationProperties`. If none, add `@EnableConfigurationProperties(SupportProperties.class)` to the existing config class that loads most smoothly — otherwise create:

`backend/common/src/main/java/com/fuoverflow/common/config/CommonConfig.java`:

```java
package com.fuoverflow.common.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SupportProperties.class)
public class CommonConfig {}
```

- [ ] **Step 5: Run tests**

```bash
cd backend && mvn -q test -pl common -Dtest=GlobalExceptionHandlerTest
```

Expected: PASS.

- [ ] **Step 6: Build full project to catch wiring issues**

```bash
cd backend && mvn -q -DskipTests package
```

Expected: BUILD SUCCESS.

- [ ] **Step 7: Commit**

```bash
git add backend/common/src
git commit -m "feat(common): update GlobalExceptionHandler with support contact in 5xx message"
```

---

### Task 3: Fix `OAuthEmailNotVerifiedException`

**Files:**
- Modify: `backend/auth/src/main/java/com/fuoverflow/auth/exception/OAuthEmailNotVerifiedException.java`

**Interfaces:**
- Consumes: `ForbiddenException(String code, String message)` from `common`

- [ ] **Step 1: Write failing test**

Create `backend/auth/src/test/java/com/fuoverflow/auth/exception/OAuthEmailNotVerifiedExceptionTest.java`:

```java
package com.fuoverflow.auth.exception;

import com.fuoverflow.common.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class OAuthEmailNotVerifiedExceptionTest {

    @Test
    void shouldBeApiExceptionWith403() {
        var ex = new OAuthEmailNotVerifiedException();
        assertThat(ex).isInstanceOf(ApiException.class);
        assertThat(ex.status()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(ex.code()).isEqualTo("OAUTH_EMAIL_NOT_VERIFIED");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd backend && mvn -q test -pl auth -Dtest=OAuthEmailNotVerifiedExceptionTest
```

Expected: FAIL — class extends `RuntimeException` not `ApiException`, no `status()`/`code()` methods, no no-arg constructor.

- [ ] **Step 3: Fix `OAuthEmailNotVerifiedException`**

Replace `backend/auth/src/main/java/com/fuoverflow/auth/exception/OAuthEmailNotVerifiedException.java`:

```java
package com.fuoverflow.auth.exception;

import com.fuoverflow.common.exception.ForbiddenException;

public class OAuthEmailNotVerifiedException extends ForbiddenException {
    public OAuthEmailNotVerifiedException() {
        super("OAUTH_EMAIL_NOT_VERIFIED",
              "Tài khoản OAuth2 chưa xác thực email. Vui lòng xác thực email trước khi đăng nhập.");
    }
}
```

- [ ] **Step 4: Find and fix all call sites**

Search for usages of `OAuthEmailNotVerifiedException` that pass a message string argument — they must be updated to use the no-arg constructor:

```bash
grep -rn "new OAuthEmailNotVerifiedException" backend/
```

Update each call site from `new OAuthEmailNotVerifiedException("some message")` to `new OAuthEmailNotVerifiedException()`.

- [ ] **Step 5: Run tests**

```bash
cd backend && mvn -q test -pl auth -Dtest=OAuthEmailNotVerifiedExceptionTest
```

Expected: PASS.

- [ ] **Step 6: Build full project**

```bash
cd backend && mvn -q -DskipTests package
```

Expected: BUILD SUCCESS.

- [ ] **Step 7: Commit**

```bash
git add backend/auth/src
git commit -m "fix(auth): OAuthEmailNotVerifiedException now returns 403 instead of 500"
```
