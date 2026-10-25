package com.polyo.polyo.matchroom;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Repository;

@Repository
public class MatchRoomRepository {
    private final AtomicLong sequence = new AtomicLong();
    private final ConcurrentHashMap<Long, MatchRoom> rooms = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> roomIdsByCode = new ConcurrentHashMap<>();
    private final EntryCodeGenerator codeGenerator;

    public MatchRoomRepository(EntryCodeGenerator codeGenerator) {
        this.codeGenerator = codeGenerator;
    }

    public MatchRoom create(String roomName, boolean isPrivate, MatchRoomUser host) {
        long id = sequence.incrementAndGet();
        for (int attempt = 0; attempt < 10; attempt++) {
            String code = codeGenerator.generate();
            if (roomIdsByCode.putIfAbsent(code, id) == null) {
                MatchRoom room = MatchRoom.create(id, roomName, isPrivate, code, host);
                rooms.put(id, room);
                return room;
            }
        }
        throw new IllegalStateException("Could not allocate a unique room code");
    }

    public List<MatchRoom> findAll() {
        return List.copyOf(rooms.values());
    }

    public MatchRoom joinById(long roomId, MatchRoomUser user) {
        return join(roomId, user, false);
    }

    public MatchRoom joinByCode(String code, MatchRoomUser user) {
        Long roomId = roomIdsByCode.get(code);
        if (roomId == null) {
            throw MatchRoomException.notFound();
        }
        return join(roomId, user, true);
    }

    private MatchRoom join(long roomId, MatchRoomUser user, boolean codeProvided) {
        // Both entry routes and deletion serialize on the same room key.
        return rooms.compute(roomId, (id, room) -> {
            if (room == null) {
                throw MatchRoomException.notFound();
            }
            return room.join(user, codeProvided);
        });
    }

    public void delete(long roomId, MatchRoomUser user) {
        rooms.compute(roomId, (id, room) -> {
            if (room == null) {
                throw MatchRoomException.notFound();
            }
            room.checkDeletableBy(user);
            roomIdsByCode.remove(room.entryCode(), id);
            return null;
        });
    }
}
