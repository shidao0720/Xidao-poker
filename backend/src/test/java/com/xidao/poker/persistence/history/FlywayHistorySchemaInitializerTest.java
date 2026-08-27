package com.xidao.poker.persistence.history;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FlywayHistorySchemaInitializerTest {
    @Test
    void failedMigrationRemainsRetryableAndSuccessIsCached() {
        Flyway flyway = mock(Flyway.class);
        when(flyway.migrate())
                .thenThrow(new IllegalStateException("database unavailable"))
                .thenReturn(null);
        FlywayHistorySchemaInitializer initializer = new FlywayHistorySchemaInitializer(flyway);

        assertThatThrownBy(initializer::ensureReady)
                .isInstanceOf(IllegalStateException.class);
        initializer.ensureReady();
        initializer.ensureReady();

        verify(flyway, times(2)).migrate();
    }
}
