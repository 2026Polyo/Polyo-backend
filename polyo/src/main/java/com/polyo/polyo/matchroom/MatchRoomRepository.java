package com.polyo.polyo.matchroom;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface MatchRoomRepository extends JpaRepository<MatchRoom, Long> {
    // Entry and deletion lock the room row (SELECT ... FOR UPDATE), so they run one at a time per room.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from MatchRoom r where r.id = :id")
    Optional<MatchRoom> findByIdForUpdate(long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from MatchRoom r where r.entryCode = :entryCode")
    Optional<MatchRoom> findByEntryCodeForUpdate(String entryCode);

    boolean existsByEntryCode(String entryCode);

    Page<MatchRoom> findByStatusNot(MatchRoomStatus status, Pageable pageable);
}
