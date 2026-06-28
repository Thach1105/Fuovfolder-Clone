package com.fuoverflow.payment.api.dto;

import java.util.List;

public record PaymentAnalyticsResponse(
        long totalRevenue,
        long totalTransactions,
        long totalPointsIssued,
        StatusBreakdown statusBreakdown,
        double conversionRate,
        long averageDepositAmount,
        List<TierRevenue> revenueByTier,
        List<TopUser> topUsers
) {
    public record StatusBreakdown(long paid, long pending, long failed, long expired) {}
    public record TierRevenue(String tierLabel, long count, long totalRevenue) {}
    public record TopUser(String userId, String username, long totalDeposited, long transactionCount) {}
}
