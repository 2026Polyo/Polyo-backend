package com.polyo.polyo.matchroom;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;

public record MatchRoomResponse(long roomId, String roomName, String hostUserId,
                                @JsonProperty("isPrivate") boolean isPrivate, String entryCode,
                                int maxPlayers, int playerCount, MatchRoomStatus status,
                                List<MatchRoom.Member> members, Instant createdAt) {
    public static MatchRoomResponse from(MatchRoom room) {
        return new MatchRoomResponse(room.id(), room.roomName(), room.hostUserId(), room.isPrivate(), room.entryCode(),
                MatchRoom.MAX_PLAYERS, room.members().size(), room.status(), room.members(), room.createdAt());
    }
}
