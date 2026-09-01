package com.xidao.poker.web.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record BroadcastMailRequest(
        @NotBlank @Pattern(regexp = "ANNOUNCEMENT|NOTICE|REWARD") String type,
        @NotBlank @Size(max = 80) String subject,
        @NotBlank @Size(max = 2000) String body,
        @Min(0) @Max(10_000_000) long rewardChips,
        @Min(0) @Max(1_000_000) long rewardCrystals,
        @Size(max = 64) String rewardSkinKey,
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{8,64}") String requestId
) { }
