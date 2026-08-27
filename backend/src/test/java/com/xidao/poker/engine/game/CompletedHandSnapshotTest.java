package com.xidao.poker.engine.game;

import com.xidao.poker.engine.action.PlayerAction;
import com.xidao.poker.engine.history.CompletedHandSnapshot;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompletedHandSnapshotTest {
    @Test
    void exposesSensitiveHistoryOnlyAfterSettlementAndCalculatesStacks() {
        GameSession session = new GameSession("completed-game", new GameConfig(5, 10, 100, 2), 42L);
        session.addPlayer("A", "Alice");
        session.addPlayer("B", "Bob");
        session.setReady("A", true);
        session.setReady("B", true);

        assertThatThrownBy(session::completedHandSnapshot)
                .isInstanceOf(IllegalStateException.class);

        session.startGame("A");
        String actor = session.currentActorPlayerId();
        session.handle(session.currentHandId(), session.currentTurnId(), PlayerAction.fold(actor));

        CompletedHandSnapshot completed = session.completedHandSnapshot();
        assertThat(completed.sessionId()).isEqualTo("completed-game");
        assertThat(completed.totalPot()).isEqualTo(15);
        assertThat(completed.players()).hasSize(2).allSatisfy(player ->
                assertThat(player.holeCards()).hasSize(2));
        assertThat(completed.players()).filteredOn(player -> player.playerId().equals(actor))
                .singleElement()
                .satisfies(player -> {
                    assertThat(player.folded()).isTrue();
                    assertThat(player.startingStack()).isEqualTo(100);
                    assertThat(player.endingStack()).isEqualTo(95);
                    assertThat(player.totalContribution()).isEqualTo(5);
                });
        assertThat(completed.players()).filteredOn(player -> !player.playerId().equals(actor))
                .singleElement()
                .satisfies(player -> {
                    assertThat(player.startingStack()).isEqualTo(100);
                    assertThat(player.endingStack()).isEqualTo(105);
                    assertThat(player.winnings()).isEqualTo(15);
                });
        assertThat(completed.actions()).singleElement()
                .satisfies(action -> {
                    assertThat(action.playerId()).isEqualTo(actor);
                    assertThat(action.action().name()).isEqualTo("FOLD");
                    assertThat(action.phase()).isEqualTo(GamePhase.PREFLOP);
                });
        assertThat(completed.actionHistoryComplete()).isTrue();
    }
}
