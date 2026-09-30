package com.fuoverflow.membership.application;

import com.fuoverflow.award.application.PointsWalletService;
import com.fuoverflow.common.voucher.VoucherDiscountResult;
import com.fuoverflow.common.voucher.VoucherRedemptionPort;
import com.fuoverflow.membership.api.dto.SubscribeMembershipRequest;
import com.fuoverflow.membership.api.dto.UpdateMembershipPlanRequest;
import com.fuoverflow.membership.persistence.*;
import com.fuoverflow.membership.support.MembershipFeatures;
import com.fuoverflow.user.application.PermissionResolverService;
import com.fuoverflow.user.application.RoleAssignmentService;
import com.fuoverflow.user.domain.RoleType;
import com.fuoverflow.user.domain.UserStatus;
import com.fuoverflow.user.persistence.*;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MembershipRegressionTest {
    private final UUID userId = UUID.randomUUID();
    private final MembershipRepository memberships = mock(MembershipRepository.class);
    private final MembershipPlanRepository plans = mock(MembershipPlanRepository.class);
    private final RoleRepository roles = mock(RoleRepository.class);
    private final RoleAssignmentService assignments = mock(RoleAssignmentService.class);
    private final MembershipRoleSyncService sync = new MembershipRoleSyncService(memberships, plans, assignments, roles);
    private final PointsWalletService wallet = mock(PointsWalletService.class);
    private final UserRepository users = mock(UserRepository.class);
    private final VoucherRedemptionPort vouchers = mock(VoucherRedemptionPort.class);

    private MembershipPlanEntity plan(String interval, String role) {
        return MembershipPlanEntity.create(UUID.randomUUID(), "member", "Member", null, 100,
                "VND", interval, "active", MembershipFeatures.buildFeaturesJson(role, 30), Instant.now());
    }

    private MembershipService service(MembershipPlanEntity plan) {
        UserEntity user = mock(UserEntity.class);
        when(user.getStatus()).thenReturn(UserStatus.ACTIVE);
        when(user.getEmailVerifiedAt()).thenReturn(Instant.now());
        when(users.findById(userId)).thenReturn(Optional.of(user));
        when(plans.findBySlugAndStatus("member", "active")).thenReturn(Optional.of(plan));
        return new MembershipService(plans, memberships, wallet, users, mock(MembershipRoleSyncService.class), vouchers);
    }

    @Test void fullyDiscountedMembershipIsCreatedWithoutDebit() {
        var service = service(plan("month", "MEMBER"));
        when(vouchers.redeem(eq("FREE"), eq(userId), eq("membership"), any(), eq(100)))
                .thenReturn(VoucherDiscountResult.success(UUID.randomUUID(), 100, 0, "Free"));
        var result = service.subscribe(userId, new SubscribeMembershipRequest("member", "FREE"));
        assertTrue(result.active());
        verifyNoInteractions(wallet);
        verify(memberships).save(any(MembershipEntity.class));
    }

    @Test void lifetimeMembershipHasNoExpiry() {
        var service = service(plan("lifetime", "MEMBER"));
        var result = service.subscribe(userId, new SubscribeMembershipRequest("member", null));
        assertNull(result.expiresAt());
        verify(wallet).debit(eq(userId), eq(100), anyString(), eq("membership"), eq(result.membershipId()));
    }

    @Test void finiteMembershipKeepsConfiguredDuration() {
        var service = service(plan("month", "MEMBER"));
        Instant before = Instant.now().plusSeconds(30L * 86400);
        var result = service.subscribe(userId, new SubscribeMembershipRequest("member", null));
        assertFalse(result.expiresAt().isBefore(before));
        assertTrue(result.expiresAt().isBefore(before.plusSeconds(10)));
    }

    private void stubRoles() {
        when(roles.findAllByOrderByRoleTypeAscSlugAsc()).thenReturn(List.of(
                RoleEntity.createCustom(UUID.randomUUID(), "MEMBER", "Member", RoleType.MEMBERSHIP, null, List.of(), Instant.now()),
                RoleEntity.createCustom(UUID.randomUUID(), "VIP", "VIP", RoleType.MEMBERSHIP, null, List.of(), Instant.now())));
    }

    @Test void expiringOldMembershipPreservesRoleOfNewSubscription() {
        stubRoles();
        var plan = plan("month", "MEMBER");
        var active = MembershipEntity.create(UUID.randomUUID(), userId, plan.getId(), Instant.now(), Instant.now().plusSeconds(86400), Instant.now());
        var old = MembershipEntity.create(UUID.randomUUID(), userId, plan.getId(), Instant.now().minusSeconds(86400), Instant.now().minusSeconds(60), Instant.now());
        when(memberships.findExpiredActive(any())).thenReturn(List.of(old));
        when(memberships.findActiveByUserId(eq(userId), any())).thenReturn(List.of(active));
        when(plans.findById(plan.getId())).thenReturn(Optional.of(plan));
        new MembershipExpiryService(memberships, plans, sync, mock(PermissionResolverService.class)).expireElapsedMemberships();
        assertEquals("expired", old.getStatus());
        verify(assignments, never()).revokeGlobalRole(userId, "MEMBER");
        verify(assignments).assignGlobalRole(userId, "MEMBER", null);
    }

    @Test void noActiveMembershipRevokesMembershipRoles() {
        stubRoles();
        sync.syncActiveMembershipRoles(userId);
        verify(assignments).revokeGlobalRole(userId, "MEMBER");
        verify(assignments).revokeGlobalRole(userId, "VIP");
        verify(assignments, never()).assignGlobalRole(any(), anyString(), any());
    }

    @Test void editingPlanRoleReplacesRoleForExistingSubscriber() {
        stubRoles();
        var plan = plan("month", "MEMBER");
        when(plans.findById(plan.getId())).thenReturn(Optional.of(plan));
        var vip = RoleEntity.createCustom(UUID.randomUUID(), "VIP", "VIP", RoleType.MEMBERSHIP, null, List.of(), Instant.now());
        when(roles.findBySlug("VIP")).thenReturn(Optional.of(vip));
        when(memberships.findActiveUserIdsByPlanId(plan.getId())).thenReturn(List.of(userId));
        when(memberships.findActiveByUserId(eq(userId), any())).thenReturn(List.of(
                MembershipEntity.create(UUID.randomUUID(), userId, plan.getId(), Instant.now(), null, Instant.now())));
        new MembershipPlanAdminService(plans, roles, memberships, sync).updatePlan(plan.getId(),
                new UpdateMembershipPlanRequest("Member", null, 100, "month", "VIP", 30, "active", null));
        verify(assignments).revokeGlobalRole(userId, "MEMBER");
        verify(assignments).assignGlobalRole(userId, "VIP", null);
        verify(assignments, never()).revokeGlobalRole(userId, "VIP");
    }
}
