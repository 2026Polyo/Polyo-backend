package com.polyo.polyo.matchroom;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public record MatchRoom(long id, String roomName, String hostUserId, boolean isPrivate,
                        String entryCode, MatchRoomStatus status, List<Member> members, Instant createdAt) {
    public static final int MAX_PLAYERS = 2;

    public MatchRoom {
        members = List.copyOf(members);
    }

    public static MatchRoom create(long id, String roomName, boolean isPrivate, String entryCode, MatchRoomUser host) {
        return new MatchRoom(id, roomName, host.id(), isPrivate, entryCode, MatchRoomStatus.WAITING,
                List.of(new Member(host.id(), host.nickname())), Instant.now());
    }

    public MatchRoom join(MatchRoomUser user, boolean codeProvided) {
        if (members.stream().anyMatch(member -> member.userId().equals(user.id()))) {
            return this;
        }
        if (isPrivate && !codeProvided) {
            throw new MatchRoomException("ROOM_CODE_REQUIRED", "비공개 방은 입장 코드가 필요합니다.", 403);
        }
        if (status == MatchRoomStatus.PLAYING) {
            throw new MatchRoomException("MATCH_ALREADY_STARTED", "이미 게임이 시작된 방입니다.", 409);
        }
        if (members.size() >= MAX_PLAYERS) {
            throw new MatchRoomException("ROOM_FULL", "방 정원이 가득 찼습니다.", 409);
        }
        List<Member> updatedMembers = new ArrayList<>(members);
        updatedMembers.add(new Member(user.id(), user.nickname()));
        return new MatchRoom(id, roomName, hostUserId, isPrivate, entryCode, MatchRoomStatus.FULL,
                updatedMembers, createdAt);
    }

    public void checkDeletableBy(MatchRoomUser user) {
        if (!hostUserId.equals(user.id())) {
            throw new MatchRoomException("ROOM_HOST_ONLY", "방장만 방을 삭제할 수 있습니다.", 403);
        }
        if (status == MatchRoomStatus.PLAYING) {
            throw new MatchRoomException("MATCH_ALREADY_STARTED", "이미 게임이 시작된 방입니다.", 409);
        }
    }

    public record Member(String userId, String nickname) {
    }
}
