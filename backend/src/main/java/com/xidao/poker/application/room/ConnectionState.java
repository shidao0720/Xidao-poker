package com.xidao.poker.application.room;

/** 仅描述网络连接生命周期，不代替 Engine 的 PlayerStatus。 */
public enum ConnectionState {
    CONNECTED,
    RECONNECTING,
    CLOSED
}
