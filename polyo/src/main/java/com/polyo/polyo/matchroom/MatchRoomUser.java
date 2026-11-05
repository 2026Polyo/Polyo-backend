package com.polyo.polyo.matchroom;

import java.security.Principal;

/** User identity supplied by the authentication integration; contains no credentials. */
public record MatchRoomUser(String id, String nickname) implements Principal {
    /** Keeps Authentication.getName() compatible with APIs that read the numeric user ID. */
    @Override
    public String getName() {
        return id;
    }
}
