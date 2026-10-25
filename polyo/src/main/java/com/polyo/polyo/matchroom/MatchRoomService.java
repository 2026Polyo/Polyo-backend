package com.polyo.polyo.matchroom;

import java.util.Comparator;
import org.springframework.stereotype.Service;

@Service
public class MatchRoomService {
    private final MatchRoomRepository repository;

    public MatchRoomService(MatchRoomRepository repository) {
        this.repository = repository;
    }

    public MatchRoomResponse create(CreateMatchRoomRequest request, MatchRoomUser user) {
        return MatchRoomResponse.from(repository.create(request.roomName(), request.isPrivate(), user));
    }

    public MatchRoomListResponse list(int page, int size) {
        var rooms = repository.findAll().stream()
                .filter(room -> room.status() != MatchRoomStatus.PLAYING)
                .sorted(Comparator.comparing(MatchRoom::createdAt).thenComparingLong(MatchRoom::id).reversed())
                .toList();
        var content = rooms.stream().skip((long) page * size).limit(size)
                .map(MatchRoomListResponse.RoomSummary::from).toList();
        int totalPages = (int) ((rooms.size() + (long) size - 1) / size);
        return new MatchRoomListResponse(content, page, size, rooms.size(), totalPages);
    }

    public MatchRoomResponse joinById(long roomId, MatchRoomUser user) {
        return MatchRoomResponse.from(repository.joinById(roomId, user));
    }

    public MatchRoomResponse joinByCode(JoinByCodeRequest request, MatchRoomUser user) {
        return MatchRoomResponse.from(repository.joinByCode(request.entryCode(), user));
    }

    public void delete(long roomId, MatchRoomUser user) {
        repository.delete(roomId, user);
    }
}
