package com.xidao.poker.web.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AdminPasswordResetRequest(
        @NotBlank @Size(min = 8, max = 72) String newPassword,
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{8,64}") String requestId
) { }
