package com.polyo.polyo.matchroom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import com.polyo.polyo.TestcontainersConfiguration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class MatchRoomApiTests {
    private static final AtomicInteger SEQUENCE = new AtomicInteger();
    private static final String ROOT = "/api/match-room";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired MatchRoomRepository rooms;

    @BeforeEach
    void removeRooms() {
        rooms.deleteAll();
    }

    @Test
    void publicRoomLifecycleUsesAuthenticatedPrincipal() throws Exception {
        Client host = authenticatedUser();
        Client guest = authenticatedUser();
        Client third = authenticatedUser();
        JsonNode room = createRoom(host, "  같이 플레이해요  ", false);
        long id = room.path("roomId").asLong();
        String code = room.path("entryCode").asText();
        assertThat(room.path("roomName").asText()).isEqualTo("같이 플레이해요");
        assertThat(room.path("hostUserId").asText()).isEqualTo(host.id());
        assertThat(room.path("isPrivate").asBoolean()).isFalse();
        assertThat(room.path("maxPlayers").asInt()).isEqualTo(2);
        assertThat(room.path("playerCount").asInt()).isEqualTo(1);
        assertThat(room.path("status").asText()).isEqualTo("WAITING");
        assertThat(room.path("members").size()).isEqualTo(1);
        assertThat(code).matches("[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{8}");
        assertThat(rooms.existsById(id)).isTrue();
        assertThat(rooms.existsByEntryCode(code)).isTrue();

        mvc.perform(post(ROOT + "/{id}/members", id).with(guest.authentication()).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.playerCount").value(2))
                .andExpect(jsonPath("$.data.status").value("FULL"))
                .andExpect(jsonPath("$.data.members[1].userId").value(guest.id()));
        mvc.perform(post(ROOT + "/{id}/members", id).with(guest.authentication()).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.members.length()").value(2));
        mvc.perform(post(ROOT + "/{id}/members", id).with(third.authentication()).with(csrf()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("ROOM_FULL"));
        mvc.perform(delete(ROOT + "/{id}", id).with(guest.authentication()).with(csrf()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("ROOM_HOST_ONLY"));
        mvc.perform(delete(ROOT + "/{id}", id).with(host.authentication()).with(csrf()))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        assertThat(rooms.existsById(id)).isFalse();
        mvc.perform(post(ROOT + "/{id}/members", id).with(guest.authentication()).with(csrf()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("ROOM_NOT_FOUND"));
        mvc.perform(post(ROOT + "/code/members").with(guest.authentication()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("entryCode", code))))
                .andExpect(status().isNotFound());
        mvc.perform(delete(ROOT + "/{id}", id).with(host.authentication()).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void privateRoomRequiresCodeAndNormalizesItsInput() throws Exception {
        Client host = authenticatedUser();
        Client guest = authenticatedUser();
        JsonNode room = createRoom(host, "비공개 방", true);
        long id = room.path("roomId").asLong();
        assertThat(room.path("isPrivate").asBoolean()).isTrue();
        mvc.perform(post(ROOT + "/{id}/members", id).with(guest.authentication()).with(csrf()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("ROOM_CODE_REQUIRED"));
        mvc.perform(post(ROOT + "/code/members").with(guest.authentication()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                                "entryCode", " " + room.path("entryCode").asText().toLowerCase(java.util.Locale.ROOT) + " "))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.isPrivate").value(true))
                .andExpect(jsonPath("$.data.playerCount").value(2));
        mvc.perform(post(ROOT + "/{id}/members", id).with(guest.authentication()).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.playerCount").value(2));
    }

    @Test
    void publicRoomAlsoAcceptsCodeAndDuplicateCodeJoinIsIdempotent() throws Exception {
        Client host = authenticatedUser();
        Client guest = authenticatedUser();
        Client third = authenticatedUser();
        JsonNode room = createRoom(host, "공개 코드 방", false);
        String body = json.writeValueAsString(Map.of("entryCode", room.path("entryCode").asText()));
        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(post(ROOT + "/code/members").with(guest.authentication()).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.playerCount").value(2));
        }
        mvc.perform(post(ROOT + "/code/members").with(third.authentication()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("ROOM_FULL"));
    }

    @Test
    void listPaginatesIncludesPrivateRoomsAndNeverExposesCodesOrPasswords() throws Exception {
        Client host = authenticatedUser();
        JsonNode first = createRoom(host, "첫 방", false);
        JsonNode second = createRoom(host, "둘째 방", true);
        String response = mvc.perform(get(ROOT).param("size", "1").with(host.authentication()).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.rooms[0].roomId").value(second.path("roomId").asLong()))
                .andExpect(jsonPath("$.data.rooms[0].isPrivate").value(true))
                .andExpect(jsonPath("$.data.rooms[0].entryCode").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain("password", first.path("entryCode").asText(), second.path("entryCode").asText());
        mvc.perform(get(ROOT).param("page", "1").param("size", "1")
                        .with(host.authentication()).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.rooms[0].roomId").value(first.path("roomId").asLong()));
        mvc.perform(get(ROOT).param("page", "99").with(host.authentication()).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.rooms").isEmpty())
                .andExpect(jsonPath("$.data.totalElements").value(2));
    }

    @Test
    void emptyListIsSuccessful() throws Exception {
        Client user = authenticatedUser();
        mvc.perform(get(ROOT).with(user.authentication()).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.rooms").isEmpty())
                .andExpect(jsonPath("$.data.totalPages").value(0));
    }

    @Test
    void allFiveEndpointsRequireAuthentication() throws Exception {
        mvc.perform(get(ROOT).accept(MediaType.APPLICATION_JSON)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
        mvc.perform(post(ROOT).with(csrf()).accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(ROOT + "/1/members").with(csrf()).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(ROOT + "/code/members").with(csrf()).accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(delete(ROOT + "/1").with(csrf()).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void requestsWithoutDomainUserIdentityAreRejected() throws Exception {
        mvc.perform(post(ROOT).with(user("external-principal")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roomName\":\"방\",\"isPrivate\":false}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
        mvc.perform(get(ROOT).with(user("external-principal")))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
        assertThat(rooms.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"roomName\":\"   \",\"isPrivate\":false}",
            "{\"roomName\":\"방\"}", "{\"roomName\":null,\"isPrivate\":false}",
            "{\"roomName\":\"방\",\"isPrivate\":null}", "{broken-json",
            "{\"roomName\":123,\"isPrivate\":false}",
            "{\"roomName\":\"방\",\"isPrivate\":\"false\"}",
            "{\"roomName\":\"1234567890123456789012345678901\",\"isPrivate\":false}"})
    void invalidCreationIsRejected(String body) throws Exception {
        Client user = authenticatedUser();
        mvc.perform(post(ROOT).with(user.authentication()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
        assertThat(rooms.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"entryCode\":null}", "{\"entryCode\":\"\"}",
            "{\"entryCode\":\"ABCDEFG\"}", "{\"entryCode\":\"ABCD0123\"}"})
    void invalidCodeIsRejected(String body) throws Exception {
        Client user = authenticatedUser();
        mvc.perform(post(ROOT + "/code/members").with(user.authentication()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    void unknownCodeAndInvalidPathAndPageParametersHaveSpecifiedErrors() throws Exception {
        Client user = authenticatedUser();
        mvc.perform(post(ROOT + "/code/members").with(user.authentication()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"entryCode\":\"ABCDEFGH\"}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("ROOM_NOT_FOUND"));
        for (String path : new String[] {"0", "-1", "abc", "9999999999999999999999999999"}) {
            mvc.perform(post(ROOT + "/" + path + "/members").with(user.authentication()).with(csrf()))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
            mvc.perform(delete(ROOT + "/" + path).with(user.authentication()).with(csrf()))
                    .andExpect(status().isBadRequest());
        }
        for (String query : new String[] {"page=-1", "size=0", "size=101", "page=abc"}) {
            mvc.perform(get(ROOT + "?" + query).with(user.authentication()).with(csrf()))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
        }
    }

    private JsonNode createRoom(Client host, String roomName, boolean isPrivate) throws Exception {
        String response = mvc.perform(post(ROOT).with(host.authentication()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("roomName", roomName, "isPrivate", isPrivate))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(response).path("data");
    }

    private Client authenticatedUser() {
        int number = SEQUENCE.incrementAndGet();
        return new Client(String.valueOf(number), "유저" + number);
    }

    private record Client(String id, String nickname) {
        RequestPostProcessor authentication() {
            return SecurityMockMvcRequestPostProcessors.authentication(
                    UsernamePasswordAuthenticationToken.authenticated(
                            new MatchRoomUser(id, nickname), null, List.of()));
        }
    }
}

