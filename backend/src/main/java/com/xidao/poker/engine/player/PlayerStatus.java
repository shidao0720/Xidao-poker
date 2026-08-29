package com.xidao.poker.engine.player;

/**
 * 兼容前端显示的聚合状态。规则判断不得依赖该枚举；应分别读取连接、座位和本手状态。
 */
public enum PlayerStatus {
    ACTIVE,
    FOLDED,
    ALL_IN,
    DISCONNECTED,
    SPECTATOR,
    BUSTED
}
