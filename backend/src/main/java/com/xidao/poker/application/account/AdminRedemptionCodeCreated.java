package com.xidao.poker.application.account;

/** The plaintext code is returned once and is never persisted. */
public record AdminRedemptionCodeCreated(String code, AdminRedemptionCode redemptionCode) { }
