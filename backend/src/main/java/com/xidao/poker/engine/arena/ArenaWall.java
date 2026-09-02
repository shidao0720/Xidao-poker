package com.xidao.poker.engine.arena;

public record ArenaWall(double x, double y, double width, double height) {
    public ArenaWall {
        if (x < 0 || y < 0 || width <= 0 || height <= 0) {
            throw new IllegalArgumentException("wall dimensions are invalid");
        }
    }
}
