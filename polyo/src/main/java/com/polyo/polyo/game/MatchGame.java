package com.polyo.polyo.game;

import com.polyo.polyo.matchroom.MatchRoom;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.function.LongSupplier;

/**
 * One two-player match: both players take turns using the shared six-block tray on their own board.
 * A player loses by having no placeable block on their turn, running out of time, or forfeiting.
 * Not thread-safe; {@link GameHub} serializes access per room.
 */
final class MatchGame {
    static final long INITIAL_TURN_MS = 10_000;
    static final long MIN_TURN_MS = 2_800;

    enum FinishReason { NO_MOVES, TIMEOUT, FORFEIT }

    record LastMove(String userId, int scoreGain, int cleared, Set<String> clearingCells) {
    }

    static final class Player {
        final String userId;
        final String nickname;
        final PlayerBoard board = new PlayerBoard();
        long turnLimitMs = INITIAL_TURN_MS;

        Player(String userId, String nickname) {
            this.userId = userId;
            this.nickname = nickname;
        }
    }

    private final List<Player> players;
    private final Random random;
    private final LongSupplier clock;
    private List<Piece> tray;
    private int turnIndex;
    private long turnStartedAt;
    private String winnerUserId;
    private FinishReason finishReason;
    private LastMove lastMove;

    MatchGame(List<MatchRoom.Member> members, Random random, LongSupplier clock) {
        this.players = members.stream().map(member -> new Player(member.userId(), member.nickname())).toList();
        this.random = random;
        this.clock = clock;
        this.tray = PieceCatalog.nextTray(random);
        this.turnIndex = random.nextInt(players.size());
        this.turnStartedAt = clock.getAsLong();
    }

    void place(String userId, String pieceUid, int row, int col) {
        requireRunning();
        Player player = current();
        if (!player.userId.equals(userId)) {
            throw new GameException("NOT_YOUR_TURN", "상대 차례입니다.");
        }
        int slot = slotOf(pieceUid);
        Piece piece = tray.get(slot);
        if (!player.board.canPlace(piece, row, col)) {
            throw new GameException("INVALID_PLACEMENT", "그 위치에는 놓을 수 없습니다.");
        }

        PlayerBoard.Outcome outcome = player.board.place(piece, row, col);
        lastMove = new LastMove(userId, outcome.scoreGain(), outcome.cleared(), outcome.clearingCells());
        player.turnLimitMs = Math.max(MIN_TURN_MS, player.turnLimitMs - turnDecayMs(player.board.score()));

        tray.set(slot, null);
        if (remainingPieces().isEmpty()) {
            players.forEach(each -> each.board.onTrayCompleted());
            tray = PieceCatalog.nextTray(random);
        }
        turnIndex = (turnIndex + 1) % players.size();
        turnStartedAt = clock.getAsLong();
        if (!current().board.hasMove(remainingPieces())) {
            finish(current(), FinishReason.NO_MOVES);
        }
    }

    /** Ends the game if the current turn's time has run out. Returns whether it did. */
    boolean expireTurn() {
        if (finished() || remainingTurnMs() > 0) {
            return false;
        }
        finish(current(), FinishReason.TIMEOUT);
        return true;
    }

    void forfeit(String userId) {
        requireRunning();
        finish(players.stream().filter(player -> player.userId.equals(userId)).findFirst()
                .orElseThrow(() -> new GameException("NOT_A_PLAYER", "이 게임의 참가자가 아닙니다.")), FinishReason.FORFEIT);
    }

    long remainingTurnMs() {
        return Math.max(0, current().turnLimitMs - (clock.getAsLong() - turnStartedAt));
    }

    boolean finished() { return winnerUserId != null; }
    Player current() { return players.get(turnIndex); }
    List<Player> players() { return players; }
    List<Piece> tray() { return tray; }
    String winnerUserId() { return winnerUserId; }
    FinishReason finishReason() { return finishReason; }
    LastMove lastMove() { return lastMove; }

    List<Piece> remainingPieces() {
        return tray.stream().filter(Objects::nonNull).toList();
    }

    // Package-private seam so tests can stage a specific tray.
    void setTray(List<Piece> pieces) {
        tray = new ArrayList<>(pieces);
    }

    private static long turnDecayMs(int score) {
        if (score >= 200) {
            return 200;
        }
        return score >= 100 ? 100 : 0;
    }

    private void finish(Player loser, FinishReason reason) {
        winnerUserId = players.stream().filter(player -> player != loser).findFirst().orElseThrow().userId;
        finishReason = reason;
    }

    private int slotOf(String pieceUid) {
        for (int slot = 0; slot < tray.size(); slot++) {
            Piece piece = tray.get(slot);
            if (piece != null && piece.uid().equals(pieceUid)) {
                return slot;
            }
        }
        throw new GameException("PIECE_NOT_AVAILABLE", "사용할 수 없는 블록입니다.");
    }

    private void requireRunning() {
        if (finished()) {
            throw new GameException("GAME_FINISHED", "이미 끝난 게임입니다.");
        }
    }
}
