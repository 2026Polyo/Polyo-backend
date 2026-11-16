package com.polyo.polyo.ranking;

import com.polyo.polyo.auth.User;
import com.polyo.polyo.auth.UserRepository;
import com.polyo.polyo.score.GameMode;
import com.polyo.polyo.score.UserGameScore;
import com.polyo.polyo.score.UserGameScoreRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RankingService {
    private final UserRepository userRepository;
    private final UserGameScoreRepository scoreRepository;

    public RankingService(UserRepository userRepository, UserGameScoreRepository scoreRepository) {
        this.userRepository = userRepository;
        this.scoreRepository = scoreRepository;
    }

    @Transactional(readOnly = true)
    public RankingResponse getRanking(String type) {
        RankingType rankingType;
        try {
            rankingType = RankingType.from(type);
        } catch (IllegalArgumentException exception) {
            throw new RankingException("INVALID_RANKING_TYPE", exception.getMessage(), 400);
        }

        return getRanking(rankingType);
    }

    @Transactional(readOnly = true)
    public RankingResponse getRanking(RankingType rankingType) {
        List<RankingEntry> entries = switch (rankingType) {
            case TROPHY -> userRepository.findAll().stream()
                    .sorted(Comparator.comparingInt(User::getTrophy).reversed()
                            .thenComparing(User::getId))
                    .map(user -> new RankingEntry(0, String.valueOf(user.getId()), user.getNickname(), user.getTrophy()))
                    .toList();
            case NORMAL, HARD -> scoreRepository.findAll().stream()
                    .sorted(Comparator.comparingInt((UserGameScore score) -> score.bestScore(
                                    rankingType == RankingType.HARD ? GameMode.HARD : GameMode.NORMAL))
                            .reversed()
                            .thenComparing(UserGameScore::getUserId))
                    .map(score -> new RankingEntry(0, String.valueOf(score.getUserId()),
                            score.getUser().getNickname(), score.bestScore(
                                    rankingType == RankingType.HARD ? GameMode.HARD : GameMode.NORMAL)))
                    .toList();
        };

        List<RankingEntry> ranked = new ArrayList<>(entries.size());
        for (int index = 0; index < entries.size(); index++) {
            RankingEntry entry = entries.get(index);
            ranked.add(new RankingEntry(index + 1, entry.userId(), entry.nickname(), entry.score()));
        }
        return new RankingResponse(rankingType.name().toLowerCase(), ranked);
    }
}
