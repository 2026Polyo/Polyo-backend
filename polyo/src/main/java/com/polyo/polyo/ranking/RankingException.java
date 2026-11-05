package com.polyo.polyo.ranking;

public class RankingException extends RuntimeException {
    private final String code;
    private final int status;

    public RankingException(String code, String message, int status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String getCode() {
        return code;
    }

    public int getStatus() {
        return status;
    }
}
