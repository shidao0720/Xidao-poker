package com.xidao.poker.engine.action;

public final class IllegalActionException extends RuntimeException {
    private final ActionErrorCode code;

    public IllegalActionException(ActionErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public ActionErrorCode code() { return code; }
}
