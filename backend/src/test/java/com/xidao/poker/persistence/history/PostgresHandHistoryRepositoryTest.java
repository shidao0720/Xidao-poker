package com.xidao.poker.persistence.history;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xidao.poker.application.history.CompletedHandArchive;
import com.xidao.poker.application.history.HandHistorySaveResult;
import com.xidao.poker.test.HistoryFixtures;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PostgresHandHistoryRepositoryTest {
    @Test
    void savesOneHandAggregateAndUpdatesStatistics() {
        HandHistoryMapper mapper = mock(HandHistoryMapper.class);
        when(mapper.insertHand(any(), anyString(), anyString(), anyString())).thenReturn(1);
        PostgresHandHistoryRepository repository = new PostgresHandHistoryRepository(mapper, new ObjectMapper());
        CompletedHandArchive archive = HistoryFixtures.archive(1);

        assertThat(repository.save(archive)).isEqualTo(HandHistorySaveResult.SAVED);

        verify(mapper).upsertGame(archive);
        verify(mapper, times(2)).upsertUser(any(), any());
        verify(mapper, times(2)).insertPlayer(anyString(), anyLong(), any(), anyString());
        verify(mapper).insertAction(anyString(), anyLong(), any());
        verify(mapper, times(2)).upsertStatistic(any(), anyInt(), any());
        verify(mapper).markHandSaved(archive.gameId(), archive.endedAt());
    }

    @Test
    void duplicateHandIsIdempotentAndDoesNotDoubleCountStatistics() {
        HandHistoryMapper mapper = mock(HandHistoryMapper.class);
        when(mapper.insertHand(any(), anyString(), anyString(), anyString())).thenReturn(0);
        PostgresHandHistoryRepository repository = new PostgresHandHistoryRepository(mapper, new ObjectMapper());

        assertThat(repository.save(HistoryFixtures.archive(1)))
                .isEqualTo(HandHistorySaveResult.ALREADY_EXISTS);

        verify(mapper, never()).upsertUser(any(), any());
        verify(mapper, never()).insertPlayer(anyString(), anyLong(), any(), anyString());
        verify(mapper, never()).insertAction(anyString(), anyLong(), any());
        verify(mapper, never()).upsertStatistic(any(), anyInt(), any());
        verify(mapper, never()).markHandSaved(anyString(), any());
    }
}
