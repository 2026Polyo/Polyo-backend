package com.polyo.polyo.game;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

/** Reads the room id from the /ws/match/{roomId} path during the handshake. */
class GameRoomInterceptor implements HandshakeInterceptor {
    static final String ROOM_ID = "roomId";

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler handler,
            Map<String, Object> attributes) {
        String path = request.getURI().getPath();
        try {
            attributes.put(ROOM_ID, Long.parseLong(path.substring(path.lastIndexOf('/') + 1)));
            return true;
        } catch (NumberFormatException exception) {
            response.setStatusCode(HttpStatus.BAD_REQUEST);
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler handler,
            Exception exception) {
    }
}
