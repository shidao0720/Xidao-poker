package com.xidao.poker.engine.player;

import com.xidao.poker.engine.card.Card;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 游戏引擎中的玩家状态。实时筹码和下注状态只保存在服务端内存中。
 */
public final class Player {
    private final String id;
    private final String name;
    private final int seat;
    private final List<Card> holeCards = new ArrayList<>(2);
    private int stack;
    private int streetBet;
    private int totalContribution;
    private boolean hasActed;
    private boolean ready;
    private PlayerStatus status;
    private PlayerStatus statusBeforeDisconnect;

    public Player(String id, String name, int seat, int stack) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("player id is required");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("player name is required");
        if (seat < 0 || seat > 9) throw new IllegalArgumentException("seat must be between 0 and 9");
        if (stack < 0) throw new IllegalArgumentException("stack cannot be negative");
        this.id = id;
        this.name = name;
        this.seat = seat;
        this.stack = stack;
        this.status = stack == 0 ? PlayerStatus.SPECTATOR : PlayerStatus.ACTIVE;
    }

    public void beginHand() {
        holeCards.clear();
        streetBet = 0;
        totalContribution = 0;
        hasActed = false;
        if (status == PlayerStatus.DISCONNECTED) {
            statusBeforeDisconnect = stack == 0 ? PlayerStatus.BUSTED : PlayerStatus.ACTIVE;
        } else {
            status = stack == 0 ? PlayerStatus.BUSTED : PlayerStatus.ACTIVE;
        }
    }

    public void beginStreet() {
        streetBet = 0;
        hasActed = false;
    }

    public int contribute(int requested) {
        if (requested < 0) throw new IllegalArgumentException("contribution cannot be negative");
        int paid = Math.min(requested, stack);
        stack -= paid;
        streetBet += paid;
        totalContribution += paid;
        if (stack == 0) status = PlayerStatus.ALL_IN;
        return paid;
    }

    public void addWinnings(int amount) {
        if (amount < 0) throw new IllegalArgumentException("winnings cannot be negative");
        stack += amount;
    }

    public void deal(Card card) {
        Objects.requireNonNull(card, "card");
        if (holeCards.size() >= 2) throw new IllegalStateException("player already has two hole cards");
        holeCards.add(card);
    }

    public String id() { return id; }
    public String name() { return name; }
    public int seat() { return seat; }
    public int stack() { return stack; }
    public int streetBet() { return streetBet; }
    public int totalContribution() { return totalContribution; }
    public PlayerStatus status() { return status; }
    public boolean hasActed() { return hasActed; }
    public boolean ready() { return ready; }
    public List<Card> holeCards() { return Collections.unmodifiableList(holeCards); }

    public boolean canAct() {
        return status == PlayerStatus.ACTIVE;
    }

    public boolean isInHand() {
        PlayerStatus effective = effectiveStatus();
        return effective == PlayerStatus.ACTIVE || effective == PlayerStatus.ALL_IN;
    }

    public boolean isFolded() {
        return effectiveStatus() == PlayerStatus.FOLDED;
    }

    public void fold() {
        requireStatus(PlayerStatus.ACTIVE, "only an active player can fold");
        status = PlayerStatus.FOLDED;
    }

    public void disconnect() {
        if (status == PlayerStatus.DISCONNECTED) return;
        statusBeforeDisconnect = status;
        status = PlayerStatus.DISCONNECTED;
    }

    public void reconnect() {
        requireStatus(PlayerStatus.DISCONNECTED, "player is not disconnected");
        status = statusBeforeDisconnect == null ? PlayerStatus.SPECTATOR : statusBeforeDisconnect;
        statusBeforeDisconnect = null;
    }

    public void markBusted() {
        status = PlayerStatus.BUSTED;
    }

    public void becomeSpectator() {
        status = PlayerStatus.SPECTATOR;
    }

    private PlayerStatus effectiveStatus() {
        return status == PlayerStatus.DISCONNECTED && statusBeforeDisconnect != null
                ? statusBeforeDisconnect
                : status;
    }

    private void requireStatus(PlayerStatus expected, String message) {
        if (status != expected) throw new IllegalStateException(message);
    }

    public void setHasActed(boolean hasActed) { this.hasActed = hasActed; }
    public void setReady(boolean ready) { this.ready = ready; }
}
