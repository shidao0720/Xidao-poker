package com.xidao.poker.engine.action;

import java.util.Objects;

public final class IllegalActionException extends RuntimeException {
    private final ActionErrorCode code;

    public IllegalActionException(ActionErrorCode code, String message) {
        super(message);
        this.code = Objects.requireNonNull(code, "action error code");
    }

    public ActionErrorCode code() { return code; }
}
