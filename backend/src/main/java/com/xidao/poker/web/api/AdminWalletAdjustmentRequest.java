package com.xidao.poker.web.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AdminWalletAdjustmentRequest(
        long chipDelta,
        long crystalDelta,
        @NotBlank @Size(min = 2, max = 200) String reason,
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{8,64}") String requestId
) { }
