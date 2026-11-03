package com.polyo.polyo.matchroom;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.hibernate.annotations.BatchSize;

@Entity
@Table(name = "match_room")
public class MatchRoom {
    public static final int MAX_PLAYERS = 2;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_name", nullable = false, length = 30)
    private String roomName;

    @Column(name = "host_user_id", nullable = false, length = 64)
    private String hostUserId;

    @Column(name = "is_private", nullable = false)
    private boolean isPrivate;

    @Column(name = "entry_code", nullable = false, unique = true, length = 8)
    private String entryCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MatchRoomStatus status;

    @ElementCollection
    @CollectionTable(name = "match_room_member", joinColumns = @JoinColumn(name = "room_id"))
    @OrderColumn(name = "join_order")
    @BatchSize(size = 100)
    private List<Member> members = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected MatchRoom() {
    }

    MatchRoom(String roomName, String hostUserId, boolean isPrivate, String entryCode, MatchRoomStatus status,
              List<Member> members, Instant createdAt) {
        this.roomName = roomName;
        this.hostUserId = hostUserId;
        this.isPrivate = isPrivate;
        this.entryCode = entryCode;
        this.status = status;
        this.members = new ArrayList<>(members);
        this.createdAt = createdAt;
    }

    public static MatchRoom create(String roomName, boolean isPrivate, String entryCode, MatchRoomUser host) {
        // PostgreSQL stores microseconds, so the response matches what is read back later.
        return new MatchRoom(roomName, host.id(), isPrivate, entryCode, MatchRoomStatus.WAITING,
                List.of(new Member(host.id(), host.nickname())), Instant.now().truncatedTo(ChronoUnit.MICROS));
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
        members.add(new Member(user.id(), user.nickname()));
        status = MatchRoomStatus.FULL;
        return this;
    }

    public void checkDeletableBy(MatchRoomUser user) {
        if (!hostUserId.equals(user.id())) {
            throw new MatchRoomException("ROOM_HOST_ONLY", "방장만 방을 삭제할 수 있습니다.", 403);
        }
        if (status == MatchRoomStatus.PLAYING) {
            throw new MatchRoomException("MATCH_ALREADY_STARTED", "이미 게임이 시작된 방입니다.", 409);
        }
    }

    public long id() { return id; }
    public String roomName() { return roomName; }
    public String hostUserId() { return hostUserId; }
    public boolean isPrivate() { return isPrivate; }
    public String entryCode() { return entryCode; }
    public MatchRoomStatus status() { return status; }
    public List<Member> members() { return Collections.unmodifiableList(members); }
    public Instant createdAt() { return createdAt; }

    @Embeddable
    public record Member(
            @Column(name = "user_id", nullable = false, length = 64) String userId,
            @Column(nullable = false, length = 50) String nickname) {
    }
}
