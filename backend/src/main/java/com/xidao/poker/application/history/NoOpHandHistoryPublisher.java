package com.xidao.poker.application.history;

/** 未启用 PostgreSQL 时的显式适配器。 */
public enum NoOpHandHistoryPublisher implements HandHistoryPublisher {
    INSTANCE;

    @Override
    public HistoryPublishResult publish(CompletedHandArchive archive) {
        if (archive == null) throw new IllegalArgumentException("completed hand archive is required");
        return HistoryPublishResult.DISABLED;
    }
}
