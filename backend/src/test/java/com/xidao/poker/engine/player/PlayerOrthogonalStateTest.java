package com.xidao.poker.engine.player;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlayerOrthogonalStateTest {
    @Test
    void disconnectedAllInPlayerKeepsCurrentHandEligibility() {
        Player player = new Player("A", "Alice", 0, 100);
        player.activateForNextHand();
        player.beginHand();
        player.contribute(100);

        player.disconnect();

        assertThat(player.connectionStatus()).isEqualTo(ConnectionStatus.DISCONNECTED);
        assertThat(player.seatStatus()).isEqualTo(SeatStatus.SEATED);
        assertThat(player.handStatus()).isEqualTo(HandStatus.ALL_IN);
        assertThat(player.isInHand()).isTrue();
        assertThat(player.canAct()).isFalse();
        assertThat(player.status()).isEqualTo(PlayerStatus.DISCONNECTED);

        player.reconnect();
        assertThat(player.connectionStatus()).isEqualTo(ConnectionStatus.CONNECTED);
        assertThat(player.handStatus()).isEqualTo(HandStatus.ALL_IN);
        assertThat(player.status()).isEqualTo(PlayerStatus.ALL_IN);
    }

    @Test
    void activeDisconnectForfeitsOnlyTheHandAndReconnectDoesNotRestoreActionRights() {
        Player player = new Player("A", "Alice", 0, 100);
        player.activateForNextHand();
        player.beginHand();

        player.disconnectAndForfeitHand();
        player.reconnect();

        assertThat(player.connectionStatus()).isEqualTo(ConnectionStatus.CONNECTED);
        assertThat(player.seatStatus()).isEqualTo(SeatStatus.SEATED);
        assertThat(player.handStatus()).isEqualTo(HandStatus.FOLDED);
        assertThat(player.isFolded()).isTrue();
        assertThat(player.canAct()).isFalse();
    }

    @Test
    void voluntaryLeaveAfterAllInChangesSeatAndConnectionButKeepsHandEligibility() {
        Player player = new Player("A", "Alice", 0, 100);
        player.activateForNextHand();
        player.beginHand();
        player.contribute(100);

        player.becomeSpectator();
        player.disconnect();

        assertThat(player.connectionStatus()).isEqualTo(ConnectionStatus.DISCONNECTED);
        assertThat(player.seatStatus()).isEqualTo(SeatStatus.SPECTATOR);
        assertThat(player.handStatus()).isEqualTo(HandStatus.ALL_IN);
        assertThat(player.isInHand()).isTrue();
        assertThat(player.canAct()).isFalse();
    }
}
