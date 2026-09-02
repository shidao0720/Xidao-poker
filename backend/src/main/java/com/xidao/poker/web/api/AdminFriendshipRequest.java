package com.xidao.poker.web.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

public record AdminFriendshipRequest(
        UUID firstAccountId,
        UUID secondAccountId,
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{8,64}") String requestId
) { }
