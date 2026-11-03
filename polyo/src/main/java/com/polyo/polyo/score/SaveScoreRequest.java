package com.polyo.polyo.score;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record SaveScoreRequest(
        @NotBlank(message = "게임 모드는 필수입니다.") String gameMode,
        @Min(value = 0, message = "점수는 0 이상이어야 합니다.") int score) {
}
