package com.xidao.poker.application.history;

/** 非阻塞地接收已完成手牌；返回值用于记录历史保存健康度，不能回滚实时牌局。 */
@FunctionalInterface
public interface HandHistoryPublisher {
    HistoryPublishResult publish(CompletedHandArchive archive);
}
