package com.polyo.polyo.matchroom;

import java.security.Principal;

/**
 * User identity supplied by the authentication integration; contains no credentials. As the security
 * principal, {@link #getName()} is the user ID, so {@code Authentication.getName()} returns it too.
 */
public record MatchRoomUser(String id, String nickname) implements Principal {
    @Override
    public String getName() {
        return id;
    }
}
