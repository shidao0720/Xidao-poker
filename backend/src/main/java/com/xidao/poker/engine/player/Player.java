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
    public static final String DEFAULT_AVATAR_KEY = "default";
    private final String id;
    private final String name;
    private final String avatarKey;
    private final int seat;
    private final List<Card> holeCards = new ArrayList<>(2);
    private int stack;
    private int streetBet;
    private int totalContribution;
    private boolean hasActed;
    private boolean ready;
    private ConnectionStatus connectionStatus;
    private SeatStatus seatStatus;
    private HandStatus handStatus;

    public Player(String id, String name, int seat, int stack) {
        this(id, name, seat, stack, DEFAULT_AVATAR_KEY);
    }

    public Player(String id, String name, int seat, int stack, String avatarKey) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("player id is required");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("player name is required");
        if (seat < 0 || seat > 9) throw new IllegalArgumentException("seat must be between 0 and 9");
        if (stack < 0) throw new IllegalArgumentException("stack cannot be negative");
        this.id = id;
        this.name = name;
        this.avatarKey = avatarKey == null || avatarKey.isBlank() ? DEFAULT_AVATAR_KEY : avatarKey;
        this.seat = seat;
        this.stack = stack;
        this.connectionStatus = ConnectionStatus.CONNECTED;
        this.seatStatus = stack == 0 ? SeatStatus.BUSTED : SeatStatus.SEATED;
        this.handStatus = HandStatus.NOT_IN_HAND;
    }

    public void beginHand() {
        holeCards.clear();
        streetBet = 0;
        totalContribution = 0;
        hasActed = false;
        seatStatus = stack == 0 ? SeatStatus.BUSTED : SeatStatus.SEATED;
        handStatus = stack == 0 ? HandStatus.NOT_IN_HAND : HandStatus.ACTIVE;
    }

    /** 将有筹码的等待玩家或观察者加入下一手；不会恢复仍处于离线状态的玩家。 */
    public void activateForNextHand() {
        if (connectionStatus == ConnectionStatus.DISCONNECTED) {
            throw new IllegalStateException("disconnected player cannot join a hand");
        }
        if (stack <= 0) {
            seatStatus = SeatStatus.BUSTED;
            throw new IllegalStateException("player has no chips");
        }
        seatStatus = SeatStatus.SEATED;
    }

    public void beginStreet() {
        streetBet = 0;
        hasActed = false;
    }

    public int contribute(int requested) {
        if (requested < 0) throw new IllegalArgumentException("contribution cannot be negative");
        int paid = Math.min(requested, stack);
        stack -= paid;
        streetBet = Math.addExact(streetBet, paid);
        totalContribution = Math.addExact(totalContribution, paid);
        if (stack == 0) handStatus = HandStatus.ALL_IN;
        return paid;
    }

    public void addWinnings(int amount) {
        if (amount < 0) throw new IllegalArgumentException("winnings cannot be negative");
        stack = Math.addExact(stack, amount);
    }

    public void deal(Card card) {
        Objects.requireNonNull(card, "card");
        if (holeCards.size() >= 2) throw new IllegalStateException("player already has two hole cards");
        holeCards.add(card);
    }

    public String id() { return id; }
    public String name() { return name; }
    public String avatarKey() { return avatarKey; }
    public int seat() { return seat; }
    public int stack() { return stack; }
    public int streetBet() { return streetBet; }
    public int totalContribution() { return totalContribution; }
    public PlayerStatus status() {
        if (connectionStatus == ConnectionStatus.DISCONNECTED) return PlayerStatus.DISCONNECTED;
        if (seatStatus == SeatStatus.BUSTED) return PlayerStatus.BUSTED;
        if (seatStatus == SeatStatus.SPECTATOR && handStatus == HandStatus.NOT_IN_HAND) {
            return PlayerStatus.SPECTATOR;
        }
        return switch (handStatus) {
            case ACTIVE -> PlayerStatus.ACTIVE;
            case FOLDED -> PlayerStatus.FOLDED;
            case ALL_IN -> PlayerStatus.ALL_IN;
            case NOT_IN_HAND -> seatStatus == SeatStatus.SPECTATOR
                    ? PlayerStatus.SPECTATOR
                    : PlayerStatus.ACTIVE;
        };
    }
    public ConnectionStatus connectionStatus() { return connectionStatus; }
    public SeatStatus seatStatus() { return seatStatus; }
    public HandStatus handStatus() { return handStatus; }
    public boolean hasActed() { return hasActed; }
    public boolean ready() { return ready; }
    public List<Card> holeCards() { return Collections.unmodifiableList(holeCards); }

    public boolean canAct() {
        return connectionStatus == ConnectionStatus.CONNECTED
                && seatStatus == SeatStatus.SEATED
                && handStatus == HandStatus.ACTIVE;
    }

    public boolean isInHand() {
        return handStatus == HandStatus.ACTIVE || handStatus == HandStatus.ALL_IN;
    }

    public boolean isFolded() {
        return handStatus == HandStatus.FOLDED;
    }

    public void fold() {
        if (!canAct()) throw new IllegalStateException("only an active player can fold");
        handStatus = HandStatus.FOLDED;
    }

    public void disconnect() {
        connectionStatus = ConnectionStatus.DISCONNECTED;
    }

    /**
     * 行动中的玩家掉线时保留 DISCONNECTED 生命周期，同时在当前手牌内按弃牌处理。
     * 这样既能在 30 秒内重连，也不会占住行动指针或继续争夺尚未跟平的底池。
     */
    public void disconnectAndForfeitHand() {
        if (handStatus == HandStatus.ACTIVE) handStatus = HandStatus.FOLDED;
        connectionStatus = ConnectionStatus.DISCONNECTED;
    }

    public void reconnect() {
        if (connectionStatus != ConnectionStatus.DISCONNECTED) {
            throw new IllegalStateException("player is not disconnected");
        }
        connectionStatus = ConnectionStatus.CONNECTED;
    }

    public void markBusted() {
        seatStatus = SeatStatus.BUSTED;
    }

    public void becomeSpectator() {
        seatStatus = SeatStatus.SPECTATOR;
    }

    /** 一手结束后清理手内状态，同时保留离线生命周期供重连。 */
    public void finishHand() {
        if (stack == 0) seatStatus = SeatStatus.BUSTED;
        else if (seatStatus != SeatStatus.SPECTATOR) seatStatus = SeatStatus.SEATED;
        handStatus = HandStatus.NOT_IN_HAND;
        hasActed = false;
        streetBet = 0;
    }

    public boolean isDisconnected() {
        return connectionStatus == ConnectionStatus.DISCONNECTED;
    }

    public boolean isSpectator() {
        return seatStatus == SeatStatus.SPECTATOR;
    }

    public boolean isBusted() {
        return seatStatus == SeatStatus.BUSTED;
    }

    public void setHasActed(boolean hasActed) { this.hasActed = hasActed; }
    public void setReady(boolean ready) { this.ready = ready; }
}
