package com.xidao.poker.engine.arena;

public record ArenaConfig(
        int maxPlayers,
        double width,
        double height,
        double vehicleRadius,
        double projectileRadius,
        double forwardSpeed,
        double reverseSpeed,
        double turnSpeed,
        double projectileSpeed,
        long shotCooldownMillis,
        long projectileLifetimeMillis,
        int maximumProjectilesPerPlayer
) {
    public ArenaConfig {
        if (maxPlayers < 2 || maxPlayers > 10) throw new IllegalArgumentException("max players must be 2..10");
        if (width < 400 || height < 300) throw new IllegalArgumentException("arena dimensions are too small");
        if (vehicleRadius <= 0 || projectileRadius <= 0) throw new IllegalArgumentException("radii must be positive");
        if (forwardSpeed <= 0 || reverseSpeed <= 0 || turnSpeed <= 0 || projectileSpeed <= 0) {
            throw new IllegalArgumentException("movement values must be positive");
        }
        if (shotCooldownMillis <= 0 || projectileLifetimeMillis <= 0 || maximumProjectilesPerPlayer <= 0) {
            throw new IllegalArgumentException("projectile limits are invalid");
        }
    }

    public static ArenaConfig standard(int maxPlayers) {
        return new ArenaConfig(maxPlayers, 1800, 1080, 19, 5,
                225, 145, 2.75, 560, 430, 5_000, 5);
    }
}
