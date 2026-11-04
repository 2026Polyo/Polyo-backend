package com.polyo.polyo.score;

import com.polyo.polyo.auth.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/scores")
public class ScoreController {
    private final ScoreService scoreService;

    public ScoreController(ScoreService scoreService) {
        this.scoreService = scoreService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ScoreResponse>> saveScore(
            Authentication authentication, @Valid @RequestBody SaveScoreRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                scoreService.saveBestScore(Long.valueOf(authentication.getName()), request)));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<BestScoresResponse>> getBestScores(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(
                scoreService.getBestScores(Long.valueOf(authentication.getName()))));
    }
}
