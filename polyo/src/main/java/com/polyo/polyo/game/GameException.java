package com.polyo.polyo.game;

public class GameException extends RuntimeException {
    private final String code;

    public GameException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
