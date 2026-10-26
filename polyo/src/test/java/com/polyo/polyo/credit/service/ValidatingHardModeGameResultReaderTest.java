package com.polyo.polyo.credit.service;

import static com.polyo.polyo.common.error.CommonErrorCode.*;
import static com.polyo.polyo.credit.domain.RecordedGameResult.Mode.*;
import static com.polyo.polyo.credit.exception.CreditErrorCode.*;

import static org.junit.jupiter.api.Assertions.*;

import com.polyo.polyo.common.error.ApiException;
import com.polyo.polyo.credit.domain.RecordedGameResult;
import com.polyo.polyo.credit.exception.CreditErrorCode;
import com.polyo.polyo.credit.exception.CreditException;

import org.junit.jupiter.api.Test;

import java.util.Optional;

class ValidatingHardModeGameResultReaderTest {
    private RecordedGameResult game(
            String id,
            String user,
            RecordedGameResult.Mode mode,
            boolean finished,
            boolean verified,
            long score) {
        return new RecordedGameResult(id, user, mode, finished, verified, score);
    }

    private void rejects(RecordedGameResult game, CreditErrorCode code) {
        var reader = new ValidatingHardModeGameResultReader(id -> Optional.of(game));
        assertEquals(
                code,
                assertThrows(
                                CreditException.class,
                                () -> reader.readVerifiedResult("user_1", "game_1"))
                        .errorCode());
    }

    @Test
    void returnsOnlyAnOwnedFinishedVerifiedHardModeResult() {
        var reader =
                new ValidatingHardModeGameResultReader(
                        id -> Optional.of(game(id, "user_1", HARD, true, true, 5000)));
        var result = reader.readVerifiedResult("user_1", "game_1");
        assertEquals("game_1", result.gameId());
        assertEquals("user_1", result.userId());
        assertEquals(5000, result.score());
    }

    @Test
    void normalModeCannotEarnCoinsEvenWithHighScore() {
        rejects(
                game("game_1", "user_1", NORMAL, true, true, Long.MAX_VALUE),
                CREDIT_GAME_NOT_ELIGIBLE);
    }

    @Test
    void anotherUsersGameIsDeniedBeforeItsStatusIsDisclosed() {
        rejects(game("game_1", "user_2", NORMAL, false, false, 0), CREDIT_GAME_ACCESS_DENIED);
    }

    @Test
    void unfinishedGamesAreRejected() {
        rejects(game("game_1", "user_1", HARD, false, true, 5000), CREDIT_GAME_NOT_FINISHED);
    }

    @Test
    void unverifiedResultsAreRejected() {
        rejects(game("game_1", "user_1", HARD, true, false, 5000), CREDIT_GAME_RESULT_NOT_VERIFIED);
    }

    @Test
    void missingGameIsReported() {
        var reader = new ValidatingHardModeGameResultReader(id -> Optional.empty());
        assertEquals(
                CREDIT_GAME_NOT_FOUND,
                assertThrows(
                                CreditException.class,
                                () -> reader.readVerifiedResult("user_1", "game_1"))
                        .errorCode());
    }

    @Test
    void malformedSourceRecordsAreRejected() {
        rejects(game("wrong_game", "user_1", HARD, true, true, 5000), CREDIT_INVALID_GAME_RESULT);
        rejects(game("game_1", "user_1", null, true, true, 5000), CREDIT_INVALID_GAME_RESULT);
        rejects(game("game_1", "user_1", HARD, true, true, -1), CREDIT_INVALID_GAME_RESULT);
    }

    @Test
    void invalidIdentityOrInputDoesNotCallTheSource() {
        var reader =
                new ValidatingHardModeGameResultReader(
                        id -> {
                            fail("Source must not be called");
                            return Optional.empty();
                        });
        assertEquals(
                AUTH_UNAUTHORIZED,
                assertThrows(ApiException.class, () -> reader.readVerifiedResult(null, "game_1"))
                        .errorCode());
        assertEquals(
                INVALID_REQUEST_BODY,
                assertThrows(ApiException.class, () -> reader.readVerifiedResult("user_1", " "))
                        .errorCode());
    }
}
