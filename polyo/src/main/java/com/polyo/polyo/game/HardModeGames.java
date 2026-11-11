package com.polyo.polyo.game;

import static com.polyo.polyo.credit.exception.CreditErrorCode.*;

import com.polyo.polyo.credit.domain.RecordedGameResult;
import com.polyo.polyo.credit.exception.CreditException;
import com.polyo.polyo.credit.port.GameResultSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/** Server owns the tray, clock and score. Each action locks and updates a single game row. */
@Service
public class HardModeGames implements GameResultSource {
    private static final int SCORE_MULTIPLIER = 2;
    private static final long INITIAL_MS = 10_000;
    private static final long MINIMUM_MS = 2_800;

    public record Move(int moveNumber, int slot, int row, int col) {}
    public record Snapshot(String gameId, List<String> tray, int score, int moveNumber,
                           long remainingMs, long turnLimitMs, boolean finished) {}

    public record State(String[][] board, List<String> tray, int score, int combo, boolean trayCleared,
                        int moveNumber, long deadline, long turnLimit, Long pausedRemaining,
                        boolean finished, Move lastMove) {
        long remaining(long now) {
            return finished ? 0 : pausedRemaining != null ? pausedRemaining : Math.max(0, deadline - now);
        }

        State withClock(long deadline, Long pausedRemaining, boolean finished) {
            return new State(board, tray, score, combo, trayCleared, moveNumber, deadline, turnLimit,
                    pausedRemaining, finished, lastMove);
        }

        Snapshot snapshot(String gameId, long now) {
            return new Snapshot(gameId, tray, score, moveNumber, remaining(now), turnLimit, finished);
        }
    }

    private record StoredGame(long userId, State state) {}

    private final JdbcClient jdbc;
    private final JsonMapper json;

    public HardModeGames(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Transactional
    public Snapshot start(long userId) {
        var id = UUID.randomUUID().toString();
        var board = new PlayerBoard();
        var tray = PieceCatalog.nextHardTray(board, ThreadLocalRandom.current());
        long now = System.currentTimeMillis();
        var state = new State(board.cells(), tray, 0, 0, false, 0, now + INITIAL_MS,
                INITIAL_MS, null, false, null);
        jdbc.sql("INSERT INTO hard_mode_games (game_id, user_id, state) VALUES (:id, :userId, :state)")
                .param("id", id).param("userId", userId).param("state", json.writeValueAsString(state)).update();
        return state.snapshot(id, now);
    }

    @Transactional
    public Snapshot place(long userId, String id, Move move) {
        var state = lockGame(userId, id);
        long now = System.currentTimeMillis();
        if (state.finished() || state.remaining(now) == 0) {
            return persist(id, state.withClock(0, null, true));
        }
        if (move.equals(state.lastMove())) return state.snapshot(id, now);
        if (state.pausedRemaining() != null || move.moveNumber() != state.moveNumber()) {
            throw new CreditException(CREDIT_GAME_MOVE_CONFLICT);
        }
        String shape = state.tray().get(move.slot());
        if (shape == null) throw new CreditException(CREDIT_INVALID_GAME_MOVE);

        var board = new PlayerBoard(state.board(), state.score(), state.combo(), state.trayCleared());
        var piece = PieceCatalog.plain(shape, "cyan");
        if (!board.canPlace(piece, move.row(), move.col())) throw new CreditException(CREDIT_INVALID_GAME_MOVE);
        board.place(piece, move.row(), move.col(), SCORE_MULTIPLIER);
        List<String> tray = new ArrayList<>(state.tray());
        tray.set(move.slot(), null);
        if (tray.stream().allMatch(Objects::isNull)) {
            board.onTrayCompleted();
            tray = PieceCatalog.nextHardTray(board, ThreadLocalRandom.current());
        }
        boolean finished = !board.hasMove(tray.stream().filter(Objects::nonNull)
                .map(name -> PieceCatalog.plain(name, "cyan")).toList());
        long decay = board.score() >= 200 ? 200 : board.score() >= 100 ? 100 : 0;
        long limit = Math.max(MINIMUM_MS, state.turnLimit() - decay);
        return persist(id, new State(board.cells(), tray, board.score(), board.combo(), board.clearedThisTray(),
                state.moveNumber() + 1, System.currentTimeMillis() + limit, limit, null, finished, move));
    }

    @Transactional
    public Snapshot setPaused(long userId, String id, boolean paused) {
        var state = lockGame(userId, id);
        long now = System.currentTimeMillis();
        if (state.finished() || state.remaining(now) == 0) {
            return persist(id, state.withClock(0, null, true));
        }
        if (paused == (state.pausedRemaining() != null)) return state.snapshot(id, now);
        long remaining = state.remaining(now);
        return persist(id, paused ? state.withClock(0, remaining, false)
                : state.withClock(now + remaining, null, false));
    }

    @Transactional
    public Snapshot finish(long userId, String id) {
        var state = lockGame(userId, id);
        long now = System.currentTimeMillis();
        if (state.finished()) return state.snapshot(id, now);
        if (state.remaining(now) > 0) throw new CreditException(CREDIT_GAME_NOT_FINISHED);
        return persist(id, state.withClock(0, null, true));
    }

    @Override
    public Optional<RecordedGameResult> findByGameId(String gameId) {
        return jdbc.sql("SELECT user_id, finished, score FROM hard_mode_games WHERE game_id = :id")
                .param("id", gameId).query((rs, index) -> {
                    boolean finished = rs.getBoolean("finished");
                    return new RecordedGameResult(gameId, rs.getString("user_id"), RecordedGameResult.Mode.HARD,
                            finished, finished, rs.getInt("score"));
                }).optional();
    }

    private State lockGame(long userId, String id) {
        var game = jdbc.sql("SELECT user_id, state FROM hard_mode_games WHERE game_id = :id FOR UPDATE")
                .param("id", id)
                .query((rs, index) -> new StoredGame(rs.getLong("user_id"), json.readValue(rs.getString("state"), State.class)))
                .optional().orElseThrow(() -> new CreditException(CREDIT_GAME_NOT_FOUND));
        if (game.userId() != userId) throw new CreditException(CREDIT_GAME_ACCESS_DENIED);
        return game.state();
    }

    private Snapshot persist(String id, State state) {
        jdbc.sql("""
                UPDATE hard_mode_games SET state = :state, score = :score, finished = :finished WHERE game_id = :id
                """).param("state", json.writeValueAsString(state)).param("score", state.score())
                .param("finished", state.finished()).param("id", id).update();
        return state.snapshot(id, System.currentTimeMillis());
    }
}
