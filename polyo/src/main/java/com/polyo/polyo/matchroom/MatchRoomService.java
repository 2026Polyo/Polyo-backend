package com.polyo.polyo.matchroom;

import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MatchRoomService {
    private static final int CODE_ATTEMPTS = 10;

    private final MatchRoomRepository repository;
    private final EntryCodeGenerator codeGenerator;

    public MatchRoomService(MatchRoomRepository repository, EntryCodeGenerator codeGenerator) {
        this.repository = repository;
        this.codeGenerator = codeGenerator;
    }

    @Transactional
    public MatchRoomResponse create(CreateMatchRoomRequest request, MatchRoomUser user) {
        MatchRoom room = MatchRoom.create(request.roomName(), request.isPrivate(), allocateEntryCode(), user);
        return MatchRoomResponse.from(repository.save(room));
    }

    @Transactional(readOnly = true)
    public MatchRoomListResponse list(int page, int size) {
        var sort = Sort.by(Sort.Direction.DESC, "createdAt", "id");
        var rooms = repository.findByStatusNot(MatchRoomStatus.PLAYING, PageRequest.of(page, size, sort));
        var content = rooms.getContent().stream().map(MatchRoomListResponse.RoomSummary::from).toList();
        return new MatchRoomListResponse(content, page, size, rooms.getTotalElements(), rooms.getTotalPages());
    }

    @Transactional
    public MatchRoomResponse joinById(long roomId, MatchRoomUser user) {
        MatchRoom room = repository.findByIdForUpdate(roomId).orElseThrow(MatchRoomException::notFound);
        return MatchRoomResponse.from(room.join(user, false));
    }

    @Transactional
    public MatchRoomResponse joinByCode(JoinByCodeRequest request, MatchRoomUser user) {
        MatchRoom room = repository.findByEntryCodeForUpdate(request.entryCode())
                .orElseThrow(MatchRoomException::notFound);
        return MatchRoomResponse.from(room.join(user, true));
    }

    @Transactional
    public void delete(long roomId, MatchRoomUser user) {
        MatchRoom room = repository.findByIdForUpdate(roomId).orElseThrow(MatchRoomException::notFound);
        room.checkDeletableBy(user);
        repository.delete(room);
    }

    /** The room's members, after checking that {@code user} is one of them. */
    @Transactional(readOnly = true)
    public List<MatchRoom.Member> membersOf(long roomId, MatchRoomUser user) {
        MatchRoom room = repository.findById(roomId).orElseThrow(MatchRoomException::notFound);
        List<MatchRoom.Member> members = List.copyOf(room.members());
        if (members.stream().noneMatch(member -> member.userId().equals(user.id()))) {
            throw new MatchRoomException("ROOM_MEMBER_ONLY", "방 참가자만 접속할 수 있습니다.", 403);
        }
        return members;
    }

    @Transactional
    public void markPlaying(long roomId) {
        repository.findByIdForUpdate(roomId).orElseThrow(MatchRoomException::notFound).start();
    }

    /** A finished room is removed: matches are one game long and results are not stored yet. */
    @Transactional
    public void finish(long roomId) {
        repository.findByIdForUpdate(roomId).ifPresent(repository::delete);
    }

    private String allocateEntryCode() {
        for (int attempt = 0; attempt < CODE_ATTEMPTS; attempt++) {
            String code = codeGenerator.generate();
            if (!repository.existsByEntryCode(code)) {
                return code;
            }
        }
        throw new IllegalStateException("Could not allocate a unique room code");
    }
}
