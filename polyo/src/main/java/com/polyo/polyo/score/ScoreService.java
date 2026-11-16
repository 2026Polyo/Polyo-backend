package com.polyo.polyo.score;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ScoreService {
    private final UserGameScoreRepository scoreRepository;

    public ScoreService(UserGameScoreRepository scoreRepository) {
        this.scoreRepository = scoreRepository;
    }

    @Transactional
    public ScoreResponse saveBestScore(Long userId, SaveScoreRequest request) {
        GameMode mode;
        try {
            mode = GameMode.from(request.gameMode());
        } catch (IllegalArgumentException exception) {
            throw new ScoreException("INVALID_GAME_MODE", exception.getMessage(), 400);
        }

        UserGameScore score = scoreRepository.findByUserId(userId)
                .orElseGet(() -> new UserGameScore(userId));
        score.updateIfHigher(mode, request.score());
        UserGameScore saved = scoreRepository.save(score);
        return new ScoreResponse(request.gameMode().toLowerCase(), saved.bestScore(mode));
    }

    @Transactional
    public BestScoresResponse getBestScores(Long userId) {
        UserGameScore score = scoreRepository.findByUserId(userId)
                .orElseGet(() -> scoreRepository.save(new UserGameScore(userId)));
        return new BestScoresResponse(score.getNormalBestScore(), score.getHardBestScore());
    }
}
