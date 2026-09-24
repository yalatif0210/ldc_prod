package com.markov.lab.controller.dto;

public record AdjustmentRequest(
        Long intrantMvtDataId,
        Long adjustmentTypeId,
        Integer quantity,
        String comment
) {
}
