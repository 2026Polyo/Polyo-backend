package com.polyo.polyo.credit.web;

import com.polyo.polyo.common.api.ApiSuccess;
import com.polyo.polyo.credit.port.AuthenticatedUserProvider;
import com.polyo.polyo.game.HardModeGames;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/credit/v11/hard-games")
public class HardModeGameController {
    public record Placement(@NotNull @Min(0) Integer moveNumber,
                            @NotNull @Min(0) @Max(2) Integer slot,
                            @NotNull @Min(0) @Max(7) Integer row,
                            @NotNull @Min(0) @Max(7) Integer col) {}
    public record Pause(@NotNull Boolean paused) {}

    private final HardModeGames games;
    private final AuthenticatedUserProvider users;

    public HardModeGameController(HardModeGames games, AuthenticatedUserProvider users) {
        this.games = games;
        this.users = users;
    }

    @PostMapping
    @Operation(summary = "보상 대상 하드모드 시작")
    public ApiSuccess<HardModeGames.Snapshot> start() {
        return ApiSuccess.of(games.start(userId()));
    }

    @PostMapping("/{gameId}/moves")
    @Operation(summary = "하드모드 블록 배치", description = "점수와 남은 시간은 서버가 계산한다.")
    public ApiSuccess<HardModeGames.Snapshot> place(@PathVariable String gameId, @Valid @RequestBody Placement body) {
        return ApiSuccess.of(games.place(userId(), gameId,
                new HardModeGames.Move(body.moveNumber(), body.slot(), body.row(), body.col())));
    }

    @PutMapping("/{gameId}/timer")
    @Operation(summary = "하드모드 타이머 일시 정지 또는 재개")
    public ApiSuccess<HardModeGames.Snapshot> pause(@PathVariable String gameId, @Valid @RequestBody Pause body) {
        return ApiSuccess.of(games.setPaused(userId(), gameId, body.paused()));
    }

    @PutMapping("/{gameId}/completion")
    @Operation(summary = "하드모드 종료 확인", description = "둘 수 있는 블록이 없거나 제한 시간이 지난 게임만 종료한다.")
    public ApiSuccess<HardModeGames.Snapshot> finish(@PathVariable String gameId) {
        return ApiSuccess.of(games.finish(userId(), gameId));
    }

    private long userId() {
        return Long.parseLong(users.requireUserId());
    }
}
