package com.xidao.poker.engine.arena;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.security.SecureRandom;

/** Pure Java, server-authoritative vehicle arena. No Spring or WebSocket dependencies. */
public final class ArenaSession {
    /** Short server-side transition window reserved for elimination visuals; no victory overlay is shown. */
    private static final long ROUND_TRANSITION_MILLIS = 3_000;
    private static final double MAX_PHYSICS_STEP = 4.0;
    private static final long SKILL_SPAWN_INTERVAL_MILLIS = 20_000;
    private static final long SKILL_PICKUP_LIFETIME_MILLIS = 32_000;
    private static final long OVERDRIVE_DURATION_MILLIS = 8_000;
    private static final long RAPID_FIRE_DURATION_MILLIS = 9_000;
    private static final long AEGIS_DURATION_MILLIS = 15_000;
    private static final double SKILL_RADIUS = 15;
    private static final int MAX_SKILLS_ON_MAP = 4;

    private final String roomId;
    private final String roomName;
    private final ArenaConfig config;
    private ArenaMap map;
    private final boolean regenerateMapEachRound;
    private final Random mapRandom;
    private final Map<String, PlayerState> players = new LinkedHashMap<>();
    private final List<ProjectileState> projectiles = new ArrayList<>();
    private final List<SkillState> skills = new ArrayList<>();
    private final List<ArenaEliminationSnapshot> eliminations = new ArrayList<>();
    private final Random skillRandom;
    private ArenaPhase phase = ArenaPhase.WAITING;
    private String ownerId;
    private String winnerId;
    private long roundId;
    private long tick;
    private long nextProjectileId = 1;
    private long nextSkillId = 1;
    private long nextEliminationId = 1;
    private long nextSkillSpawnAt;
    private long lastTickAt;
    private long roundEndsAt;
    private int roundParticipantCount;

    public ArenaSession(String roomId, String roomName, ArenaConfig config, ArenaMap map) {
        this(roomId, roomName, config, map, 31L * roomId.hashCode() + roomName.hashCode(), false);
    }

    public ArenaSession(String roomId, String roomName, ArenaConfig config, ArenaMap map, long simulationSeed) {
        this(roomId, roomName, config, map, simulationSeed, false);
    }

    private ArenaSession(String roomId, String roomName, ArenaConfig config, ArenaMap map,
                         long simulationSeed, boolean regenerateMapEachRound) {
        if (roomId == null || roomId.isBlank()) throw new IllegalArgumentException("room id is required");
        if (roomName == null || roomName.isBlank()) throw new IllegalArgumentException("room name is required");
        this.roomId = roomId;
        this.roomName = roomName;
        this.config = config;
        this.map = map;
        this.skillRandom = new Random(simulationSeed);
        this.mapRandom = new Random(simulationSeed ^ 0x9E3779B97F4A7C15L);
        this.regenerateMapEachRound = regenerateMapEachRound;
        if (map.spawns().size() < config.maxPlayers()) {
            throw new IllegalArgumentException("arena map does not have enough spawns");
        }
    }

    public ArenaSession(String roomId, String roomName, int maxPlayers) {
        this(roomId, roomName, maxPlayers, new SecureRandom().nextLong());
    }

    public ArenaSession(String roomId, String roomName, int maxPlayers, long seed) {
        this(roomId, roomName, ArenaConfig.standard(maxPlayers),
                ArenaMap.generated(ArenaConfig.standard(maxPlayers), seed), seed ^ 0x5DEECE66DL, true);
    }

    public void addPlayer(String playerId, String name, String avatarKey) {
        requireId(playerId);
        PlayerState existing = players.get(playerId);
        if (existing != null) {
            existing.connected = true;
            existing.name = cleanName(name);
            existing.avatarKey = cleanAvatar(avatarKey);
            return;
        }
        if (players.size() >= config.maxPlayers()) throw new IllegalStateException("arena room is full");
        int seat = firstAvailableSeat();
        ArenaSpawn spawn = map.spawns().get(seat);
        ArenaLifeState life = phase == ArenaPhase.RUNNING ? ArenaLifeState.SPECTATING : ArenaLifeState.WAITING;
        PlayerState player = new PlayerState(playerId, cleanName(name), cleanAvatar(avatarKey), seat,
                spawn.x(), spawn.y(), spawn.angle(), life);
        players.put(playerId, player);
        if (ownerId == null) ownerId = playerId;
    }

    public void reconnect(String playerId, String name, String avatarKey) {
        PlayerState player = requirePlayer(playerId);
        player.connected = true;
        player.name = cleanName(name);
        player.avatarKey = cleanAvatar(avatarKey);
        if (ownerId == null) ownerId = playerId;
    }

    public void disconnect(String playerId) {
        PlayerState player = requirePlayer(playerId);
        player.connected = false;
        player.input = VehicleInput.idle(player.input.sequence());
        transferOwnerIfNecessary();
    }

    public void removePlayer(String playerId, long nowMillis) {
        PlayerState removed = players.remove(playerId);
        if (removed == null) return;
        projectiles.removeIf(projectile -> projectile.ownerId.equals(playerId));
        if (playerId.equals(ownerId)) ownerId = null;
        transferOwnerIfNecessary();
        checkRoundEnd(nowMillis);
    }

    public void setReady(String playerId, boolean ready) {
        if (phase != ArenaPhase.WAITING) throw new IllegalStateException("arena is not waiting");
        PlayerState player = requirePlayer(playerId);
        if (!player.connected) throw new IllegalStateException("disconnected player cannot ready");
        player.ready = ready;
    }

    public void startRound(String playerId, long nowMillis) {
        if (!playerId.equals(ownerId)) throw new IllegalStateException("only the owner can start the round");
        if (phase != ArenaPhase.WAITING) throw new IllegalStateException("arena is not waiting");
        List<PlayerState> participants = players.values().stream().filter(player -> player.connected).toList();
        if (participants.size() < 2) throw new IllegalStateException("at least two connected players are required");
        if (participants.stream().anyMatch(player -> !player.ready)) {
            throw new IllegalStateException("all connected players must be ready");
        }
        beginRound(participants, nowMillis);
    }

    private void beginRound(List<PlayerState> participants, long nowMillis) {
        roundId++;
        if (regenerateMapEachRound && roundId > 1) {
            map = ArenaMap.generated(config, mapRandom.nextLong());
        }
        phase = ArenaPhase.RUNNING;
        winnerId = null;
        roundEndsAt = 0;
        roundParticipantCount = participants.size();
        projectiles.clear();
        skills.clear();
        eliminations.clear();
        nextSkillSpawnAt = nowMillis + SKILL_SPAWN_INTERVAL_MILLIS;
        lastTickAt = nowMillis;
        for (PlayerState player : players.values()) {
            player.input = VehicleInput.idle(player.input.sequence());
            player.ready = false;
            if (!player.connected) {
                player.lifeState = ArenaLifeState.SPECTATING;
                continue;
            }
            ArenaSpawn spawn = map.spawns().get(player.seat);
            player.x = spawn.x();
            player.y = spawn.y();
            player.angle = spawn.angle();
            player.lifeState = ArenaLifeState.ALIVE;
            player.lastShotAt = nowMillis - config.shotCooldownMillis();
            player.speedBoostUntil = 0;
            player.rapidFireUntil = 0;
            player.shieldUntil = 0;
        }
    }

    public void acceptInput(String playerId, VehicleInput input) {
        if (phase != ArenaPhase.RUNNING) return;
        PlayerState player = requirePlayer(playerId);
        if (!player.connected || player.lifeState != ArenaLifeState.ALIVE) return;
        if (input.sequence() <= player.input.sequence()) return;
        player.input = input;
    }

    public void tick(long nowMillis) {
        tick++;
        if (phase == ArenaPhase.ROUND_OVER) {
            if (nowMillis >= roundEndsAt) continueOrWait(nowMillis);
            return;
        }
        if (phase != ArenaPhase.RUNNING) return;
        double dt = lastTickAt == 0 ? 1.0 / 30.0 : Math.min(0.1, Math.max(0.001, (nowMillis - lastTickAt) / 1000.0));
        lastTickAt = nowMillis;
        updateSkills(nowMillis);
        expireProjectiles(nowMillis);
        for (PlayerState player : players.values()) updatePlayer(player, dt, nowMillis);
        updateProjectiles(dt, nowMillis);
        checkRoundEnd(nowMillis);
    }

    public ArenaSnapshot snapshot() {
        List<ArenaPlayerSnapshot> playerViews = players.values().stream()
                .sorted(Comparator.comparingInt(player -> player.seat))
                .map(player -> new ArenaPlayerSnapshot(player.id, player.name, player.avatarKey, player.seat,
                        player.connected, player.ready, player.lifeState, player.x, player.y, player.angle,
                        player.kills, player.wins, player.speedBoostUntil > lastTickAt,
                        player.rapidFireUntil > lastTickAt, player.shieldUntil > lastTickAt))
                .toList();
        List<ArenaProjectileSnapshot> projectileViews = projectiles.stream()
                .map(projectile -> new ArenaProjectileSnapshot(projectile.id, projectile.ownerId,
                        projectile.x, projectile.y, projectile.velocityX, projectile.velocityY, projectile.bounces))
                .toList();
        List<ArenaSkillSnapshot> skillViews = skills.stream()
                .map(skill -> new ArenaSkillSnapshot(skill.id, skill.type, skill.x, skill.y, skill.expiresAt))
                .toList();
        long skillSpawnInMillis = phase == ArenaPhase.RUNNING
                ? Math.max(0, nextSkillSpawnAt - lastTickAt) : 0;
        return new ArenaSnapshot(roomId, roomName, ownerId, phase, roundId, tick,
                config.width(), config.height(), config.maxPlayers(), config.maximumProjectilesPerPlayer(),
                map.walls(), playerViews, projectileViews,
                skillViews, eliminations, skillSpawnInMillis, winnerId, roundEndsAt);
    }

    public int maxPlayers() { return config.maxPlayers(); }
    public int memberCount() { return players.size(); }
    public int connectedCount() { return (int) players.values().stream().filter(player -> player.connected).count(); }
    public ArenaPhase phase() { return phase; }
    public long roundId() { return roundId; }

    private void updatePlayer(PlayerState player, double dt, long nowMillis) {
        if (!player.connected || player.lifeState != ArenaLifeState.ALIVE) return;
        int turnDirection = (player.input.turnRight() ? 1 : 0) - (player.input.turnLeft() ? 1 : 0);
        player.angle = normalizeAngle(player.angle + turnDirection * config.turnSpeed() * dt);
        double speed = player.input.forward() == player.input.backward() ? 0
                : player.input.forward() ? config.forwardSpeed() : -config.reverseSpeed();
        if (player.speedBoostUntil > nowMillis) speed *= 1.55;
        if (speed != 0) movePlayer(player, Math.cos(player.angle) * speed * dt, Math.sin(player.angle) * speed * dt);
        collectSkills(player, nowMillis);
        long cooldown = player.rapidFireUntil > nowMillis ? 155 : config.shotCooldownMillis();
        if (player.input.fire() && nowMillis - player.lastShotAt >= cooldown
                && projectileCount(player.id) < config.maximumProjectilesPerPlayer()) {
            fire(player, nowMillis);
        }
    }

    private void updateSkills(long nowMillis) {
        skills.removeIf(skill -> nowMillis >= skill.expiresAt);
        while (nowMillis >= nextSkillSpawnAt) {
            if (skills.size() < MAX_SKILLS_ON_MAP) spawnSkill(nowMillis);
            nextSkillSpawnAt += SKILL_SPAWN_INTERVAL_MILLIS;
        }
    }

    private void spawnSkill(long nowMillis) {
        double margin = SKILL_RADIUS + 12;
        for (int attempt = 0; attempt < 160; attempt++) {
            double x = margin + skillRandom.nextDouble() * (config.width() - margin * 2);
            double y = margin + skillRandom.nextDouble() * (config.height() - margin * 2);
            if (collides(x, y, SKILL_RADIUS + 5)) continue;
            if (players.values().stream().anyMatch(player -> squaredDistance(x, y, player.x, player.y)
                    < Math.pow(SKILL_RADIUS + config.vehicleRadius() + 35, 2))) continue;
            if (skills.stream().anyMatch(skill -> squaredDistance(x, y, skill.x, skill.y)
                    < Math.pow(SKILL_RADIUS * 3, 2))) continue;
            ArenaSkillType[] types = ArenaSkillType.values();
            skills.add(new SkillState(nextSkillId++, types[skillRandom.nextInt(types.length)],
                    x, y, nowMillis + SKILL_PICKUP_LIFETIME_MILLIS));
            return;
        }
    }

    private void collectSkills(PlayerState player, long nowMillis) {
        Iterator<SkillState> iterator = skills.iterator();
        double pickupDistanceSquared = Math.pow(config.vehicleRadius() + SKILL_RADIUS, 2);
        while (iterator.hasNext()) {
            SkillState skill = iterator.next();
            if (squaredDistance(player.x, player.y, skill.x, skill.y) > pickupDistanceSquared) continue;
            switch (skill.type) {
                case OVERDRIVE -> player.speedBoostUntil = nowMillis + OVERDRIVE_DURATION_MILLIS;
                case RAPID_FIRE -> player.rapidFireUntil = nowMillis + RAPID_FIRE_DURATION_MILLIS;
                case AEGIS -> player.shieldUntil = nowMillis + AEGIS_DURATION_MILLIS;
            }
            iterator.remove();
        }
    }

    private void movePlayer(PlayerState player, double deltaX, double deltaY) {
        int steps = Math.max(1, (int) Math.ceil(Math.max(Math.abs(deltaX), Math.abs(deltaY)) / MAX_PHYSICS_STEP));
        double stepX = deltaX / steps;
        double stepY = deltaY / steps;
        for (int step = 0; step < steps; step++) {
            double nextX = player.x + stepX;
            if (!collides(nextX, player.y, config.vehicleRadius())
                    && !collidesWithPlayer(player.id, nextX, player.y, config.vehicleRadius())) {
                player.x = nextX;
            }
            double nextY = player.y + stepY;
            if (!collides(player.x, nextY, config.vehicleRadius())
                    && !collidesWithPlayer(player.id, player.x, nextY, config.vehicleRadius())) {
                player.y = nextY;
            }
        }
    }

    private void fire(PlayerState player, long nowMillis) {
        double offset = config.vehicleRadius() + config.projectileRadius() + 4;
        double velocityX = Math.cos(player.angle) * config.projectileSpeed();
        double velocityY = Math.sin(player.angle) * config.projectileSpeed();
        double[] spawn = safeProjectileSpawn(player, offset);
        projectiles.add(new ProjectileState(nextProjectileId++, player.id,
                spawn[0], spawn[1],
                velocityX, velocityY, nowMillis));
        player.lastShotAt = nowMillis;
    }

    private double[] safeProjectileSpawn(PlayerState player, double desiredOffset) {
        double directionX = Math.cos(player.angle);
        double directionY = Math.sin(player.angle);
        for (double offset = desiredOffset; offset >= 0; offset -= 1.0) {
            double x = player.x + directionX * offset;
            double y = player.y + directionY * offset;
            if (!collides(x, y, config.projectileRadius())) return new double[]{x, y};
        }
        return new double[]{player.x, player.y};
    }

    private void expireProjectiles(long nowMillis) {
        projectiles.removeIf(projectile -> nowMillis - projectile.createdAt >= config.projectileLifetimeMillis());
    }

    private void updateProjectiles(double dt, long nowMillis) {
        Iterator<ProjectileState> iterator = projectiles.iterator();
        while (iterator.hasNext()) {
            ProjectileState projectile = iterator.next();
            double maximumDelta = Math.max(Math.abs(projectile.velocityX * dt), Math.abs(projectile.velocityY * dt));
            int steps = Math.max(1, (int) Math.ceil(maximumDelta / MAX_PHYSICS_STEP));
            double stepSeconds = dt / steps;
            boolean removed = false;
            for (int step = 0; step < steps && !removed; step++) {
                removed = advanceProjectile(projectile, stepSeconds, nowMillis);
            }
            if (removed) iterator.remove();
        }
    }

    private boolean advanceProjectile(ProjectileState projectile, double stepSeconds, long nowMillis) {
        double radius = config.projectileRadius();
        double previousX = projectile.x;
        double previousY = projectile.y;
        double nextX = previousX + projectile.velocityX * stepSeconds;
        boolean reflected = false;
        if (collides(nextX, previousY, radius)) {
            projectile.velocityX = -projectile.velocityX;
            reflected = true;
        } else {
            projectile.x = nextX;
        }

        double nextY = previousY + projectile.velocityY * stepSeconds;
        if (collides(projectile.x, nextY, radius)) {
            projectile.velocityY = -projectile.velocityY;
            reflected = true;
        } else {
            projectile.y = nextY;
        }

        if (collides(projectile.x, projectile.y, radius)) {
            projectile.x = previousX;
            projectile.y = previousY;
            projectile.velocityX = -projectile.velocityX;
            projectile.velocityY = -projectile.velocityY;
            reflected = true;
        }
        if (reflected) {
            projectile.bounces++;
            projectile.ownerArmed = true;
        }

        PlayerState owner = players.get(projectile.ownerId);
        double hitRadius = config.vehicleRadius() + radius;
        if (!projectile.ownerArmed && owner != null
                && squaredDistance(owner.x, owner.y, projectile.x, projectile.y) > Math.pow(hitRadius + 1, 2)) {
            projectile.ownerArmed = true;
        }

        for (PlayerState player : players.values()) {
            if (player.lifeState != ArenaLifeState.ALIVE) continue;
            if (player.id.equals(projectile.ownerId) && !projectile.ownerArmed) continue;
            double dx = player.x - projectile.x;
            double dy = player.y - projectile.y;
            if (dx * dx + dy * dy > hitRadius * hitRadius) continue;
            if (player.shieldUntil > nowMillis) {
                player.shieldUntil = 0;
                return true;
            }
            player.lifeState = ArenaLifeState.ELIMINATED;
            player.input = VehicleInput.idle(player.input.sequence());
            if (owner != null && owner != player) owner.kills++;
            eliminations.add(new ArenaEliminationSnapshot(nextEliminationId++, roundId,
                    player.id, projectile.ownerId, nowMillis));
            return true;
        }
        return false;
    }

    private void checkRoundEnd(long nowMillis) {
        if (phase != ArenaPhase.RUNNING) return;
        List<PlayerState> alive = players.values().stream()
                .filter(player -> player.lifeState == ArenaLifeState.ALIVE)
                .toList();
        if (roundParticipantCount < 2 || alive.size() > 1) return;
        winnerId = alive.size() == 1 ? alive.getFirst().id : null;
        if (winnerId != null) players.get(winnerId).wins++;
        phase = ArenaPhase.ROUND_OVER;
        roundEndsAt = nowMillis + ROUND_TRANSITION_MILLIS;
        projectiles.clear();
        skills.clear();
        players.values().forEach(player -> player.input = VehicleInput.idle(player.input.sequence()));
    }

    private void continueOrWait(long nowMillis) {
        List<PlayerState> participants = players.values().stream()
                .filter(player -> player.connected)
                .toList();
        if (participants.size() >= 2) {
            beginRound(participants, nowMillis);
        } else {
            resetToWaiting();
        }
    }

    private void resetToWaiting() {
        phase = ArenaPhase.WAITING;
        winnerId = null;
        roundEndsAt = 0;
        roundParticipantCount = 0;
        projectiles.clear();
        skills.clear();
        eliminations.clear();
        nextSkillSpawnAt = 0;
        for (PlayerState player : players.values()) {
            player.ready = false;
            player.lifeState = player.connected ? ArenaLifeState.WAITING : ArenaLifeState.SPECTATING;
            player.speedBoostUntil = 0;
            player.rapidFireUntil = 0;
            player.shieldUntil = 0;
        }
    }

    private boolean collides(double x, double y, double radius) {
        if (x - radius < 0 || y - radius < 0 || x + radius > config.width() || y + radius > config.height()) {
            return true;
        }
        return map.walls().stream().anyMatch(wall -> circleIntersectsRect(x, y, radius, wall));
    }

    private boolean collidesWithPlayer(String movingPlayerId, double x, double y, double radius) {
        double minimumDistance = radius + config.vehicleRadius();
        double minimumDistanceSquared = minimumDistance * minimumDistance;
        return players.values().stream()
                .filter(other -> !other.id.equals(movingPlayerId))
                .filter(other -> other.lifeState == ArenaLifeState.ALIVE)
                .anyMatch(other -> {
                    double dx = x - other.x;
                    double dy = y - other.y;
                    return dx * dx + dy * dy < minimumDistanceSquared;
                });
    }

    private static boolean circleIntersectsRect(double x, double y, double radius, ArenaWall wall) {
        double closestX = clamp(x, wall.x(), wall.x() + wall.width());
        double closestY = clamp(y, wall.y(), wall.y() + wall.height());
        double dx = x - closestX;
        double dy = y - closestY;
        return dx * dx + dy * dy <= radius * radius;
    }

    private long projectileCount(String playerId) {
        return projectiles.stream().filter(projectile -> projectile.ownerId.equals(playerId)).count();
    }

    private int firstAvailableSeat() {
        for (int seat = 0; seat < config.maxPlayers(); seat++) {
            int candidate = seat;
            if (players.values().stream().noneMatch(player -> player.seat == candidate)) return seat;
        }
        throw new IllegalStateException("arena room is full");
    }

    private void transferOwnerIfNecessary() {
        if (ownerId != null) {
            PlayerState owner = players.get(ownerId);
            if (owner != null && owner.connected) return;
        }
        ownerId = players.values().stream().filter(player -> player.connected)
                .min(Comparator.comparingInt(player -> player.seat))
                .map(player -> player.id).orElse(null);
    }

    private PlayerState requirePlayer(String playerId) {
        PlayerState player = players.get(playerId);
        if (player == null) throw new IllegalArgumentException("arena player was not found");
        return player;
    }

    private static void requireId(String value) {
        if (value == null || value.isBlank() || value.length() > 64
                || value.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("player id is invalid");
        }
    }

    private static String cleanName(String value) {
        if (value == null || value.isBlank() || value.strip().length() > 32) {
            throw new IllegalArgumentException("player name must be 1..32 characters");
        }
        return value.strip();
    }

    private static String cleanAvatar(String value) {
        if (value == null || value.isBlank()) return "default";
        String clean = value.strip().toLowerCase();
        return clean.matches("[a-z0-9_-]{1,32}") ? clean : "default";
    }

    private static double normalizeAngle(double value) {
        double normalized = value % (Math.PI * 2);
        return normalized < -Math.PI ? normalized + Math.PI * 2
                : normalized > Math.PI ? normalized - Math.PI * 2 : normalized;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static double squaredDistance(double x1, double y1, double x2, double y2) {
        double dx = x1 - x2;
        double dy = y1 - y2;
        return dx * dx + dy * dy;
    }

    private static final class PlayerState {
        private final String id;
        private String name;
        private String avatarKey;
        private final int seat;
        private boolean connected = true;
        private boolean ready;
        private ArenaLifeState lifeState;
        private double x;
        private double y;
        private double angle;
        private int kills;
        private int wins;
        private long lastShotAt;
        private long speedBoostUntil;
        private long rapidFireUntil;
        private long shieldUntil;
        private VehicleInput input = VehicleInput.idle(0);

        private PlayerState(String id, String name, String avatarKey, int seat,
                            double x, double y, double angle, ArenaLifeState lifeState) {
            this.id = id;
            this.name = name;
            this.avatarKey = avatarKey;
            this.seat = seat;
            this.x = x;
            this.y = y;
            this.angle = angle;
            this.lifeState = lifeState;
        }
    }

    private static final class SkillState {
        private final long id;
        private final ArenaSkillType type;
        private final double x;
        private final double y;
        private final long expiresAt;

        private SkillState(long id, ArenaSkillType type, double x, double y, long expiresAt) {
            this.id = id;
            this.type = type;
            this.x = x;
            this.y = y;
            this.expiresAt = expiresAt;
        }
    }

    private static final class ProjectileState {
        private final long id;
        private final String ownerId;
        private double x;
        private double y;
        private double velocityX;
        private double velocityY;
        private final long createdAt;
        private int bounces;
        private boolean ownerArmed;

        private ProjectileState(long id, String ownerId, double x, double y,
                                double velocityX, double velocityY, long createdAt) {
            this.id = id;
            this.ownerId = ownerId;
            this.x = x;
            this.y = y;
            this.velocityX = velocityX;
            this.velocityY = velocityY;
            this.createdAt = createdAt;
        }
    }
}
