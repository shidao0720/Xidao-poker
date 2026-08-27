package com.xidao.poker.persistence.history;

import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/** 延迟到首次历史写入时迁移，数据库离线不会阻止实时服务启动。 */
public final class FlywayHistorySchemaInitializer implements HistorySchemaInitializer {
    private static final Logger log = LoggerFactory.getLogger(FlywayHistorySchemaInitializer.class);

    private final Flyway flyway;
    private volatile boolean ready;

    public FlywayHistorySchemaInitializer(Flyway flyway) {
        this.flyway = Objects.requireNonNull(flyway, "flyway");
    }

    @Override
    public void ensureReady() {
        if (ready) return;
        synchronized (this) {
            if (ready) return;
            flyway.migrate();
            ready = true;
            log.info("HAND_HISTORY_SCHEMA_READY");
        }
    }
}
