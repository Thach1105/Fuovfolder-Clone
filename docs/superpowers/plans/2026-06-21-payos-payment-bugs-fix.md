# PayOS Payment Integration Bugs Fix

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix critical security, concurrency, and data consistency bugs in PayOS payment integration

**Architecture:** Multi-layered Spring Boot backend with payment gateway integration, point system, and webhook processing. Fixes address security (webhook auth), race conditions (payment confirmation, balance creation), data consistency (amount units, idempotency), and access control.

**Tech Stack:** Java 21, Spring Boot 3.5.x, Spring Security, JPA/Hibernate, PostgreSQL, PayOS SDK 2.0.1

## Global Constraints

- Java 21 required
- Spring Boot 3.5.x
- All tests must pass before commit
- Follow existing code patterns in payment module
- Maintain backwards compatibility with existing API contracts where possible
- Use TDD: write test → verify fail → implement → verify pass → commit
- Transaction boundaries must ensure data consistency
- No breaking changes to public API endpoints
- Database schema changes require new Flyway migration

---

## Task 1: Security - Webhook Endpoint Access Control

**Priority:** CRITICAL - Payment confirmations currently fail with 401

**Files:**
- Modify: `backend/auth/src/main/java/com/fuoverflow/auth/config/SecurityConfig.java:33-60`
- Test: `backend/auth/src/test/java/com/fuoverflow/auth/config/SecurityConfigTest.java` (create)

**Interfaces:**
- Consumes: Existing SecurityFilterChain bean
- Produces: Updated security config with webhook endpoint in permitAll list

- [ ] **Step 1: Write test for webhook endpoint access**

Create `backend/auth/src/test/java/com/fuoverflow/auth/config/SecurityConfigTest.java`:

```java
package com.fuoverflow.auth.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void webhookEndpoint_shouldBeAccessibleWithoutAuth() throws Exception {
        mockMvc.perform(post("/api/v1/payment/payos/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().is4xxClientError()); // 400 bad request OK, not 401
    }
}
```

- [ ] **Step 2: Run test to verify it fails with 401**

```bash
cd backend
mvn -q -pl auth -am test -Dtest=SecurityConfigTest
```

Expected: Test FAILS - webhook returns 401 Unauthorized instead of 400/500

- [ ] **Step 3: Add webhook endpoint to permitAll in SecurityConfig**

Edit `backend/auth/src/main/java/com/fuoverflow/auth/config/SecurityConfig.java`:

Find line 34-44 (the first `.requestMatchers()` block) and add webhook path:

```java
.requestMatchers(
        "/api/v1/auth/register",
        "/api/v1/auth/login",
        "/api/v1/auth/refresh",
        "/api/v1/auth/email/verify",
        "/api/v1/auth/password/forgot",
        "/api/v1/auth/password/reset",
        "/api/v1/auth/introspect",
        "/api/v1/payment/payos/webhook",  // ADD THIS LINE
        "/actuator/health",
        "/actuator/health/**"
).permitAll()
```

- [ ] **Step 4: Run test to verify it passes**

```bash
cd backend
mvn -q -pl auth -am test -Dtest=SecurityConfigTest
```

Expected: Test PASSES - webhook no longer returns 401

- [ ] **Step 5: Add .env.example entries for PayOS config**

Edit `.env.example` after line 97 (after COURSERA config):

```bash
# --- PayOS Payment Gateway ---
PAYOS_CLIENT_ID=your-payos-client-id
PAYOS_API_KEY=your-payos-api-key
PAYOS_CHECKSUM_KEY=your-payos-checksum-key
```

- [ ] **Step 6: Commit security fix**

```bash
git add backend/auth/src/main/java/com/fuoverflow/auth/config/SecurityConfig.java
git add backend/auth/src/test/java/com/fuoverflow/auth/config/SecurityConfigTest.java
git add .env.example
git commit -m "fix(security): allow PayOS webhook endpoint without auth

- Add /api/v1/payment/payos/webhook to permitAll list
- Add test verifying webhook accessible without 401
- Add PayOS config entries to .env.example

Fixes webhook rejection causing payment confirmations to fail.

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

## Task 2: Race Condition - Point Balance Creation

**Priority:** CRITICAL - Concurrent requests cause unique constraint violations

**Files:**
- Modify: `backend/payment/src/main/java/com/fuoverflow/payment/application/PointService.java:21-25`
- Test: `backend/payment/src/test/java/com/fuoverflow/payment/application/PointServiceConcurrencyTest.java` (create)

**Interfaces:**
- Consumes: PointBalanceRepository
- Produces: Thread-safe getOrCreateBalance(UUID userId) method

- [ ] **Step 1: Write concurrency test**

Create `backend/payment/src/test/java/com/fuoverflow/payment/application/PointServiceConcurrencyTest.java`:

```java
package com.fuoverflow.payment.application;

import com.fuoverflow.payment.persistence.PointBalanceEntity;
import com.fuoverflow.payment.persistence.PointBalanceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class PointServiceConcurrencyTest {

    @Autowired
    private PointService pointService;

    @Autowired
    private PointBalanceRepository balanceRepo;

    @Test
    void getOrCreateBalance_withConcurrentCalls_shouldCreateOnlyOneBalance() throws Exception {
        UUID userId = UUID.randomUUID();
        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    pointService.getOrCreateBalance(userId);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    // Catch to avoid failing other threads
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // Start all threads simultaneously
        doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        long balanceCount = balanceRepo.findByUserId(userId).stream().count();
        assertThat(balanceCount).isEqualTo(1);
        assertThat(successCount.get()).isGreaterThan(0);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd backend
mvn -q -pl payment -am test -Dtest=PointServiceConcurrencyTest
```

Expected: Test FAILS - unique constraint violation or multiple balances created

- [ ] **Step 3: Fix getOrCreateBalance with proper synchronization**

Edit `backend/payment/src/main/java/com/fuoverflow/payment/application/PointService.java`:

Replace lines 21-25:

```java
@Transactional
public PointBalanceEntity getOrCreateBalance(UUID userId) {
    return balanceRepo.findByUserId(userId)
            .orElseGet(() -> {
                try {
                    return balanceRepo.save(PointBalanceEntity.create(userId));
                } catch (org.springframework.dao.DataIntegrityViolationException e) {
                    // Race condition: another thread created it, fetch again
                    return balanceRepo.findByUserId(userId)
                            .orElseThrow(() -> new IllegalStateException("Failed to get or create balance"));
                }
            });
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
cd backend
mvn -q -pl payment -am test -Dtest=PointServiceConcurrencyTest
```

Expected: Test PASSES - only one balance created, no exceptions

- [ ] **Step 5: Commit race condition fix**

```bash
git add backend/payment/src/main/java/com/fuoverflow/payment/application/PointService.java
git add backend/payment/src/test/java/com/fuoverflow/payment/application/PointServiceConcurrencyTest.java
git commit -m "fix(payment): prevent race condition in point balance creation

- Catch DataIntegrityViolationException in getOrCreateBalance
- Retry fetch after concurrent insert detected
- Add concurrency test with 10 parallel threads

Prevents unique constraint violation when multiple requests
create balance for same user simultaneously.

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

## Task 3: Race Condition - Payment Confirmation with Optimistic Locking

**Priority:** CRITICAL - Duplicate webhooks can create duplicate payments and point credits

**Files:**
- Modify: `backend/payment/src/main/java/com/fuoverflow/payment/application/PaymentService.java:112-131`
- Modify: `backend/payment/src/main/java/com/fuoverflow/payment/persistence/PaymentRepository.java:8-14`
- Test: `backend/payment/src/test/java/com/fuoverflow/payment/application/PaymentServiceConcurrencyTest.java` (create)

**Interfaces:**
- Consumes: OrderEntity with @Version, PaymentRepository
- Produces: Idempotent confirmPaymentByOrderCode method

- [ ] **Step 1: Write duplicate webhook test**

Create `backend/payment/src/test/java/com/fuoverflow/payment/application/PaymentServiceConcurrencyTest.java`:

```java
package com.fuoverflow.payment.application;

import com.fuoverflow.payment.persistence.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class PaymentServiceConcurrencyTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private OrderRepository orderRepo;

    @Autowired
    private PaymentRepository paymentRepo;

    @Autowired
    private PointBalanceRepository balanceRepo;

    @Test
    void confirmPayment_withDuplicateWebhooks_shouldProcessOnlyOnce() throws Exception {
        UUID userId = UUID.randomUUID();
        String orderCode = "test-order-" + System.currentTimeMillis();
        
        OrderEntity order = OrderEntity.create(userId, 10000, "VND", "payos", orderCode);
        orderRepo.save(order);

        int threadCount = 5;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    paymentService.confirmPaymentByOrderCode(orderCode);
                } catch (Exception e) {
                    // Expected for duplicate attempts
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        long paymentCount = paymentRepo.findByOrderId(order.getId()).size();
        assertThat(paymentCount).isEqualTo(1);
        
        PointBalanceEntity balance = balanceRepo.findByUserId(userId).orElseThrow();
        assertThat(balance.getBalancePoints()).isEqualTo(100); // 10000 cents / 100
    }
}
```


- [ ] **Step 2: Run test to verify it fails**

```bash
cd backend
mvn -q -pl payment -am test -Dtest=PaymentServiceConcurrencyTest
```

Expected: Test FAILS - duplicate payment rows or duplicate point credit under parallel execution

- [ ] **Step 3: Add repository lookup by provider payment id**

Edit `backend/payment/src/main/java/com/fuoverflow/payment/persistence/PaymentRepository.java` to ensure this method exists and is used by service:

```java
public interface PaymentRepository extends JpaRepository<PaymentEntity, UUID> {
    Optional<PaymentEntity> findByProviderAndProviderPaymentId(String provider, String providerPaymentId);

    java.util.List<PaymentEntity> findByUserIdAndStatus(UUID userId, String status);

    java.util.List<PaymentEntity> findByOrderId(UUID orderId);
}
```

- [ ] **Step 4: Extract single confirmation path and handle duplicate processing safely**

Edit `backend/payment/src/main/java/com/fuoverflow/payment/application/PaymentService.java`.

Add helper method:

```java
private void creditPointsForPaidOrder(OrderEntity order, PaymentEntity payment) {
    long points = order.getTotalCents() / 100;
    pointService.creditPoints(order.getUserId(), points, "payment", payment.getId(), "Deposit points from PayOS");
}
```

Replace `confirmPaymentByOrderCode` with:

```java
@Transactional
public void confirmPaymentByOrderCode(String orderCode) {
    OrderEntity order = orderRepo.findByProviderOrderId(orderCode)
            .orElseThrow(() -> new NotFoundException("ORDER_NOT_FOUND", "Order not found for code: " + orderCode));

    if (!"pending".equals(order.getStatus())) {
        return;
    }

    if (paymentRepo.findByProviderAndProviderPaymentId("payos", orderCode).isPresent()) {
        order.markAsPaid();
        orderRepo.save(order);
        return;
    }

    PaymentEntity payment = PaymentEntity.create(
            order.getId(), order.getUserId(), "payos", orderCode,
            order.getTotalCents(), "VND");
    payment.markPaid(Instant.now());
    paymentRepo.save(payment);

    order.markAsPaid();
    orderRepo.save(order);

    creditPointsForPaidOrder(order, payment);
}
```

- [ ] **Step 5: Align `confirmPayment` with same ownership + idempotent logic**

Replace `confirmPayment` with:

```java
@Transactional
public void confirmPayment(String orderCode, UUID userId) {
    OrderEntity order = orderRepo.findByProviderOrderId(orderCode)
            .orElseThrow(() -> new NotFoundException("ORDER_NOT_FOUND", "Order not found for code: " + orderCode));

    if (!order.getUserId().equals(userId)) {
        throw new IllegalArgumentException("Order does not belong to user");
    }

    confirmPaymentByOrderCode(orderCode);
}
```

- [ ] **Step 6: Run test to verify it passes**

```bash
cd backend
mvn -q -pl payment -am test -Dtest=PaymentServiceConcurrencyTest
```

Expected: Test PASSES - exactly one payment row, one point credit effect

- [ ] **Step 7: Commit payment confirmation fix**

```bash
git add backend/payment/src/main/java/com/fuoverflow/payment/application/PaymentService.java
git add backend/payment/src/main/java/com/fuoverflow/payment/persistence/PaymentRepository.java
git add backend/payment/src/test/java/com/fuoverflow/payment/application/PaymentServiceConcurrencyTest.java
git commit -m "fix(payment): make PayOS confirmation idempotent

- Reuse single confirmation flow
- Skip duplicate processing if payment row already exists
- Validate order ownership in manual confirmation path
- Add concurrency test for duplicate webhooks

Prevents duplicate payment records and duplicate point credits.

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

## Task 4: Money Unit Semantics and Request Validation

**Priority:** CRITICAL - current amount handling is ambiguous and likely wrong

**Files:**
- Modify: `backend/payment/src/main/java/com/fuoverflow/payment/api/dto/CreatePaymentLinkRequest.java:8-21`
- Modify: `backend/payment/src/main/java/com/fuoverflow/payment/application/PaymentService.java:31-58`
- Test: `backend/payment/src/test/java/com/fuoverflow/payment/application/PaymentServiceAmountTest.java` (create)

**Interfaces:**
- Consumes: `CreatePaymentLinkRequest.amount()`
- Produces: explicit VND integer semantics for PayOS + consistent DB storage in `orders.total_cents`

- [ ] **Step 1: Decide and document canonical amount unit**

Adopt this rule for the whole module:

```text
API request amount = whole-number VND
PayOS amount = whole-number VND
orders.subtotal_cents / total_cents = store same VND amount for now (despite legacy _cents suffix)
points earned = VND amount / 100
```

Reason: PayOS expects integer VND; changing DB column semantics now is smaller than introducing mixed unit conversion bugs.

- [ ] **Step 2: Write failing tests for whole-number amount validation**

Create `backend/payment/src/test/java/com/fuoverflow/payment/application/PaymentServiceAmountTest.java`:

```java
package com.fuoverflow.payment.application;

import com.fuoverflow.payment.api.dto.CreatePaymentLinkRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentServiceAmountTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void createPaymentLinkRequest_shouldRejectFractionalVndAmount() {
        CreatePaymentLinkRequest request = new CreatePaymentLinkRequest(
                new BigDecimal("1000.50"),
                "http://localhost/return",
                "http://localhost/cancel",
                "nap diem"
        );

        Set<ConstraintViolation<CreatePaymentLinkRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
    }

    @Test
    void createPaymentLinkRequest_shouldAcceptWholeNumberVndAmount() {
        CreatePaymentLinkRequest request = new CreatePaymentLinkRequest(
                new BigDecimal("1000"),
                "http://localhost/return",
                "http://localhost/cancel",
                "nap diem"
        );

        Set<ConstraintViolation<CreatePaymentLinkRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

```bash
cd backend
mvn -q -pl payment -am test -Dtest=PaymentServiceAmountTest
```

Expected: fractional value currently passes validation → test fails

- [ ] **Step 4: Tighten DTO validation and description constraints**

Edit `backend/payment/src/main/java/com/fuoverflow/payment/api/dto/CreatePaymentLinkRequest.java`:

```java
package com.fuoverflow.payment.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreatePaymentLinkRequest(
        @NotNull(message = "amount is required")
        @DecimalMin(value = "1000", message = "amount must be >= 1000 VND")
        @Digits(integer = 10, fraction = 0, message = "amount must be a whole-number VND value")
        BigDecimal amount,

        @NotBlank(message = "returnUrl is required")
        String returnUrl,

        @NotBlank(message = "cancelUrl is required")
        String cancelUrl,

        @Size(max = 255, message = "description must be <= 255 characters")
        String description
) {
}
```

- [ ] **Step 5: Remove incorrect `* 100` conversion in service**

Edit `backend/payment/src/main/java/com/fuoverflow/payment/application/PaymentService.java` in `createPaymentLink`:

```java
int totalAmountVnd = amount.intValueExact();

OrderEntity order = OrderEntity.create(userId,
        totalAmountVnd,
        "VND", "payos", String.valueOf(orderCode));
```

Keep PayOS request amount consistent:

```java
.amount(amount.longValueExact())
```

- [ ] **Step 6: Run tests to verify pass**

```bash
cd backend
mvn -q -pl payment -am test -Dtest=PaymentServiceAmountTest
```

Expected: tests PASS; fractional VND rejected

- [ ] **Step 7: Commit money semantics fix**

```bash
git add backend/payment/src/main/java/com/fuoverflow/payment/api/dto/CreatePaymentLinkRequest.java
git add backend/payment/src/main/java/com/fuoverflow/payment/application/PaymentService.java
git add backend/payment/src/test/java/com/fuoverflow/payment/application/PaymentServiceAmountTest.java
git commit -m "fix(payment): enforce whole-number VND amount semantics

- Reject fractional amount values
- Remove incorrect *100 conversion before order persistence
- Keep PayOS and DB amount handling consistent
- Add amount validation tests

Prevents DB/gateway unit mismatch in PayOS payment flow.

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---
## Task 5: Access Control - Restrict Payment Status to Order Owner

**Priority:** HIGH - current endpoint leaks other users' payment metadata

**Files:**
- Modify: `backend/payment/src/main/java/com/fuoverflow/payment/api/PaymentController.java:36-39`
- Modify: `backend/payment/src/main/java/com/fuoverflow/payment/application/PaymentService.java:86-109`
- Test: `backend/payment/src/test/java/com/fuoverflow/payment/api/PaymentControllerTest.java` (create)

**Interfaces:**
- Consumes: `AuthContext.currentUserId()`
- Produces: `getPaymentStatus(String orderCode, UUID userId)` owner-checked service API

- [ ] **Step 1: Write failing controller/service ownership test**

Create `backend/payment/src/test/java/com/fuoverflow/payment/api/PaymentControllerTest.java` with test skeleton:

```java
package com.fuoverflow.payment.api;

import com.fuoverflow.payment.application.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PaymentController.class)
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PaymentService paymentService;

    @Test
    void getPaymentStatus_shouldRequireAuthenticatedUserContext() throws Exception {
        mockMvc.perform(get("/api/v1/payment/status")
                        .param("orderCode", "12345")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }
}
```

Also add service-level test later or in separate class asserting owner mismatch throws `NotFoundException`.

- [ ] **Step 2: Add owner-aware service signature**

Edit `backend/payment/src/main/java/com/fuoverflow/payment/application/PaymentService.java`:

```java
@Transactional(readOnly = true)
public PaymentStatusResponse getPaymentStatus(String orderCode, UUID userId) {
    OrderEntity order = orderRepo.findByProviderOrderId(orderCode)
            .orElseThrow(() -> new NotFoundException("ORDER_NOT_FOUND", "Order not found"));

    if (!order.getUserId().equals(userId)) {
        throw new NotFoundException("ORDER_NOT_FOUND", "Order not found");
    }

    java.util.Optional<PaymentEntity> paymentOpt = paymentRepo.findByOrderId(order.getId())
            .stream().findFirst();

    String status = order.getStatus();
    long pointsEarned = 0;
    if (paymentOpt.isPresent() && "succeeded".equals(paymentOpt.get().getStatus())) {
        pointsEarned = order.getTotalCents() / 100;
    }

    return new PaymentStatusResponse(
            orderCode,
            status,
            order.getTotalCents(),
            order.getCurrency(),
            pointsEarned,
            order.getProvider(),
            order.getProviderOrderId()
    );
}
```

- [ ] **Step 3: Pass authenticated user from controller**

Edit `backend/payment/src/main/java/com/fuoverflow/payment/api/PaymentController.java`:

```java
@GetMapping("/status")
public ApiResponse<PaymentStatusResponse> getPaymentStatus(@RequestParam String orderCode) {
    UUID userId = AuthContext.currentUserId();
    return ApiResponse.ok(paymentService.getPaymentStatus(orderCode, userId));
}
```

- [ ] **Step 4: Run targeted tests**

```bash
cd backend
mvn -q -pl payment -am test -Dtest=PaymentControllerTest
```

Expected: endpoint still requires auth and uses current user in service path

- [ ] **Step 5: Commit access control fix**

```bash
git add backend/payment/src/main/java/com/fuoverflow/payment/api/PaymentController.java
git add backend/payment/src/main/java/com/fuoverflow/payment/application/PaymentService.java
git add backend/payment/src/test/java/com/fuoverflow/payment/api/PaymentControllerTest.java
git commit -m "fix(payment): restrict payment status to order owner

- Pass authenticated user id to payment status service
- Return not found for cross-user order lookups
- Add controller test coverage

Prevents users from querying other users' payment status.

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

## Task 6: API Contract Consistency - Rename Misleading `checkoutUrl` Field

**Priority:** HIGH - current status response returns order code in `checkoutUrl`

**Files:**
- Modify: `backend/payment/src/main/java/com/fuoverflow/payment/api/dto/PaymentStatusResponse.java:4-12`
- Test: `backend/payment/src/test/java/com/fuoverflow/payment/application/PaymentStatusResponseTest.java` (create)

**Interfaces:**
- Consumes: existing status response structure
- Produces: `providerOrderId` field instead of misleading `checkoutUrl`

- [ ] **Step 1: Write failing DTO expectation test**

Create `backend/payment/src/test/java/com/fuoverflow/payment/application/PaymentStatusResponseTest.java`:

```java
package com.fuoverflow.payment.application;

import com.fuoverflow.payment.api.dto.PaymentStatusResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentStatusResponseTest {

    @Test
    void response_shouldExposeProviderOrderIdInsteadOfCheckoutUrl() {
        PaymentStatusResponse response = new PaymentStatusResponse(
                "order-1", "pending", 1000L, "VND", 0L, "payos", "provider-order-1"
        );

        assertThat(response.providerOrderId()).isEqualTo("provider-order-1");
    }
}
```

- [ ] **Step 2: Run test to verify compile fails**

```bash
cd backend
mvn -q -pl payment -am test -Dtest=PaymentStatusResponseTest
```

Expected: compile/test FAILS because `providerOrderId()` does not exist yet

- [ ] **Step 3: Rename DTO field and adjust references**

Edit `backend/payment/src/main/java/com/fuoverflow/payment/api/dto/PaymentStatusResponse.java`:

```java
package com.fuoverflow.payment.api.dto;

public record PaymentStatusResponse(
        String orderCode,
        String status,
        long amountCents,
        String currency,
        long pointsEarned,
        String provider,
        String providerOrderId
) {}
```

- [ ] **Step 4: Run tests to verify pass**

```bash
cd backend
mvn -q -pl payment -am test -Dtest=PaymentStatusResponseTest
```

Expected: test PASSES

- [ ] **Step 5: Commit API contract fix**

```bash
git add backend/payment/src/main/java/com/fuoverflow/payment/api/dto/PaymentStatusResponse.java
git add backend/payment/src/test/java/com/fuoverflow/payment/application/PaymentStatusResponseTest.java
git commit -m "fix(payment): rename misleading checkoutUrl status field

- Replace checkoutUrl with providerOrderId in status response
- Keep returned value aligned with field semantics
- Add DTO contract test

Clarifies API output and prevents frontend misuse.

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

## Task 7: Webhook Event Idempotency and Invalid Signature Handling

**Priority:** HIGH - current event dedupe key is weak and invalid signature path can violate unique index

**Files:**
- Modify: `backend/payment/src/main/java/com/fuoverflow/payment/api/PayOSWebhookController.java:38-77`
- Test: `backend/payment/src/test/java/com/fuoverflow/payment/api/PayOSWebhookControllerTest.java` (create)

**Interfaces:**
- Consumes: `payOS.webhooks().verify(body)`
- Produces: stable event persistence with unique id per webhook attempt

- [ ] **Step 1: Write failing invalid-signature test**

Create `backend/payment/src/test/java/com/fuoverflow/payment/api/PayOSWebhookControllerTest.java`:

```java
package com.fuoverflow.payment.api;

import com.fuoverflow.payment.application.PaymentService;
import com.fuoverflow.payment.persistence.PaymentWebhookEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import vn.payos.PayOS;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PayOSWebhookController.class)
class PayOSWebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PaymentService paymentService;

    @MockBean
    private PaymentWebhookEventRepository webhookRepo;

    @MockBean
    private PayOS payOS;

    @Test
    void invalidSignature_shouldReturnBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/payment/payos/webhook")
                        .contentType(APPLICATION_JSON)
                        .content("{\"bad\":true}"))
                .andExpect(status().isBadRequest());
    }
}
```

- [ ] **Step 2: Strengthen provider event id derivation**

Edit `backend/payment/src/main/java/com/fuoverflow/payment/api/PayOSWebhookController.java` and add helpers:

```java
private String deriveProviderEventId(WebhookData webhookData) {
    if (webhookData.getReference() != null && !webhookData.getReference().isBlank()) {
        return webhookData.getReference();
    }
    return "payos-order-" + webhookData.getOrderCode() + "-code-" + webhookData.getCode();
}

private String invalidEventId(String body) {
    return "invalid-" + java.util.UUID.nameUUIDFromBytes(
            body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
}
```

- [ ] **Step 3: Use deterministic invalid signature ids instead of literal `unknown`**

Replace invalid signature block with:

```java
} catch (PayOSException e) {
    log.warn("Invalid PayOS webhook signature: {}", e.getMessage());
    String invalidEventId = invalidEventId(body);
    if (webhookRepo.findByProviderAndProviderEventId("payos", invalidEventId).isEmpty()) {
        PaymentWebhookEventEntity event = PaymentWebhookEventEntity.create(
                "payos", invalidEventId, "payment.invalid", body, false);
        webhookRepo.save(event);
    }
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid signature");
}
```

- [ ] **Step 4: Use derived event id in happy path**

Replace provider event id assignment with:

```java
String providerEventId = deriveProviderEventId(webhookData);
```

- [ ] **Step 5: Run targeted tests**

```bash
cd backend
mvn -q -pl payment -am test -Dtest=PayOSWebhookControllerTest
```

Expected: invalid signature path returns 400; no constant `unknown` id remains

- [ ] **Step 6: Commit webhook id fix**

```bash
git add backend/payment/src/main/java/com/fuoverflow/payment/api/PayOSWebhookController.java
git add backend/payment/src/test/java/com/fuoverflow/payment/api/PayOSWebhookControllerTest.java
git commit -m "fix(payment): harden PayOS webhook event id handling

- Derive stable event id from reference or orderCode+code
- Persist invalid signatures with deterministic unique ids
- Remove literal unknown provider_event_id usage
- Add webhook controller test coverage

Prevents duplicate-key failures during invalid webhook handling.

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---
## Task 8: Idempotent Payment Link Creation and Order Creation Failure Cleanup

**Priority:** HIGH - current create flow creates orphan pending orders and ignores idempotency

**Files:**
- Modify: `backend/payment/src/main/java/com/fuoverflow/payment/application/PaymentService.java:31-62`
- Modify: `backend/payment/src/main/java/com/fuoverflow/payment/persistence/OrderRepository.java:8-14`
- Test: `backend/payment/src/test/java/com/fuoverflow/payment/application/PaymentLinkCreationTest.java` (create)

**Interfaces:**
- Consumes: `OrderRepository.findByIdempotencyKey(String)`
- Produces: idempotent `createPaymentLink(..., UUID userId)` behavior that only persists order after successful PayOS response

- [ ] **Step 1: Introduce request idempotency source**

Use this helper in service:

```java
private String buildIdempotencyKey(UUID userId, BigDecimal amount, String returnUrl, String cancelUrl, String description) {
    String raw = userId + "|" + amount.toPlainString() + "|" + returnUrl + "|" + cancelUrl + "|" + (description == null ? "" : description);
    return java.util.UUID.nameUUIDFromBytes(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
}
```

- [ ] **Step 2: Write failing test for duplicate create request**

Create `backend/payment/src/test/java/com/fuoverflow/payment/application/PaymentLinkCreationTest.java`:

```java
package com.fuoverflow.payment.application;

import com.fuoverflow.payment.persistence.OrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import vn.payos.PayOS;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class PaymentLinkCreationTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private OrderRepository orderRepo;

    @MockitoBean
    private PayOS payOS;

    @Test
    void createPaymentLink_duplicateRequest_shouldNotCreateAdditionalOrder() {
        UUID userId = UUID.randomUUID();
        // Assuming PayOS mock returns a stubbed response
        paymentService.createPaymentLink(
                new BigDecimal("5000"),
                "deposit",
                "http://localhost/return",
                "http://localhost/cancel",
                userId
        );
        long countAfterFirst = orderRepo.findAll().stream()
                .filter(o -> o.getUserId().equals(userId))
                .count();

        paymentService.createPaymentLink(
                new BigDecimal("5000"),
                "deposit",
                "http://localhost/return",
                "http://localhost/cancel",
                userId
        );

        long countAfterSecond = orderRepo.findAll().stream()
                .filter(o -> o.getUserId().equals(userId))
                .count();

        assertThat(countAfterSecond).isEqualTo(countAfterFirst);
    }
}
```

- [ ] **Step 3: Move order persistence after successful PayOS call**

Refactor `createPaymentLink` to:

1. Build deterministic idempotency key
2. Return existing pending order response if already present
3. Call PayOS first
4. Persist order only after PayOS success

Implementation:

```java
@Transactional
public PayOSPaymentLinkResponse createPaymentLink(BigDecimal amount, String description,
                                                  String returnUrl, String cancelUrl, UUID userId) {
    String idempotencyKey = buildIdempotencyKey(userId, amount, returnUrl, cancelUrl, description);
    OrderEntity existing = orderRepo.findByIdempotencyKey(idempotencyKey).orElse(null);
    if (existing != null) {
        return new PayOSPaymentLinkResponse(null, null, existing.getProviderOrderId());
    }

    long orderCode = Math.abs(java.util.UUID.randomUUID().getMostSignificantBits());

    CreatePaymentLinkRequest request = CreatePaymentLinkRequest.builder()
            .orderCode(orderCode)
            .amount(amount.longValueExact())
            .description(description)
            .returnUrl(returnUrl)
            .cancelUrl(cancelUrl)
            .build();

    CreatePaymentLinkResponse response = payOS.paymentRequests().create(request);

    OrderEntity order = OrderEntity.create(userId,
            amount.intValueExact(),
            "VND", "payos", String.valueOf(orderCode));
    order.setIdempotencyKey(idempotencyKey);
    orderRepo.save(order);

    return new PayOSPaymentLinkResponse(
            response.getCheckoutUrl(),
            response.getQrCode(),
            String.valueOf(orderCode)
    );
}
```

- [ ] **Step 4: Run targeted tests**

```bash
cd backend
mvn -q -pl payment -am test -Dtest=PaymentLinkCreationTest
```

Expected: duplicate requests no longer create multiple orders

- [ ] **Step 5: Commit creation-flow fix**

```bash
git add backend/payment/src/main/java/com/fuoverflow/payment/application/PaymentService.java
git add backend/payment/src/test/java/com/fuoverflow/payment/application/PaymentLinkCreationTest.java
git commit -m "fix(payment): make payment link creation idempotent

- Derive deterministic idempotency key for create requests
- Avoid persisting order before successful PayOS response
- Switch order code generation away from currentTimeMillis
- Add payment link creation tests

Prevents duplicate pending orders and orphan rows on gateway failure.

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

## Task 9: Status Naming Consistency Across Order and Payment

**Priority:** MEDIUM - current module mixes `paid` and `succeeded`

**Files:**
- Modify: `backend/payment/src/main/java/com/fuoverflow/payment/persistence/PaymentEntity.java:35-69`
- Modify: `backend/payment/src/main/java/com/fuoverflow/payment/application/PaymentService.java:94-98`
- Test: `backend/payment/src/test/java/com/fuoverflow/payment/application/PaymentStatusNormalizationTest.java` (create)

**Interfaces:**
- Consumes: payment status values
- Produces: one canonical success status for payment rows

- [ ] **Step 1: Pick canonical payment success status**

Use `paid` everywhere if you want alignment with `orders.status`. This is simpler than preserving `succeeded`.

- [ ] **Step 2: Write failing test**

Create `backend/payment/src/test/java/com/fuoverflow/payment/application/PaymentStatusNormalizationTest.java`:

```java
package com.fuoverflow.payment.application;

import com.fuoverflow.payment.persistence.PaymentEntity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentStatusNormalizationTest {

    @Test
    void markPaid_shouldSetStatusToPaid() {
        PaymentEntity payment = PaymentEntity.create(
                UUID.randomUUID(), UUID.randomUUID(), "payos", "order-1", 1000, "VND");
        payment.markPaid(Instant.now());
        assertThat(payment.getStatus()).isEqualTo("paid");
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

```bash
cd backend
mvn -q -pl payment -am test -Dtest=PaymentStatusNormalizationTest
```

Expected: test FAILS because current `markPaid` sets `succeeded`

- [ ] **Step 4: Implement normalization**

Edit `backend/payment/src/main/java/com/fuoverflow/payment/persistence/PaymentEntity.java`:

```java
public void markPaid(Instant paidAt) {
    this.status = "paid";
    this.paidAt = paidAt;
}
```

Edit `backend/payment/src/main/java/com/fuoverflow/payment/application/PaymentService.java`:

```java
if (paymentOpt.isPresent() && "paid".equals(paymentOpt.get().getStatus())) {
    pointsEarned = order.getTotalCents() / 100;
}
```

DB constraint already allows both `paid` and `succeeded` (V26 migration), so existing rows are safe; new writes use `paid`.

- [ ] **Step 5: Run targeted test**

```bash
cd backend
mvn -q -pl payment -am test -Dtest=PaymentStatusNormalizationTest
```

Expected: tests PASS

- [ ] **Step 6: Commit status consistency fix**

```bash
git add backend/payment/src/main/java/com/fuoverflow/payment/persistence/PaymentEntity.java
git add backend/payment/src/main/java/com/fuoverflow/payment/application/PaymentService.java
git add backend/payment/src/test/java/com/fuoverflow/payment/application/PaymentStatusNormalizationTest.java
git commit -m "fix(payment): normalize payment success status naming

- Use paid as canonical payment success state
- Align status checks with order status semantics
- Add normalization tests

Reduces ambiguity across order/payment state handling.

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

## Task 10: Documentation Drift - Schema/Knowledge Update

**Priority:** LOW - code and knowledge schema currently diverge for points tables

**Files:**
- Modify: `.knowledge/database-schema.sql:778-791`

**Interfaces:**
- Consumes: current points implementation
- Produces: accurate schema reference for future work

- [ ] **Step 1: Update knowledge doc to reflect actual tables**

In `.knowledge/database-schema.sql`, replace/augment the points ledger section with:

```sql
-- Bảng số dư điểm hiện tại: denormalized balance for quick reads
create table point_balances (
    id uuid primary key,
    user_id uuid not null,
    balance_points bigint not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create unique index ux_point_balances_user on point_balances(user_id);

-- Bảng lịch sử giao dịch điểm
create table point_transactions (
    id uuid primary key,
    user_id uuid not null,
    amount_points bigint not null,
    direction varchar(16) not null,
    type varchar(64) not null,
    reference_type varchar(64) null,
    reference_id uuid null,
    payment_id uuid null,
    description text null,
    created_at timestamptz not null default now(),
    constraint point_transactions_direction_check check (direction in ('credit','debit'))
);
```

Keep a note if `points_ledger` remains planned/future but unused.

- [ ] **Step 2: Commit docs update**

```bash
git add .knowledge/database-schema.sql
git commit -m "docs(payment): align schema reference with points implementation

- Document point_balances and point_transactions tables
- Remove drift between knowledge doc and codebase

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

## Task 11: Final Integration Verification

**Priority:** REQUIRED - do not claim complete until verified

**Files:**
- No code changes required unless failures found
- Verify all files changed in Tasks 1-10

**Interfaces:**
- Consumes: all prior tasks complete
- Produces: validated payment module with evidence

- [ ] **Step 1: Run targeted auth tests**

```bash
cd backend
mvn -q -pl auth -am test -Dtest=SecurityConfigTest
```

Expected: PASS

- [ ] **Step 2: Run targeted payment tests**

```bash
cd backend
mvn -q -pl payment -am test -Dtest=PointServiceConcurrencyTest,PaymentServiceConcurrencyTest,PaymentServiceAmountTest,PaymentControllerTest,PaymentStatusResponseTest,PayOSWebhookControllerTest,PaymentLinkCreationTest,PaymentStatusNormalizationTest
```

Expected: PASS

- [ ] **Step 3: Run impacted module test suite**

```bash
cd backend
mvn -q -pl payment,auth -am test
```

Expected: PASS

- [ ] **Step 4: Sanity-check changed files**

Confirm these exact files reflect final design:

```text
backend/auth/src/main/java/com/fuoverflow/auth/config/SecurityConfig.java
backend/auth/src/test/java/com/fuoverflow/auth/config/SecurityConfigTest.java
backend/payment/src/main/java/com/fuoverflow/payment/api/PaymentController.java
backend/payment/src/main/java/com/fuoverflow/payment/api/PayOSWebhookController.java
backend/payment/src/main/java/com/fuoverflow/payment/api/dto/CreatePaymentLinkRequest.java
backend/payment/src/main/java/com/fuoverflow/payment/api/dto/PaymentStatusResponse.java
backend/payment/src/main/java/com/fuoverflow/payment/application/PaymentService.java
backend/payment/src/main/java/com/fuoverflow/payment/application/PointService.java
backend/payment/src/test/java/com/fuoverflow/payment/application/PointServiceConcurrencyTest.java
backend/payment/src/test/java/com/fuoverflow/payment/application/PaymentServiceConcurrencyTest.java
backend/payment/src/test/java/com/fuoverflow/payment/application/PaymentServiceAmountTest.java
backend/payment/src/test/java/com/fuoverflow/payment/api/PaymentControllerTest.java
backend/payment/src/test/java/com/fuoverflow/payment/application/PaymentStatusResponseTest.java
backend/payment/src/test/java/com/fuoverflow/payment/api/PayOSWebhookControllerTest.java
backend/payment/src/test/java/com/fuoverflow/payment/application/PaymentLinkCreationTest.java
backend/payment/src/test/java/com/fuoverflow/payment/application/PaymentStatusNormalizationTest.java
.env.example
.knowledge/database-schema.sql
```

- [ ] **Step 5: Create integration commit or stop for review**

If executing in one branch with per-task commits already present, no new code commit required. Otherwise create a final integration commit:

```bash
git status
git log --oneline -10
```

Expected: clean working tree, task commits present, all tests green

---

## Self-Review

### Spec coverage

- webhook auth bug → Task 1
- missing PayOS env config → Task 1
- point balance race → Task 2
- payment confirmation duplicate processing → Task 3
- amount unit ambiguity → Task 4
- owner validation for status endpoint → Task 5
- misleading response field → Task 6
- weak webhook event ids + invalid signature duplicate key → Task 7
- idempotency + orphan order creation → Task 8
- timestamp order code collision risk (folded into Task 8 helper)
- status naming inconsistency → Task 9
- schema docs drift → Task 10
- verification → Task 11

### Placeholder scan

- No TBD/TODO placeholders left
- Each task includes concrete file paths
- Each task includes concrete commands
- Each code-changing step includes implementation snippets

### Type consistency

- `getPaymentStatus(String orderCode, UUID userId)` introduced in Task 5 and used by controller there
- `providerOrderId` field introduced in Task 6 and used consistently
- `generateOrderCode()` helper folded into Task 8 create flow

Plan complete and saved to `docs/superpowers/plans/2026-06-21-payos-payment-bugs-fix.md`. Two execution options:

**1. Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints

**Which approach?**
