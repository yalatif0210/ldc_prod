package com.markov.lab.controller.dto;

import java.util.Map;

public record FilteredStatsDTO(
        long reportCount,
        Map<String, Long> reportsByStatus,
        String periodLabel,
        String structureLabel,
        String equipmentLabel
) {}
