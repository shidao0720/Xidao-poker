package com.xidao.poker.web.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record IdentityRequest(
        @NotBlank @Size(min = 2, max = 64) String realName,
        @NotBlank @Size(min = 1, max = 12) String gameId,
        @NotBlank @Size(min = 8, max = 72) String password
) { }
