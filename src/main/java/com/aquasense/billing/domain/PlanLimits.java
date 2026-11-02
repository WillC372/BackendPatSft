package com.aquasense.billing.domain;

public record PlanLimits(PlanType plan, int maxDevices, int maxCustomProfiles, int maxAnalysesPerDay,
                         Integer maxAnalysisDays, boolean forecastEnabled, int maxReportsPerMonth,
                         boolean advancedReports, Integer maxReportDays) {

    private static final PlanLimits FREE = new PlanLimits(PlanType.FREE, 2, 2, 3, 30, false, 3, false, 30);
    private static final PlanLimits PREMIUM = new PlanLimits(PlanType.PREMIUM, 10, 20, 50, null, true, 100, true,
            null);

    public static PlanLimits of(PlanType plan) {
        return plan == PlanType.PREMIUM ? PREMIUM : FREE;
    }
}
