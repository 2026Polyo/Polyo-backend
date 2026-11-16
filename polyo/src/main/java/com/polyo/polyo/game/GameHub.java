package com.polyo.polyo.game;

import com.polyo.polyo.matchroom.MatchRoom;
import com.polyo.polyo.matchroom.MatchRoomEvents;
import com.polyo.polyo.matchroom.MatchRoomService;
import com.polyo.polyo.matchroom.MatchRoomUser;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.HashSet;
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
import org.springframework.transaction.event.TransactionalEventListener;
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
    /** Emote ids the clients know how to draw. */
    private static final Set<String> EMOTES = Set.of("smile", "laugh", "wow", "sad", "angry", "cool");
    /** Emotes closer together than this from one player are dropped, so a stuck key cannot flood the room. */
    private static final long EMOTE_INTERVAL_MS = 500;
    /** Cursors closer together than this from one player are dropped; clients send at most one per 100ms. */
    private static final long CURSOR_INTERVAL_MS = 50;
    /** Tray uids are 36-character UUIDs, so a cursor naming a longer one is dropped instead of relayed. */
    private static final int MAX_CURSOR_PIECE_UID_LENGTH = 64;

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
        /** Guests who pressed ready. The host starts the game instead of readying. */
        final Set<String> ready = new HashSet<>();
        final Map<String, Long> lastEmoteAt = new HashMap<>();
        final Map<String, Long> lastCursorAt = new HashMap<>();
        String hostUserId;
        List<MatchRoom.Member> members = List.of();
        MatchGame game;
        ScheduledFuture<?> turnTimer;
    }

    record ConnectedMessage(String type, Set<String> connectedUserIds, Set<String> readyUserIds, String hostUserId,
                            List<MatchRoom.Member> members) {
    }

    record EmoteMessage(String type, String userId, String emote) {
    }

    /**
     * The block a player holds ({@code pieceUid}, null when none) and where it would land, in PLACE's
     * coordinates. {@code row} and {@code col} are null together when there is no spot to show.
     */
    record CursorMessage(String type, String userId, String pieceUid, Integer row, Integer col) {
    }

    record ErrorMessage(String type, String code, String message) {
    }

    /** Sent before the server closes a waiting-room socket. {@code reason}: ROOM_DELETED or LEFT_ROOM. */
    record ClosedMessage(String type, String reason) {
    }

    void connect(WebSocketSession raw, long roomId, MatchRoomUser user) {
        WebSocketSession session = new ConcurrentWebSocketSessionDecorator(raw, SEND_TIME_LIMIT_MS, SEND_BUFFER_BYTES);
        RoomChannel channel = channels.computeIfAbsent(roomId, id -> new RoomChannel());
        synchronized (channel) {
            // The channel may have finished while this request was on its way in.
            if (channels.get(roomId) != channel) {
                throw new GameException("ROOM_CLOSED", "이미 끝난 방입니다.");
            }
            // Membership is read under the lock: a leave or delete that commits later waits for it and then
            // closes this session, and one that committed earlier makes this read fail.
            MatchRoomService.Lobby lobby;
            try {
                lobby = rooms.lobbyOf(roomId, user);
            } catch (RuntimeException exception) {
                removeIfIdle(roomId, channel);
                throw exception;
            }
            channel.hostUserId = lobby.hostUserId();
            channel.members = lobby.members();
            WebSocketSession previous = channel.sessions.put(user.id(), session);
            if (previous != null) {
                closeQuietly(previous);
            }
            // The game no longer starts on connect: the guest readies and the host sends START.
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
            // A dropped guest must ready again, so the host never starts against an empty seat.
            channel.ready.remove(userId);
            if (!removeIfIdle(roomId, channel)) {
                broadcastCurrent(channel);
            }
        }
    }

    /** Runs after the deleting transaction commits, so a reconnect cannot find the room again. */
    @TransactionalEventListener
    public void onRoomClosed(MatchRoomEvents.Closed event) {
        RoomChannel channel = channels.remove(event.roomId());
        if (channel == null) {
            return;
        }
        synchronized (channel) {
            String payload = json.writeValueAsString(new ClosedMessage("ROOM_CLOSED", "ROOM_DELETED"));
            List.copyOf(channel.sessions.values()).forEach(session -> sendAndClose(session, payload));
            channel.sessions.clear();
        }
    }

    @TransactionalEventListener
    public void onMemberLeft(MatchRoomEvents.MemberLeft event) {
        RoomChannel channel = channels.get(event.roomId());
        if (channel == null) {
            return;
        }
        synchronized (channel) {
            WebSocketSession session = channel.sessions.remove(event.userId());
            channel.ready.remove(event.userId());
            channel.members = channel.members.stream()
                    .filter(member -> !member.userId().equals(event.userId())).toList();
            if (session != null) {
                sendAndClose(session, json.writeValueAsString(new ClosedMessage("ROOM_CLOSED", "LEFT_ROOM")));
            }
            // The host hears about it even if the guest never connected.
            if (!removeIfIdle(event.roomId(), channel)) {
                broadcastCurrent(channel);
            }
        }
    }

    /** Drops a channel nobody is using; the caller holds its lock. */
    private boolean removeIfIdle(long roomId, RoomChannel channel) {
        if (channel.sessions.isEmpty() && channel.game == null) {
            channels.remove(roomId, channel);
            return true;
        }
        return false;
    }

    /** A guest marks themselves ready (or not) before the game. */
    void setReady(long roomId, MatchRoomUser user, boolean ready) {
        RoomChannel channel = openChannel(roomId);
        synchronized (channel) {
            if (channel.game != null) {
                throw new GameException("GAME_ALREADY_STARTED", "이미 게임이 시작되었습니다.");
            }
            if (user.id().equals(channel.hostUserId)) {
                throw new GameException("HOST_CANNOT_READY", "방장은 준비 없이 게임을 시작합니다.");
            }
            boolean changed = ready ? channel.ready.add(user.id()) : channel.ready.remove(user.id());
            if (changed) {
                broadcastCurrent(channel);
            }
        }
    }

    /** The host starts the game once the guest is connected and ready. */
    void start(long roomId, MatchRoomUser user) {
        RoomChannel channel = openChannel(roomId);
        synchronized (channel) {
            if (channel.game != null) {
                throw new GameException("GAME_ALREADY_STARTED", "이미 게임이 시작되었습니다.");
            }
            // Read under the lock so a leave that commits meanwhile cannot slip past the checks below.
            MatchRoomService.Lobby lobby = rooms.lobbyOf(roomId, user);
            channel.hostUserId = lobby.hostUserId();
            channel.members = lobby.members();
            if (!lobby.hostUserId().equals(user.id())) {
                throw new GameException("NOT_HOST", "방장만 게임을 시작할 수 있습니다.");
            }
            List<MatchRoom.Member> members = lobby.members();
            List<String> memberIds = members.stream().map(MatchRoom.Member::userId).toList();
            if (members.size() < MatchRoom.MAX_PLAYERS || !channel.sessions.keySet().containsAll(memberIds)) {
                throw new GameException("OPPONENT_NOT_CONNECTED", "상대가 아직 접속하지 않았습니다.");
            }
            boolean guestReady = memberIds.stream()
                    .filter(id -> !id.equals(user.id())).allMatch(channel.ready::contains);
            if (!guestReady) {
                throw new GameException("OPPONENT_NOT_READY", "상대가 아직 준비하지 않았습니다.");
            }
            if (!rooms.markPlaying(roomId, members)) {
                throw new GameException("ROOM_CHANGED", "참가자가 바뀌었습니다. 다시 시도해 주세요.");
            }
            channel.game = new MatchGame(members, new SecureRandom(), System::currentTimeMillis);
            channel.ready.clear();
            scheduleTurnTimer(roomId, channel);
            broadcastCurrent(channel);
        }
    }

    /** Relays an emote to everyone in the room, the sender included. */
    void emote(long roomId, String userId, String emote) {
        if (!EMOTES.contains(emote)) {
            throw new GameException("INVALID_EMOTE", "알 수 없는 감정표현입니다.");
        }
        RoomChannel channel = openChannel(roomId);
        synchronized (channel) {
            long now = System.currentTimeMillis();
            Long last = channel.lastEmoteAt.get(userId);
            if (last != null && now - last < EMOTE_INTERVAL_MS) {
                return;
            }
            channel.lastEmoteAt.put(userId, now);
            String payload = json.writeValueAsString(new EmoteMessage("EMOTE", userId, emote));
            List.copyOf(channel.sessions.values()).forEach(session -> send(session, payload));
        }
    }

    /**
     * Relays what the current player is holding and where it would land, to the other player only.
     * Cursors are frequent and only decorative, so one that does not fit is dropped without an ERROR.
     */
    void cursor(long roomId, String userId, String pieceUid, Integer row, Integer col) {
        if (!isValidCursor(pieceUid, row, col)) {
            return;
        }
        // Unlike an emote, a missing channel is not an error.
        RoomChannel channel = channels.get(roomId);
        if (channel == null) {
            return;
        }
        synchronized (channel) {
            MatchGame game = channel.game;
            if (game == null || game.finished() || !game.current().userId.equals(userId)) {
                return;
            }
            long now = System.currentTimeMillis();
            Long last = channel.lastCursorAt.get(userId);
            if (last != null && now - last < CURSOR_INTERVAL_MS) {
                return;
            }
            channel.lastCursorAt.put(userId, now);
            String payload = json.writeValueAsString(new CursorMessage("CURSOR", userId, pieceUid, row, col));
            // Not echoed like an emote: the sender already sees their own cursor. Collected into a list
            // before sending, because a failed send can remove a session (see broadcastCurrent).
            channel.sessions.entrySet().stream()
                    .filter(entry -> !entry.getKey().equals(userId))
                    .map(Map.Entry::getValue)
                    .toList()
                    .forEach(session -> send(session, payload));
        }
    }

    /** Row and col are null together, or both in the range the clients draw: -SIZE to SIZE - 1. */
    private static boolean isValidCursor(String pieceUid, Integer row, Integer col) {
        if (pieceUid != null && pieceUid.length() > MAX_CURSOR_PIECE_UID_LENGTH) {
            return false;
        }
        if (row == null || col == null) {
            return row == null && col == null;
        }
        return isCursorCoordinate(row) && isCursorCoordinate(col);
    }

    private static boolean isCursorCoordinate(int value) {
        return value >= -PlayerBoard.SIZE && value < PlayerBoard.SIZE;
    }

    private RoomChannel openChannel(long roomId) {
        RoomChannel channel = channels.get(roomId);
        if (channel == null) {
            throw new GameException("ROOM_CLOSED", "이미 끝난 방입니다.");
        }
        return channel;
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
                : new ConnectedMessage("WAITING", Set.copyOf(channel.sessions.keySet()), Set.copyOf(channel.ready),
                        channel.hostUserId, channel.members);
        String payload = json.writeValueAsString(message);
        // Sending to a dying socket can run its close handler on this thread, which removes it from
        // sessions (the lock is re-entrant), so iterate over a copy.
        List.copyOf(channel.sessions.values()).forEach(session -> send(session, payload));
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

    private void sendAndClose(WebSocketSession session, String payload) {
        send(session, payload);
        try {
            session.close(CloseStatus.NORMAL);
        } catch (IOException exception) {
            log.debug("Could not close game session", exception);
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
