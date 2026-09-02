package com.xidao.poker.web.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record CreateRedemptionCodeRequest(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{4,64}") String code,
        @NotBlank @Pattern(regexp = "CHIP|CRYSTAL") String currency,
        @Min(1) @Max(10_000_000) long rewardAmount,
        @Min(1) @Max(1_000_000) Integer maxRedemptions,
        Instant validFrom,
        Instant validUntil,
        @NotBlank @Size(min = 8, max = 64)
        @Pattern(regexp = "[A-Za-z0-9_-]{8,64}") String requestId
) { }
