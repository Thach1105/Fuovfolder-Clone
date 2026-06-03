package com.fuoverflow.user.api.dto;

public record AdminOverviewResponse(
        long totalUsers,
        long activeUsers,
        long pendingVerificationUsers,
        long disabledUsers,
        long adminUsers,
        long subAdminUsers
) {
}
