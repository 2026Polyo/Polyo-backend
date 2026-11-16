package com.polyo.polyo.matchroom;

public class MatchRoomException extends RuntimeException {
    private final String code;
    private final int status;

    public MatchRoomException(String code, String message, int status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String code() { return code; }
    public int status() { return status; }

    public static MatchRoomException notFound() {
        return new MatchRoomException("ROOM_NOT_FOUND", "방을 찾을 수 없습니다.", 404);
    }
}
