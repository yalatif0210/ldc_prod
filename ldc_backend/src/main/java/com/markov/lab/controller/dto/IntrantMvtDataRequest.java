package com.markov.lab.controller.dto;

public record IntrantMvtDataRequest(
        Long reportId,
        Long intrantId,
        Integer entryStock,
        Integer distributionStock,
        Integer availableStock
) {
}
