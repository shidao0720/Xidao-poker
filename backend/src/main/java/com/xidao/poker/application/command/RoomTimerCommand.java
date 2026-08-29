package com.xidao.poker.application.command;

/**
 * 所有可能改变房间状态的定时任务都必须转换为该命令后，再进入 RoomRuntime 串行边界。
 */
public sealed interface RoomTimerCommand
        permits TurnTimeoutCommand, DisconnectExpiryCommand, EmptyRoomCleanupCommand {
    String roomId();
    String commandId();
    long handId();
    long turnId();
}
