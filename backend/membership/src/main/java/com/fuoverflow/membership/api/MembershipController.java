package com.fuoverflow.membership.api;

import com.fuoverflow.common.security.RequirePermission;
import com.fuoverflow.common.web.ApiResponse;
import com.fuoverflow.membership.api.dto.MembershipPlanResponse;
import com.fuoverflow.membership.api.dto.MembershipStatusResponse;
import com.fuoverflow.membership.api.dto.SubscribeMembershipRequest;
import com.fuoverflow.membership.application.MembershipService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/membership")
public class MembershipController {
    private final MembershipService membershipService;

    public MembershipController(MembershipService membershipService) {
        this.membershipService = membershipService;
    }

    @GetMapping("/plans")
    public ApiResponse<List<MembershipPlanResponse>> plans() {
        return ApiResponse.ok(membershipService.listPlans());
    }

    @GetMapping("/me")
    @RequirePermission("membership.plan:read")
    public ApiResponse<MembershipStatusResponse> me(Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        return ApiResponse.ok(membershipService.currentMembership(userId));
    }

    @PostMapping("/subscribe")
    @RequirePermission("membership.subscribe:create")
    public ApiResponse<MembershipStatusResponse> subscribe(
            Authentication authentication,
            @Valid @RequestBody SubscribeMembershipRequest request) {
        UUID userId = UUID.fromString(authentication.getName());
        return ApiResponse.ok(membershipService.subscribe(userId, request));
    }
}
