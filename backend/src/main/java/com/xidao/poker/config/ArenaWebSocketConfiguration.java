package com.xidao.poker.config;

import com.xidao.poker.web.arena.ArenaHandshakeInterceptor;
import com.xidao.poker.web.arena.ArenaWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistration;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
public class ArenaWebSocketConfiguration implements WebSocketConfigurer {
    private final ArenaWebSocketHandler handler;
    private final ArenaHandshakeInterceptor handshake;
    private final PokerNetworkProperties properties;

    public ArenaWebSocketConfiguration(ArenaWebSocketHandler handler,
                                       ArenaHandshakeInterceptor handshake,
                                       PokerNetworkProperties properties) {
        this.handler = handler;
        this.handshake = handshake;
        this.properties = properties;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        WebSocketHandlerRegistration registration = registry.addHandler(handler, "/ws/arena")
                .addInterceptors(handshake);
        if (!properties.allowedOriginPatterns().isEmpty()) {
            registration.setAllowedOriginPatterns(properties.allowedOriginPatterns().toArray(String[]::new));
        }
    }
}
