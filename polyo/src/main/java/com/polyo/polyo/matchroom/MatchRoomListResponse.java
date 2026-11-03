package com.polyo.polyo.matchroom;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;

public record MatchRoomListResponse(List<RoomSummary> rooms, int page, int size, long totalElements, int totalPages) {
    public record RoomSummary(long roomId, String roomName, String hostUserId, String hostNickname,
                              @JsonProperty("isPrivate") boolean isPrivate, int maxPlayers, int playerCount,
                              MatchRoomStatus status, Instant createdAt) {
        public static RoomSummary from(MatchRoom room) {
            return new RoomSummary(room.id(), room.roomName(), room.hostUserId(), room.members().getFirst().nickname(),
                    room.isPrivate(), MatchRoom.MAX_PLAYERS, room.members().size(), room.status(), room.createdAt());
        }
    }
}
