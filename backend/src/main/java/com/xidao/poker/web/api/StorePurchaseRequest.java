package com.xidao.poker.web.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record StorePurchaseRequest(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{8,64}") String requestId,
        @NotBlank @Size(max = 64) String itemKey
) {}
