package com.xidao.poker.engine.game;

import com.xidao.poker.engine.action.ActionErrorCode;
import com.xidao.poker.engine.action.IllegalActionException;
import com.xidao.poker.engine.action.PlayerAction;
import com.xidao.poker.engine.event.GameEvent;
import com.xidao.poker.engine.event.GameEventType;
import com.xidao.poker.engine.player.Player;
import com.xidao.poker.engine.snapshot.GameSnapshot;
import com.xidao.poker.engine.snapshot.PlayerSnapshot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 整场房间游戏的聚合根，管理玩家、房主和连续多手牌。
 *
 * <p>公开写方法使用 synchronized 作为第一版房间级锁。观察者只存在于 Session，
 * 绝不会被传入 Hand；Hand 的行动轮转因此只面对经过筛选的本手参与者。</p>
 */
public final class GameSession {
    private final String id;
    private final GameConfig config;
    private final long baseSeed;
    private final Map<String, Player> playersById = new LinkedHashMap<>();
    private final List<GameEvent> eventLog = new ArrayList<>();
    private String ownerId;
    private Hand currentHand;
    private long handCounter;
    private long sequence;
    private Integer lastButtonSeat;
    private boolean gameStarted;

    public GameSession(String id, GameConfig config, long baseSeed) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("session id is required");
        if (config == null) throw new IllegalArgumentException("game config is required");
        this.id = id;
        this.config = config;
        this.baseSeed = baseSeed;
    }

    public synchronized List<GameEvent> addPlayer(String playerId, String name) {
        if (playersById.containsKey(playerId)) throw new IllegalArgumentException("player already joined");
        if (playersById.size() >= config.maxPlayers()) throw new IllegalStateException("room is full");
        int seat = firstFreeSeat();
        Player player = new Player(playerId, name, seat, config.buyIn());
        if (handInProgress()) player.becomeSpectator();
        playersById.put(playerId, player);
        if (ownerId == null) ownerId = playerId;
        return publish(List.of(GameEvent.of(GameEventType.PLAYER_JOINED, currentHandId(), playerId, Map.of(
                "name", name,
                "seat", seat,
                "status", player.status().name()
        ))));
    }

    public synchronized List<GameEvent> setReady(String playerId, boolean ready) {
        Player player = requirePlayer(playerId);
        if (player.isDisconnected() || player.isBusted()) {
            throw new IllegalActionException(ActionErrorCode.INVALID_ACTION, "player cannot change ready state");
        }
        player.setReady(ready);
        return publish(List.of(GameEvent.of(GameEventType.READY_CHANGED, currentHandId(), playerId, Map.of(
                "ready", ready,
                "phase", phase().name()
        ))));
    }

    public synchronized List<GameEvent> startGame(String requestedBy) {
        if (!requestedBy.equals(ownerId)) {
            throw new IllegalActionException(ActionErrorCode.INVALID_ACTION, "only the room owner can start");
        }
        if (handInProgress()) {
            throw new IllegalActionException(ActionErrorCode.INVALID_PHASE, "a hand is already in progress");
        }
        List<Player> eligible = eligibleForNextHand();
        if (eligible.size() < 2) {
            throw new IllegalStateException("at least two connected players with chips are required");
        }
        if (eligible.stream().anyMatch(p -> !p.ready())) {
            throw new IllegalStateException("all eligible players must be ready");
        }
        for (Player player : eligible) player.activateForNextHand();

        int buttonSeat = nextButtonSeat(eligible);
        lastButtonSeat = buttonSeat;
        handCounter++;
        currentHand = new Hand(handCounter, config, eligible, buttonSeat, baseSeed + handCounter);

        List<GameEvent> raw = new ArrayList<>();
        if (!gameStarted) {
            gameStarted = true;
            raw.add(GameEvent.of(GameEventType.GAME_STARTED, handCounter, null, Map.of(
                    "sessionId", id,
                    "playerCount", eligible.size()
            )));
        }
        raw.addAll(currentHand.start());
        return publish(raw);
    }

    public synchronized List<GameEvent> handle(PlayerAction action) {
        requireHandInProgress();
        return publish(currentHand.handle(action));
    }

    public synchronized List<GameEvent> disconnect(String playerId) {
        Player player = requirePlayer(playerId);
        List<GameEvent> raw;
        if (handInProgress() && currentHand.containsPlayer(playerId)) {
            raw = new ArrayList<>(currentHand.disconnect(playerId));
        } else {
            player.disconnect();
            raw = new ArrayList<>(List.of(GameEvent.of(
                    GameEventType.PLAYER_DISCONNECTED, currentHandId(), playerId, Map.of("seat", player.seat()))));
        }
        if (playerId.equals(ownerId)) {
            String nextOwner = connectedPlayers().stream()
                    .filter(p -> !p.id().equals(playerId))
                    .min(Comparator.comparingInt(Player::seat))
                    .map(Player::id)
                    .orElse(null);
            if (nextOwner != null) {
                ownerId = nextOwner;
                raw.add(GameEvent.of(GameEventType.OWNER_CHANGED, currentHandId(), nextOwner, Map.of(
                        "previousOwnerId", playerId,
                        "ownerId", nextOwner
                )));
            }
        }
        return publish(raw);
    }

    public synchronized List<GameEvent> reconnect(String playerId) {
        Player player = requirePlayer(playerId);
        List<GameEvent> raw;
        if (currentHand != null && currentHand.containsPlayer(playerId) && player.isDisconnected()) {
            raw = currentHand.reconnect(playerId);
        } else {
            player.reconnect();
            raw = List.of(GameEvent.of(
                    GameEventType.PLAYER_RECONNECTED, currentHandId(), playerId, Map.of("seat", player.seat())));
        }
        return publish(raw);
    }

    public synchronized GameSnapshot snapshot(String viewerId) {
        requirePlayer(viewerId);
        List<PlayerSnapshot> playerSnapshots = playersById.values().stream()
                .sorted(Comparator.comparingInt(Player::seat))
                .map(player -> new PlayerSnapshot(
                        player.id(),
                        player.name(),
                        player.seat(),
                        player.stack(),
                        player.streetBet(),
                        player.status(),
                        player.ready(),
                        currentHand == null ? List.of() : currentHand.visibleHoleCards(viewerId, player.id())
                ))
                .toList();

        return new GameSnapshot(
                id,
                ownerId,
                currentHandId(),
                phase(),
                currentHand == null ? null : currentHand.buttonSeat(),
                currentHand == null ? null : currentHand.smallBlindSeat(),
                currentHand == null ? null : currentHand.bigBlindSeat(),
                currentHand == null ? null : currentHand.currentActorSeat(),
                currentHand == null ? 0 : currentHand.currentBet(),
                currentHand == null ? config.bigBlind() : currentHand.minimumRaise(),
                currentHand == null ? 0 : currentHand.potAmount(),
                currentHand == null ? List.of() : currentHand.communityCards(),
                playerSnapshots,
                currentHand == null ? java.util.Set.of() : currentHand.legalActions(viewerId),
                currentHand == null ? List.of() : currentHand.awards(),
                sequence
        );
    }

    public synchronized GamePhase phase() {
        if (currentHand != null) return currentHand.phase();
        return allEligibleReady() ? GamePhase.READY : GamePhase.WAITING;
    }

    private List<GameEvent> publish(List<GameEvent> rawEvents) {
        List<GameEvent> published = new ArrayList<>(rawEvents.size());
        for (GameEvent raw : rawEvents) {
            GameEvent sequenced = raw.withSequence(++sequence);
            eventLog.add(sequenced);
            published.add(sequenced);
        }
        return List.copyOf(published);
    }

    private List<Player> eligibleForNextHand() {
        return playersById.values().stream()
                .filter(p -> !p.isDisconnected())
                .filter(p -> p.stack() > 0)
                .sorted(Comparator.comparingInt(Player::seat))
                .toList();
    }

    private List<Player> connectedPlayers() {
        return playersById.values().stream().filter(p -> !p.isDisconnected()).toList();
    }

    private boolean allEligibleReady() {
        List<Player> eligible = eligibleForNextHand();
        return eligible.size() >= 2 && eligible.stream().allMatch(Player::ready);
    }

    private int nextButtonSeat(List<Player> eligible) {
        if (lastButtonSeat == null) {
            Player owner = playersById.get(ownerId);
            if (owner != null && eligible.contains(owner)) return owner.seat();
            return eligible.getFirst().seat();
        }
        for (int offset = 1; offset <= config.maxPlayers(); offset++) {
            int candidate = Math.floorMod(lastButtonSeat + offset, config.maxPlayers());
            if (eligible.stream().anyMatch(p -> p.seat() == candidate)) return candidate;
        }
        throw new IllegalStateException("no eligible button seat");
    }

    private int firstFreeSeat() {
        for (int seat = 0; seat < config.maxPlayers(); seat++) {
            int candidate = seat;
            if (playersById.values().stream().noneMatch(p -> p.seat() == candidate)) return seat;
        }
        throw new IllegalStateException("room is full");
    }

    private boolean handInProgress() {
        return currentHand != null && currentHand.phase() != GamePhase.ROUND_END;
    }

    private void requireHandInProgress() {
        if (!handInProgress()) {
            throw new IllegalActionException(ActionErrorCode.INVALID_PHASE, "no hand is in progress");
        }
    }

    private Player requirePlayer(String playerId) {
        Player player = playersById.get(playerId);
        if (player == null) throw new IllegalActionException(ActionErrorCode.UNKNOWN_PLAYER, "unknown player");
        return player;
    }

    private long currentHandId() {
        return currentHand == null ? 0 : currentHand.id();
    }

    public String id() { return id; }
    public GameConfig config() { return config; }
    public synchronized String ownerId() { return ownerId; }
    // 仅供同包引擎测试与诊断使用，避免应用层绕过 Session 锁直接修改聚合内部对象。
    synchronized Hand currentHand() { return currentHand; }
    synchronized List<Player> players() { return List.copyOf(playersById.values()); }
    public synchronized List<GameEvent> eventLog() { return List.copyOf(eventLog); }
}
