package com.markov.lab.controller.dto;

public record ResetPasswordRequest(Long userId, String newPassword) {}
