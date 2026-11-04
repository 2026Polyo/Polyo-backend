package com.polyo.polyo.game;

import static org.assertj.core.api.Assertions.assertThat;

import com.polyo.polyo.TestcontainersConfiguration;
import com.polyo.polyo.matchroom.CreateMatchRoomRequest;
import com.polyo.polyo.matchroom.MatchRoomRepository;
import com.polyo.polyo.matchroom.MatchRoomService;
import com.polyo.polyo.matchroom.MatchRoomStatus;
import com.polyo.polyo.matchroom.MatchRoomUser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.game.auth-timeout-ms=1000")
@Import(TestcontainersConfiguration.class)
class GameSocketTests {
    private static final AtomicInteger SEQUENCE = new AtomicInteger(1000);

    @LocalServerPort int port;
    @Autowired MatchRoomService rooms;
    @Autowired MatchRoomRepository repository;
    @Autowired JsonMapper json;
    @Value("${app.auth.jwt-secret}") String secret;

    private final class Client extends TextWebSocketHandler {
        final BlockingQueue<JsonNode> received = new LinkedBlockingQueue<>();
        final CompletableFuture<CloseStatus> closed = new CompletableFuture<>();
        final MatchRoomUser user;
        WebSocketSession session;

        Client(MatchRoomUser user, long roomId) throws Exception {
            this.user = user;
            session = new StandardWebSocketClient()
                    .execute(this, "ws://localhost:" + port + "/ws/match/" + roomId).get(5, TimeUnit.SECONDS);
        }

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) {
            received.add(json.readTree(message.getPayload()));
        }

        @Override
        public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
            closed.complete(status);
        }

        CloseStatus awaitClose() throws Exception {
            return closed.get(5, TimeUnit.SECONDS);
        }

        void send(Map<String, Object> message) throws Exception {
            session.sendMessage(new TextMessage(json.writeValueAsString(message)));
        }

        void authenticate() throws Exception {
            send(Map.of("type", "AUTH", "token", token(user)));
        }

        JsonNode next(String type) throws Exception {
            for (;;) {
                JsonNode message = received.poll(5, TimeUnit.SECONDS);
                assertThat(message).as("waiting for " + type).isNotNull();
                if (type.equals(message.path("type").asString())) {
                    return message;
                }
            }
        }
    }

    private MatchRoomUser newUser() {
        int id = SEQUENCE.incrementAndGet();
        return new MatchRoomUser(String.valueOf(id), "user" + id);
    }

    private String token(MatchRoomUser user) {
        return Jwts.builder().subject(user.id()).claim("nickname", user.nickname())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8))).compact();
    }

    private long fullRoom(MatchRoomUser host, MatchRoomUser guest) {
        long roomId = rooms.create(new CreateMatchRoomRequest("대전", false), host).roomId();
        rooms.joinById(roomId, guest);
        return roomId;
    }

    /** Guest readies, host starts; returns the first STATE the host sees. */
    private JsonNode startGame(Client host, Client guest) throws Exception {
        guest.send(Map.of("type", "READY", "ready", true));
        waitForReady(host, guest.user.id());
        host.send(Map.of("type", "START"));
        guest.next("STATE");
        return host.next("STATE");
    }

    private JsonNode waitForReady(Client client, String userId) throws Exception {
        for (;;) {
            JsonNode waiting = client.next("WAITING");
            if (waiting.path("readyUserIds").toString().contains("\"" + userId + "\"")) {
                return waiting;
            }
        }
    }

    @Test
    void gameStartsWhenHostStartsAndAppliesMoves() throws Exception {
        MatchRoomUser hostUser = newUser();
        MatchRoomUser guestUser = newUser();
        long roomId = fullRoom(hostUser, guestUser);
        Client host = new Client(hostUser, roomId);
        Client guest = new Client(guestUser, roomId);

        host.authenticate();
        assertThat(host.next("WAITING").path("connectedUserIds").size()).isEqualTo(1);
        guest.authenticate();
        guest.next("WAITING");
        JsonNode state = startGame(host, guest);

        assertThat(state.path("status").asString()).isEqualTo("PLAYING");
        assertThat(state.path("tray").size()).isEqualTo(6);
        assertThat(state.path("players").size()).isEqualTo(2);
        assertThat(state.path("turnRemainingMs").asLong()).isBetween(1L, 10_000L);
        assertThat(repository.findById(roomId).orElseThrow().status()).isEqualTo(MatchRoomStatus.PLAYING);

        boolean hostFirst = hostUser.id().equals(state.path("turnUserId").asString());
        Client mover = hostFirst ? host : guest;
        Client waiter = hostFirst ? guest : host;
        JsonNode piece = state.path("tray").get(0);

        waiter.send(Map.of("type", "PLACE", "pieceUid", piece.path("uid").asString(), "row", 0, "col", 0));
        assertThat(waiter.next("ERROR").path("code").asString()).isEqualTo("NOT_YOUR_TURN");
        mover.send(Map.of("type", "PLACE", "pieceUid", "nope", "row", 0, "col", 0));
        assertThat(mover.next("ERROR").path("code").asString()).isEqualTo("PIECE_NOT_AVAILABLE");

        mover.send(Map.of("type", "PLACE", "pieceUid", piece.path("uid").asString(), "row", 0, "col", 0));
        JsonNode afterMove = waiter.next("STATE");
        assertThat(afterMove.path("turnUserId").asString()).isEqualTo(waiter.user.id());
        assertThat(afterMove.path("tray").get(0).isNull()).isTrue();
        assertThat(afterMove.path("lastMove").path("userId").asString()).isEqualTo(mover.user.id());

        mover.send(Map.of("type", "FORFEIT"));
        JsonNode finished = waiter.next("STATE");
        assertThat(finished.path("status").asString()).isEqualTo("FINISHED");
        assertThat(finished.path("winnerUserId").asString()).isEqualTo(waiter.user.id());
        assertThat(finished.path("finishReason").asString()).isEqualTo("FORFEIT");
        assertThat(repository.findById(roomId)).isEmpty();
    }

    @Test
    void reconnectingPlayerGetsTheCurrentGame() throws Exception {
        MatchRoomUser hostUser = newUser();
        MatchRoomUser guestUser = newUser();
        long roomId = fullRoom(hostUser, guestUser);
        Client host = new Client(hostUser, roomId);
        Client guest = new Client(guestUser, roomId);
        host.authenticate();
        guest.authenticate();
        guest.next("WAITING");
        String firstTray = startGame(host, guest).path("tray").toString();

        host.session.close();
        Client again = new Client(hostUser, roomId);
        again.authenticate();

        assertThat(again.next("STATE").path("tray").toString()).isEqualTo(firstTray);
        guest.send(Map.of("type", "FORFEIT"));
        again.next("STATE");
    }

    @Test
    void onlyTheHostStartsAndOnlyOnceTheGuestIsReady() throws Exception {
        MatchRoomUser hostUser = newUser();
        MatchRoomUser guestUser = newUser();
        long roomId = fullRoom(hostUser, guestUser);
        Client host = new Client(hostUser, roomId);
        Client guest = new Client(guestUser, roomId);
        host.authenticate();
        host.next("WAITING");
        guest.authenticate();

        // Both connected is no longer enough to start.
        JsonNode waiting = host.next("WAITING");
        assertThat(waiting.path("connectedUserIds").size()).isEqualTo(2);
        assertThat(waiting.path("hostUserId").asString()).isEqualTo(hostUser.id());
        assertThat(waiting.path("members").size()).isEqualTo(2);
        assertThat(waiting.path("readyUserIds").size()).isZero();
        assertThat(repository.findById(roomId).orElseThrow().status()).isEqualTo(MatchRoomStatus.FULL);

        host.send(Map.of("type", "START"));
        assertThat(host.next("ERROR").path("code").asString()).isEqualTo("OPPONENT_NOT_READY");
        guest.send(Map.of("type", "START"));
        assertThat(guest.next("ERROR").path("code").asString()).isEqualTo("NOT_HOST");
        host.send(Map.of("type", "READY", "ready", true));
        assertThat(host.next("ERROR").path("code").asString()).isEqualTo("HOST_CANNOT_READY");
        guest.send(Map.of("type", "READY", "ready", "yes"));
        assertThat(guest.next("ERROR").path("code").asString()).isEqualTo("INVALID_MESSAGE");

        guest.send(Map.of("type", "READY", "ready", true));
        waitForReady(host, guestUser.id());
        guest.send(Map.of("type", "READY", "ready", false));
        assertThat(host.next("WAITING").path("readyUserIds").size()).isZero();

        // A guest who drops must ready again.
        guest.send(Map.of("type", "READY", "ready", true));
        waitForReady(host, guestUser.id());
        guest.session.close();
        assertThat(host.next("WAITING").path("readyUserIds").size()).isZero();
        Client again = new Client(guestUser, roomId);
        again.authenticate();
        again.next("WAITING");
        host.send(Map.of("type", "START"));
        assertThat(host.next("ERROR").path("code").asString()).isEqualTo("OPPONENT_NOT_READY");

        assertThat(startGame(host, again).path("status").asString()).isEqualTo("PLAYING");
        assertThat(repository.findById(roomId).orElseThrow().status()).isEqualTo(MatchRoomStatus.PLAYING);
        host.send(Map.of("type", "START"));
        assertThat(host.next("ERROR").path("code").asString()).isEqualTo("GAME_ALREADY_STARTED");
        again.send(Map.of("type", "READY", "ready", false));
        assertThat(again.next("ERROR").path("code").asString()).isEqualTo("GAME_ALREADY_STARTED");

        again.send(Map.of("type", "FORFEIT"));
        host.next("STATE");
    }

    @Test
    void startFailsWhileTheGuestIsAway() throws Exception {
        MatchRoomUser hostUser = newUser();
        long roomId = fullRoom(hostUser, newUser());
        Client host = new Client(hostUser, roomId);
        host.authenticate();
        host.next("WAITING");

        host.send(Map.of("type", "START"));

        assertThat(host.next("ERROR").path("code").asString()).isEqualTo("OPPONENT_NOT_CONNECTED");
    }

    @Test
    void emotesAreRelayedToBothPlayersAndThrottled() throws Exception {
        MatchRoomUser hostUser = newUser();
        MatchRoomUser guestUser = newUser();
        long roomId = fullRoom(hostUser, guestUser);
        Client host = new Client(hostUser, roomId);
        Client guest = new Client(guestUser, roomId);
        host.authenticate();
        guest.authenticate();
        guest.next("WAITING");

        host.send(Map.of("type", "EMOTE", "emote", "laugh"));
        JsonNode relayed = guest.next("EMOTE");
        assertThat(relayed.path("userId").asString()).isEqualTo(hostUser.id());
        assertThat(relayed.path("emote").asString()).isEqualTo("laugh");
        assertThat(host.next("EMOTE").path("emote").asString()).isEqualTo("laugh");

        // Sent within the interval: dropped without an error.
        host.send(Map.of("type", "EMOTE", "emote", "sad"));
        Thread.sleep(600);
        host.send(Map.of("type", "EMOTE", "emote", "cool"));
        assertThat(guest.next("EMOTE").path("emote").asString()).isEqualTo("cool");

        guest.send(Map.of("type", "EMOTE", "emote", "heart"));
        assertThat(guest.next("ERROR").path("code").asString()).isEqualTo("INVALID_EMOTE");
        guest.send(Map.of("type", "EMOTE"));
        assertThat(guest.next("ERROR").path("code").asString()).isEqualTo("INVALID_MESSAGE");
    }

    @Test
    void rejectsBadTokensAndOutsiders() throws Exception {
        MatchRoomUser hostUser = newUser();
        long roomId = fullRoom(hostUser, newUser());

        Client anonymous = new Client(hostUser, roomId);
        anonymous.send(Map.of("type", "AUTH", "token", "not-a-token"));
        assertThat(anonymous.next("ERROR").path("code").asString()).isEqualTo("UNAUTHENTICATED");

        Client outsider = new Client(newUser(), roomId);
        outsider.authenticate();
        assertThat(outsider.next("ERROR").path("code").asString()).isEqualTo("ROOM_MEMBER_ONLY");

        Client noRoom = new Client(hostUser, 99_999_999L);
        noRoom.authenticate();
        assertThat(noRoom.next("ERROR").path("code").asString()).isEqualTo("ROOM_NOT_FOUND");
    }

    @Test
    void deletingTheRoomClosesWaitingSockets() throws Exception {
        MatchRoomUser hostUser = newUser();
        MatchRoomUser guestUser = newUser();
        long roomId = fullRoom(hostUser, guestUser);
        Client host = new Client(hostUser, roomId);
        host.authenticate();
        host.next("WAITING");

        rooms.delete(roomId, hostUser);

        assertThat(host.next("ROOM_CLOSED").path("reason").asString()).isEqualTo("ROOM_DELETED");
        assertThat(host.awaitClose()).isEqualTo(CloseStatus.NORMAL);
        Client guest = new Client(guestUser, roomId);
        guest.authenticate();
        assertThat(guest.next("ERROR").path("code").asString()).isEqualTo("ROOM_NOT_FOUND");
    }

    @Test
    void hostLeavingClosesTheRoom() throws Exception {
        MatchRoomUser hostUser = newUser();
        long roomId = rooms.create(new CreateMatchRoomRequest("대전", false), hostUser).roomId();
        Client host = new Client(hostUser, roomId);
        host.authenticate();
        host.next("WAITING");

        rooms.leave(roomId, hostUser);

        assertThat(host.next("ROOM_CLOSED").path("reason").asString()).isEqualTo("ROOM_DELETED");
        host.awaitClose();
        assertThat(repository.findById(roomId)).isEmpty();
    }

    @Test
    void guestLeavingClosesTheirSocketAndFreesTheSeat() throws Exception {
        MatchRoomUser hostUser = newUser();
        MatchRoomUser guestUser = newUser();
        long roomId = fullRoom(hostUser, guestUser);
        Client guest = new Client(guestUser, roomId);
        guest.authenticate();
        guest.next("WAITING");

        rooms.leave(roomId, guestUser);

        assertThat(guest.next("ROOM_CLOSED").path("reason").asString()).isEqualTo("LEFT_ROOM");
        guest.awaitClose();
        assertThat(repository.findById(roomId).orElseThrow().status()).isEqualTo(MatchRoomStatus.WAITING);

        Client again = new Client(guestUser, roomId);
        again.authenticate();
        assertThat(again.next("ERROR").path("code").asString()).isEqualTo("ROOM_MEMBER_ONLY");

        // The host alone must not start a game.
        Client host = new Client(hostUser, roomId);
        host.authenticate();
        assertThat(host.next("WAITING").path("connectedUserIds").size()).isEqualTo(1);
    }

    @Test
    void hostHearsWhenAGuestWhoNeverConnectedLeaves() throws Exception {
        MatchRoomUser hostUser = newUser();
        MatchRoomUser guestUser = newUser();
        long roomId = fullRoom(hostUser, guestUser);
        Client host = new Client(hostUser, roomId);
        host.authenticate();
        host.next("WAITING");

        rooms.leave(roomId, guestUser);

        assertThat(host.next("WAITING").path("connectedUserIds").size()).isEqualTo(1);
        assertThat(host.closed).isNotDone();
    }

    @Test
    void socketWithoutAuthIsClosedAfterTimeout() throws Exception {
        long roomId = rooms.create(new CreateMatchRoomRequest("대전", false), newUser()).roomId();
        Client silent = new Client(newUser(), roomId);

        assertThat(silent.next("ERROR").path("code").asString()).isEqualTo("AUTH_TIMEOUT");
        assertThat(silent.awaitClose()).isEqualTo(CloseStatus.POLICY_VIOLATION);
    }
}
