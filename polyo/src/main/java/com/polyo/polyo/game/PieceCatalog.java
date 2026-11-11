package com.polyo.polyo.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/** Block shapes and tray generation; shapes mirror the frontend's constants/gameData.ts. */
final class PieceCatalog {
    static final int TRAY_SIZE = 6;
    /** Chance that a tray slot holds a special block. */
    static final double SPECIAL_CHANCE = 0.15;

    private static final List<String> COLORS = List.of(
            "cyan", "lime", "amber", "rose", "violet", "blue", "teal", "orange", "pink", "green", "indigo");
    private static final List<String> SPECIALS = List.of("ghost", "line", "echo", "golden", "bomb", "fill", "boost");

    private static final Map<String, int[][]> SHAPES = new LinkedHashMap<>();

    static {
        shape("square-3x3", "000102101112202122");
        shape("rectangle-2x3", "000102101112");
        shape("rectangle-3x2", "000110112021");
        shape("square-2x2", "00011011");
        shape("corner-bottom-left", "0010202122");
        shape("corner-top-left", "0001021020");
        shape("corner-top-right", "0001021222");
        shape("corner-bottom-right", "0212202122");
        shape("line-5-horizontal", "0001020304");
        shape("line-5-vertical", "0010203040");
        shape("line-4-horizontal", "00010203");
        shape("line-4-vertical", "00102030");
        shape("tetromino-l-0deg", "00102021");
        shape("tetromino-l-90deg", "00010210");
        shape("tetromino-l-180deg", "00011121");
        shape("tetromino-l-270deg", "02101112");
        shape("tetromino-j-0deg", "01112021");
        shape("tetromino-j-90deg", "00101112");
        shape("tetromino-j-180deg", "00011020");
        shape("tetromino-j-270deg", "00010212");
        shape("tetromino-z-horizontal", "00011112");
        shape("tetromino-z-vertical", "01101120");
        shape("tetromino-s-horizontal", "01021011");
        shape("tetromino-s-vertical", "00101121");
        shape("tetromino-t-down", "00010211");
        shape("tetromino-t-right", "00101120");
        shape("tetromino-t-up", "01101112");
        shape("tetromino-t-left", "01101121");
        shape("monomino-single", "00");
        shape("domino-horizontal", "0001");
        shape("domino-vertical", "0010");
        shape("diagonal-2-down-right", "0011");
        shape("diagonal-2-down-left", "0110");
        shape("diagonal-3-down-right", "001122");
        shape("diagonal-3-down-left", "021120");
        shape("diagonal-4-down-right", "00112233");
        shape("rectangle-3x4", "000102031011121320212223");
        shape("rectangle-4x3", "000102101112202122303132");
        shape("square-4x4", "00010203101112132021222330313233");
    }

    private static final List<String> ALL_SHAPES = List.copyOf(SHAPES.keySet());
    private static final List<String> DEFAULT_DECK = List.of(
            "square-2x2", "line-4-horizontal", "line-4-vertical",
            "tetromino-l-0deg", "tetromino-l-90deg", "tetromino-l-180deg", "tetromino-l-270deg",
            "tetromino-j-0deg", "tetromino-j-90deg", "tetromino-j-180deg", "tetromino-j-270deg",
            "tetromino-z-horizontal", "tetromino-z-vertical", "tetromino-s-horizontal", "tetromino-s-vertical",
            "tetromino-t-down", "tetromino-t-right", "tetromino-t-up", "tetromino-t-left");

    private PieceCatalog() {
    }

    private static void shape(String id, String digits) {
        int[][] cells = new int[digits.length() / 2][];
        for (int i = 0; i < cells.length; i++) {
            cells[i] = new int[] {digits.charAt(i * 2) - '0', digits.charAt(i * 2 + 1) - '0'};
        }
        SHAPES.put(id, cells);
    }

    static List<Piece> nextTray(Random random) {
        List<Piece> tray = new ArrayList<>(TRAY_SIZE);
        for (int i = 0; i < TRAY_SIZE; i++) {
            tray.add(random.nextDouble() < SPECIAL_CHANCE ? randomSpecial(random) : randomPlain(random));
        }
        return tray;
    }

    /** Build three pieces that can be played in some order on the current board. */
    static List<String> nextHardTray(PlayerBoard board, Random random) {
        var simulation = new PlayerBoard(board.cells(), 0, 0, false);
        var shapes = new ArrayList<>(ALL_SHAPES);
        var tray = new ArrayList<String>(3);
        for (int slot = 0; slot < 3; slot++) {
            Collections.shuffle(shapes, random);
            tray.add(placeSimulatedPiece(simulation, shapes));
        }
        Collections.shuffle(tray, random);
        return tray;
    }

    private static String placeSimulatedPiece(PlayerBoard board, List<String> shapes) {
        for (String shape : shapes) {
            Piece piece = plain(shape, "cyan");
            for (int row = 0; row < PlayerBoard.SIZE; row++) {
                for (int col = 0; col < PlayerBoard.SIZE; col++) {
                    if (board.canPlace(piece, row, col)) {
                        board.place(piece, row, col);
                        return shape;
                    }
                }
            }
        }
        throw new IllegalStateException("Settled board must have room for a single cell");
    }

    private static Piece randomPlain(Random random) {
        String id = DEFAULT_DECK.get(random.nextInt(DEFAULT_DECK.size()));
        return plain(id, COLORS.get(random.nextInt(COLORS.size())));
    }

    // Specials use the full shape catalog, as in single play.
    private static Piece randomSpecial(Random random) {
        String special = SPECIALS.get(random.nextInt(SPECIALS.size()));
        return special(special, ALL_SHAPES.get(random.nextInt(ALL_SHAPES.size())));
    }

    static Piece plain(String shapeId, String color) {
        return new Piece(UUID.randomUUID().toString(), shapeId, List.of(SHAPES.get(shapeId)), color, null);
    }

    static Piece special(String special, String shapeId) {
        return new Piece(UUID.randomUUID().toString(), special + "-" + shapeId, List.of(SHAPES.get(shapeId)), special, special);
    }
}
