package com.polyo.polyo.matchroom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class MatchRoomConcurrencyTests {
    private final MatchRoomUser host = user("1");
    private final MatchRoomUser guest = user("2");
    private final MatchRoomUser third = user("3");

    @Test
    void simultaneousIdAndCodeJoinsOnlyAddOneNewPlayer() throws Exception {
        MatchRoomRepository repository = new MatchRoomRepository(new EntryCodeGenerator());
        for (int attempt = 0; attempt < 30; attempt++) {
            MatchRoom room = repository.create("동시 입장", false, host);
            List<String> outcomes = race(
                    () -> outcome(() -> repository.joinById(room.id(), guest)),
                    () -> outcome(() -> repository.joinByCode(room.entryCode(), third)));
            assertThat(outcomes).containsExactlyInAnyOrder("OK", "ROOM_FULL");
            MatchRoom stored = repository.joinById(room.id(), host);
            assertThat(stored.members()).hasSize(2);
            assertThat(stored.status()).isEqualTo(MatchRoomStatus.FULL);
        }
    }

    @Test
    void simultaneousDuplicateRequestsDoNotCreateDuplicateParticipants() throws Exception {
        MatchRoomRepository repository = new MatchRoomRepository(new EntryCodeGenerator());
        MatchRoom room = repository.create("중복 입장", false, host);
        List<String> outcomes = race(
                () -> outcome(() -> repository.joinById(room.id(), guest)),
                () -> outcome(() -> repository.joinByCode(room.entryCode(), guest)));
        assertThat(outcomes).containsExactly("OK", "OK");
        assertThat(repository.joinById(room.id(), host).members()).hasSize(2);
    }

    @Test
    void deletionAndCodeJoinCannotLeaveADeletedRoomOrCodeBehind() throws Exception {
        MatchRoomRepository repository = new MatchRoomRepository(new EntryCodeGenerator());
        for (int attempt = 0; attempt < 30; attempt++) {
            MatchRoom room = repository.create("삭제 경합", false, host);
            List<String> outcomes = race(
                    () -> outcome(() -> repository.delete(room.id(), host)),
                    () -> outcome(() -> repository.joinByCode(room.entryCode(), guest)));
            assertThat(outcomes.getFirst()).isEqualTo("OK");
            assertThat(outcomes.get(1)).isIn("OK", "ROOM_NOT_FOUND");
            assertThat(repository.findAll()).isEmpty();
            assertThatThrownBy(() -> repository.joinByCode(room.entryCode(), guest))
                    .isInstanceOf(MatchRoomException.class).hasMessage("방을 찾을 수 없습니다.");
        }
    }

    @Test
    void codeCollisionRetriesWithoutReplacingTheExistingRoom() {
        EntryCodeGenerator generator = mock(EntryCodeGenerator.class);
        when(generator.generate()).thenReturn("ABCDEFGH", "ABCDEFGH", "HJKLMNPQ");
        MatchRoomRepository repository = new MatchRoomRepository(generator);
        MatchRoom first = repository.create("첫 방", true, host);
        MatchRoom second = repository.create("둘째 방", true, guest);
        assertThat(first.entryCode()).isEqualTo("ABCDEFGH");
        assertThat(second.entryCode()).isEqualTo("HJKLMNPQ");
        assertThat(repository.joinByCode(first.entryCode(), host).id()).isEqualTo(first.id());
        assertThat(repository.joinByCode(second.entryCode(), guest).id()).isEqualTo(second.id());
    }

    @Test
    void playingRoomRejectsNewUsersAndDeletionButAllowsExistingMembers() {
        MatchRoom room = new MatchRoom(1, "진행 중", host.id(), false, "ABCDEFGH", MatchRoomStatus.PLAYING,
                List.of(new MatchRoom.Member(host.id(), host.nickname()), new MatchRoom.Member(guest.id(), guest.nickname())),
                Instant.now());
        assertThat(room.join(host, false)).isSameAs(room);
        assertThatThrownBy(() -> room.join(third, true)).isInstanceOf(MatchRoomException.class)
                .hasMessage("이미 게임이 시작된 방입니다.");
        assertThatThrownBy(() -> room.checkDeletableBy(host)).isInstanceOf(MatchRoomException.class)
                .hasMessage("이미 게임이 시작된 방입니다.");
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
