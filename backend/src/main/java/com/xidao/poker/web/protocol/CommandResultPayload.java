package com.xidao.poker.web.protocol;

public record CommandResultPayload(boolean duplicate, boolean ignored, long lastSequence) {
}
