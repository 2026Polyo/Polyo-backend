package com.polyo.polyo.matchroom;

/** Room changes that connected game sockets must hear about. Published inside the changing transaction. */
public final class MatchRoomEvents {
    private MatchRoomEvents() {
    }

    /** The room was deleted while no game was running: by the host's delete or the host leaving. */
    public record Closed(long roomId) {
    }

    /** A guest left the room while no game was running. */
    public record MemberLeft(long roomId, String userId) {
    }
}
