package com.xidao.poker.engine.arena;

public record VehicleInput(
        long sequence,
        boolean forward,
        boolean backward,
        boolean turnLeft,
        boolean turnRight,
        boolean fire
) {
    public static VehicleInput idle(long sequence) {
        return new VehicleInput(sequence, false, false, false, false, false);
    }
}
