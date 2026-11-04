package com.polyo.polyo.game;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
class GameWebSocketConfig implements WebSocketConfigurer {
    private final GameWebSocketHandler handler;

    GameWebSocketConfig(GameWebSocketHandler handler) {
        this.handler = handler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // Any origin is fine: the socket carries no cookies and is authorized only by the AUTH message.
        registry.addHandler(handler, "/ws/match/*")
                .addInterceptors(new GameRoomInterceptor())
                .setAllowedOriginPatterns("*");
    }
}
