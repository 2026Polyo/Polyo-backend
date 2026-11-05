package com.polyo.polyo.game;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * One player's board, special-cell marks and scoring. Placement rules mirror the frontend's
 * resolvePlacement.ts without augments; cell keys are "row-col" like the frontend's.
 */
final class PlayerBoard {
    static final int SIZE = 8;

    private static final int LINE_SCORE = 20;
    private static final int MULTI_LINE_BONUS = 15;
    private static final int COMBO_BONUS = 5;
    private static final int GHOST_CLEAR_BONUS = 20;
    private static final int BOMB_CLEAR_BONUS = 5;
    private static final int ECHO_SCORE_PER_CELL = 5;
    private static final double BOOST_RATE_PER_CELL = 0.05;

    private String[][] cells = new String[SIZE][SIZE];
    private Set<String> ghostCells = new HashSet<>();
    private Set<String> goldenCells = new HashSet<>();
    private Set<String> bombCells = new HashSet<>();
    private Set<String> boostCells = new HashSet<>();
    private int score;
    private int combo;
    private boolean clearedThisTray;

    record Outcome(int scoreGain, int cleared, Set<String> clearingCells) {
    }

    boolean canPlace(Piece piece, int row, int col) {
        for (int[] cell : piece.cells()) {
            int r = row + cell[0];
            int c = col + cell[1];
            if (r < 0 || r >= SIZE || c < 0 || c >= SIZE) {
                return false;
            }
            if (!piece.is("ghost") && cells[r][c] != null) {
                return false;
            }
        }
        return true;
    }

    boolean hasMove(Collection<Piece> pieces) {
        for (Piece piece : pieces) {
            for (int row = 0; row < SIZE; row++) {
                for (int col = 0; col < SIZE; col++) {
                    if (canPlace(piece, row, col)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Places a piece that {@link #canPlace} accepted and applies every effect. */
    Outcome place(Piece piece, int row, int col) {
        int echoBonus = piece.is("echo") ? filledCount() * ECHO_SCORE_PER_CELL : 0;

        List<String> pieceKeys = new ArrayList<>();
        List<String> overlapped = new ArrayList<>();
        String[][] placed = copy(cells);
        for (int[] cell : piece.cells()) {
            int r = row + cell[0];
            int c = col + cell[1];
            pieceKeys.add(key(r, c));
            if (cells[r][c] != null && piece.is("ghost")) {
                overlapped.add(key(r, c));
            }
            placed[r][c] = piece.color();
        }

        int extraCells = 0;
        Cleared result;
        if (piece.is("line")) {
            result = clearCrosses(placed, piece, row, col);
        } else {
            if (piece.is("fill")) {
                extraCells = fillNeighbors(placed, piece, row, col);
            }
            result = clearFullLines(placed);
        }

        Set<String> clearing = new LinkedHashSet<>(result.cells());
        int placementScore = piece.cells().size() + extraCells;
        int clearScore = result.count() * LINE_SCORE;
        int multiLineScore = Math.max(0, result.count() - 1) * MULTI_LINE_BONUS;
        int comboScore = result.count() * combo * COMBO_BONUS;
        int baseScore = placementScore + clearScore + multiLineScore + comboScore;

        int ghostBonus = resolveGhosts(overlapped, clearing);
        int goldenBonus = resolveGolden(clearing, clearScore);
        if (piece.is("golden")) {
            goldenCells.addAll(pieceKeys);
        }
        if (piece.is("bomb")) {
            bombCells.addAll(pieceKeys);
        }
        int bombBonus = detonateBombs(result.board(), clearing);

        double multiplier = 1 + BOOST_RATE_PER_CELL * boostCells.size();
        int gain = (int) Math.round((baseScore + ghostBonus + goldenBonus + echoBonus + bombBonus) * multiplier);

        boostCells.removeAll(clearing);
        if (piece.is("boost")) {
            pieceKeys.stream().filter(key -> !clearing.contains(key)).forEach(boostCells::add);
        }

        cells = result.board();
        score += gain;
        combo += result.count();
        clearedThisTray |= result.count() > 0;
        return new Outcome(gain, result.count(), clearing);
    }

    /** A combo only survives a tray in which the player cleared at least one line. */
    void onTrayCompleted() {
        if (!clearedThisTray) {
            combo = 0;
        }
        clearedThisTray = false;
    }

    int score() { return score; }
    int combo() { return combo; }

    // Package-private seams so tests can stage states that normal play would clear away.
    void fillForTest() {
        Arrays.stream(cells).forEach(row -> Arrays.fill(row, "cyan"));
    }

    void addScoreForTest(int points) {
        score += points;
    }

    String[][] cells() {
        return copy(cells);
    }

    private record Cleared(String[][] board, int count, List<String> cells) {
    }

    private Cleared clearCrosses(String[][] placed, Piece piece, int row, int col) {
        Set<Integer> rows = new LinkedHashSet<>();
        Set<Integer> cols = new LinkedHashSet<>();
        for (int[] cell : piece.cells()) {
            rows.add(row + cell[0]);
            cols.add(col + cell[1]);
        }
        String[][] next = copy(placed);
        List<String> cleared = new ArrayList<>();
        for (int r : rows) {
            for (int c = 0; c < SIZE; c++) {
                if (next[r][c] != null) {
                    cleared.add(key(r, c));
                }
                next[r][c] = null;
            }
        }
        for (int c : cols) {
            for (int r = 0; r < SIZE; r++) {
                if (next[r][c] != null) {
                    cleared.add(key(r, c));
                }
                next[r][c] = null;
            }
        }
        return new Cleared(next, rows.size() + cols.size(), cleared);
    }

    private int fillNeighbors(String[][] placed, Piece piece, int row, int col) {
        Set<String> filled = new HashSet<>();
        int[][] deltas = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        for (int[] cell : piece.cells()) {
            for (int[] delta : deltas) {
                int r = row + cell[0] + delta[0];
                int c = col + cell[1] + delta[1];
                if (r >= 0 && r < SIZE && c >= 0 && c < SIZE && placed[r][c] == null) {
                    filled.add(key(r, c));
                }
            }
        }
        for (String key : filled) {
            placed[row(key)][col(key)] = piece.color();
        }
        return filled.size();
    }

    private Cleared clearFullLines(String[][] board) {
        List<Integer> fullRows = new ArrayList<>();
        List<Integer> fullCols = new ArrayList<>();
        for (int i = 0; i < SIZE; i++) {
            boolean rowFull = true;
            boolean colFull = true;
            for (int j = 0; j < SIZE; j++) {
                rowFull &= board[i][j] != null;
                colFull &= board[j][i] != null;
            }
            if (rowFull) {
                fullRows.add(i);
            }
            if (colFull) {
                fullCols.add(i);
            }
        }
        String[][] next = copy(board);
        Set<String> cleared = new LinkedHashSet<>();
        for (int r : fullRows) {
            for (int c = 0; c < SIZE; c++) {
                cleared.add(key(r, c));
                next[r][c] = null;
            }
        }
        for (int c : fullCols) {
            for (int r = 0; r < SIZE; r++) {
                cleared.add(key(r, c));
                next[r][c] = null;
            }
        }
        return new Cleared(next, fullRows.size() + fullCols.size(), List.copyOf(cleared));
    }

    private int resolveGhosts(List<String> overlapped, Set<String> clearing) {
        ghostCells.addAll(overlapped);
        int before = ghostCells.size();
        ghostCells.removeAll(clearing);
        return (before - ghostCells.size()) * GHOST_CLEAR_BONUS;
    }

    private int resolveGolden(Set<String> clearing, int clearScore) {
        if (goldenCells.isEmpty() || clearing.isEmpty() || clearScore == 0) {
            return 0;
        }
        double perCell = (double) clearScore / clearing.size();
        int before = goldenCells.size();
        goldenCells.removeAll(clearing);
        return (int) Math.round((before - goldenCells.size()) * perCell);
    }

    // A bomb goes off when its own cell is cleared, wiping its row and column and chaining into
    // other bombs. `board` is the settled board and is updated in place; `clearing` grows with the blast.
    private int detonateBombs(String[][] board, Set<String> clearing) {
        Deque<String> queue = new ArrayDeque<>();
        for (String bomb : List.copyOf(bombCells)) {
            if (clearing.contains(bomb)) {
                queue.add(bomb);
                bombCells.remove(bomb);
            }
        }
        int destroyed = 0;
        while (!queue.isEmpty()) {
            String bomb = queue.poll();
            for (int i = 0; i < SIZE; i++) {
                for (String target : List.of(key(row(bomb), i), key(i, col(bomb)))) {
                    int r = row(target);
                    int c = col(target);
                    boolean hadBlock = board[r][c] != null;
                    board[r][c] = null;
                    if (hadBlock && clearing.add(target)) {
                        destroyed++;
                    }
                    if (bombCells.remove(target)) {
                        queue.add(target);
                    }
                }
            }
        }
        return destroyed * BOMB_CLEAR_BONUS;
    }

    private int filledCount() {
        int count = 0;
        for (String[] row : cells) {
            for (String cell : row) {
                if (cell != null) {
                    count++;
                }
            }
        }
        return count;
    }

    private static String[][] copy(String[][] board) {
        return Arrays.stream(board).map(String[]::clone).toArray(String[][]::new);
    }

    private static String key(int row, int col) {
        return row + "-" + col;
    }

    private static int row(String key) {
        return key.charAt(0) - '0';
    }

    private static int col(String key) {
        return key.charAt(2) - '0';
    }
}
