package com.polyo.polyo.credit.domain;

/** created=true면 최초 정산(201), false면 기존 정산 재사용(200). */
public record GameRewardResult(GameRewardSettlement settlement, boolean created) {}
