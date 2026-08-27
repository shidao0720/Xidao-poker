package com.xidao.poker.web.protocol;

public enum ClientMessageType {
    READY,
    START_GAME,
    PLAYER_ACTION,
    REQUEST_SNAPSHOT,
    REPLAY_EVENTS,
    LEAVE,
    PING
}
