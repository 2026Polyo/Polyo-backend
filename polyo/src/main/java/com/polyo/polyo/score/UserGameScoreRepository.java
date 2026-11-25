package com.polyo.polyo.score;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UserGameScoreRepository extends JpaRepository<UserGameScore, Long> {
    Optional<UserGameScore> findByUserId(Long userId);

    @Query("select score from UserGameScore score order by score.normalBestScore desc, score.userId asc")
    List<UserGameScore> findAllRankedByNormalScore();

    @Query("select score from UserGameScore score order by score.hardBestScore desc, score.userId asc")
    List<UserGameScore> findAllRankedByHardScore();
}
