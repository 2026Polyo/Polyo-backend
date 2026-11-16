package com.polyo.polyo.score;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserGameScoreRepository extends JpaRepository<UserGameScore, Long> {
    Optional<UserGameScore> findByUserId(Long userId);
}
