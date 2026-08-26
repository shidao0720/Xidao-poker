package com.xidao.poker.application.room;

public final class RoomApplicationException extends RuntimeException {
    private final RoomApplicationErrorCode code;

    public RoomApplicationException(RoomApplicationErrorCode code, String message) {
        super(message);
        if (code == null) throw new IllegalArgumentException("error code is required");
        this.code = code;
    }

    public RoomApplicationErrorCode code() {
        return code;
    }
}
