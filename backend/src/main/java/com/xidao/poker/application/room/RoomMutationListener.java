package com.xidao.poker.application.room;

/** 已提交房间命令的轻量通知；监听器不得反向改变本次提交结果。 */
@FunctionalInterface
public interface RoomMutationListener {
    void afterCommittedMutation(String roomId);
}
