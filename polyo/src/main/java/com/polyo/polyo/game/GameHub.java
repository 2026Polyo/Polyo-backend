package com.polyo.polyo.game;

import com.polyo.polyo.matchroom.MatchRoom;
import com.polyo.polyo.matchroom.MatchRoomService;
import com.polyo.polyo.matchroom.MatchRoomUser;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import tools.jackson.databind.json.JsonMapper;

/**
 * Connects the two players of a room to their in-memory game. Every change to a room's sessions or
 * game happens while holding that room's lock, so moves and timeouts apply one at a time.
 */
@Component
public class GameHub {
    private static final Logger log = LoggerFactory.getLogger(GameHub.class);
    private static final int SEND_TIME_LIMIT_MS = 5_000;
    private static final int SEND_BUFFER_BYTES = 64 * 1024;

    private final MatchRoomService rooms;
    private final JsonMapper json;
    private final Map<Long, RoomChannel> channels = new ConcurrentHashMap<>();
    private final ScheduledExecutorService timers = Executors.newSingleThreadScheduledExecutor();

    public GameHub(MatchRoomService rooms, JsonMapper json) {
        this.rooms = rooms;
        this.json = json;
    }

    private static final class RoomChannel {
        final Map<String, WebSocketSession> sessions = new HashMap<>();
        MatchGame game;
        ScheduledFuture<?> turnTimer;
    }

    record ConnectedMessage(String type, Set<String> connectedUserIds) {
    }

    record ErrorMessage(String type, String code, String message) {
    }

    void connect(WebSocketSession raw, long roomId, MatchRoomUser user) {
        List<MatchRoom.Member> members = rooms.membersOf(roomId, user);
        WebSocketSession session = new ConcurrentWebSocketSessionDecorator(raw, SEND_TIME_LIMIT_MS, SEND_BUFFER_BYTES);
        RoomChannel channel = channels.computeIfAbsent(roomId, id -> new RoomChannel());
        synchronized (channel) {
            // The channel may have finished while this request was on its way in.
            if (channels.get(roomId) != channel) {
                throw new GameException("ROOM_CLOSED", "이미 끝난 방입니다.");
            }
            WebSocketSession previous = channel.sessions.put(user.id(), session);
            if (previous != null) {
                closeQuietly(previous);
            }
            if (channel.game == null && members.size() == MatchRoom.MAX_PLAYERS
                    && channel.sessions.keySet().containsAll(members.stream().map(MatchRoom.Member::userId).toList())) {
                rooms.markPlaying(roomId);
                channel.game = new MatchGame(members, new SecureRandom(), System::currentTimeMillis);
                scheduleTurnTimer(roomId, channel);
            }
            broadcastCurrent(channel);
        }
    }

    void disconnect(WebSocketSession raw, long roomId, String userId) {
        RoomChannel channel = channels.get(roomId);
        if (channel == null) {
            return;
        }
        synchronized (channel) {
            WebSocketSession session = channel.sessions.get(userId);
            // A reconnect may already have replaced this session.
            if (session == null || !session.getId().equals(raw.getId())) {
                return;
            }
            channel.sessions.remove(userId);
            if (channel.sessions.isEmpty() && channel.game == null) {
                channels.remove(roomId, channel);
            } else {
                broadcastCurrent(channel);
            }
        }
    }

    void place(long roomId, String userId, String pieceUid, int row, int col) {
        update(roomId, game -> game.place(userId, pieceUid, row, col));
    }

    void forfeit(long roomId, String userId) {
        update(roomId, game -> game.forfeit(userId));
    }

    private void update(long roomId, Consumer<MatchGame> move) {
        RoomChannel channel = channels.get(roomId);
        if (channel == null) {
            throw new GameException("GAME_NOT_STARTED", "아직 시작되지 않은 게임입니다.");
        }
        synchronized (channel) {
            if (channel.game == null) {
                throw new GameException("GAME_NOT_STARTED", "아직 시작되지 않은 게임입니다.");
            }
            move.accept(channel.game);
            afterChange(roomId, channel);
        }
    }

    private void afterChange(long roomId, RoomChannel channel) {
        if (channel.turnTimer != null) {
            channel.turnTimer.cancel(false);
        }
        if (!channel.game.finished()) {
            scheduleTurnTimer(roomId, channel);
            broadcastCurrent(channel);
            return;
        }
        channels.remove(roomId, channel);
        // Players must hear the result even if the room cannot be removed.
        try {
            rooms.finish(roomId);
        } finally {
            broadcastCurrent(channel);
        }
    }

    private void scheduleTurnTimer(long roomId, RoomChannel channel) {
        channel.turnTimer = timers.schedule(() -> expireTurn(roomId, channel),
                channel.game.remainingTurnMs(), TimeUnit.MILLISECONDS);
    }

    private void expireTurn(long roomId, RoomChannel channel) {
        try {
            synchronized (channel) {
                if (channels.get(roomId) == channel && channel.game.expireTurn()) {
                    afterChange(roomId, channel);
                }
            }
        } catch (RuntimeException exception) {
            log.error("Failed to end timed-out turn in room {}", roomId, exception);
        }
    }

    private void broadcastCurrent(RoomChannel channel) {
        Object message = channel.game != null
                ? GameStateMessage.of(channel.game, channel.sessions.keySet())
                : new ConnectedMessage("WAITING", Set.copyOf(channel.sessions.keySet()));
        String payload = json.writeValueAsString(message);
        channel.sessions.values().forEach(session -> send(session, payload));
    }

    void sendError(WebSocketSession session, String code, String message) {
        send(session, json.writeValueAsString(new ErrorMessage("ERROR", code, message)));
    }

    private void send(WebSocketSession session, String payload) {
        try {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(payload));
            }
        } catch (IOException | RuntimeException exception) {
            // A dead connection is cleaned up by its close event; the other player must still get the update.
            log.debug("Could not send game message", exception);
        }
    }

    private void closeQuietly(WebSocketSession session) {
        try {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("replaced by a newer connection"));
        } catch (IOException exception) {
            log.debug("Could not close replaced session", exception);
        }
    }

    @PreDestroy
    void shutdown() {
        timers.shutdownNow();
    }
}
