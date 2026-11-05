package com.polyo.polyo.game;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/** The full match state pushed to both players after every change. */
record GameStateMessage(String type, String status, String turnUserId, long turnRemainingMs,
                        List<PlayerState> players, List<PieceState> tray, String winnerUserId, String finishReason,
                        MatchGame.LastMove lastMove) {

    record PlayerState(String userId, String nickname, String[][] board, int score, int combo, long turnLimitMs,
                       boolean connected) {
    }

    record PieceState(String uid, String id, List<int[]> cells, String color, String special) {
    }

    static GameStateMessage of(MatchGame game, Set<String> connected) {
        List<PlayerState> players = game.players().stream()
                .map(player -> new PlayerState(player.userId, player.nickname, player.board.cells(),
                        player.board.score(), player.board.combo(), player.turnLimitMs,
                        connected.contains(player.userId)))
                .toList();
        boolean finished = game.finished();
        return new GameStateMessage("STATE", finished ? "FINISHED" : "PLAYING", game.current().userId,
                finished ? 0 : game.remainingTurnMs(), players, pieces(game.tray()), game.winnerUserId(),
                finished ? game.finishReason().name() : null, game.lastMove());
    }

    private static List<PieceState> pieces(Collection<Piece> tray) {
        return tray.stream().map(piece -> piece == null ? null
                : new PieceState(piece.uid(), piece.id(), piece.cells(), piece.color(), piece.special())).toList();
    }
}
