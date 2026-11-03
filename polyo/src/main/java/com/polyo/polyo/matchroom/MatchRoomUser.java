package com.polyo.polyo.matchroom;

/** User identity supplied by the authentication integration; contains no credentials. */
public record MatchRoomUser(String id, String nickname) {
}
