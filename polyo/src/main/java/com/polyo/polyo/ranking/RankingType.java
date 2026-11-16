package com.polyo.polyo.ranking;

public enum RankingType {
    NORMAL,
    HARD,
    TROPHY;

    public static RankingType from(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("랭킹 종류는 필수입니다.");
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("지원하지 않는 랭킹 종류입니다. (normal, hard, trophy)");
        }
    }
}
