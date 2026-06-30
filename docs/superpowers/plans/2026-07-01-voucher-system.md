# Voucher System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a complete voucher/coupon system allowing admins to create discount codes (percentage or fixed-point) that users apply at checkout to reduce points cost on source, membership, and coursera purchases.

**Architecture:** New `backend/voucher/` Maven module following the existing modular monolith pattern. VoucherService exposes `preview()` and `redeem()` methods called by source/membership/coursera modules at checkout. Concurrency handled via atomic `UPDATE ... WHERE used_count < max_usage`. Admin frontend adds pages under `Fuexam-admin/app/vouchers/`. User frontend adds voucher input to existing checkout dialogs.

**Tech Stack:** Java 21, Spring Boot 3.5.3, Spring Data JPA, PostgreSQL, Flyway, Next.js 15, React 19, Radix UI, TailwindCSS, React Hook Form + Zod.

## Global Constraints

- Java 21, Spring Boot 3.5.3
- No DB foreign keys — validate references in service layer
- Flyway migrations only — Hibernate `ddl-auto=validate`
- snake_case table/column names, uuid PKs, timestamptz for times
- `ApiResponse<T>` envelope for all responses
- `@RequirePermission` for authorization
- Constructor injection, records for DTOs
- Next migration: `V38`

---

### Task 1: Flyway Migration — voucher tables + RBAC permissions

**Files:**
- Create: `backend/app/src/main/resources/db/migration/V38__voucher_system.sql`

**Interfaces:**
- Produces: Tables `vouchers`, `voucher_user_assignments`, `voucher_redemptions`; permission slugs `voucher.admin:read`, `voucher.admin:create`, `voucher.admin:update`, `voucher.admin:delete`

- [ ] **Step 1: Write the migration SQL**

Create file `backend/app/src/main/resources/db/migration/V38__voucher_system.sql`:

```sql
-- Voucher definitions
create table vouchers (
    id                       uuid primary key,
    code                     varchar(50)  not null,
    description              text,
    discount_type            varchar(16)  not null,
    discount_value           int          not null,
    max_discount_points      int,
    min_order_points         int          not null default 0,
    max_usage                int          not null,
    used_count               int          not null default 0,
    max_usage_per_user       int          not null default 1,
    applicable_types         varchar(255) not null,
    required_membership_slugs varchar(255),
    starts_at                timestamptz  not null,
    ends_at                  timestamptz  not null,
    active                   boolean      not null default true,
    created_by               uuid         not null,
    created_at               timestamptz  not null default now(),
    updated_at               timestamptz  not null default now(),
    constraint chk_voucher_discount_type check (discount_type in ('percentage', 'fixed')),
    constraint chk_voucher_discount_value check (discount_value > 0),
    constraint chk_voucher_max_usage check (max_usage > 0),
    constraint chk_voucher_dates check (ends_at > starts_at)
);

create unique index uq_vouchers_code_active on vouchers (upper(code)) where active = true;
create index idx_vouchers_active_dates on vouchers (active, starts_at, ends_at);

-- User-specific voucher assignments
create table voucher_user_assignments (
    id          uuid primary key,
    voucher_id  uuid        not null,
    user_id     uuid        not null,
    created_at  timestamptz not null default now()
);

create unique index uq_voucher_user_assignment on voucher_user_assignments (voucher_id, user_id);

-- Redemption history
create table voucher_redemptions (
    id               uuid primary key,
    voucher_id       uuid        not null,
    user_id          uuid        not null,
    transaction_type varchar(32) not null,
    transaction_id   uuid        not null,
    original_points  int         not null,
    discount_points  int         not null,
    final_points     int         not null,
    created_at       timestamptz not null default now()
);

create index idx_voucher_redemptions_voucher_user on voucher_redemptions (voucher_id, user_id);
create index idx_voucher_redemptions_transaction on voucher_redemptions (transaction_type, transaction_id);

-- RBAC permissions
insert into permissions (slug, module, resource, action, description) values
    ('voucher.admin:read',   'voucher', 'voucher.admin', 'read',   'Admin: xem danh sách voucher'),
    ('voucher.admin:create', 'voucher', 'voucher.admin', 'create', 'Admin: tạo voucher mới'),
    ('voucher.admin:update', 'voucher', 'voucher.admin', 'update', 'Admin: cập nhật voucher'),
    ('voucher.admin:delete', 'voucher', 'voucher.admin', 'delete', 'Admin: xóa voucher')
on conflict (slug) do nothing;

update roles
set permissions_json = permissions_json || '["voucher.admin:read","voucher.admin:create","voucher.admin:update","voucher.admin:delete"]'::jsonb
where slug = 'ADMIN'
  and not (permissions_json @> '["voucher.admin:read"]'::jsonb);

update roles
set permissions_json = permissions_json || '["voucher.admin:read"]'::jsonb
where slug = 'SUB_ADMIN'
  and not (permissions_json @> '["voucher.admin:read"]'::jsonb);
```

- [ ] **Step 2: Verify migration applies**

Run:
```bash
cd backend && mvn -q -pl app -am compile flyway:migrate -Dflyway.configFiles=src/main/resources/flyway.conf
```

If Flyway is not configured as a Maven plugin, verify with:
```bash
cd backend && mvn -q -pl app -am spring-boot:run -Dspring-boot.run.profiles=local &
sleep 15 && curl -s http://localhost:8080/actuator/health | grep UP
```
Expected: Application starts without Flyway migration errors. Kill the process after verification.

- [ ] **Step 3: Commit**

```bash
git add backend/app/src/main/resources/db/migration/V38__voucher_system.sql
git commit -m "feat: V38 add voucher tables and RBAC permissions"
```

---

### Task 2: Maven Module + Entities + Repositories

**Files:**
- Create: `backend/voucher/pom.xml`
- Create: `backend/voucher/src/main/java/com/fuoverflow/voucher/VoucherModule.java`
- Create: `backend/voucher/src/main/java/com/fuoverflow/voucher/persistence/VoucherEntity.java`
- Create: `backend/voucher/src/main/java/com/fuoverflow/voucher/persistence/VoucherRepository.java`
- Create: `backend/voucher/src/main/java/com/fuoverflow/voucher/persistence/VoucherUserAssignmentEntity.java`
- Create: `backend/voucher/src/main/java/com/fuoverflow/voucher/persistence/VoucherUserAssignmentRepository.java`
- Create: `backend/voucher/src/main/java/com/fuoverflow/voucher/persistence/VoucherRedemptionEntity.java`
- Create: `backend/voucher/src/main/java/com/fuoverflow/voucher/persistence/VoucherRedemptionRepository.java`
- Modify: `backend/pom.xml` — add `<module>voucher</module>`
- Modify: `backend/app/pom.xml` — add voucher dependency

**Interfaces:**
- Produces: `VoucherEntity`, `VoucherRepository` (with `atomicIncrement` query), `VoucherUserAssignmentEntity/Repository`, `VoucherRedemptionEntity/Repository`

- [ ] **Step 1: Create `backend/voucher/pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.fuoverflow</groupId>
        <artifactId>fuoverflow-backend</artifactId>
        <version>0.0.1-SNAPSHOT</version>
        <relativePath>../pom.xml</relativePath>
    </parent>
    <artifactId>fuoverflow-voucher</artifactId>
    <packaging>jar</packaging>
    <name>fuoverflow-voucher</name>
    <dependencies>
        <dependency>
            <groupId>com.fuoverflow</groupId>
            <artifactId>fuoverflow-common</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>com.fuoverflow</groupId>
            <artifactId>fuoverflow-user</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>com.fuoverflow</groupId>
            <artifactId>fuoverflow-membership</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 2: Add module to parent pom.xml**

In `backend/pom.xml`, inside `<modules>`, add `<module>voucher</module>` after the `user` module entry.

- [ ] **Step 3: Add voucher dependency to app pom.xml**

In `backend/app/pom.xml`, inside `<dependencies>`, add:
```xml
<dependency>
    <groupId>com.fuoverflow</groupId>
    <artifactId>fuoverflow-voucher</artifactId>
    <version>${project.version}</version>
</dependency>
```

- [ ] **Step 4: Create VoucherModule marker**

Create `backend/voucher/src/main/java/com/fuoverflow/voucher/VoucherModule.java`:
```java
package com.fuoverflow.voucher;

public final class VoucherModule {
    private VoucherModule() {
    }
}
```

- [ ] **Step 5: Create VoucherEntity**

Create `backend/voucher/src/main/java/com/fuoverflow/voucher/persistence/VoucherEntity.java`:
```java
package com.fuoverflow.voucher.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "vouchers")
public class VoucherEntity {
    @Id
    private UUID id;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "discount_type", nullable = false, length = 16)
    private String discountType;

    @Column(name = "discount_value", nullable = false)
    private int discountValue;

    @Column(name = "max_discount_points")
    private Integer maxDiscountPoints;

    @Column(name = "min_order_points", nullable = false)
    private int minOrderPoints;

    @Column(name = "max_usage", nullable = false)
    private int maxUsage;

    @Column(name = "used_count", nullable = false)
    private int usedCount;

    @Column(name = "max_usage_per_user", nullable = false)
    private int maxUsagePerUser;

    @Column(name = "applicable_types", nullable = false)
    private String applicableTypes;

    @Column(name = "required_membership_slugs")
    private String requiredMembershipSlugs;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public String getDescription() { return description; }
    public String getDiscountType() { return discountType; }
    public int getDiscountValue() { return discountValue; }
    public Integer getMaxDiscountPoints() { return maxDiscountPoints; }
    public int getMinOrderPoints() { return minOrderPoints; }
    public int getMaxUsage() { return maxUsage; }
    public int getUsedCount() { return usedCount; }
    public int getMaxUsagePerUser() { return maxUsagePerUser; }
    public String getApplicableTypes() { return applicableTypes; }
    public String getRequiredMembershipSlugs() { return requiredMembershipSlugs; }
    public Instant getStartsAt() { return startsAt; }
    public Instant getEndsAt() { return endsAt; }
    public boolean isActive() { return active; }
    public UUID getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setCode(String code) { this.code = code; }
    public void setDescription(String description) { this.description = description; }
    public void setDiscountType(String discountType) { this.discountType = discountType; }
    public void setDiscountValue(int discountValue) { this.discountValue = discountValue; }
    public void setMaxDiscountPoints(Integer maxDiscountPoints) { this.maxDiscountPoints = maxDiscountPoints; }
    public void setMinOrderPoints(int minOrderPoints) { this.minOrderPoints = minOrderPoints; }
    public void setMaxUsage(int maxUsage) { this.maxUsage = maxUsage; }
    public void setMaxUsagePerUser(int maxUsagePerUser) { this.maxUsagePerUser = maxUsagePerUser; }
    public void setApplicableTypes(String applicableTypes) { this.applicableTypes = applicableTypes; }
    public void setRequiredMembershipSlugs(String requiredMembershipSlugs) { this.requiredMembershipSlugs = requiredMembershipSlugs; }
    public void setStartsAt(Instant startsAt) { this.startsAt = startsAt; }
    public void setEndsAt(Instant endsAt) { this.endsAt = endsAt; }
    public void setActive(boolean active) { this.active = active; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public static VoucherEntity create(
            UUID id, String code, String description, String discountType, int discountValue,
            Integer maxDiscountPoints, int minOrderPoints, int maxUsage, int maxUsagePerUser,
            String applicableTypes, String requiredMembershipSlugs,
            Instant startsAt, Instant endsAt, UUID createdBy, Instant now) {
        VoucherEntity e = new VoucherEntity();
        e.id = id;
        e.code = code.toUpperCase();
        e.description = description;
        e.discountType = discountType;
        e.discountValue = discountValue;
        e.maxDiscountPoints = maxDiscountPoints;
        e.minOrderPoints = minOrderPoints;
        e.maxUsage = maxUsage;
        e.usedCount = 0;
        e.maxUsagePerUser = maxUsagePerUser;
        e.applicableTypes = applicableTypes;
        e.requiredMembershipSlugs = requiredMembershipSlugs;
        e.startsAt = startsAt;
        e.endsAt = endsAt;
        e.active = true;
        e.createdBy = createdBy;
        e.createdAt = now;
        e.updatedAt = now;
        return e;
    }
}
```

- [ ] **Step 6: Create VoucherRepository**

Create `backend/voucher/src/main/java/com/fuoverflow/voucher/persistence/VoucherRepository.java`:
```java
package com.fuoverflow.voucher.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface VoucherRepository extends JpaRepository<VoucherEntity, UUID> {

    @Query("SELECT v FROM VoucherEntity v WHERE upper(v.code) = upper(:code) AND v.active = true")
    Optional<VoucherEntity> findActiveByCode(@Param("code") String code);

    Optional<VoucherEntity> findByCodeIgnoreCase(String code);

    @Modifying
    @Query("""
            UPDATE VoucherEntity v SET v.usedCount = v.usedCount + 1, v.updatedAt = CURRENT_TIMESTAMP
            WHERE v.id = :id AND v.usedCount < v.maxUsage AND v.active = true
            """)
    int atomicIncrementUsedCount(@Param("id") UUID id);
}
```

- [ ] **Step 7: Create VoucherUserAssignmentEntity**

Create `backend/voucher/src/main/java/com/fuoverflow/voucher/persistence/VoucherUserAssignmentEntity.java`:
```java
package com.fuoverflow.voucher.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "voucher_user_assignments")
public class VoucherUserAssignmentEntity {
    @Id
    private UUID id;

    @Column(name = "voucher_id", nullable = false)
    private UUID voucherId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public UUID getId() { return id; }
    public UUID getVoucherId() { return voucherId; }
    public UUID getUserId() { return userId; }
    public Instant getCreatedAt() { return createdAt; }

    public static VoucherUserAssignmentEntity create(UUID id, UUID voucherId, UUID userId, Instant now) {
        VoucherUserAssignmentEntity e = new VoucherUserAssignmentEntity();
        e.id = id;
        e.voucherId = voucherId;
        e.userId = userId;
        e.createdAt = now;
        return e;
    }
}
```

- [ ] **Step 8: Create VoucherUserAssignmentRepository**

Create `backend/voucher/src/main/java/com/fuoverflow/voucher/persistence/VoucherUserAssignmentRepository.java`:
```java
package com.fuoverflow.voucher.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface VoucherUserAssignmentRepository extends JpaRepository<VoucherUserAssignmentEntity, UUID> {
    List<VoucherUserAssignmentEntity> findByVoucherId(UUID voucherId);
    boolean existsByVoucherIdAndUserId(UUID voucherId, UUID userId);
    long countByVoucherId(UUID voucherId);
    void deleteByVoucherIdAndUserId(UUID voucherId, UUID userId);
}
```

- [ ] **Step 9: Create VoucherRedemptionEntity**

Create `backend/voucher/src/main/java/com/fuoverflow/voucher/persistence/VoucherRedemptionEntity.java`:
```java
package com.fuoverflow.voucher.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "voucher_redemptions")
public class VoucherRedemptionEntity {
    @Id
    private UUID id;

    @Column(name = "voucher_id", nullable = false)
    private UUID voucherId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "transaction_type", nullable = false, length = 32)
    private String transactionType;

    @Column(name = "transaction_id", nullable = false)
    private UUID transactionId;

    @Column(name = "original_points", nullable = false)
    private int originalPoints;

    @Column(name = "discount_points", nullable = false)
    private int discountPoints;

    @Column(name = "final_points", nullable = false)
    private int finalPoints;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public UUID getId() { return id; }
    public UUID getVoucherId() { return voucherId; }
    public UUID getUserId() { return userId; }
    public String getTransactionType() { return transactionType; }
    public UUID getTransactionId() { return transactionId; }
    public int getOriginalPoints() { return originalPoints; }
    public int getDiscountPoints() { return discountPoints; }
    public int getFinalPoints() { return finalPoints; }
    public Instant getCreatedAt() { return createdAt; }

    public static VoucherRedemptionEntity create(
            UUID id, UUID voucherId, UUID userId, String transactionType,
            UUID transactionId, int originalPoints, int discountPoints,
            int finalPoints, Instant now) {
        VoucherRedemptionEntity e = new VoucherRedemptionEntity();
        e.id = id;
        e.voucherId = voucherId;
        e.userId = userId;
        e.transactionType = transactionType;
        e.transactionId = transactionId;
        e.originalPoints = originalPoints;
        e.discountPoints = discountPoints;
        e.finalPoints = finalPoints;
        e.createdAt = now;
        return e;
    }
}
```

- [ ] **Step 10: Create VoucherRedemptionRepository**

Create `backend/voucher/src/main/java/com/fuoverflow/voucher/persistence/VoucherRedemptionRepository.java`:
```java
package com.fuoverflow.voucher.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface VoucherRedemptionRepository extends JpaRepository<VoucherRedemptionEntity, UUID> {
    long countByVoucherIdAndUserId(UUID voucherId, UUID userId);
    Page<VoucherRedemptionEntity> findByVoucherIdOrderByCreatedAtDesc(UUID voucherId, Pageable pageable);
}
```

- [ ] **Step 11: Verify compilation**

```bash
cd backend && mvn -q -DskipTests compile
```
Expected: BUILD SUCCESS

- [ ] **Step 12: Commit**

```bash
git add backend/voucher/ backend/pom.xml backend/app/pom.xml
git commit -m "feat: add voucher Maven module with entities and repositories"
```

---

### Task 3: VoucherService — Core validate/preview/redeem logic

**Files:**
- Create: `backend/voucher/src/main/java/com/fuoverflow/voucher/domain/VoucherDiscountResult.java`
- Create: `backend/voucher/src/main/java/com/fuoverflow/voucher/application/VoucherService.java`

**Interfaces:**
- Consumes: `VoucherRepository.findActiveByCode(code)`, `VoucherRepository.atomicIncrementUsedCount(id)`, `VoucherRedemptionRepository.countByVoucherIdAndUserId(voucherId, userId)`, `VoucherUserAssignmentRepository.existsByVoucherIdAndUserId(voucherId, userId)`, `VoucherUserAssignmentRepository.countByVoucherId(voucherId)`, `MembershipRepository.findActiveByUserId(userId, now)`
- Produces: `VoucherService.preview(code, userId, transactionType, originalPoints)` → `VoucherDiscountResult`, `VoucherService.redeem(code, userId, transactionType, transactionId, originalPoints)` → `VoucherDiscountResult`

- [ ] **Step 1: Create VoucherDiscountResult record**

Create `backend/voucher/src/main/java/com/fuoverflow/voucher/domain/VoucherDiscountResult.java`:
```java
package com.fuoverflow.voucher.domain;

import java.util.UUID;

public record VoucherDiscountResult(
        boolean valid,
        UUID voucherId,
        int discountPoints,
        int finalPoints,
        String message
) {
    public static VoucherDiscountResult invalid(String message) {
        return new VoucherDiscountResult(false, null, 0, 0, message);
    }

    public static VoucherDiscountResult success(UUID voucherId, int discountPoints, int finalPoints, String message) {
        return new VoucherDiscountResult(true, voucherId, discountPoints, finalPoints, message);
    }
}
```

- [ ] **Step 2: Create VoucherService**

Create `backend/voucher/src/main/java/com/fuoverflow/voucher/application/VoucherService.java`:
```java
package com.fuoverflow.voucher.application;

import com.fuoverflow.common.exception.BadRequestException;
import com.fuoverflow.common.exception.ConflictException;
import com.fuoverflow.common.exception.ForbiddenException;
import com.fuoverflow.common.exception.NotFoundException;
import com.fuoverflow.membership.persistence.MembershipEntity;
import com.fuoverflow.membership.persistence.MembershipPlanEntity;
import com.fuoverflow.membership.persistence.MembershipPlanRepository;
import com.fuoverflow.membership.persistence.MembershipRepository;
import com.fuoverflow.voucher.domain.VoucherDiscountResult;
import com.fuoverflow.voucher.persistence.VoucherEntity;
import com.fuoverflow.voucher.persistence.VoucherRedemptionEntity;
import com.fuoverflow.voucher.persistence.VoucherRedemptionRepository;
import com.fuoverflow.voucher.persistence.VoucherRepository;
import com.fuoverflow.voucher.persistence.VoucherUserAssignmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class VoucherService {

    private final VoucherRepository voucherRepository;
    private final VoucherRedemptionRepository redemptionRepository;
    private final VoucherUserAssignmentRepository assignmentRepository;
    private final MembershipRepository membershipRepository;
    private final MembershipPlanRepository membershipPlanRepository;

    public VoucherService(
            VoucherRepository voucherRepository,
            VoucherRedemptionRepository redemptionRepository,
            VoucherUserAssignmentRepository assignmentRepository,
            MembershipRepository membershipRepository,
            MembershipPlanRepository membershipPlanRepository) {
        this.voucherRepository = voucherRepository;
        this.redemptionRepository = redemptionRepository;
        this.assignmentRepository = assignmentRepository;
        this.membershipRepository = membershipRepository;
        this.membershipPlanRepository = membershipPlanRepository;
    }

    @Transactional(readOnly = true)
    public VoucherDiscountResult preview(String code, UUID userId, String transactionType, int originalPoints) {
        return validate(code, userId, transactionType, originalPoints);
    }

    @Transactional
    public VoucherDiscountResult redeem(String code, UUID userId, String transactionType,
                                        UUID transactionId, int originalPoints) {
        VoucherDiscountResult result = validate(code, userId, transactionType, originalPoints);
        if (!result.valid()) {
            throw new BadRequestException("VOUCHER_INVALID", result.message());
        }
        int updated = voucherRepository.atomicIncrementUsedCount(result.voucherId());
        if (updated == 0) {
            throw new ConflictException("VOUCHER_EXHAUSTED", "Voucher vừa hết, vui lòng thử voucher khác");
        }
        redemptionRepository.save(VoucherRedemptionEntity.create(
                UUID.randomUUID(), result.voucherId(), userId, transactionType,
                transactionId, originalPoints, result.discountPoints(), result.finalPoints(),
                Instant.now()));
        return result;
    }

    private VoucherDiscountResult validate(String code, UUID userId, String transactionType, int originalPoints) {
        if (code == null || code.isBlank()) {
            return VoucherDiscountResult.invalid("Mã voucher không được để trống");
        }
        VoucherEntity voucher = voucherRepository.findActiveByCode(code.trim())
                .orElseThrow(() -> new NotFoundException("VOUCHER_NOT_FOUND", "Mã voucher không hợp lệ"));

        Instant now = Instant.now();

        if (now.isBefore(voucher.getStartsAt())) {
            return VoucherDiscountResult.invalid("Voucher chưa có hiệu lực");
        }
        if (now.isAfter(voucher.getEndsAt())) {
            return VoucherDiscountResult.invalid("Voucher đã hết hạn");
        }
        if (voucher.getUsedCount() >= voucher.getMaxUsage()) {
            return VoucherDiscountResult.invalid("Voucher đã hết lượt sử dụng");
        }

        Set<String> types = Arrays.stream(voucher.getApplicableTypes().split(","))
                .map(String::trim)
                .collect(Collectors.toSet());
        if (!types.contains(transactionType)) {
            return VoucherDiscountResult.invalid("Voucher không áp dụng cho loại giao dịch này");
        }

        if (originalPoints < voucher.getMinOrderPoints()) {
            return VoucherDiscountResult.invalid(
                    "Đơn hàng cần tối thiểu " + voucher.getMinOrderPoints() + " points");
        }

        long userUsage = redemptionRepository.countByVoucherIdAndUserId(voucher.getId(), userId);
        if (userUsage >= voucher.getMaxUsagePerUser()) {
            return VoucherDiscountResult.invalid("Bạn đã sử dụng hết lượt cho voucher này");
        }

        if (voucher.getRequiredMembershipSlugs() != null && !voucher.getRequiredMembershipSlugs().isBlank()) {
            Set<String> requiredSlugs = Arrays.stream(voucher.getRequiredMembershipSlugs().split(","))
                    .map(String::trim)
                    .collect(Collectors.toSet());
            List<MembershipEntity> activeMemberships = membershipRepository.findActiveByUserId(userId, now);
            boolean hasRequired = activeMemberships.stream()
                    .anyMatch(m -> membershipPlanRepository.findById(m.getPlanId())
                            .map(plan -> requiredSlugs.contains(plan.getSlug()))
                            .orElse(false));
            if (!hasRequired) {
                return VoucherDiscountResult.invalid("Voucher yêu cầu membership " +
                        String.join(", ", requiredSlugs));
            }
        }

        long assignmentCount = assignmentRepository.countByVoucherId(voucher.getId());
        if (assignmentCount > 0 && !assignmentRepository.existsByVoucherIdAndUserId(voucher.getId(), userId)) {
            throw new ForbiddenException("VOUCHER_NOT_ASSIGNED", "Voucher này không dành cho bạn");
        }

        int discount = calculateDiscount(voucher, originalPoints);
        int finalPoints = originalPoints - discount;
        String message = "percentage".equals(voucher.getDiscountType())
                ? "Giảm " + voucher.getDiscountValue() + "%"
                        + (voucher.getMaxDiscountPoints() != null ? ", tối đa " + voucher.getMaxDiscountPoints() + " points" : "")
                : "Giảm " + voucher.getDiscountValue() + " points";

        return VoucherDiscountResult.success(voucher.getId(), discount, finalPoints, message);
    }

    private int calculateDiscount(VoucherEntity voucher, int originalPoints) {
        if ("percentage".equals(voucher.getDiscountType())) {
            int discount = (int) ((long) originalPoints * voucher.getDiscountValue() / 100);
            if (voucher.getMaxDiscountPoints() != null) {
                discount = Math.min(discount, voucher.getMaxDiscountPoints());
            }
            return Math.min(discount, originalPoints);
        } else {
            return Math.min(voucher.getDiscountValue(), originalPoints);
        }
    }
}
```

- [ ] **Step 3: Verify compilation**

```bash
cd backend && mvn -q -DskipTests compile
```
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add backend/voucher/src/main/java/com/fuoverflow/voucher/domain/ backend/voucher/src/main/java/com/fuoverflow/voucher/application/VoucherService.java
git commit -m "feat: add VoucherService with validate, preview, and redeem logic"
```

---

### Task 4: VoucherAdminService + Admin DTOs + Admin Controller

**Files:**
- Create: `backend/voucher/src/main/java/com/fuoverflow/voucher/api/dto/CreateVoucherRequest.java`
- Create: `backend/voucher/src/main/java/com/fuoverflow/voucher/api/dto/UpdateVoucherRequest.java`
- Create: `backend/voucher/src/main/java/com/fuoverflow/voucher/api/dto/VoucherResponse.java`
- Create: `backend/voucher/src/main/java/com/fuoverflow/voucher/api/dto/AssignUsersRequest.java`
- Create: `backend/voucher/src/main/java/com/fuoverflow/voucher/api/dto/VoucherRedemptionResponse.java`
- Create: `backend/voucher/src/main/java/com/fuoverflow/voucher/api/dto/VoucherPreviewRequest.java`
- Create: `backend/voucher/src/main/java/com/fuoverflow/voucher/api/dto/VoucherPreviewResponse.java`
- Create: `backend/voucher/src/main/java/com/fuoverflow/voucher/application/VoucherAdminService.java`
- Create: `backend/voucher/src/main/java/com/fuoverflow/voucher/api/VoucherAdminController.java`
- Create: `backend/voucher/src/main/java/com/fuoverflow/voucher/api/VoucherController.java`

**Interfaces:**
- Consumes: `VoucherEntity.create(...)`, `VoucherRepository`, `VoucherUserAssignmentEntity.create(...)`, `VoucherUserAssignmentRepository`, `VoucherRedemptionRepository`, `VoucherService.preview(...)`
- Produces: REST endpoints under `/api/v1/admin/vouchers` and `/api/v1/vouchers/preview`

- [ ] **Step 1: Create request/response DTOs**

Create `backend/voucher/src/main/java/com/fuoverflow/voucher/api/dto/CreateVoucherRequest.java`:
```java
package com.fuoverflow.voucher.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record CreateVoucherRequest(
        @NotBlank @Size(max = 50) String code,
        String description,
        @NotBlank String discountType,
        @Min(1) int discountValue,
        Integer maxDiscountPoints,
        @Min(0) int minOrderPoints,
        @Min(1) int maxUsage,
        @Min(1) int maxUsagePerUser,
        @NotBlank String applicableTypes,
        String requiredMembershipSlugs,
        @NotNull Instant startsAt,
        @NotNull Instant endsAt
) {
}
```

Create `backend/voucher/src/main/java/com/fuoverflow/voucher/api/dto/UpdateVoucherRequest.java`:
```java
package com.fuoverflow.voucher.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record UpdateVoucherRequest(
        @NotBlank @Size(max = 50) String code,
        String description,
        @NotBlank String discountType,
        @Min(1) int discountValue,
        Integer maxDiscountPoints,
        @Min(0) int minOrderPoints,
        @Min(1) int maxUsage,
        @Min(1) int maxUsagePerUser,
        @NotBlank String applicableTypes,
        String requiredMembershipSlugs,
        @NotNull Instant startsAt,
        @NotNull Instant endsAt
) {
}
```

Create `backend/voucher/src/main/java/com/fuoverflow/voucher/api/dto/VoucherResponse.java`:
```java
package com.fuoverflow.voucher.api.dto;

import java.time.Instant;
import java.util.UUID;

public record VoucherResponse(
        UUID id,
        String code,
        String description,
        String discountType,
        int discountValue,
        Integer maxDiscountPoints,
        int minOrderPoints,
        int maxUsage,
        int usedCount,
        int maxUsagePerUser,
        String applicableTypes,
        String requiredMembershipSlugs,
        Instant startsAt,
        Instant endsAt,
        boolean active,
        UUID createdBy,
        Instant createdAt,
        Instant updatedAt
) {
}
```

Create `backend/voucher/src/main/java/com/fuoverflow/voucher/api/dto/AssignUsersRequest.java`:
```java
package com.fuoverflow.voucher.api.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

public record AssignUsersRequest(
        @NotEmpty List<UUID> userIds
) {
}
```

Create `backend/voucher/src/main/java/com/fuoverflow/voucher/api/dto/VoucherRedemptionResponse.java`:
```java
package com.fuoverflow.voucher.api.dto;

import java.time.Instant;
import java.util.UUID;

public record VoucherRedemptionResponse(
        UUID id,
        UUID voucherId,
        UUID userId,
        String transactionType,
        UUID transactionId,
        int originalPoints,
        int discountPoints,
        int finalPoints,
        Instant createdAt
) {
}
```

Create `backend/voucher/src/main/java/com/fuoverflow/voucher/api/dto/VoucherPreviewRequest.java`:
```java
package com.fuoverflow.voucher.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record VoucherPreviewRequest(
        @NotBlank String code,
        @NotBlank String transactionType,
        @Min(1) int originalPoints
) {
}
```

Create `backend/voucher/src/main/java/com/fuoverflow/voucher/api/dto/VoucherPreviewResponse.java`:
```java
package com.fuoverflow.voucher.api.dto;

public record VoucherPreviewResponse(
        boolean valid,
        int discountPoints,
        int finalPoints,
        String message
) {
}
```

- [ ] **Step 2: Create VoucherAdminService**

Create `backend/voucher/src/main/java/com/fuoverflow/voucher/application/VoucherAdminService.java`:
```java
package com.fuoverflow.voucher.application;

import com.fuoverflow.common.exception.BadRequestException;
import com.fuoverflow.common.exception.ConflictException;
import com.fuoverflow.common.exception.NotFoundException;
import com.fuoverflow.voucher.api.dto.VoucherRedemptionResponse;
import com.fuoverflow.voucher.api.dto.VoucherResponse;
import com.fuoverflow.voucher.persistence.VoucherEntity;
import com.fuoverflow.voucher.persistence.VoucherRedemptionRepository;
import com.fuoverflow.voucher.persistence.VoucherRepository;
import com.fuoverflow.voucher.persistence.VoucherUserAssignmentEntity;
import com.fuoverflow.voucher.persistence.VoucherUserAssignmentRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class VoucherAdminService {
    private static final Set<String> VALID_DISCOUNT_TYPES = Set.of("percentage", "fixed");
    private static final Set<String> VALID_APPLICABLE_TYPES = Set.of("source", "membership", "coursera");

    private final VoucherRepository voucherRepository;
    private final VoucherUserAssignmentRepository assignmentRepository;
    private final VoucherRedemptionRepository redemptionRepository;

    public VoucherAdminService(
            VoucherRepository voucherRepository,
            VoucherUserAssignmentRepository assignmentRepository,
            VoucherRedemptionRepository redemptionRepository) {
        this.voucherRepository = voucherRepository;
        this.assignmentRepository = assignmentRepository;
        this.redemptionRepository = redemptionRepository;
    }

    @Transactional(readOnly = true)
    public List<VoucherResponse> listAll() {
        return voucherRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public VoucherResponse get(UUID id) {
        return toResponse(requireVoucher(id));
    }

    @Transactional
    public VoucherResponse create(String code, String description, String discountType,
                                   int discountValue, Integer maxDiscountPoints, int minOrderPoints,
                                   int maxUsage, int maxUsagePerUser, String applicableTypes,
                                   String requiredMembershipSlugs, Instant startsAt, Instant endsAt,
                                   UUID createdBy) {
        validateDiscountType(discountType);
        validateApplicableTypes(applicableTypes);
        if (voucherRepository.findByCodeIgnoreCase(code.trim()).isPresent()) {
            throw new ConflictException("VOUCHER_CODE_EXISTS", "Mã voucher đã tồn tại");
        }
        if ("percentage".equals(discountType) && discountValue > 100) {
            throw new BadRequestException("INVALID_PERCENTAGE", "Phần trăm giảm giá không được vượt quá 100");
        }
        Instant now = Instant.now();
        VoucherEntity entity = VoucherEntity.create(
                UUID.randomUUID(), code.trim(), blankToNull(description), discountType,
                discountValue, maxDiscountPoints, minOrderPoints, maxUsage, maxUsagePerUser,
                applicableTypes.trim(), blankToNull(requiredMembershipSlugs),
                startsAt, endsAt, createdBy, now);
        voucherRepository.save(entity);
        return toResponse(entity);
    }

    @Transactional
    public VoucherResponse update(UUID id, String code, String description, String discountType,
                                   int discountValue, Integer maxDiscountPoints, int minOrderPoints,
                                   int maxUsage, int maxUsagePerUser, String applicableTypes,
                                   String requiredMembershipSlugs, Instant startsAt, Instant endsAt) {
        VoucherEntity entity = requireVoucher(id);
        validateDiscountType(discountType);
        validateApplicableTypes(applicableTypes);
        var existing = voucherRepository.findByCodeIgnoreCase(code.trim());
        if (existing.isPresent() && !existing.get().getId().equals(id)) {
            throw new ConflictException("VOUCHER_CODE_EXISTS", "Mã voucher đã tồn tại");
        }
        if ("percentage".equals(discountType) && discountValue > 100) {
            throw new BadRequestException("INVALID_PERCENTAGE", "Phần trăm giảm giá không được vượt quá 100");
        }
        entity.setCode(code.trim().toUpperCase());
        entity.setDescription(blankToNull(description));
        entity.setDiscountType(discountType);
        entity.setDiscountValue(discountValue);
        entity.setMaxDiscountPoints(maxDiscountPoints);
        entity.setMinOrderPoints(minOrderPoints);
        entity.setMaxUsage(maxUsage);
        entity.setMaxUsagePerUser(maxUsagePerUser);
        entity.setApplicableTypes(applicableTypes.trim());
        entity.setRequiredMembershipSlugs(blankToNull(requiredMembershipSlugs));
        entity.setStartsAt(startsAt);
        entity.setEndsAt(endsAt);
        entity.setUpdatedAt(Instant.now());
        voucherRepository.save(entity);
        return toResponse(entity);
    }

    @Transactional
    public VoucherResponse toggleActive(UUID id) {
        VoucherEntity entity = requireVoucher(id);
        entity.setActive(!entity.isActive());
        entity.setUpdatedAt(Instant.now());
        voucherRepository.save(entity);
        return toResponse(entity);
    }

    @Transactional
    public void assignUsers(UUID voucherId, List<UUID> userIds) {
        requireVoucher(voucherId);
        Instant now = Instant.now();
        for (UUID userId : userIds) {
            if (!assignmentRepository.existsByVoucherIdAndUserId(voucherId, userId)) {
                assignmentRepository.save(VoucherUserAssignmentEntity.create(
                        UUID.randomUUID(), voucherId, userId, now));
            }
        }
    }

    @Transactional
    public void removeAssignment(UUID voucherId, UUID userId) {
        requireVoucher(voucherId);
        assignmentRepository.deleteByVoucherIdAndUserId(voucherId, userId);
    }

    @Transactional(readOnly = true)
    public Page<VoucherRedemptionResponse> listRedemptions(UUID voucherId, int page, int size) {
        requireVoucher(voucherId);
        return redemptionRepository.findByVoucherIdOrderByCreatedAtDesc(
                voucherId, PageRequest.of(page, Math.min(size, 100)))
                .map(r -> new VoucherRedemptionResponse(
                        r.getId(), r.getVoucherId(), r.getUserId(), r.getTransactionType(),
                        r.getTransactionId(), r.getOriginalPoints(), r.getDiscountPoints(),
                        r.getFinalPoints(), r.getCreatedAt()));
    }

    private VoucherEntity requireVoucher(UUID id) {
        return voucherRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("VOUCHER_NOT_FOUND", "Voucher không tồn tại"));
    }

    private void validateDiscountType(String discountType) {
        if (!VALID_DISCOUNT_TYPES.contains(discountType)) {
            throw new BadRequestException("INVALID_DISCOUNT_TYPE", "Discount type phải là percentage hoặc fixed");
        }
    }

    private void validateApplicableTypes(String applicableTypes) {
        for (String type : applicableTypes.split(",")) {
            if (!VALID_APPLICABLE_TYPES.contains(type.trim())) {
                throw new BadRequestException("INVALID_APPLICABLE_TYPE",
                        "Applicable type không hợp lệ: " + type.trim());
            }
        }
    }

    private VoucherResponse toResponse(VoucherEntity e) {
        return new VoucherResponse(
                e.getId(), e.getCode(), e.getDescription(), e.getDiscountType(),
                e.getDiscountValue(), e.getMaxDiscountPoints(), e.getMinOrderPoints(),
                e.getMaxUsage(), e.getUsedCount(), e.getMaxUsagePerUser(),
                e.getApplicableTypes(), e.getRequiredMembershipSlugs(),
                e.getStartsAt(), e.getEndsAt(), e.isActive(),
                e.getCreatedBy(), e.getCreatedAt(), e.getUpdatedAt());
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
```

- [ ] **Step 3: Create VoucherAdminController**

Create `backend/voucher/src/main/java/com/fuoverflow/voucher/api/VoucherAdminController.java`:
```java
package com.fuoverflow.voucher.api;

import com.fuoverflow.common.security.RequirePermission;
import com.fuoverflow.common.web.ApiResponse;
import com.fuoverflow.voucher.api.dto.AssignUsersRequest;
import com.fuoverflow.voucher.api.dto.CreateVoucherRequest;
import com.fuoverflow.voucher.api.dto.UpdateVoucherRequest;
import com.fuoverflow.voucher.api.dto.VoucherRedemptionResponse;
import com.fuoverflow.voucher.api.dto.VoucherResponse;
import com.fuoverflow.voucher.application.VoucherAdminService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/vouchers")
@RequirePermission("admin.panel:access")
public class VoucherAdminController {
    private final VoucherAdminService adminService;

    public VoucherAdminController(VoucherAdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping
    @RequirePermission("voucher.admin:read")
    public ApiResponse<List<VoucherResponse>> list() {
        return ApiResponse.ok(adminService.listAll());
    }

    @GetMapping("/{id}")
    @RequirePermission("voucher.admin:read")
    public ApiResponse<VoucherResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(adminService.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission("voucher.admin:create")
    public ApiResponse<VoucherResponse> create(
            Authentication authentication,
            @Valid @RequestBody CreateVoucherRequest request) {
        UUID createdBy = UUID.fromString(authentication.getName());
        return ApiResponse.ok(adminService.create(
                request.code(), request.description(), request.discountType(),
                request.discountValue(), request.maxDiscountPoints(), request.minOrderPoints(),
                request.maxUsage(), request.maxUsagePerUser(), request.applicableTypes(),
                request.requiredMembershipSlugs(), request.startsAt(), request.endsAt(), createdBy));
    }

    @PutMapping("/{id}")
    @RequirePermission("voucher.admin:update")
    public ApiResponse<VoucherResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateVoucherRequest request) {
        return ApiResponse.ok(adminService.update(
                id, request.code(), request.description(), request.discountType(),
                request.discountValue(), request.maxDiscountPoints(), request.minOrderPoints(),
                request.maxUsage(), request.maxUsagePerUser(), request.applicableTypes(),
                request.requiredMembershipSlugs(), request.startsAt(), request.endsAt()));
    }

    @PatchMapping("/{id}/toggle")
    @RequirePermission("voucher.admin:update")
    public ApiResponse<VoucherResponse> toggle(@PathVariable UUID id) {
        return ApiResponse.ok(adminService.toggleActive(id));
    }

    @PostMapping("/{id}/assignments")
    @RequirePermission("voucher.admin:update")
    public ApiResponse<Void> assignUsers(
            @PathVariable UUID id,
            @Valid @RequestBody AssignUsersRequest request) {
        adminService.assignUsers(id, request.userIds());
        return ApiResponse.ok(null, "Users assigned");
    }

    @DeleteMapping("/{id}/assignments/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequirePermission("voucher.admin:update")
    public void removeAssignment(@PathVariable UUID id, @PathVariable UUID userId) {
        adminService.removeAssignment(id, userId);
    }

    @GetMapping("/{id}/redemptions")
    @RequirePermission("voucher.admin:read")
    public ApiResponse<Page<VoucherRedemptionResponse>> redemptions(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(adminService.listRedemptions(id, page, size));
    }
}
```

- [ ] **Step 4: Create VoucherController (user preview)**

Create `backend/voucher/src/main/java/com/fuoverflow/voucher/api/VoucherController.java`:
```java
package com.fuoverflow.voucher.api;

import com.fuoverflow.common.web.ApiResponse;
import com.fuoverflow.voucher.api.dto.VoucherPreviewRequest;
import com.fuoverflow.voucher.api.dto.VoucherPreviewResponse;
import com.fuoverflow.voucher.application.VoucherService;
import com.fuoverflow.voucher.domain.VoucherDiscountResult;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/vouchers")
public class VoucherController {
    private final VoucherService voucherService;

    public VoucherController(VoucherService voucherService) {
        this.voucherService = voucherService;
    }

    @PostMapping("/preview")
    public ApiResponse<VoucherPreviewResponse> preview(
            Authentication authentication,
            @Valid @RequestBody VoucherPreviewRequest request) {
        UUID userId = UUID.fromString(authentication.getName());
        VoucherDiscountResult result = voucherService.preview(
                request.code(), userId, request.transactionType(), request.originalPoints());
        return ApiResponse.ok(new VoucherPreviewResponse(
                result.valid(), result.discountPoints(), result.finalPoints(), result.message()));
    }
}
```

- [ ] **Step 5: Verify compilation**

```bash
cd backend && mvn -q -DskipTests compile
```
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add backend/voucher/src/main/java/com/fuoverflow/voucher/api/ backend/voucher/src/main/java/com/fuoverflow/voucher/application/VoucherAdminService.java
git commit -m "feat: add voucher admin service, admin controller, and user preview endpoint"
```

---

### Task 5: Integrate voucher into checkout flows (source, membership, coursera)

**Files:**
- Modify: `backend/source/src/main/java/com/fuoverflow/source/api/dto/CreatePurchaseRequest.java`
- Modify: `backend/source/src/main/java/com/fuoverflow/source/application/SourcePurchaseService.java`
- Modify: `backend/source/pom.xml` — add voucher dependency
- Modify: `backend/membership/src/main/java/com/fuoverflow/membership/api/dto/SubscribeMembershipRequest.java`
- Modify: `backend/membership/src/main/java/com/fuoverflow/membership/application/MembershipService.java`
- Modify: `backend/membership/pom.xml` — add voucher dependency
- Modify: `backend/coursera/src/main/java/com/fuoverflow/coursera/api/dto/CreateCourseraRequestBody.java`
- Modify: `backend/coursera/src/main/java/com/fuoverflow/coursera/application/CourseraRequestService.java`
- Modify: `backend/coursera/pom.xml` — add voucher dependency

**Interfaces:**
- Consumes: `VoucherService.redeem(code, userId, transactionType, transactionId, originalPoints)` → `VoucherDiscountResult`

- [ ] **Step 1: Add voucher dependency to source, membership, coursera pom.xml**

Add to each module's `pom.xml` inside `<dependencies>`:
```xml
<dependency>
    <groupId>com.fuoverflow</groupId>
    <artifactId>fuoverflow-voucher</artifactId>
    <version>${project.version}</version>
</dependency>
```

Modules to update:
- `backend/source/pom.xml`
- `backend/membership/pom.xml`
- `backend/coursera/pom.xml`

- [ ] **Step 2: Update CreatePurchaseRequest (source)**

In `backend/source/src/main/java/com/fuoverflow/source/api/dto/CreatePurchaseRequest.java`, add the optional `voucherCode` field:

```java
package com.fuoverflow.source.api.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreatePurchaseRequest(
        @NotNull UUID catalogItemId,
        String voucherCode
) {
}
```

- [ ] **Step 3: Update SourcePurchaseService to apply voucher**

In `backend/source/src/main/java/com/fuoverflow/source/application/SourcePurchaseService.java`:

Add constructor parameter:
```java
import com.fuoverflow.voucher.application.VoucherService;
import com.fuoverflow.voucher.domain.VoucherDiscountResult;
```

Add field and update constructor:
```java
private final VoucherService voucherService;

public SourcePurchaseService(
        SourcePurchaseRepository purchaseRepository,
        SourcePurchaseEventRepository eventRepository,
        SourceCatalogItemRepository catalogRepository,
        UserRepository userRepository,
        PointsWalletService walletService,
        SourceProperties properties,
        VoucherService voucherService) {
    // ... existing assignments ...
    this.voucherService = voucherService;
}
```

Update `purchase` method signature to accept `voucherCode`:
```java
public PurchaseResponse purchase(UUID userId, UUID catalogItemId, String idempotencyKey, String voucherCode) {
```

After fetching the catalog price (`int price = catalog.getPricePoints();`), add voucher logic before the debit call:

```java
int price = catalog.getPricePoints();
VoucherDiscountResult voucherResult = null;
if (voucherCode != null && !voucherCode.isBlank()) {
    voucherResult = voucherService.redeem(voucherCode, userId, "source", purchaseId, price);
    price = voucherResult.finalPoints();
}
```

The existing debit call already uses `price`, so it will now debit the discounted amount.

- [ ] **Step 4: Update SourcePurchaseController to pass voucherCode**

In `backend/source/src/main/java/com/fuoverflow/source/api/SourcePurchaseController.java`, update the `purchase` method:

```java
return ApiResponse.ok(purchaseService.purchase(userId, body.catalogItemId(), idempotencyKey, body.voucherCode()));
```

- [ ] **Step 5: Update SubscribeMembershipRequest**

In `backend/membership/src/main/java/com/fuoverflow/membership/api/dto/SubscribeMembershipRequest.java`:

```java
package com.fuoverflow.membership.api.dto;

import jakarta.validation.constraints.NotBlank;

public record SubscribeMembershipRequest(@NotBlank String planSlug, String voucherCode) {
}
```

- [ ] **Step 6: Update MembershipService to apply voucher**

In `backend/membership/src/main/java/com/fuoverflow/membership/application/MembershipService.java`:

Add import and field:
```java
import com.fuoverflow.voucher.application.VoucherService;
import com.fuoverflow.voucher.domain.VoucherDiscountResult;
```

Add `VoucherService voucherService` to constructor.

In the `subscribe` method, after `int price = plan.getPriceCents();` and before the `walletService.debit()` call, add:

```java
int price = plan.getPriceCents();
if (request.voucherCode() != null && !request.voucherCode().isBlank()) {
    VoucherDiscountResult voucherResult = voucherService.redeem(
            request.voucherCode(), userId, "membership", plan.getId(), price);
    price = voucherResult.finalPoints();
}
```

- [ ] **Step 7: Update CreateCourseraRequestBody**

In `backend/coursera/src/main/java/com/fuoverflow/coursera/api/dto/CreateCourseraRequestBody.java`:

```java
package com.fuoverflow.coursera.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateCourseraRequestBody(
        @NotNull UUID catalogItemId,
        @NotBlank @Email @Size(max = 255) String courseraEmail,
        @NotBlank @Size(min = 1, max = 255) String courseraPassword,
        @Size(max = 2000) String userNotes,
        String voucherCode
) {
}
```

- [ ] **Step 8: Update CourseraRequestService to apply voucher**

In `backend/coursera/src/main/java/com/fuoverflow/coursera/application/CourseraRequestService.java`:

Add import and field:
```java
import com.fuoverflow.voucher.application.VoucherService;
import com.fuoverflow.voucher.domain.VoucherDiscountResult;
```

Add `VoucherService voucherService` to constructor.

In the `create` method, after `int totalPoints = catalog.getPricePoints();` and before the debit call, add:

```java
int totalPoints = catalog.getPricePoints();
if (body.voucherCode() != null && !body.voucherCode().isBlank()) {
    VoucherDiscountResult voucherResult = voucherService.redeem(
            body.voucherCode(), userId, "coursera", requestId, totalPoints);
    totalPoints = voucherResult.finalPoints();
}
```

- [ ] **Step 9: Verify compilation**

```bash
cd backend && mvn -q -DskipTests compile
```
Expected: BUILD SUCCESS

- [ ] **Step 10: Commit**

```bash
git add backend/source/ backend/membership/ backend/coursera/
git commit -m "feat: integrate voucher redemption into source, membership, and coursera checkout flows"
```

---

### Task 6: Admin Frontend — API client + Voucher list page

**Files:**
- Create: `Fuexam-admin/lib/api/admin-vouchers.ts`
- Modify: `Fuexam-admin/components/admin/AdminSidebar.tsx` — add Voucher nav item
- Create: `Fuexam-admin/app/vouchers/page.tsx`

**Interfaces:**
- Consumes: Backend endpoints `GET/POST/PUT/PATCH /api/v1/admin/vouchers`
- Produces: Admin voucher list page with inline create/edit form

- [ ] **Step 1: Create API client `Fuexam-admin/lib/api/admin-vouchers.ts`**

```typescript
import { apiFetch } from "@/lib/api/client";

export interface VoucherResponse {
  id: string;
  code: string;
  description: string | null;
  discountType: "percentage" | "fixed";
  discountValue: number;
  maxDiscountPoints: number | null;
  minOrderPoints: number;
  maxUsage: number;
  usedCount: number;
  maxUsagePerUser: number;
  applicableTypes: string;
  requiredMembershipSlugs: string | null;
  startsAt: string;
  endsAt: string;
  active: boolean;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
}

export interface CreateVoucherBody {
  code: string;
  description?: string;
  discountType: string;
  discountValue: number;
  maxDiscountPoints?: number | null;
  minOrderPoints: number;
  maxUsage: number;
  maxUsagePerUser: number;
  applicableTypes: string;
  requiredMembershipSlugs?: string | null;
  startsAt: string;
  endsAt: string;
}

export function listVouchers() {
  return apiFetch<VoucherResponse[]>("/api/v1/admin/vouchers");
}

export function getVoucher(id: string) {
  return apiFetch<VoucherResponse>(`/api/v1/admin/vouchers/${id}`);
}

export function createVoucher(body: CreateVoucherBody) {
  return apiFetch<VoucherResponse>("/api/v1/admin/vouchers", {
    method: "POST",
    body: JSON.stringify(body),
  });
}

export function updateVoucher(id: string, body: CreateVoucherBody) {
  return apiFetch<VoucherResponse>(`/api/v1/admin/vouchers/${id}`, {
    method: "PUT",
    body: JSON.stringify(body),
  });
}

export function toggleVoucher(id: string) {
  return apiFetch<VoucherResponse>(`/api/v1/admin/vouchers/${id}/toggle`, {
    method: "PATCH",
  });
}

export function assignVoucherUsers(id: string, userIds: string[]) {
  return apiFetch<void>(`/api/v1/admin/vouchers/${id}/assignments`, {
    method: "POST",
    body: JSON.stringify({ userIds }),
  });
}

export function removeVoucherAssignment(id: string, userId: string) {
  return apiFetch<void>(`/api/v1/admin/vouchers/${id}/assignments/${userId}`, {
    method: "DELETE",
  });
}

export interface VoucherRedemptionResponse {
  id: string;
  voucherId: string;
  userId: string;
  transactionType: string;
  transactionId: string;
  originalPoints: number;
  discountPoints: number;
  finalPoints: number;
  createdAt: string;
}

export interface PageResponse<T> {
  content: T[];
  number: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export function listVoucherRedemptions(id: string, page = 0, size = 20) {
  return apiFetch<PageResponse<VoucherRedemptionResponse>>(
    `/api/v1/admin/vouchers/${id}/redemptions?page=${page}&size=${size}`
  );
}
```

- [ ] **Step 2: Add Voucher to AdminSidebar**

In `Fuexam-admin/components/admin/AdminSidebar.tsx`, add `Ticket` to the lucide-react import and add a nav item in the "Bán hàng" section:

```typescript
{ href: "/vouchers", label: "Voucher", icon: Ticket, permission: "voucher.admin:read" },
```

Place it after the existing items in the sales/commerce section.

- [ ] **Step 3: Create voucher list page `Fuexam-admin/app/vouchers/page.tsx`**

Follow the membership plans page pattern — inline form + table. The page should:

1. List all vouchers in a table with columns: Code, Type (% / Fixed), Value, Usage (used/max), Status badge (active/inactive/expired based on dates), Applicable Types, Date Range, Actions (edit/toggle)
2. Inline form above the table for create/edit with fields from `CreateVoucherRequest`
3. Use `useState` for form state, `useCallback` for load
4. Permission check: `can(user, "voucher.admin:read")` for view, `can(user, "voucher.admin:create")` for form
5. Toggle active button per row
6. Click row to populate edit form

This is a large component (~300 lines). The implementer should follow the exact patterns from `Fuexam-admin/app/membership/plans/page.tsx` and `Fuexam-admin/app/deposit/tiers/page.tsx`, adapting form fields for voucher-specific data (discount type radio, applicable types checkboxes, date range pickers, etc.).

Key form fields:
- Code: `<Input>` with `onChange` → uppercase transform
- Description: `<Textarea>`
- Discount Type: two `<Button variant="outline">` toggles for "percentage" / "fixed"
- Discount Value: `<Input type="number">`
- Max Discount Points: `<Input type="number">` (shown only when type = "percentage")
- Min Order Points: `<Input type="number">`
- Max Usage: `<Input type="number">`
- Max Usage Per User: `<Input type="number">`
- Applicable Types: checkboxes for "source", "membership", "coursera"
- Required Membership Slugs: `<Input>` (comma-separated)
- Starts At / Ends At: `<Input type="datetime-local">`
- Active: `<Switch>`

- [ ] **Step 4: Verify frontend builds**

```bash
cd Fuexam-admin && npm run build
```
Expected: Build succeeds without type errors.

- [ ] **Step 5: Commit**

```bash
git add Fuexam-admin/lib/api/admin-vouchers.ts Fuexam-admin/components/admin/AdminSidebar.tsx Fuexam-admin/app/vouchers/
git commit -m "feat: add admin voucher management page with list, create, edit, and toggle"
```

---

### Task 7: User Frontend — Voucher input on checkout dialogs

**Files:**
- Create: `Fuexam/lib/api/voucher.ts`
- Modify: `Fuexam/app/(app)/suoc/[code]/page.tsx` — add voucher input to purchase confirm dialog
- Modify: `Fuexam/app/(app)/membership/page.tsx` — add voucher input to subscribe confirm dialog
- Modify: `Fuexam/app/(app)/coursera/page.tsx` — add voucher input to request form
- Modify: `Fuexam/lib/api/source.ts` — update `purchaseSource` to accept `voucherCode`
- Modify: `Fuexam/lib/api/membership.ts` — update `subscribeMembership` to accept `voucherCode`
- Modify: `Fuexam/lib/api/coursera.ts` — update `createCourseraRequest` to accept `voucherCode`

**Interfaces:**
- Consumes: `POST /api/v1/vouchers/preview`, updated checkout endpoints with `voucherCode`

- [ ] **Step 1: Create `Fuexam/lib/api/voucher.ts`**

```typescript
import { apiFetch } from "@/lib/api/client";

export interface VoucherPreviewResponse {
  valid: boolean;
  discountPoints: number;
  finalPoints: number;
  message: string;
}

export function previewVoucher(code: string, transactionType: string, originalPoints: number) {
  return apiFetch<VoucherPreviewResponse>("/api/v1/vouchers/preview", {
    method: "POST",
    body: JSON.stringify({ code, transactionType, originalPoints }),
  });
}
```

- [ ] **Step 2: Update API clients to pass voucherCode**

In `Fuexam/lib/api/source.ts`, update `purchaseSource` to accept optional `voucherCode`:
```typescript
export function purchaseSource(catalogItemId: string, idempotencyKey?: string, voucherCode?: string) {
  return apiFetch<SourcePurchase>("/api/v1/source/purchases", {
    method: "POST",
    headers: idempotencyKey ? { "Idempotency-Key": idempotencyKey } : undefined,
    body: JSON.stringify({ catalogItemId, voucherCode: voucherCode || undefined }),
  });
}
```

In `Fuexam/lib/api/membership.ts`, update `subscribeMembership`:
```typescript
export function subscribeMembership(planSlug: string, voucherCode?: string) {
  return apiFetch<MembershipStatusResponse>("/api/v1/membership/subscribe", {
    method: "POST",
    body: JSON.stringify({ planSlug, voucherCode: voucherCode || undefined }),
  });
}
```

In `Fuexam/lib/api/coursera.ts`, update `createCourseraRequest` payload type to include optional `voucherCode?: string`.

- [ ] **Step 3: Add voucher input to source purchase dialog**

In `Fuexam/app/(app)/suoc/[code]/page.tsx`:

Add state variables:
```typescript
const [voucherCode, setVoucherCode] = useState("");
const [voucherPreview, setVoucherPreview] = useState<VoucherPreviewResponse | null>(null);
const [voucherError, setVoucherError] = useState<string | null>(null);
const [applyingVoucher, setApplyingVoucher] = useState(false);
```

Add apply voucher handler:
```typescript
async function handleApplyVoucher() {
  if (!voucherCode.trim() || !detail) return;
  setApplyingVoucher(true);
  setVoucherError(null);
  try {
    const result = await previewVoucher(voucherCode.trim(), "source", detail.pricePoints);
    if (result.valid) {
      setVoucherPreview(result);
    } else {
      setVoucherError(result.message);
      setVoucherPreview(null);
    }
  } catch (err) {
    setVoucherError(err instanceof ApiError ? err.message : "Không thể áp dụng voucher");
    setVoucherPreview(null);
  } finally {
    setApplyingVoucher(false);
  }
}
```

Inside the `<AlertDialogContent>` (confirm purchase dialog), after the description and before the footer, add:
```tsx
<div className="space-y-2">
  <div className="flex gap-2">
    <Input
      placeholder="Nhập mã voucher"
      value={voucherCode}
      onChange={(e) => setVoucherCode(e.target.value.toUpperCase())}
      className="flex-1"
    />
    <Button variant="outline" onClick={handleApplyVoucher} disabled={applyingVoucher || !voucherCode.trim()}>
      {applyingVoucher ? "..." : "Áp dụng"}
    </Button>
  </div>
  {voucherError && <p className="text-sm text-destructive">{voucherError}</p>}
  {voucherPreview && (
    <div className="rounded-lg bg-emerald-500/10 px-3 py-2 text-sm text-emerald-700">
      <p>{voucherPreview.message}</p>
      <p>Giá gốc: {formatPoints(detail.pricePoints)} → Giá mới: {formatPoints(voucherPreview.finalPoints)} (giảm {formatPoints(voucherPreview.discountPoints)})</p>
    </div>
  )}
</div>
```

Update `handlePurchase` to pass `voucherCode`:
```typescript
await purchaseSource(detail.id, undefined, voucherPreview ? voucherCode.trim() : undefined);
```

Reset voucher state after purchase or dialog close.

- [ ] **Step 4: Add voucher input to membership subscribe dialog**

Same pattern as source. In `Fuexam/app/(app)/membership/page.tsx`:
- Add voucher state variables
- Add voucher input inside the `<AlertDialog>` for subscription confirmation
- Update `subscribe()` to pass `voucherCode`
- Show price comparison when voucher applied

- [ ] **Step 5: Add voucher input to coursera request form**

Same pattern. In `Fuexam/app/(app)/coursera/page.tsx`:
- Add voucher input field in the request form
- Preview voucher against coursera catalog item price
- Pass `voucherCode` in the create request payload

- [ ] **Step 6: Verify frontend builds**

```bash
cd Fuexam && npm run build
```
Expected: Build succeeds.

- [ ] **Step 7: Commit**

```bash
git add Fuexam/lib/api/voucher.ts Fuexam/lib/api/source.ts Fuexam/lib/api/membership.ts Fuexam/lib/api/coursera.ts Fuexam/app/
git commit -m "feat: add voucher code input to source, membership, and coursera checkout flows"
```

---

### Task 8: End-to-end verification

**Files:** None (testing only)

- [ ] **Step 1: Start backend**

```bash
cd backend && docker compose up -d postgres redis
cd backend && mvn -q -pl app -am spring-boot:run -Dspring-boot.run.profiles=local &
```
Wait for startup, then verify:
```bash
curl -s http://localhost:8080/actuator/health | grep UP
```

- [ ] **Step 2: Verify migration applied**

Check that `vouchers`, `voucher_user_assignments`, `voucher_redemptions` tables exist:
```bash
docker exec -it $(docker ps -q -f name=postgres) psql -U fuoverflow -d fuoverflow -c "\dt voucher*"
```
Expected: 3 tables listed.

- [ ] **Step 3: Verify admin endpoints**

Create a voucher via API (using admin auth token):
```bash
curl -s -X POST http://localhost:8080/api/v1/admin/vouchers \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <ADMIN_TOKEN>" \
  -d '{
    "code": "TEST20",
    "discountType": "percentage",
    "discountValue": 20,
    "maxDiscountPoints": 50000,
    "minOrderPoints": 0,
    "maxUsage": 100,
    "maxUsagePerUser": 1,
    "applicableTypes": "source,membership,coursera",
    "startsAt": "2026-01-01T00:00:00Z",
    "endsAt": "2027-12-31T23:59:59Z"
  }'
```
Expected: 201 with voucher response.

List vouchers:
```bash
curl -s http://localhost:8080/api/v1/admin/vouchers -H "Authorization: Bearer <ADMIN_TOKEN>"
```
Expected: Array containing the created voucher.

- [ ] **Step 4: Verify preview endpoint**

```bash
curl -s -X POST http://localhost:8080/api/v1/vouchers/preview \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <USER_TOKEN>" \
  -d '{"code": "TEST20", "transactionType": "source", "originalPoints": 90000}'
```
Expected: `{"valid": true, "discountPoints": 18000, "finalPoints": 72000, "message": "Giảm 20%, tối đa 50000 points"}`

- [ ] **Step 5: Start frontend and verify admin page**

```bash
cd Fuexam-admin && npm run dev &
```
Open browser at `http://localhost:3001/vouchers` (or admin port). Verify:
- Voucher list page loads
- Create form works
- Toggle active works
- Edit form populates correctly

- [ ] **Step 6: Verify user checkout with voucher**

```bash
cd Fuexam && npm run dev &
```
Navigate to a source detail page, click "Mua ngay", enter voucher code `TEST20`, click "Áp dụng". Verify discount preview shows. Complete purchase and verify discounted points deducted.

- [ ] **Step 7: Commit verification notes**

No code changes. If any fixes were needed during verification, they were committed in their respective tasks.
