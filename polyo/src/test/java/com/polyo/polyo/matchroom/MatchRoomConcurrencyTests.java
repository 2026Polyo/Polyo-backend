package com.polyo.polyo.matchroom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.polyo.polyo.TestcontainersConfiguration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class MatchRoomConcurrencyTests {
    private final MatchRoomUser host = user("1");
    private final MatchRoomUser guest = user("2");
    private final MatchRoomUser third = user("3");

    @Autowired MatchRoomService service;
    @Autowired MatchRoomRepository repository;
    @MockitoSpyBean EntryCodeGenerator codeGenerator;

    @BeforeEach
    void removeRooms() {
        repository.deleteAll();
    }

    @Test
    void simultaneousIdAndCodeJoinsOnlyAddOneNewPlayer() throws Exception {
        for (int attempt = 0; attempt < 30; attempt++) {
            MatchRoomResponse room = create("동시 입장", false, host);
            List<String> outcomes = race(
                    () -> outcome(() -> service.joinById(room.roomId(), guest)),
                    () -> outcome(() -> service.joinByCode(new JoinByCodeRequest(room.entryCode()), third)));
            assertThat(outcomes).containsExactlyInAnyOrder("OK", "ROOM_FULL");
            MatchRoomResponse stored = service.joinById(room.roomId(), host);
            assertThat(stored.members()).hasSize(2);
            assertThat(stored.status()).isEqualTo(MatchRoomStatus.FULL);
        }
    }

    @Test
    void simultaneousDuplicateRequestsDoNotCreateDuplicateParticipants() throws Exception {
        MatchRoomResponse room = create("중복 입장", false, host);
        List<String> outcomes = race(
                () -> outcome(() -> service.joinById(room.roomId(), guest)),
                () -> outcome(() -> service.joinByCode(new JoinByCodeRequest(room.entryCode()), guest)));
        assertThat(outcomes).containsExactly("OK", "OK");
        assertThat(service.joinById(room.roomId(), host).members()).hasSize(2);
    }

    @Test
    void deletionAndCodeJoinCannotLeaveADeletedRoomOrCodeBehind() throws Exception {
        for (int attempt = 0; attempt < 30; attempt++) {
            MatchRoomResponse room = create("삭제 경합", false, host);
            List<String> outcomes = race(
                    () -> outcome(() -> service.delete(room.roomId(), host)),
                    () -> outcome(() -> service.joinByCode(new JoinByCodeRequest(room.entryCode()), guest)));
            assertThat(outcomes.getFirst()).isEqualTo("OK");
            assertThat(outcomes.get(1)).isIn("OK", "ROOM_NOT_FOUND");
            assertThat(repository.count()).isZero();
            assertThatThrownBy(() -> service.joinByCode(new JoinByCodeRequest(room.entryCode()), guest))
                    .isInstanceOf(MatchRoomException.class).hasMessage("방을 찾을 수 없습니다.");
        }
    }

    @Test
    void codeCollisionRetriesWithoutReplacingTheExistingRoom() {
        when(codeGenerator.generate()).thenReturn("ABCDEFGH", "ABCDEFGH", "HJKLMNPQ");
        MatchRoomResponse first = create("첫 방", true, host);
        MatchRoomResponse second = create("둘째 방", true, guest);
        assertThat(first.entryCode()).isEqualTo("ABCDEFGH");
        assertThat(second.entryCode()).isEqualTo("HJKLMNPQ");
        assertThat(service.joinByCode(new JoinByCodeRequest(first.entryCode()), host).roomId()).isEqualTo(first.roomId());
        assertThat(service.joinByCode(new JoinByCodeRequest(second.entryCode()), guest).roomId()).isEqualTo(second.roomId());
    }

    @Test
    void playingRoomRejectsNewUsersAndDeletionButAllowsExistingMembers() {
        MatchRoom room = new MatchRoom("진행 중", host.id(), false, "ABCDEFGH", MatchRoomStatus.PLAYING,
                List.of(new MatchRoom.Member(host.id(), host.nickname()), new MatchRoom.Member(guest.id(), guest.nickname())),
                Instant.now());
        assertThat(room.join(host, false)).isSameAs(room);
        assertThatThrownBy(() -> room.join(third, true)).isInstanceOf(MatchRoomException.class)
                .hasMessage("이미 게임이 시작된 방입니다.");
        assertThatThrownBy(() -> room.checkDeletableBy(host)).isInstanceOf(MatchRoomException.class)
                .hasMessage("이미 게임이 시작된 방입니다.");
    }

    private MatchRoomResponse create(String roomName, boolean isPrivate, MatchRoomUser user) {
        return service.create(new CreateMatchRoomRequest(roomName, isPrivate), user);
    }

    private List<String> race(Callable<String> first, Callable<String> second) throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            Future<String> firstResult = executor.submit(() -> { ready.countDown(); start.await(); return first.call(); });
            Future<String> secondResult = executor.submit(() -> { ready.countDown(); start.await(); return second.call(); });
            try {
                assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            } finally {
                start.countDown();
            }
            return List.of(firstResult.get(5, TimeUnit.SECONDS), secondResult.get(5, TimeUnit.SECONDS));
        }
    }

    private String outcome(Runnable operation) {
        try {
            operation.run();
            return "OK";
        } catch (MatchRoomException exception) {
            return exception.code();
        }
    }

    private static MatchRoomUser user(String id) {
        return new MatchRoomUser(id, "유저" + id);
    }
}
