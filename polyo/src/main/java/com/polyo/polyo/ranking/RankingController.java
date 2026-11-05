package com.polyo.polyo.ranking;

import com.polyo.polyo.common.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/v1/rankings", "/api/v1/ranking"})
public class RankingController {
    private final RankingService rankingService;

    public RankingController(RankingService rankingService) {
        this.rankingService = rankingService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<RankingResponse>> getRanking(
            @RequestParam(name = "type", required = false) String type,
            @RequestParam(name = "mode", required = false) String mode) {
        String requestedType = type == null ? mode : type;
        return ResponseEntity.ok(ApiResponse.success(rankingService.getRanking(requestedType)));
    }

    @GetMapping("/{type}")
    public ResponseEntity<ApiResponse<RankingResponse>> getRankingByPath(
            @org.springframework.web.bind.annotation.PathVariable String type) {
        return ResponseEntity.ok(ApiResponse.success(rankingService.getRanking(type)));
    }
}
