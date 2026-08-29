package com.xidao.poker.web.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AvatarUpdateRequest(
        @NotBlank @Size(max = 32) String avatarKey
) { }
