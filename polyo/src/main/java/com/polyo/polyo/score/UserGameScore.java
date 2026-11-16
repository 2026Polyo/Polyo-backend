package com.polyo.polyo.score;

import com.polyo.polyo.auth.User;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import lombok.Getter;

@Entity
@Getter
@Table(name = "user_game_scores")
public class UserGameScore {
    @Id
    @Column(name = "user_id")
    private Long userId;

    @OneToOne
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;

    @Column(name = "normal_best_score", nullable = false)
    private int normalBestScore;

    @Column(name = "hard_best_score", nullable = false)
    private int hardBestScore;

    protected UserGameScore() {
    }

    public UserGameScore(Long userId) {
        this.userId = userId;
    }

    public int bestScore(GameMode mode) {
        return mode == GameMode.HARD ? hardBestScore : normalBestScore;
    }

    public boolean updateIfHigher(GameMode mode, int score) {
        if (mode == GameMode.HARD && score > hardBestScore) {
            hardBestScore = score;
            return true;
        }
        if (mode == GameMode.NORMAL && score > normalBestScore) {
            normalBestScore = score;
            return true;
        }
        return false;
    }
}
