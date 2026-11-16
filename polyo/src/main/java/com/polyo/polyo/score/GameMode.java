package com.polyo.polyo.score;

public enum GameMode {
    NORMAL,
    HARD;

    public static GameMode from(String value) {
        return switch (value.toLowerCase()) {
            case "normal" -> NORMAL;
            case "hard" -> HARD;
            default -> throw new IllegalArgumentException("지원하지 않는 게임 모드입니다.");
        };
    }
}
