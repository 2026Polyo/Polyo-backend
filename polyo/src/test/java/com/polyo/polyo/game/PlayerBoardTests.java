package com.polyo.polyo.game;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PlayerBoardTests {
    private static Piece single() {
        return PieceCatalog.plain("monomino-single", "cyan");
    }

    private static Piece fourAcross() {
        return PieceCatalog.plain("line-4-horizontal", "cyan");
    }

    /** Fills row {@code row} except column 7. */
    private static void fillRowExceptLast(PlayerBoard board, int row) {
        board.place(fourAcross(), row, 0);
        board.place(PieceCatalog.plain("monomino-single", "lime"), row, 4);
        board.place(PieceCatalog.plain("monomino-single", "lime"), row, 5);
        board.place(PieceCatalog.plain("monomino-single", "lime"), row, 6);
    }

    @Test
    void scoresPlacedCells() {
        PlayerBoard board = new PlayerBoard();
        assertThat(board.place(fourAcross(), 0, 0).scoreGain()).isEqualTo(4);
        assertThat(board.score()).isEqualTo(4);
    }

    @Test
    void rejectsOverlapAndOutOfBounds() {
        PlayerBoard board = new PlayerBoard();
        board.place(single(), 0, 0);
        assertThat(board.canPlace(single(), 0, 0)).isFalse();
        assertThat(board.canPlace(fourAcross(), 0, 5)).isFalse();
        assertThat(board.canPlace(single(), -1, 0)).isFalse();
        assertThat(board.canPlace(single(), 8, 0)).isFalse();
    }

    @Test
    void clearsFullLineAndCountsCombo() {
        PlayerBoard board = new PlayerBoard();
        fillRowExceptLast(board, 0);

        PlayerBoard.Outcome first = board.place(single(), 0, 7);

        assertThat(first.cleared()).isEqualTo(1);
        assertThat(first.scoreGain()).isEqualTo(1 + 20);
        assertThat(first.clearingCells()).hasSize(8);
        assertThat(board.cells()[0]).containsOnlyNulls();
        assertThat(board.combo()).isEqualTo(1);

        fillRowExceptLast(board, 1);
        assertThat(board.place(single(), 1, 7).scoreGain()).isEqualTo(1 + 20 + 5);
        assertThat(board.combo()).isEqualTo(2);
    }

    @Test
    void comboResetsOnlyAfterATrayWithoutAClear() {
        PlayerBoard board = new PlayerBoard();
        fillRowExceptLast(board, 0);
        board.place(single(), 0, 7);

        board.onTrayCompleted();
        assertThat(board.combo()).isEqualTo(1);
        board.onTrayCompleted();
        assertThat(board.combo()).isZero();
    }

    @Test
    void ghostOverlapsAndPaysWhenTheOverlappedCellClears() {
        PlayerBoard board = new PlayerBoard();
        board.place(single(), 0, 0);
        Piece ghost = PieceCatalog.special("ghost", "monomino-single");
        assertThat(board.canPlace(ghost, 0, 0)).isTrue();
        board.place(ghost, 0, 0);

        board.place(PieceCatalog.plain("monomino-single", "lime"), 0, 1);
        board.place(PieceCatalog.plain("domino-horizontal", "lime"), 0, 2);
        board.place(PieceCatalog.plain("line-4-horizontal", "lime"), 0, 4);
        // The last placement completed row 0: 4 cells + 20 line + 20 ghost bonus.
        assertThat(board.cells()[0]).containsOnlyNulls();
        assertThat(board.score()).isEqualTo(1 + 1 + 1 + 2 + 4 + 20 + 20);
    }

    @Test
    void lineBlockClearsTheCrossThroughItsCell() {
        PlayerBoard board = new PlayerBoard();
        board.place(fourAcross(), 3, 0);
        board.place(PieceCatalog.plain("line-4-vertical", "lime"), 4, 2);

        PlayerBoard.Outcome outcome = board.place(PieceCatalog.special("line", "monomino-single"), 3, 7);

        assertThat(outcome.cleared()).isEqualTo(2);
        assertThat(board.cells()[3]).containsOnlyNulls();
        assertThat(board.cells()[4][2]).isEqualTo("lime");
    }

    @Test
    void echoPaysForFilledCellsBeforeIt() {
        PlayerBoard board = new PlayerBoard();
        board.place(fourAcross(), 0, 0);
        assertThat(board.place(PieceCatalog.special("echo", "monomino-single"), 7, 7).scoreGain()).isEqualTo(1 + 4 * 5);
    }

    @Test
    void fillAddsEmptyNeighbours() {
        PlayerBoard board = new PlayerBoard();
        PlayerBoard.Outcome outcome = board.place(PieceCatalog.special("fill", "monomino-single"), 4, 4);
        assertThat(outcome.scoreGain()).isEqualTo(1 + 4);
        assertThat(board.cells()[3][4]).isEqualTo("fill");
        assertThat(board.cells()[5][4]).isEqualTo("fill");
        assertThat(board.cells()[4][3]).isEqualTo("fill");
        assertThat(board.cells()[4][5]).isEqualTo("fill");
    }

    @Test
    void boostMultipliesLaterPlacementsWhileOnTheBoard() {
        PlayerBoard board = new PlayerBoard();
        board.place(PieceCatalog.special("boost", "square-2x2"), 0, 0);
        // Four boost cells: +20%.
        assertThat(board.place(PieceCatalog.plain("square-3x3", "cyan"), 4, 4).scoreGain()).isEqualTo(11);
    }

    @Test
    void goldenCellPaysItsShareOfTheClearScoreAgain() {
        PlayerBoard board = new PlayerBoard();
        board.place(PieceCatalog.special("golden", "monomino-single"), 0, 0);
        board.place(PieceCatalog.plain("domino-horizontal", "lime"), 0, 1);
        board.place(PieceCatalog.plain("line-4-horizontal", "lime"), 0, 3);

        // 1 cell + 20 line + 20/8 for the golden cell, rounded.
        assertThat(board.place(single(), 0, 7).scoreGain()).isEqualTo(1 + 20 + 3);
    }

    @Test
    void bombDetonatesWhenItsCellIsCleared() {
        PlayerBoard board = new PlayerBoard();
        board.place(PieceCatalog.special("bomb", "monomino-single"), 0, 3);
        board.place(PieceCatalog.plain("line-4-vertical", "lime"), 1, 3);
        board.place(PieceCatalog.plain("line-4-horizontal", "lime"), 0, 4);
        board.place(PieceCatalog.plain("domino-horizontal", "lime"), 0, 0);

        PlayerBoard.Outcome outcome = board.place(PieceCatalog.plain("monomino-single", "cyan"), 0, 2);

        assertThat(outcome.cleared()).isEqualTo(1);
        assertThat(board.cells()[1][3]).isNull();
        assertThat(board.cells()[4][3]).isNull();
        // 1 cell + 20 line + 4 blasted cells at 5 each.
        assertThat(outcome.scoreGain()).isEqualTo(1 + 20 + 4 * 5);
    }
}
