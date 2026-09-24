package com.markov.lab.controller.dto;

public record LabActivityDataRequest(
        Long reportId,
        Long informationId,
        Integer value
) {
}
