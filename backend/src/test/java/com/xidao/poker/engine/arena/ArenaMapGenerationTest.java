package com.xidao.poker.engine.arena;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;

import static org.assertj.core.api.Assertions.assertThat;

class ArenaMapGenerationTest {
    private static final int SAMPLE_STEP = 10;

    @Test
    void generatedMazesAreFragmentedRedundantAndEverySpawnIsReachableWithVehicleClearance() {
        ArenaConfig config = ArenaConfig.standard(10);

        for (long seed = 0; seed < 30; seed++) {
            ArenaMap map = ArenaMap.generated(config, seed);

            assertThat(map.walls()).as("seed %s wall count", seed).hasSize(64);
            assertThat(map.walls()).allSatisfy(wall ->
                    assertThat(Math.max(wall.width(), wall.height()))
                            .isLessThanOrEqualTo(config.width() / ArenaMap.GRID_COLUMNS + 0.001));
            assertThat(ArenaMap.generatedTopologyHasTwoRoutes(seed))
                    .as("seed %s has at least two independent routes", seed).isTrue();
            assertThat(map.spawns()).hasSize(10);
            boolean[][] reachable = floodReachable(map, config, map.spawns().getFirst());
            for (ArenaSpawn spawn : map.spawns()) {
                assertThat(isClear(spawn.x(), spawn.y(), config.vehicleRadius() + 3, map, config)).isTrue();
                assertThat(reachable[index(spawn.y())][index(spawn.x())])
                        .as("seed %s spawn at %.0f,%.0f is reachable", seed, spawn.x(), spawn.y())
                        .isTrue();
            }
        }
    }

    private static boolean[][] floodReachable(ArenaMap map, ArenaConfig config, ArenaSpawn start) {
        int columns = (int) (config.width() / SAMPLE_STEP) + 1;
        int rows = (int) (config.height() / SAMPLE_STEP) + 1;
        boolean[][] visited = new boolean[rows][columns];
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        int startX = index(start.x());
        int startY = index(start.y());
        visited[startY][startX] = true;
        queue.add(new int[]{startX, startY});
        int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty()) {
            int[] point = queue.removeFirst();
            for (int[] direction : directions) {
                int x = point[0] + direction[0];
                int y = point[1] + direction[1];
                if (x < 0 || x >= columns || y < 0 || y >= rows || visited[y][x]) continue;
                if (!isClear(x * SAMPLE_STEP, y * SAMPLE_STEP,
                        config.vehicleRadius() + 3, map, config)) continue;
                visited[y][x] = true;
                queue.addLast(new int[]{x, y});
            }
        }
        return visited;
    }

    private static boolean isClear(double x, double y, double radius, ArenaMap map, ArenaConfig config) {
        if (x - radius < 0 || y - radius < 0 || x + radius > config.width() || y + radius > config.height()) {
            return false;
        }
        return map.walls().stream().noneMatch(wall -> intersects(x, y, radius, wall));
    }

    private static boolean intersects(double x, double y, double radius, ArenaWall wall) {
        double closestX = Math.max(wall.x(), Math.min(x, wall.x() + wall.width()));
        double closestY = Math.max(wall.y(), Math.min(y, wall.y() + wall.height()));
        double dx = x - closestX;
        double dy = y - closestY;
        return dx * dx + dy * dy <= radius * radius;
    }

    private static int index(double coordinate) {
        return (int) Math.round(coordinate / SAMPLE_STEP);
    }
}
