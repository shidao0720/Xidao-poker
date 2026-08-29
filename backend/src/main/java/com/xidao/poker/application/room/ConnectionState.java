package com.xidao.poker.application.room;

/** 仅描述应用层 Socket 生命周期，不代替 Engine 的连接、座位与当前手三个正交状态。 */
public enum ConnectionState {
    CONNECTED,
    RECONNECTING,
    CLOSED
}
