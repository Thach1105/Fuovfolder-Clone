# Points Balance Sync — Unified Ledger + Cached Balance

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Unify the points system so every ledger write (credit/debit/adjust) atomically updates `point_balances`, and all balance reads use the cached balance instead of `SUM(delta)`.

**Architecture:** `points_ledger` remains the source of truth (append-only audit trail). `point_balances` becomes a denormalized cache updated atomically in the same transaction as every ledger INSERT. A scheduled reconciliation job detects drift between `SUM(ledger)` and cached balance. The payment module's duplicate `PointService.creditPoints/debitPoints` is retired — all point mutations route through `PointsWalletService`.

**Tech Stack:** Java 21, Spring Boot 3.5.x, JPA/Hibernate, Flyway, `@Scheduled`

## Global Constraints

- No DB foreign keys.
- Hibernate `ddl-auto = validate`.
- Flyway for all schema changes.
- `point_balances` table already exists (V26). Entity `PointBalanceEntity` already exists in payment module.
- Next migration version: **V37**.
- Constructor injection only.
- No new infrastructure (no Redis, no Kafka).

## Current State Analysis

**Problem:** Two independent point systems exist:

| System | Ledger table | Balance table | Module |
|--------|-------------|---------------|--------|
| Award  | `points_ledger` | _(none — uses SUM)_ | `award` |
| Payment | `point_transactions` | `point_balances` | `payment` |

**Callers of `PointsWalletService`** (award module — the one we keep):
- `AdminPointsController` — admin adjust
- `PaymentService.creditPointsForPaidOrder()` — deposit topup
- `CourseraRequestService` — debit for coursera request
- `CourseraRequestAdminService` — credit refund
- `MembershipService` — debit for membership purchase

**Callers of `PointService`** (payment module — the one we retire):
- `PointController` — `GET /api/v1/points/balance` and `GET /api/v1/points/transactions`

**Target:** All callers use `PointsWalletService`. Every mutation updates both `points_ledger` AND `point_balances` atomically. Balance reads hit `point_balances` (O(1)).

---

### Task 1: Move `PointBalanceEntity` + Repository to Award Module

The `point_balances` table entity currently lives in the `payment` module but needs to be accessed by `PointsWalletService` in the `award` module.

**Files:**
- Create: `backend/award/src/main/java/com/fuoverflow/award/persistence/PointBalanceEntity.java`
- Create: `backend/award/src/main/java/com/fuoverflow/award/persistence/PointBalanceRepository.java`
- Modify: `backend/payment/src/main/java/com/fuoverflow/payment/persistence/PointBalanceEntity.java` (delete)
- Modify: `backend/payment/src/main/java/com/fuoverflow/payment/persistence/PointBalanceRepository.java` (delete)
- Modify: `backend/payment/src/main/java/com/fuoverflow/payment/application/PointService.java` (update imports)
- Modify: `backend/payment/src/main/java/com/fuoverflow/payment/api/PointController.java` (update imports)
- Test: `backend/payment/src/test/java/com/fuoverflow/payment/application/PointServiceTest.java` (update imports)

**Interfaces:**
- Produces: `com.fuoverflow.award.persistence.PointBalanceEntity` — same API as current payment version
- Produces: `com.fuoverflow.award.persistence.PointBalanceRepository` — `findByUserId(UUID): Optional<PointBalanceEntity>`

- [ ] **Step 1: Create `PointBalanceEntity` in award module**

```java
// backend/award/src/main/java/com/fuoverflow/award/persistence/PointBalanceEntity.java
package com.fuoverflow.award.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "point_balances")
public class PointBalanceEntity {
    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(name = "balance_points", nullable = false)
    private long balancePoints;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PointBalanceEntity() {}

    public static PointBalanceEntity create(UUID userId) {
        PointBalanceEntity e = new PointBalanceEntity();
        e.id = UUID.randomUUID();
        e.userId = userId;
        e.balancePoints = 0L;
        e.createdAt = Instant.now();
        e.updatedAt = Instant.now();
        return e;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public long getBalancePoints() { return balancePoints; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void adjustBalance(int delta) {
        this.balancePoints += delta;
        this.updatedAt = Instant.now();
    }

    public void setBalancePoints(long points) {
        this.balancePoints = points;
        this.updatedAt = Instant.now();
    }
}
```

- [ ] **Step 2: Create `PointBalanceRepository` in award module**

```java
// backend/award/src/main/java/com/fuoverflow/award/persistence/PointBalanceRepository.java
package com.fuoverflow.award.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface PointBalanceRepository extends JpaRepository<PointBalanceEntity, UUID> {
    Optional<PointBalanceEntity> findByUserId(UUID userId);
}
```

- [ ] **Step 3: Delete old entity/repo from payment module**

Delete these files:
- `backend/payment/src/main/java/com/fuoverflow/payment/persistence/PointBalanceEntity.java`
- `backend/payment/src/main/java/com/fuoverflow/payment/persistence/PointBalanceRepository.java`
- `backend/payment/src/main/java/com/fuoverflow/payment/domain/PointBalance.java`

- [ ] **Step 4: Update `PointService` in payment to import from award module**

```java
// backend/payment/src/main/java/com/fuoverflow/payment/application/PointService.java
// Change import from:
//   import com.fuoverflow.payment.persistence.PointBalanceEntity;
//   import com.fuoverflow.payment.persistence.PointBalanceRepository;
// To:
import com.fuoverflow.award.persistence.PointBalanceEntity;
import com.fuoverflow.award.persistence.PointBalanceRepository;
```

- [ ] **Step 5: Update `PointController` in payment to import from award module**

```java
// Change import from:
//   import com.fuoverflow.payment.persistence.PointBalanceEntity;
// To:
import com.fuoverflow.award.persistence.PointBalanceEntity;
```

- [ ] **Step 6: Update `PointServiceTest` to import from award module**

```java
// Change import from:
//   import com.fuoverflow.payment.persistence.PointBalanceEntity;
//   import com.fuoverflow.payment.persistence.PointBalanceRepository;
// To:
import com.fuoverflow.award.persistence.PointBalanceEntity;
import com.fuoverflow.award.persistence.PointBalanceRepository;
```

- [ ] **Step 7: Add award module dependency to payment pom.xml if not present**

Check `backend/payment/pom.xml` — if `<artifactId>award</artifactId>` dependency is not listed, add:

```xml
<dependency>
    <groupId>com.fuoverflow</groupId>
    <artifactId>award</artifactId>
    <version>${project.version}</version>
</dependency>
```

- [ ] **Step 8: Compile and verify**

```bash
cd backend && mvn -q -DskipTests compile
```

Expected: BUILD SUCCESS

- [ ] **Step 9: Run tests**

```bash
cd backend && mvn -q test
```

Expected: All tests pass.

- [ ] **Step 10: Commit**

```bash
git add -A
git commit -m "refactor: move PointBalanceEntity to award module for unified points system"
```

---

### Task 2: Update `PointsWalletService` to Sync `point_balances` Atomically

Every ledger write must also update the cached balance in the same `@Transactional`.

**Files:**
- Modify: `backend/award/src/main/java/com/fuoverflow/award/application/PointsWalletService.java`
- Test: `backend/award/src/test/java/com/fuoverflow/award/application/PointsWalletServiceTest.java`

**Interfaces:**
- Consumes: `PointBalanceRepository.findByUserId(UUID)`, `PointBalanceEntity.adjustBalance(int)`
- Produces: `getBalance(UUID): long` — now reads from `point_balances` instead of `SUM(delta)`

- [ ] **Step 1: Write failing test — balance is updated after credit**

```java
// backend/award/src/test/java/com/fuoverflow/award/application/PointsWalletServiceTest.java
package com.fuoverflow.award.application;

import com.fuoverflow.award.persistence.PointBalanceEntity;
import com.fuoverflow.award.persistence.PointBalanceRepository;
import com.fuoverflow.award.persistence.PointsLedgerEntity;
import com.fuoverflow.award.persistence.PointsLedgerRepository;
import com.fuoverflow.user.persistence.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PointsWalletServiceTest {

    @Mock private PointsLedgerRepository ledgerRepo;
    @Mock private PointBalanceRepository balanceRepo;
    @Mock private UserRepository userRepo;

    private PointsWalletService service;

    @BeforeEach
    void setUp() {
        service = new PointsWalletService(ledgerRepo, balanceRepo, userRepo);
    }

    @Test
    void credit_shouldInsertLedgerAndUpdateBalance() {
        UUID userId = UUID.randomUUID();
        when(userRepo.existsByIdAndDeletedAtIsNull(userId)).thenReturn(true);

        PointBalanceEntity balance = PointBalanceEntity.create(userId);
        when(balanceRepo.findByUserId(userId)).thenReturn(Optional.of(balance));
        when(ledgerRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.credit(userId, 500, "test", "manual", UUID.randomUUID());

        assertThat(balance.getBalancePoints()).isEqualTo(500);
        verify(balanceRepo).save(balance);
        verify(ledgerRepo).save(any(PointsLedgerEntity.class));
    }

    @Test
    void debit_shouldInsertLedgerAndReduceBalance() {
        UUID userId = UUID.randomUUID();
        when(userRepo.existsByIdAndDeletedAtIsNull(userId)).thenReturn(true);

        PointBalanceEntity balance = PointBalanceEntity.create(userId);
        balance.adjustBalance(1000);
        when(balanceRepo.findByUserId(userId)).thenReturn(Optional.of(balance));
        when(ledgerRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.debit(userId, 300, "purchase", "membership", UUID.randomUUID());

        assertThat(balance.getBalancePoints()).isEqualTo(700);
        verify(balanceRepo).save(balance);
    }

    @Test
    void debit_shouldRejectInsufficientBalance() {
        UUID userId = UUID.randomUUID();
        when(userRepo.existsByIdAndDeletedAtIsNull(userId)).thenReturn(true);

        PointBalanceEntity balance = PointBalanceEntity.create(userId);
        balance.adjustBalance(100);
        when(balanceRepo.findByUserId(userId)).thenReturn(Optional.of(balance));

        assertThatThrownBy(() ->
                service.debit(userId, 500, "purchase", "membership", UUID.randomUUID())
        ).hasMessageContaining("Insufficient");
    }

    @Test
    void adjust_positiveCreatesBalanceIfNotExists() {
        UUID userId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        when(userRepo.existsByIdAndDeletedAtIsNull(userId)).thenReturn(true);
        when(balanceRepo.findByUserId(userId)).thenReturn(Optional.empty());
        when(balanceRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(ledgerRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.adjust(userId, 200, "bonus", actorId);

        ArgumentCaptor<PointBalanceEntity> captor = ArgumentCaptor.forClass(PointBalanceEntity.class);
        verify(balanceRepo).save(captor.capture());
        assertThat(captor.getValue().getBalancePoints()).isEqualTo(200);
    }

    @Test
    void getBalance_readsFromCachedBalance() {
        UUID userId = UUID.randomUUID();
        when(userRepo.existsByIdAndDeletedAtIsNull(userId)).thenReturn(true);

        PointBalanceEntity balance = PointBalanceEntity.create(userId);
        balance.adjustBalance(750);
        when(balanceRepo.findByUserId(userId)).thenReturn(Optional.of(balance));

        long result = service.getBalance(userId);

        assertThat(result).isEqualTo(750);
        verify(ledgerRepo, never()).sumDeltaByUserId(any());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd backend && mvn -q -pl award -am test -Dtest=PointsWalletServiceTest
```

Expected: FAIL — `PointsWalletService` constructor doesn't accept `PointBalanceRepository` yet.

- [ ] **Step 3: Update `PointsWalletService` implementation**

Replace the entire `PointsWalletService.java`:

```java
package com.fuoverflow.award.application;

import com.fuoverflow.award.persistence.PointBalanceEntity;
import com.fuoverflow.award.persistence.PointBalanceRepository;
import com.fuoverflow.award.persistence.PointsLedgerEntity;
import com.fuoverflow.award.persistence.PointsLedgerRepository;
import com.fuoverflow.common.exception.BadRequestException;
import com.fuoverflow.common.exception.ConflictException;
import com.fuoverflow.user.persistence.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class PointsWalletService {
    public static final String SOURCE_ADMIN_ADJUST = "admin_adjust";
    public static final String SOURCE_COURSERA_REQUEST = "coursera_request";
    public static final String SOURCE_SOURCE_PURCHASE = "source_purchase";
    public static final String SOURCE_TOPUP = "topup";

    private final PointsLedgerRepository ledgerRepository;
    private final PointBalanceRepository balanceRepository;
    private final UserRepository userRepository;

    public PointsWalletService(PointsLedgerRepository ledgerRepository,
                               PointBalanceRepository balanceRepository,
                               UserRepository userRepository) {
        this.ledgerRepository = ledgerRepository;
        this.balanceRepository = balanceRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public long getBalance(UUID userId) {
        requireUserExists(userId);
        return balanceRepository.findByUserId(userId)
                .map(PointBalanceEntity::getBalancePoints)
                .orElse(0L);
    }

    @Transactional
    public PointsLedgerEntity debit(UUID userId, int amount, String reason, String sourceType, UUID sourceId) {
        if (amount <= 0) {
            throw new BadRequestException("INVALID_AMOUNT", "Debit amount must be positive");
        }
        requireUserExists(userId);
        PointBalanceEntity balance = getOrCreateBalance(userId);
        if (balance.getBalancePoints() < amount) {
            throw new ConflictException("INSUFFICIENT_POINTS", "Insufficient Fuexam Point balance");
        }
        balance.adjustBalance(-amount);
        balanceRepository.save(balance);
        return persistLedger(userId, -amount, reason, sourceType, sourceId);
    }

    @Transactional
    public PointsLedgerEntity credit(UUID userId, int amount, String reason, String sourceType, UUID sourceId) {
        if (amount <= 0) {
            throw new BadRequestException("INVALID_AMOUNT", "Credit amount must be positive");
        }
        requireUserExists(userId);
        PointBalanceEntity balance = getOrCreateBalance(userId);
        balance.adjustBalance(amount);
        balanceRepository.save(balance);
        return persistLedger(userId, amount, reason, sourceType, sourceId);
    }

    @Transactional
    public PointsLedgerEntity adjust(UUID userId, int delta, String reason, UUID actorUserId) {
        if (delta == 0) {
            throw new BadRequestException("INVALID_AMOUNT", "Adjustment delta cannot be zero");
        }
        requireUserExists(userId);
        PointBalanceEntity balance = getOrCreateBalance(userId);
        if (delta < 0 && balance.getBalancePoints() + delta < 0) {
            throw new ConflictException("INSUFFICIENT_POINTS", "Adjustment would result in negative balance");
        }
        balance.adjustBalance(delta);
        balanceRepository.save(balance);
        String fullReason = reason != null && !reason.isBlank() ? reason : "Admin adjustment";
        return persistLedger(userId, delta, fullReason, SOURCE_ADMIN_ADJUST, actorUserId);
    }

    private PointBalanceEntity getOrCreateBalance(UUID userId) {
        return balanceRepository.findByUserId(userId)
                .orElseGet(() -> {
                    try {
                        return balanceRepository.save(PointBalanceEntity.create(userId));
                    } catch (DataIntegrityViolationException ex) {
                        return balanceRepository.findByUserId(userId)
                                .orElseThrow(() -> new IllegalStateException("Failed to create balance", ex));
                    }
                });
    }

    private PointsLedgerEntity persistLedger(UUID userId, int delta, String reason, String sourceType, UUID sourceId) {
        PointsLedgerEntity entry = PointsLedgerEntity.entry(
                UUID.randomUUID(), userId, delta, reason, sourceType, sourceId, Instant.now());
        return ledgerRepository.save(entry);
    }

    private void requireUserExists(UUID userId) {
        if (!userRepository.existsByIdAndDeletedAtIsNull(userId)) {
            throw new BadRequestException("USER_NOT_FOUND", "User not found");
        }
    }
}
```

- [ ] **Step 4: Run tests**

```bash
cd backend && mvn -q -pl award -am test -Dtest=PointsWalletServiceTest
```

Expected: All 6 tests PASS.

- [ ] **Step 5: Fix callers that inject `PointsWalletService` with old constructor**

All callers use constructor injection. Since Spring auto-wires by type, adding `PointBalanceRepository` to the constructor means Spring will inject it automatically. No caller changes needed unless tests mock the constructor.

Check test files that construct `PointsWalletService` directly:

```bash
grep -rn "new PointsWalletService\|PointsWalletService(" backend/ --include="*.java" | grep -v target | grep test
```

Update any test constructors found to include the new `PointBalanceRepository` mock parameter.

- [ ] **Step 6: Full test suite**

```bash
cd backend && mvn -q test
```

Expected: All tests pass.

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "feat: sync point_balances atomically on every ledger write"
```

---

### Task 3: Migrate Balance Reads — Retire Payment `PointService`

Replace `PointController` (payment) endpoints to read from the award module's cached balance, then delete `PointService`.

**Files:**
- Modify: `backend/payment/src/main/java/com/fuoverflow/payment/api/PointController.java`
- Modify: `backend/payment/src/main/java/com/fuoverflow/payment/application/PointService.java` (delete)
- Modify: `backend/payment/src/test/java/com/fuoverflow/payment/application/PointServiceTest.java` (delete)

**Interfaces:**
- Consumes: `PointsWalletService.getBalance(UUID)`, `PointsQueryService.listLedger(UUID, int, int)`

- [ ] **Step 1: Rewrite `PointController` to use award module services**

```java
package com.fuoverflow.payment.api;

import com.fuoverflow.award.application.PointsQueryService;
import com.fuoverflow.award.application.PointsWalletService;
import com.fuoverflow.common.web.ApiResponse;
import com.fuoverflow.payment.api.dto.PointBalanceResponse;
import com.fuoverflow.payment.support.AuthContext;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/points")
public class PointController {

    private final PointsWalletService walletService;

    public PointController(PointsWalletService walletService) {
        this.walletService = walletService;
    }

    @GetMapping("/balance")
    public ApiResponse<PointBalanceResponse> getPointBalance() {
        UUID userId = AuthContext.currentUserId();
        long balance = walletService.getBalance(userId);
        return ApiResponse.ok(new PointBalanceResponse(
                BigDecimal.valueOf(balance),
                Instant.now()
        ));
    }
}
```

- [ ] **Step 2: Delete `PointService.java` and `PointServiceTest.java`**

Delete:
- `backend/payment/src/main/java/com/fuoverflow/payment/application/PointService.java`
- `backend/payment/src/test/java/com/fuoverflow/payment/application/PointServiceTest.java`

- [ ] **Step 3: Remove `/transactions` endpoint or rewrite using ledger**

If the `/api/v1/points/transactions` endpoint is used by frontend, rewrite it to use `PointsQueryService`. If not used, remove it. Check frontend usage first:

```bash
grep -rn "points/transactions" frontend/ --include="*.ts" --include="*.tsx" --include="*.vue" 2>/dev/null
```

If used — add to `PointController`:

```java
@GetMapping("/transactions")
public ApiResponse<PointsLedgerPageResponse> getTransactions(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size) {
    UUID userId = AuthContext.currentUserId();
    return ApiResponse.ok(queryService.listLedger(userId, page, size));
}
```

And add `PointsQueryService` to the constructor.

- [ ] **Step 4: Remove unused DTOs if applicable**

If `PointTransactionResponse` is no longer referenced, delete:
- `backend/payment/src/main/java/com/fuoverflow/payment/api/dto/PointTransactionResponse.java`

- [ ] **Step 5: Compile and test**

```bash
cd backend && mvn -q test
```

Expected: All tests pass.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "refactor: retire payment PointService, use award module for all point reads"
```

---

### Task 4: Backfill Migration — Seed `point_balances` from Ledger

Existing users have ledger entries but no `point_balances` rows. Add a Flyway migration to backfill.

**Files:**
- Create: `backend/app/src/main/resources/db/migration/V37__backfill_point_balances_from_ledger.sql`

**Interfaces:**
- Produces: Every user with `points_ledger` entries gets a `point_balances` row matching `SUM(delta)`.

- [ ] **Step 1: Write migration**

```sql
-- V37__backfill_point_balances_from_ledger.sql
-- Seed point_balances from points_ledger for users who have ledger entries
-- but no cached balance row yet. Existing rows are updated to match SUM(delta).

INSERT INTO point_balances (id, user_id, balance_points, created_at, updated_at)
SELECT gen_random_uuid(), pl.user_id, SUM(pl.delta), now(), now()
FROM points_ledger pl
WHERE NOT EXISTS (
    SELECT 1 FROM point_balances pb WHERE pb.user_id = pl.user_id
)
GROUP BY pl.user_id;

UPDATE point_balances pb
SET balance_points = (
    SELECT COALESCE(SUM(pl.delta), 0)
    FROM points_ledger pl
    WHERE pl.user_id = pb.user_id
),
updated_at = now()
WHERE EXISTS (
    SELECT 1 FROM points_ledger pl WHERE pl.user_id = pb.user_id
);
```

- [ ] **Step 2: Verify migration runs**

```bash
cd backend && mvn -q -pl app -am spring-boot:run -Dspring-boot.run.profiles=local
```

Expected: Application starts, Flyway applies V37 without error.

- [ ] **Step 3: Verify data correctness**

```sql
-- Should return zero rows if backfill is correct
SELECT pb.user_id, pb.balance_points AS cached, COALESCE(SUM(pl.delta), 0) AS ledger_sum
FROM point_balances pb
LEFT JOIN points_ledger pl ON pl.user_id = pb.user_id
GROUP BY pb.user_id, pb.balance_points
HAVING pb.balance_points != COALESCE(SUM(pl.delta), 0);
```

Expected: 0 rows returned.

- [ ] **Step 4: Commit**

```bash
git add backend/app/src/main/resources/db/migration/V37__backfill_point_balances_from_ledger.sql
git commit -m "feat: V37 backfill point_balances from points_ledger"
```

---

### Task 5: Reconciliation Scheduled Job

Periodic job to detect and log drift between `SUM(ledger)` and `point_balances`.

**Files:**
- Create: `backend/award/src/main/java/com/fuoverflow/award/application/PointsReconciliationJob.java`
- Test: `backend/award/src/test/java/com/fuoverflow/award/application/PointsReconciliationJobTest.java`

**Interfaces:**
- Consumes: `PointsLedgerRepository.sumDeltaByUserId(UUID)`, `PointBalanceRepository.findAll()`
- Produces: Logs `WARN` for any user where cached ≠ ledger SUM. Optionally auto-corrects.

- [ ] **Step 1: Write failing test**

```java
package com.fuoverflow.award.application;

import com.fuoverflow.award.persistence.PointBalanceEntity;
import com.fuoverflow.award.persistence.PointBalanceRepository;
import com.fuoverflow.award.persistence.PointsLedgerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PointsReconciliationJobTest {

    @Mock private PointBalanceRepository balanceRepo;
    @Mock private PointsLedgerRepository ledgerRepo;

    @InjectMocks
    private PointsReconciliationJob job;

    @Test
    void shouldAutoCorrectDriftedBalance() {
        UUID userId = UUID.randomUUID();
        PointBalanceEntity balance = PointBalanceEntity.create(userId);
        balance.adjustBalance(500); // cached says 500

        when(balanceRepo.findAll()).thenReturn(List.of(balance));
        when(ledgerRepo.sumDeltaByUserId(userId)).thenReturn(800L); // ledger says 800

        job.reconcile();

        verify(balanceRepo).save(balance);
        // balance should now be 800
        assert balance.getBalancePoints() == 800;
    }

    @Test
    void shouldNotTouchCorrectBalance() {
        UUID userId = UUID.randomUUID();
        PointBalanceEntity balance = PointBalanceEntity.create(userId);
        balance.adjustBalance(500);

        when(balanceRepo.findAll()).thenReturn(List.of(balance));
        when(ledgerRepo.sumDeltaByUserId(userId)).thenReturn(500L);

        job.reconcile();

        verify(balanceRepo, never()).save(any());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd backend && mvn -q -pl award -am test -Dtest=PointsReconciliationJobTest
```

Expected: FAIL — class not found.

- [ ] **Step 3: Implement reconciliation job**

```java
package com.fuoverflow.award.application;

import com.fuoverflow.award.persistence.PointBalanceEntity;
import com.fuoverflow.award.persistence.PointBalanceRepository;
import com.fuoverflow.award.persistence.PointsLedgerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PointsReconciliationJob {
    private static final Logger log = LoggerFactory.getLogger(PointsReconciliationJob.class);

    private final PointBalanceRepository balanceRepository;
    private final PointsLedgerRepository ledgerRepository;

    public PointsReconciliationJob(PointBalanceRepository balanceRepository,
                                   PointsLedgerRepository ledgerRepository) {
        this.balanceRepository = balanceRepository;
        this.ledgerRepository = ledgerRepository;
    }

    @Scheduled(cron = "0 0 3 * * *") // 3 AM daily
    @Transactional
    public void reconcile() {
        log.info("Starting points reconciliation");
        int driftCount = 0;

        for (PointBalanceEntity balance : balanceRepository.findAll()) {
            long ledgerSum = ledgerRepository.sumDeltaByUserId(balance.getUserId());
            if (balance.getBalancePoints() != ledgerSum) {
                log.warn("Points drift detected: userId={}, cached={}, ledger={}",
                        balance.getUserId(), balance.getBalancePoints(), ledgerSum);
                balance.setBalancePoints(ledgerSum);
                balanceRepository.save(balance);
                driftCount++;
            }
        }

        log.info("Points reconciliation complete: {} drifted balances corrected", driftCount);
    }
}
```

- [ ] **Step 4: Ensure `@EnableScheduling` is present on app config**

Check `backend/app/src/main/java/com/fuoverflow/app/` for `@EnableScheduling`. If missing, add it to the main application class or a config class.

- [ ] **Step 5: Run tests**

```bash
cd backend && mvn -q -pl award -am test -Dtest=PointsReconciliationJobTest
```

Expected: All 2 tests PASS.

- [ ] **Step 6: Full test suite**

```bash
cd backend && mvn -q test
```

Expected: All tests pass.

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "feat: add daily points reconciliation job to detect and fix balance drift"
```

---

### Task 6: Clean Up Redundant `point_transactions` Usage

The `point_transactions` table (V26) was the payment module's own ledger. With all mutations going through `points_ledger`, decide whether to keep it for payment-specific audit or remove references.

**Files:**
- Modify: `backend/payment/src/main/java/com/fuoverflow/payment/persistence/PointTransactionEntity.java` (delete if unused)
- Modify: `backend/payment/src/main/java/com/fuoverflow/payment/persistence/PointTransactionRepository.java` (delete if unused)

- [ ] **Step 1: Check if `point_transactions` is referenced anywhere else**

```bash
grep -rn "point_transactions\|PointTransaction" backend/ --include="*.java" | grep -v target
```

If only referenced by the now-deleted `PointService` and `PointController` (already removed in Task 3), delete:
- `PointTransactionEntity.java`
- `PointTransactionRepository.java`
- `PointTransactionResponse.java` (if not already deleted)

- [ ] **Step 2: Compile and test**

```bash
cd backend && mvn -q test
```

Expected: All tests pass.

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "chore: remove unused PointTransaction entity and repository"
```

---

## Summary

| Before | After |
|--------|-------|
| Balance = `SUM(delta)` per query (O(n)) | Balance = `point_balances` row lookup (O(1)) |
| `point_balances` never updated by award | `point_balances` updated atomically with every ledger write |
| Two independent point systems (award + payment) | Single system via `PointsWalletService` |
| No drift detection | Daily reconciliation job at 3 AM |
| `PointService` in payment duplicates logic | Retired — all reads via award module |
