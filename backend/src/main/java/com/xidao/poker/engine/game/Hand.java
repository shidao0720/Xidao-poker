package com.xidao.poker.engine.game;

import com.xidao.poker.engine.action.ActionErrorCode;
import com.xidao.poker.engine.action.ActionType;
import com.xidao.poker.engine.action.IllegalActionException;
import com.xidao.poker.engine.action.PlayerAction;
import com.xidao.poker.engine.card.Card;
import com.xidao.poker.engine.deck.Deck;
import com.xidao.poker.engine.eval.HandEvaluator;
import com.xidao.poker.engine.eval.HandResult;
import com.xidao.poker.engine.event.GameEvent;
import com.xidao.poker.engine.event.GameEventType;
import com.xidao.poker.engine.history.CompletedHandAction;
import com.xidao.poker.engine.history.CompletedHandPlayer;
import com.xidao.poker.engine.history.CompletedHandSnapshot;
import com.xidao.poker.engine.player.Player;
import com.xidao.poker.engine.player.PlayerStatus;
import com.xidao.poker.engine.pot.Pot;
import com.xidao.poker.engine.pot.PotAward;
import com.xidao.poker.engine.pot.PotManager;
import com.xidao.poker.engine.snapshot.ActionOptions;
import com.xidao.poker.engine.table.BettingActionResult;
import com.xidao.poker.engine.table.BettingRound;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * 一手德州扑克的纯 Java 聚合根。
 *
 * <p>Hand 只持有本手参与者，不持有观察者。任何行动指针都由
 * {@link Player#canAct()} 计算，状态变化后立即重新协调并自动推进。</p>
 */
public final class Hand {
    static final int MAX_ARCHIVED_ACTIONS = 8_192;

    private final long id;
    private final GameConfig config;
    private final List<Player> participants;
    private final Map<String, Player> playersById;
    private final Deck deck;
    private final int buttonSeat;
    private final List<Card> communityCards = new ArrayList<>(5);
    private final Set<String> showdownPlayerIds = new LinkedHashSet<>();
    private final List<CompletedHandAction> acceptedActions = new ArrayList<>();
    private final Map<String, Long> showdownHandKeys = new LinkedHashMap<>();
    private final Map<String, String> showdownCategories = new LinkedHashMap<>();
    private List<GameEvent> emittedEvents;
    private List<Pot> settledPots = List.of();
    private List<PotAward> awards = List.of();
    private GamePhase phase = GamePhase.DEALING;
    private BettingRound bettingRound;
    private int smallBlindSeat;
    private int bigBlindSeat;
    private long turnCounter;
    private long currentTurnId;
    private boolean started;
    private boolean actionHistoryComplete = true;
    private List<CompletedHandPlayer> completedPlayers = List.of();

    public Hand(long id, GameConfig config, List<Player> participants, int buttonSeat, long seed) {
        this(id, config, participants, buttonSeat, new Random(seed));
    }

    Hand(long id, GameConfig config, List<Player> participants, int buttonSeat, Random shuffleRandom) {
        if (id <= 0) throw new IllegalArgumentException("hand id must be positive");
        if (config == null) throw new IllegalArgumentException("game config is required");
        if (participants == null) throw new IllegalArgumentException("participants are required");
        if (participants.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("participants cannot contain null");
        }
        long eligible = participants.stream()
                .filter(p -> p.stack() > 0 && !p.isDisconnected() && !p.isSpectator() && !p.isBusted())
                .count();
        if (eligible != participants.size()) {
            throw new IllegalArgumentException("hand participants cannot include spectators, busted or disconnected players");
        }
        if (eligible < 2) throw new IllegalArgumentException("at least two eligible participants required");
        if (eligible > config.maxPlayers()) throw new IllegalArgumentException("too many participants");
        if (participants.stream().map(Player::seat).distinct().count() != participants.size()) {
            throw new IllegalArgumentException("duplicate seat");
        }
        if (participants.stream().anyMatch(player -> player.seat() >= config.maxPlayers())) {
            throw new IllegalArgumentException("participant seat is outside the table");
        }
        if (participants.stream().map(Player::id).distinct().count() != participants.size()) {
            throw new IllegalArgumentException("duplicate player id");
        }
        if (participants.stream().noneMatch(p -> p.seat() == buttonSeat && p.stack() > 0)) {
            throw new IllegalArgumentException("button must belong to a participant");
        }
        this.id = id;
        this.config = config;
        this.participants = new ArrayList<>(participants);
        this.participants.sort(Comparator.comparingInt(Player::seat));
        this.playersById = new LinkedHashMap<>();
        this.participants.forEach(p -> this.playersById.put(p.id(), p));
        this.buttonSeat = buttonSeat;
        this.deck = new Deck(shuffleRandom);
    }

    List<GameEvent> start() {
        return collectEvents(this::startInternal);
    }

    private void startInternal() {
        if (started) throw new IllegalStateException("hand already started");
        started = true;
        for (Player player : participants) {
            player.beginHand();
        }

        emit(GameEventType.HAND_STARTED, null, Map.of(
                "buttonSeat", buttonSeat,
                "participantCount", participants.size()
        ));
        determineBlindSeats();
        dealHoleCards();
        postBlinds();
        transitionTo(GamePhase.PREFLOP);

        int firstActor = participants.size() == 2 ? buttonSeat : nextParticipantSeat(bigBlindSeat);
        // 即使大盲筹码不足而短码 All-in，翻牌前的 bring-in 仍是完整大盲。
        openBettingRound(firstActor, config.bigBlind());
        advanceAutomaticallyIfNeeded();
        assertActorInvariant();
    }

    List<GameEvent> handle(PlayerAction action) {
        return collectEvents(() -> handleInternal(action));
    }

    List<GameEvent> handle(PlayerAction action, long expectedTurnId) {
        if (expectedTurnId != currentTurnId) {
            throw new IllegalActionException(ActionErrorCode.STALE_TURN, "action belongs to an expired turn");
        }
        return handle(action);
    }

    private void handleInternal(PlayerAction action) {
        if (!started) throw new IllegalStateException("hand has not started");
        if (!phase.isBettingPhase() || bettingRound == null) {
            throw new IllegalActionException(ActionErrorCode.INVALID_PHASE, "actions are not allowed in " + phase);
        }
        long actedTurnId = currentTurnId;
        BettingActionResult result = bettingRound.act(action);
        Player player = requirePlayer(action.playerId());
        recordAcceptedAction(actedTurnId, action, player, result);
        emit(GameEventType.PLAYER_ACTION, player.id(), Map.of(
                "turnId", actedTurnId,
                "action", action.type().name(),
                "paid", result.paid(),
                "stack", player.stack(),
                "streetBet", player.streetBet(),
                "currentBet", result.currentBet(),
                "fullRaise", result.fullRaise(),
                "status", player.status().name(),
                "canAct", player.canAct()
        ));

        if (result.roundComplete()) {
            currentTurnId = 0;
            advanceAutomaticallyIfNeeded();
        } else {
            emitTurnChanged();
        }
        assertActorInvariant();
    }

    /**
     * 掉线不会把玩家伪装成观察者。ACTIVE 玩家按当前手弃牌；ALL_IN 玩家仍保留摊牌资格。
     */
    List<GameEvent> disconnect(String playerId) {
        return collectEvents(() -> disconnectInternal(playerId));
    }

    private void disconnectInternal(String playerId) {
        Player player = requirePlayer(playerId);
        if (player.isDisconnected()) return;
        Integer previousActor = currentActorSeat();
        if (player.status() == PlayerStatus.ACTIVE) {
            player.disconnectAndForfeitHand();
        } else {
            player.disconnect();
        }
        emit(GameEventType.PLAYER_DISCONNECTED, player.id(), Map.of(
                "seat", player.seat(),
                "forfeitedHand", player.isFolded()
        ));

        if (phase.isBettingPhase() && bettingRound != null) {
            boolean complete = bettingRound.reconcileAfterEligibilityChange(player.seat());
            if (complete) {
                currentTurnId = 0;
                advanceAutomaticallyIfNeeded();
            } else if (!java.util.Objects.equals(previousActor, currentActorSeat())) {
                emitTurnChanged();
            }
        }
        assertActorInvariant();
    }

    List<GameEvent> reconnect(String playerId) {
        return collectEvents(() -> reconnectInternal(playerId));
    }

    private void reconnectInternal(String playerId) {
        Player player = requirePlayer(playerId);
        if (!player.isDisconnected()) return;
        player.reconnect();
        emit(GameEventType.PLAYER_RECONNECTED, player.id(), Map.of(
                "seat", player.seat(),
                "status", player.status().name()
        ));
        // 当前手已因掉线弃牌的玩家只能观看到本手结束，不会重新进入行动队列。
        assertActorInvariant();
    }

    private void determineBlindSeats() {
        if (participants.size() == 2) {
            smallBlindSeat = buttonSeat;
            bigBlindSeat = nextParticipantSeat(buttonSeat);
        } else {
            smallBlindSeat = nextParticipantSeat(buttonSeat);
            bigBlindSeat = nextParticipantSeat(smallBlindSeat);
        }
    }

    private void dealHoleCards() {
        List<Player> dealOrder = participantsClockwiseFrom(smallBlindSeat);
        for (int round = 0; round < 2; round++) {
            for (Player player : dealOrder) player.deal(deck.draw());
        }
        emit(GameEventType.HOLE_CARDS_DEALT, null, Map.of("cardCount", 2));
    }

    private void postBlinds() {
        Player smallBlind = playerAt(smallBlindSeat);
        Player bigBlind = playerAt(bigBlindSeat);
        int smallPaid = smallBlind.contribute(config.smallBlind());
        int bigPaid = bigBlind.contribute(config.bigBlind());
        emit(GameEventType.BLINDS_POSTED, null, Map.of(
                "smallBlindSeat", smallBlindSeat,
                "smallBlindPaid", smallPaid,
                "bigBlindSeat", bigBlindSeat,
                "bigBlindPaid", bigPaid
        ));
    }

    private void advanceAutomaticallyIfNeeded() {
        while (phase.isBettingPhase() && bettingRound != null && bettingRound.isComplete()) {
            if (contenders().size() <= 1) {
                settleWithoutShowdown();
                return;
            }
            switch (phase) {
                case PREFLOP -> openCommunityStreet(GamePhase.FLOP, 3);
                case FLOP -> openCommunityStreet(GamePhase.TURN, 1);
                case TURN -> openCommunityStreet(GamePhase.RIVER, 1);
                case RIVER -> {
                    showdownAndSettle();
                    return;
                }
                default -> throw new IllegalStateException("unexpected betting phase " + phase);
            }
        }
        if (phase.isBettingPhase() && bettingRound != null && !bettingRound.isComplete()) {
            emitTurnChanged();
        }
    }

    private void openCommunityStreet(GamePhase nextPhase, int cardCount) {
        for (Player player : participants) player.beginStreet();
        deck.draw(); // burn card
        for (int i = 0; i < cardCount; i++) communityCards.add(deck.draw());
        transitionTo(nextPhase);
        emit(GameEventType.COMMUNITY_CARD_UPDATED, null, Map.of(
                "phase", nextPhase.name(),
                "communityCards", List.copyOf(communityCards)
        ));
        openBettingRound(nextParticipantSeat(buttonSeat), 0);
    }

    private void openBettingRound(int firstActorSeat, int currentBet) {
        bettingRound = new BettingRound(participants, firstActorSeat, currentBet, config.bigBlind(), config.maxPlayers());
        if (bettingRound.actorSeat() == null) currentTurnId = 0;
    }

    private void settleWithoutShowdown() {
        List<Player> remaining = contenders();
        if (remaining.size() != 1) throw new IllegalStateException("uncontested pot requires one player");
        transitionTo(GamePhase.SETTLEMENT);
        Player winner = remaining.getFirst();
        int amount = potAmount();
        settledPots = List.of(new Pot(amount, List.of(winner.id())));
        winner.addWinnings(amount);
        awards = List.of(new PotAward(amount, Map.of(winner.id(), amount)));
        emit(GameEventType.SETTLEMENT, winner.id(), Map.of(
                "showdown", false,
                "awards", awards
        ));
        finishHand();
    }

    private void showdownAndSettle() {
        transitionTo(GamePhase.SHOWDOWN);
        Card[] board = communityCards.toArray(Card[]::new);
        for (Player player : contenders()) {
            HandResult result = HandEvaluator.bestHand(player.holeCards().toArray(Card[]::new), board);
            showdownHandKeys.put(player.id(), result.key());
            showdownCategories.put(player.id(), result.category().name());
            showdownPlayerIds.add(player.id());
        }
        emit(GameEventType.SHOWDOWN, null, Map.of(
                "handKeys", Map.copyOf(showdownHandKeys),
                "categories", Map.copyOf(showdownCategories)
        ));

        transitionTo(GamePhase.SETTLEMENT);
        settledPots = PotManager.buildPots(participants);
        awards = PotManager.settle(
                settledPots, participants, showdownHandKeys, buttonSeat, config.maxPlayers());
        emit(GameEventType.SETTLEMENT, null, Map.of(
                "showdown", true,
                "awards", awards
        ));
        finishHand();
    }

    private void finishHand() {
        completedPlayers = participants.stream()
                .map(this::completedPlayer)
                .toList();
        for (Player player : participants) {
            boolean busted = player.stack() == 0;
            player.finishHand();
            if (busted) {
                emit(GameEventType.PLAYER_BUSTED, player.id(), Map.of("seat", player.seat()));
            }
        }
        bettingRound = null;
        currentTurnId = 0;
        transitionTo(GamePhase.ROUND_END);
        emit(GameEventType.HAND_ENDED, null, Map.of(
                "pot", potAmount(),
                "awards", awards
        ));
    }

    private void recordAcceptedAction(
            long turnId,
            PlayerAction action,
            Player player,
            BettingActionResult result
    ) {
        if (acceptedActions.size() >= MAX_ARCHIVED_ACTIONS) {
            actionHistoryComplete = false;
            return;
        }
        acceptedActions.add(new CompletedHandAction(
                acceptedActions.size() + 1,
                turnId,
                player.id(),
                phase,
                action.type(),
                result.paid(),
                player.stack(),
                player.streetBet(),
                result.currentBet(),
                result.fullRaise()
        ));
    }

    private CompletedHandPlayer completedPlayer(Player player) {
        int winnings = awards.stream()
                .map(PotAward::winnings)
                .mapToInt(winners -> winners.getOrDefault(player.id(), 0))
                .sum();
        int startingStack = Math.toIntExact(
                (long) player.stack() + player.totalContribution() - winnings
        );
        boolean showdown = showdownPlayerIds.contains(player.id());
        return new CompletedHandPlayer(
                player.id(),
                player.name(),
                player.seat(),
                startingStack,
                player.stack(),
                player.totalContribution(),
                winnings,
                player.isFolded(),
                player.isDisconnected(),
                player.stack() == 0,
                showdown,
                showdown ? showdownHandKeys.get(player.id()) : null,
                showdown ? showdownCategories.get(player.id()) : null,
                player.holeCards()
        );
    }

    private void transitionTo(GamePhase next) {
        GamePhase previous = phase;
        phase = next;
        emit(GameEventType.PHASE_CHANGED, null, Map.of(
                "from", previous.name(),
                "to", next.name()
        ));
    }

    private void emitTurnChanged() {
        Integer actor = currentActorSeat();
        if (actor == null) return;
        Player player = playerAt(actor);
        if (player == null || !player.canAct()) {
            throw new IllegalStateException("TURN_CHANGED cannot target a player who cannot act");
        }
        currentTurnId = ++turnCounter;
        emit(GameEventType.TURN_CHANGED, player.id(), Map.of(
                "seat", actor,
                "turnId", currentTurnId,
                "currentBet", bettingRound.currentBet(),
                "minimumRaise", bettingRound.minRaise()
        ));
    }

    private void emit(GameEventType type, String playerId, Map<String, Object> data) {
        if (emittedEvents == null) throw new IllegalStateException("events can only be emitted while handling a command");
        emittedEvents.add(GameEvent.of(type, id, playerId, data));
    }

    private List<GameEvent> collectEvents(Runnable operation) {
        if (emittedEvents != null) throw new IllegalStateException("nested command execution is not supported");
        emittedEvents = new ArrayList<>();
        try {
            operation.run();
            return List.copyOf(emittedEvents);
        } finally {
            emittedEvents = null;
        }
    }

    private int nextParticipantSeat(int afterSeat) {
        for (int offset = 1; offset <= config.maxPlayers(); offset++) {
            int seat = Math.floorMod(afterSeat + offset, config.maxPlayers());
            if (playerAt(seat) != null) return seat;
        }
        throw new IllegalStateException("no next participant");
    }

    private List<Player> participantsClockwiseFrom(int startSeat) {
        List<Player> ordered = new ArrayList<>(participants.size());
        int seat = startSeat;
        do {
            Player player = playerAt(seat);
            if (player != null) ordered.add(player);
            seat = Math.floorMod(seat + 1, config.maxPlayers());
        } while (seat != startSeat);
        return ordered;
    }

    private List<Player> contenders() {
        return participants.stream().filter(Player::isInHand).toList();
    }

    private Player requirePlayer(String playerId) {
        Player player = playersById.get(playerId);
        if (player == null) throw new IllegalActionException(ActionErrorCode.UNKNOWN_PLAYER, "unknown player");
        return player;
    }

    private Player playerAt(int seat) {
        return participants.stream().filter(p -> p.seat() == seat).findFirst().orElse(null);
    }

    private void assertActorInvariant() {
        Integer actor = currentActorSeat();
        if (actor == null) {
            if (phase.isBettingPhase() && bettingRound != null && !bettingRound.isComplete()) {
                throw new IllegalStateException("an incomplete betting phase must have an actor");
            }
            if (currentTurnId != 0) throw new IllegalStateException("a hand without an actor cannot expose a turn id");
            return;
        }
        Player player = playerAt(actor);
        if (player == null || !player.canAct()) {
            throw new IllegalStateException("current actor must be able to act");
        }
        if (currentTurnId <= 0) throw new IllegalStateException("a current actor must have a turn id");
    }

    public long id() { return id; }
    public GamePhase phase() { return phase; }
    public int buttonSeat() { return buttonSeat; }
    public int smallBlindSeat() { return smallBlindSeat; }
    public int bigBlindSeat() { return bigBlindSeat; }
    public Integer currentActorSeat() { return bettingRound == null ? null : bettingRound.actorSeat(); }
    public String currentActorPlayerId() {
        Integer seat = currentActorSeat();
        Player player = seat == null ? null : playerAt(seat);
        return player == null ? null : player.id();
    }
    public long currentTurnId() { return currentTurnId; }
    public int currentBet() { return bettingRound == null ? 0 : bettingRound.currentBet(); }
    public int minimumRaise() { return bettingRound == null ? config.bigBlind() : bettingRound.minRaise(); }
    public int potAmount() {
        int total = 0;
        for (Player player : participants) total = Math.addExact(total, player.totalContribution());
        return total;
    }
    public List<Pot> currentPots() {
        return settledPots.isEmpty() ? PotManager.buildPots(participants) : settledPots;
    }
    public List<Card> communityCards() { return List.copyOf(communityCards); }
    public List<Player> participants() { return List.copyOf(participants); }
    public List<PotAward> awards() { return awards; }

    CompletedHandSnapshot completedSnapshot(String sessionId) {
        if (completedPlayers.isEmpty() || phase != GamePhase.ROUND_END) {
            throw new IllegalStateException("hand has not completed");
        }
        return new CompletedHandSnapshot(
                sessionId,
                id,
                config,
                buttonSeat,
                smallBlindSeat,
                bigBlindSeat,
                potAmount(),
                communityCards,
                settledPots,
                awards,
                completedPlayers,
                acceptedActions,
                actionHistoryComplete
        );
    }

    public Set<ActionType> legalActions(String playerId) {
        return actionOptions(playerId).legalActions();
    }

    public ActionOptions actionOptions(String playerId) {
        if (!phase.isBettingPhase() || bettingRound == null || !playersById.containsKey(playerId)) {
            return ActionOptions.none();
        }
        return bettingRound.actionOptions(playerId);
    }

    public List<Card> visibleHoleCards(String viewerId, String targetPlayerId) {
        Player target = playersById.get(targetPlayerId);
        if (target == null) return List.of();
        if (targetPlayerId.equals(viewerId) || showdownPlayerIds.contains(targetPlayerId)) {
            return target.holeCards();
        }
        return List.of();
    }

    public boolean containsPlayer(String playerId) {
        return playersById.containsKey(playerId);
    }
}
