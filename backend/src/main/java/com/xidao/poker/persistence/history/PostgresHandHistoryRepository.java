package com.xidao.poker.persistence.history;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xidao.poker.application.history.CompletedHandArchive;
import com.xidao.poker.application.history.HandHistoryRepository;
import com.xidao.poker.application.history.HandHistorySaveResult;
import com.xidao.poker.engine.history.CompletedHandAction;
import com.xidao.poker.engine.history.CompletedHandPlayer;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/** PostgreSQL 历史适配器：一手牌及统计更新在同一事务中完成。 */
public class PostgresHandHistoryRepository implements HandHistoryRepository {
    private final HandHistoryMapper mapper;
    private final ObjectMapper objectMapper;
    private final HistorySchemaInitializer schemaInitializer;

    public PostgresHandHistoryRepository(HandHistoryMapper mapper, ObjectMapper objectMapper) {
        this(mapper, objectMapper, HistorySchemaInitializer.ALREADY_READY);
    }

    public PostgresHandHistoryRepository(
            HandHistoryMapper mapper,
            ObjectMapper objectMapper,
            HistorySchemaInitializer schemaInitializer
    ) {
        this.mapper = Objects.requireNonNull(mapper, "hand history mapper");
        this.objectMapper = Objects.requireNonNull(objectMapper, "object mapper");
        this.schemaInitializer = Objects.requireNonNull(schemaInitializer, "history schema initializer");
    }

    @Override
    @Transactional(transactionManager = "historyTransactionManager")
    public HandHistorySaveResult save(CompletedHandArchive archive) {
        Objects.requireNonNull(archive, "completed hand archive");
        schemaInitializer.ensureReady();
        mapper.upsertGame(archive);
        int inserted = mapper.insertHand(
                archive,
                json(archive.hand().communityCards()),
                json(archive.hand().pots()),
                json(archive.hand().awards())
        );
        if (inserted == 0) return HandHistorySaveResult.ALREADY_EXISTS;

        for (CompletedHandPlayer player : archive.hand().players()) {
            mapper.upsertUser(player, archive.endedAt());
            mapper.insertPlayer(archive.gameId(), archive.handId(), player, json(player.holeCards()));
            mapper.upsertStatistic(player, player.winnings() > 0 ? 1 : 0, archive.endedAt());
        }
        for (CompletedHandAction action : archive.hand().actions()) {
            mapper.insertAction(archive.gameId(), archive.handId(), action);
        }
        mapper.markHandSaved(archive.gameId(), archive.endedAt());
        return HandHistorySaveResult.SAVED;
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("failed to serialize completed hand history", error);
        }
    }
}
