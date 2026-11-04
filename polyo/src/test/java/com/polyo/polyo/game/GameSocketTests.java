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
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
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

    @Test
    void gameStartsWhenBothConnectAndAppliesMoves() throws Exception {
        MatchRoomUser hostUser = newUser();
        MatchRoomUser guestUser = newUser();
        long roomId = fullRoom(hostUser, guestUser);
        Client host = new Client(hostUser, roomId);
        Client guest = new Client(guestUser, roomId);

        host.authenticate();
        assertThat(host.next("WAITING").path("connectedUserIds").size()).isEqualTo(1);
        guest.authenticate();
        JsonNode state = host.next("STATE");
        guest.next("STATE");

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
        String firstTray = host.next("STATE").path("tray").toString();

        host.session.close();
        Client again = new Client(hostUser, roomId);
        again.authenticate();

        assertThat(again.next("STATE").path("tray").toString()).isEqualTo(firstTray);
        guest.send(Map.of("type", "FORFEIT"));
        again.next("STATE");
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
}
