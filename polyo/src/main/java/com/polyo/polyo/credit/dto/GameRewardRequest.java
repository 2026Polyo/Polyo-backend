package com.polyo.polyo.credit.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;

/** 사용자·점수·지급량은 요청에서 받지 않고 서버에서 확인한다. */
public record GameRewardRequest(
        @NotBlank @Schema(description = "서버가 발급한 게임 외부 식별자", example = "game_example")
                String gameId) {}
