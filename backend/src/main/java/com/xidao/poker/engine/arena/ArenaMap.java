package com.xidao.poker.engine.arena;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public record ArenaMap(List<ArenaWall> walls, List<ArenaSpawn> spawns) {
    static final int GRID_COLUMNS = 12;
    static final int GRID_ROWS = 8;
    private static final int TARGET_WALL_SEGMENTS = 64;

    public ArenaMap {
        walls = List.copyOf(walls);
        spawns = List.copyOf(spawns);
        if (spawns.size() < 2) throw new IllegalArgumentException("arena requires at least two spawns");
    }

    public static ArenaMap standard() {
        ArenaConfig config = ArenaConfig.standard(10);
        return generated(config, 0x584944414FL);
    }

    /** Builds a braided maze whose cell graph has no articulation point, so every pair of cells has
     * at least two internally independent routes. Shorter 12x8 cell boundaries keep walls fragmented. */
    public static ArenaMap generated(ArenaConfig config, long seed) {
        int columns = GRID_COLUMNS;
        int rows = GRID_ROWS;
        double wallThickness = 18;
        double cellWidth = config.width() / columns;
        double cellHeight = config.height() / rows;
        double minimumClearLane = Math.min(cellWidth, cellHeight) - wallThickness;
        if (minimumClearLane < config.vehicleRadius() * 2 + 48) {
            throw new IllegalArgumentException("generated arena lanes are too narrow for vehicles");
        }

        Random random = new Random(seed);
        Topology topology = generateTopology(random);
        boolean[][] passageRight = topology.passageRight();
        boolean[][] passageDown = topology.passageDown();

        List<ArenaWall> walls = new ArrayList<>();
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns - 1; column++) {
                if (!passageRight[row][column]) {
                    walls.add(new ArenaWall((column + 1) * cellWidth - wallThickness / 2,
                            row * cellHeight, wallThickness, cellHeight));
                }
            }
        }
        for (int row = 0; row < rows - 1; row++) {
            for (int column = 0; column < columns; column++) {
                if (!passageDown[row][column]) {
                    walls.add(new ArenaWall(column * cellWidth,
                            (row + 1) * cellHeight - wallThickness / 2, cellWidth, wallThickness));
                }
            }
        }

        List<Integer> cells = spreadSpawnCells(columns, rows, random);
        List<ArenaSpawn> spawns = new ArrayList<>();
        double centerX = config.width() / 2;
        double centerY = config.height() / 2;
        for (int index = 0; index < 10; index++) {
            int cell = cells.get(index);
            int row = cell / columns;
            int column = cell % columns;
            spawns.add(spawn((column + .5) * cellWidth, (row + .5) * cellHeight, centerX, centerY));
        }
        return new ArenaMap(walls, spawns);
    }

    static boolean generatedTopologyHasTwoRoutes(long seed) {
        Topology topology = generateTopology(new Random(seed));
        return isBiconnected(topology.passageRight(), topology.passageDown());
    }

    private static Topology generateTopology(Random random) {
        long topologySeed = random.nextLong();
        Candidate best = null;
        for (int attempt = 0; attempt < 32; attempt++) {
            Candidate candidate = generateCandidate(new Random(topologySeed
                    + 0x9E3779B97F4A7C15L * attempt));
            if (best == null || candidate.wallCount() > best.wallCount()) best = candidate;
            if (candidate.wallCount() == TARGET_WALL_SEGMENTS) return candidate.topology();
        }
        throw new IllegalStateException("could not generate a sufficiently complex redundant arena; best="
                + (best == null ? 0 : best.wallCount()));
    }

    private static Candidate generateCandidate(Random random) {
        boolean[][] passageRight = new boolean[GRID_ROWS][GRID_COLUMNS - 1];
        boolean[][] passageDown = new boolean[GRID_ROWS - 1][GRID_COLUMNS];
        for (boolean[] row : passageRight) Arrays.fill(row, true);
        for (boolean[] row : passageDown) Arrays.fill(row, true);

        List<Edge> edges = new ArrayList<>();
        for (int row = 0; row < GRID_ROWS; row++) {
            for (int column = 0; column < GRID_COLUMNS - 1; column++) {
                edges.add(new Edge(row, column, true));
            }
        }
        for (int row = 0; row < GRID_ROWS - 1; row++) {
            for (int column = 0; column < GRID_COLUMNS; column++) {
                edges.add(new Edge(row, column, false));
            }
        }
        Collections.shuffle(edges, random);
        int walls = 0;
        for (Edge edge : edges) {
            setPassage(edge, passageRight, passageDown, false);
            if (isBiconnected(passageRight, passageDown)) {
                walls++;
                if (walls == TARGET_WALL_SEGMENTS) break;
            } else {
                setPassage(edge, passageRight, passageDown, true);
            }
        }
        return new Candidate(new Topology(passageRight, passageDown), walls);
    }

    private static void setPassage(Edge edge, boolean[][] passageRight,
                                   boolean[][] passageDown, boolean open) {
        if (edge.right()) passageRight[edge.row()][edge.column()] = open;
        else passageDown[edge.row()][edge.column()] = open;
    }

    private static boolean isBiconnected(boolean[][] passageRight, boolean[][] passageDown) {
        int vertices = GRID_COLUMNS * GRID_ROWS;
        int[] discovery = new int[vertices];
        int[] low = new int[vertices];
        int[] parent = new int[vertices];
        Arrays.fill(discovery, -1);
        Arrays.fill(parent, -1);
        boolean[] articulation = new boolean[vertices];
        findArticulationPoints(0, passageRight, passageDown, discovery, low, parent,
                articulation, new int[]{0});
        return Arrays.stream(discovery).noneMatch(value -> value < 0)
                && !containsTrue(articulation);
    }

    private static void findArticulationPoints(int vertex, boolean[][] passageRight, boolean[][] passageDown,
                                               int[] discovery, int[] low, int[] parent,
                                               boolean[] articulation, int[] time) {
        discovery[vertex] = low[vertex] = ++time[0];
        int children = 0;
        for (int neighbor : neighbors(vertex, passageRight, passageDown)) {
            if (discovery[neighbor] < 0) {
                parent[neighbor] = vertex;
                children++;
                findArticulationPoints(neighbor, passageRight, passageDown,
                        discovery, low, parent, articulation, time);
                low[vertex] = Math.min(low[vertex], low[neighbor]);
                if (parent[vertex] < 0 && children > 1) articulation[vertex] = true;
                if (parent[vertex] >= 0 && low[neighbor] >= discovery[vertex]) articulation[vertex] = true;
            } else if (neighbor != parent[vertex]) {
                low[vertex] = Math.min(low[vertex], discovery[neighbor]);
            }
        }
    }

    private static List<Integer> neighbors(int vertex, boolean[][] passageRight, boolean[][] passageDown) {
        int row = vertex / GRID_COLUMNS;
        int column = vertex % GRID_COLUMNS;
        List<Integer> neighbors = new ArrayList<>(4);
        if (column < GRID_COLUMNS - 1 && passageRight[row][column]) neighbors.add(vertex + 1);
        if (column > 0 && passageRight[row][column - 1]) neighbors.add(vertex - 1);
        if (row < GRID_ROWS - 1 && passageDown[row][column]) neighbors.add(vertex + GRID_COLUMNS);
        if (row > 0 && passageDown[row - 1][column]) neighbors.add(vertex - GRID_COLUMNS);
        return neighbors;
    }

    private static boolean containsTrue(boolean[] values) {
        for (boolean value : values) if (value) return true;
        return false;
    }

    private static List<Integer> spreadSpawnCells(int columns, int rows, Random random) {
        List<Integer> selected = new ArrayList<>();
        selected.add(random.nextInt(columns * rows));
        while (selected.size() < 10) {
            int bestDistance = -1;
            List<Integer> candidates = new ArrayList<>();
            for (int cell = 0; cell < columns * rows; cell++) {
                if (selected.contains(cell)) continue;
                int row = cell / columns;
                int column = cell % columns;
                int minimumDistance = selected.stream()
                        .mapToInt(other -> Math.abs(row - other / columns)
                                + Math.abs(column - other % columns))
                        .min().orElse(0);
                if (minimumDistance > bestDistance) {
                    bestDistance = minimumDistance;
                    candidates.clear();
                }
                if (minimumDistance == bestDistance) candidates.add(cell);
            }
            selected.add(candidates.get(random.nextInt(candidates.size())));
        }
        return selected;
    }

    private static ArenaSpawn spawn(double x, double y, double targetX, double targetY) {
        return new ArenaSpawn(x, y, Math.atan2(targetY - y, targetX - x));
    }

    private record Edge(int row, int column, boolean right) { }
    private record Topology(boolean[][] passageRight, boolean[][] passageDown) { }
    private record Candidate(Topology topology, int wallCount) { }
}
