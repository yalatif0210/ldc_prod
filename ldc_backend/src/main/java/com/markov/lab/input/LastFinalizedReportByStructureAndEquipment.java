package com.markov.lab.input;

public record LastFinalizedReportByStructureAndEquipment(
        String equipment_name,
        Long structure_id,
        String period_name
) {
}
