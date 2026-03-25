package com.markov.lab.controller.dto;

public record AppStatsDTO(
        long totalUsers,
        long activeUsers,
        long totalReports,
        long totalPeriods,
        long totalStructures,
        long totalRoles
) {}
