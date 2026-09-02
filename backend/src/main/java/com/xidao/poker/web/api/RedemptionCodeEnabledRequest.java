package com.xidao.poker.web.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RedemptionCodeEnabledRequest(
        boolean enabled,
        @NotBlank @Size(min = 8, max = 64)
        @Pattern(regexp = "[A-Za-z0-9_-]{8,64}") String requestId
) { }
