package com.xidao.poker.web.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RedemptionRequest(
        @NotBlank @Size(min = 4, max = 64) String code,
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{8,64}") String requestId
) { }
