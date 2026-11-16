package com.polyo.polyo.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.polyo.polyo.matchroom.MatchRoom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MatchGameTests {
    private final AtomicLong now = new AtomicLong(1_000);
    private MatchGame game;

    @BeforeEach
    void start() {
        game = new MatchGame(List.of(new MatchRoom.Member("a", "에이"), new MatchRoom.Member("b", "비")),
                new Random(1), now::get);
        game.setTray(singles(6));
    }

    private static List<Piece> singles(int count) {
        List<Piece> pieces = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            pieces.add(PieceCatalog.plain("monomino-single", "cyan"));
        }
        return pieces;
    }

    private String turn() {
        return game.current().userId;
    }

    private String other() {
        return "a".equals(turn()) ? "b" : "a";
    }

    private MatchGame.Player playerOf(String userId) {
        return game.players().stream().filter(player -> player.userId.equals(userId)).findFirst().orElseThrow();
    }

    private void placeNext(int row, int col) {
        game.place(turn(), game.remainingPieces().get(0).uid(), row, col);
    }

    @Test
    void trayHasSixSlotsAndPlayersAlternateOnSeparateBoards() {
        assertThat(PieceCatalog.nextTray(new Random(2))).hasSize(6);
        String first = turn();
        String second = other();

        placeNext(0, 0);

        assertThat(turn()).isEqualTo(second);
        assertThat(playerOf(first).board.cells()[0][0]).isEqualTo("cyan");
        assertThat(playerOf(second).board.cells()[0][0]).isNull();
        assertThat(game.remainingPieces()).hasSize(5);
    }

    @Test
    void outOfTurnMovesAndUsedPiecesAreRejected() {
        String uid = game.tray().get(0).uid();
        assertThatThrownBy(() -> game.place(other(), uid, 0, 0))
                .isInstanceOf(GameException.class).hasMessageContaining("차례");

        game.place(turn(), uid, 0, 0);

        assertThatThrownBy(() -> game.place(turn(), uid, 1, 1))
                .isInstanceOfSatisfying(GameException.class, e -> assertThat(e.code()).isEqualTo("PIECE_NOT_AVAILABLE"));
    }

    @Test
    void invalidPlacementKeepsTheTurn() {
        String player = turn();
        assertThatThrownBy(() -> placeNext(8, 0))
                .isInstanceOfSatisfying(GameException.class, e -> assertThat(e.code()).isEqualTo("INVALID_PLACEMENT"));
        assertThat(turn()).isEqualTo(player);
        assertThat(game.remainingPieces()).hasSize(6);
    }

    @Test
    void usingAllSixDealsANewTray() {
        for (int i = 0; i < 6; i++) {
            placeNext(0, i / 2 + (i % 2));
        }
        assertThat(game.remainingPieces()).hasSize(6);
        assertThat(game.tray()).doesNotContainNull();
    }

    @Test
    void playerWhoCannotPlaceOnTheirTurnLoses() {
        String mover = turn();
        String stuck = other();
        fillBoard(playerOf(stuck).board);

        placeNext(0, 0);

        assertThat(game.finished()).isTrue();
        assertThat(game.winnerUserId()).isEqualTo(mover);
        assertThat(game.finishReason()).isEqualTo(MatchGame.FinishReason.NO_MOVES);
    }

    @Test
    void ghostBlockKeepsAStuckPlayerAlive() {
        String stuck = other();
        fillBoard(playerOf(stuck).board);
        List<Piece> tray = new ArrayList<>(singles(5));
        tray.add(PieceCatalog.special("ghost", "monomino-single"));
        game.setTray(tray);

        placeNext(0, 0);

        assertThat(game.finished()).isFalse();
    }

    private static void fillBoard(PlayerBoard board) {
        board.fillForTest();
    }

    @Test
    void runningOutOfTimeLoses() {
        String slow = turn();
        now.addAndGet(9_999);
        assertThat(game.expireTurn()).isFalse();

        now.addAndGet(1);

        assertThat(game.expireTurn()).isTrue();
        assertThat(game.winnerUserId()).isEqualTo(other().equals(slow) ? slow : other());
        assertThat(game.winnerUserId()).isNotEqualTo(slow);
        assertThat(game.finishReason()).isEqualTo(MatchGame.FinishReason.TIMEOUT);
    }

    @Test
    void turnLimitShrinksWithScoreDownToTheMinimum() {
        MatchGame.Player player = playerOf(turn());
        assertThat(player.turnLimitMs).isEqualTo(10_000);

        player.board.addScoreForTest(100);
        placeNext(0, 0);
        assertThat(player.turnLimitMs).isEqualTo(9_900);

        player.board.addScoreForTest(100);
        player.turnLimitMs = 2_900;
        placeNext(0, 0);
        placeNext(0, 1);
        assertThat(player.turnLimitMs).isEqualTo(2_800);
    }

    @Test
    void forfeitingLoses() {
        String quitter = other();
        game.forfeit(quitter);
        assertThat(game.winnerUserId()).isNotEqualTo(quitter);
        assertThat(game.finishReason()).isEqualTo(MatchGame.FinishReason.FORFEIT);
        assertThatThrownBy(() -> game.forfeit(quitter)).isInstanceOf(GameException.class);
        assertThat(Collections.frequency(List.of(game.winnerUserId()), quitter)).isZero();
    }
}
