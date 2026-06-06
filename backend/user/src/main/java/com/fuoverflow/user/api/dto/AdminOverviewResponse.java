package com.fuoverflow.user.api.dto;

public record AdminOverviewResponse(
        long totalUsers,
        long activeUsers,
        long pendingVerificationUsers,
        long disabledUsers,
        long superAdminUsers,
        long adminUsers,
        long subAdminUsers
) {
}
