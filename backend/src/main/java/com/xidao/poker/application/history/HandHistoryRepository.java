package com.xidao.poker.application.history;

/** PostgreSQL 等历史存储适配器实现此端口；实时 Engine 不依赖它。 */
@FunctionalInterface
public interface HandHistoryRepository {
    HandHistorySaveResult save(CompletedHandArchive archive);
}
