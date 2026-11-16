package com.polyo.polyo.game;

import com.polyo.polyo.auth.JwtTokenProvider;
import com.polyo.polyo.matchroom.MatchRoomException;
import com.polyo.polyo.matchroom.MatchRoomUser;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Socket endpoint for /ws/match/{roomId}. Browsers cannot set an Authorization header on a WebSocket,
 * so the first message must be {"type":"AUTH","token":"..."}; the token stays out of URLs and logs.
 * A socket that sends no AUTH within the timeout is closed.
 */
@Component
public class GameWebSocketHandler extends TextWebSocketHandler {
    private static final Logger log = LoggerFactory.getLogger(GameWebSocketHandler.class);
    private static final String USER = "user";
    /** Set once by whichever comes first: the AUTH message or the timeout. */
    private static final String AUTH_DECIDED = "authDecided";

    private final GameHub hub;
    private final JwtTokenProvider tokens;
    private final JsonMapper json;
    private final long authTimeoutMs;
    private final ScheduledExecutorService authTimers = Executors.newSingleThreadScheduledExecutor();

    public GameWebSocketHandler(GameHub hub, JwtTokenProvider tokens, JsonMapper json,
            @Value("${app.game.auth-timeout-ms:10000}") long authTimeoutMs) {
        this.hub = hub;
        this.tokens = tokens;
        this.json = json;
        this.authTimeoutMs = authTimeoutMs;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        AtomicBoolean decided = new AtomicBoolean();
        session.getAttributes().put(AUTH_DECIDED, decided);
        authTimers.schedule(() -> {
            if (decided.compareAndSet(false, true)) {
                hub.sendError(session, "AUTH_TIMEOUT", "인증 시간이 초과되었습니다.");
                closeQuietly(session);
            }
        }, authTimeoutMs, TimeUnit.MILLISECONDS);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        JsonNode body;
        try {
            body = json.readTree(message.getPayload());
        } catch (JacksonException exception) {
            hub.sendError(session, "INVALID_MESSAGE", "메시지 형식이 올바르지 않습니다.");
            return;
        }
        MatchRoomUser user = (MatchRoomUser) session.getAttributes().get(USER);
        try {
            if (user == null) {
                authenticate(session, body);
            } else {
                dispatch(session, user, body);
            }
        } catch (GameException exception) {
            hub.sendError(session, exception.code(), exception.getMessage());
        } catch (MatchRoomException exception) {
            hub.sendError(session, exception.code(), exception.getMessage());
            session.close(CloseStatus.POLICY_VIOLATION);
        }
    }

    private void authenticate(WebSocketSession session, JsonNode body) throws IOException {
        if (!((AtomicBoolean) session.getAttributes().get(AUTH_DECIDED)).compareAndSet(false, true)) {
            return; // timed out, or a failed AUTH already closed the socket
        }
        Optional<MatchRoomUser> user = "AUTH".equals(body.path("type").asString(""))
                ? tokens.parse(body.path("token").asString("")) : Optional.empty();
        if (user.isEmpty()) {
            hub.sendError(session, "UNAUTHENTICATED", "로그인이 필요합니다.");
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        try {
            hub.connect(session, roomId(session), user.get());
        } catch (GameException exception) {
            // Only one AUTH is accepted, so a socket that could not join is of no further use.
            hub.sendError(session, exception.code(), exception.getMessage());
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        session.getAttributes().put(USER, user.get());
    }

    private void dispatch(WebSocketSession session, MatchRoomUser user, JsonNode body) {
        long roomId = roomId(session);
        switch (body.path("type").asString("")) {
            case "PLACE" -> {
                if (!body.path("pieceUid").isString() || !body.path("row").isInt() || !body.path("col").isInt()) {
                    throw new GameException("INVALID_MESSAGE", "메시지 형식이 올바르지 않습니다.");
                }
                hub.place(roomId, user.id(), body.path("pieceUid").asString(), body.path("row").asInt(), body.path("col").asInt());
            }
            case "FORFEIT" -> hub.forfeit(roomId, user.id());
            case "READY" -> {
                if (!body.path("ready").isBoolean()) {
                    throw new GameException("INVALID_MESSAGE", "메시지 형식이 올바르지 않습니다.");
                }
                hub.setReady(roomId, user, body.path("ready").asBoolean());
            }
            case "START" -> hub.start(roomId, user);
            case "EMOTE" -> {
                if (!body.path("emote").isString()) {
                    throw new GameException("INVALID_MESSAGE", "메시지 형식이 올바르지 않습니다.");
                }
                hub.emote(roomId, user.id(), body.path("emote").asString());
            }
            case "CURSOR" -> {
                // Cursors are frequent and only decorative, so a malformed one is dropped instead of drawing an ERROR.
                JsonNode pieceUid = body.path("pieceUid");
                JsonNode row = body.path("row");
                JsonNode col = body.path("col");
                if ((pieceUid.isString() || pieceUid.isNull()) && (row.isInt() || row.isNull())
                        && (col.isInt() || col.isNull())) {
                    hub.cursor(roomId, user.id(), pieceUid.isNull() ? null : pieceUid.asString(),
                            row.isNull() ? null : row.asInt(), col.isNull() ? null : col.asInt());
                }
            }
            default -> throw new GameException("INVALID_MESSAGE", "메시지 형식이 올바르지 않습니다.");
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        MatchRoomUser user = (MatchRoomUser) session.getAttributes().get(USER);
        if (user != null) {
            hub.disconnect(session, roomId(session), user.id());
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.debug("Game socket transport error", exception);
    }

    private void closeQuietly(WebSocketSession session) {
        try {
            session.close(CloseStatus.POLICY_VIOLATION);
        } catch (IOException exception) {
            log.debug("Could not close unauthenticated game socket", exception);
        }
    }

    @PreDestroy
    void shutdown() {
        authTimers.shutdownNow();
    }

    private long roomId(WebSocketSession session) {
        return (long) session.getAttributes().get(GameRoomInterceptor.ROOM_ID);
    }
}
