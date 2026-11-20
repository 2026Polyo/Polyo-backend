package com.polyo.polyo.game;

import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Gives trophies for match wins. Trophies only go up: a loss costs nothing. */
@Service
public class TrophyService {
    public static final int TROPHIES_PER_WIN = 30;

    private final JdbcClient jdbc;

    public TrophyService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** What a win gave: the trophies gained and the winner's total after it. */
    public record Reward(String userId, int gained, int trophy) {
    }

    /**
     * Adds {@link #TROPHIES_PER_WIN} to the winner in one statement, so wins finishing at once in
     * different rooms cannot overwrite each other. Empty when no such user is stored.
     */
    @Transactional
    public Optional<Reward> awardWin(String userId) {
        long id;
        try {
            id = Long.parseLong(userId);
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
        return jdbc.sql("""
                UPDATE users SET trophy = LEAST(trophy::bigint + :gain, 2147483647)
                WHERE id = :userId RETURNING trophy
                """).param("gain", TROPHIES_PER_WIN).param("userId", id)
                .query(Integer.class).optional()
                .map(trophy -> new Reward(userId, TROPHIES_PER_WIN, trophy));
    }
}
