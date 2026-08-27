package com.xidao.poker.persistence.history;

/** 确保历史表结构可用；失败由异步写入器按手牌级策略重试。 */
@FunctionalInterface
public interface HistorySchemaInitializer {
    HistorySchemaInitializer ALREADY_READY = () -> { };

    void ensureReady();
}
