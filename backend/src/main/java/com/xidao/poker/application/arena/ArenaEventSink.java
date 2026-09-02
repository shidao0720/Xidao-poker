package com.xidao.poker.application.arena;

@FunctionalInterface
public interface ArenaEventSink {
    void broadcast(String roomId, ArenaUpdate update);

    static ArenaEventSink noOp() {
        return (roomId, update) -> { };
    }
}
