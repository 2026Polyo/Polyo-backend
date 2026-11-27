package com.polyo.polyo.matchroom.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.polyo.polyo.matchroom.dto.MatchRoomUser;
import com.polyo.polyo.matchroom.service.MatchRoomException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class MatchRoomTests {
    private final MatchRoomUser host = user("1");
    private final MatchRoomUser guest = user("2");
    private final MatchRoomUser third = user("3");

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

    private static MatchRoomUser user(String id) {
        return new MatchRoomUser(id, "유저" + id);
    }
}
