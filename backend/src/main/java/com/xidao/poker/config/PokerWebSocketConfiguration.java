package com.xidao.poker.config;

import com.xidao.poker.web.ws.PokerHandshakeInterceptor;
import com.xidao.poker.web.ws.PokerWebSocketHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistration;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

@Configuration
@EnableWebSocket
public class PokerWebSocketConfiguration implements WebSocketConfigurer {
    private final PokerWebSocketHandler handler;
    private final PokerHandshakeInterceptor handshakeInterceptor;
    private final PokerNetworkProperties properties;

    public PokerWebSocketConfiguration(
            PokerWebSocketHandler handler,
            PokerHandshakeInterceptor handshakeInterceptor,
            PokerNetworkProperties properties
    ) {
        this.handler = handler;
        this.handshakeInterceptor = handshakeInterceptor;
        this.properties = properties;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        WebSocketHandlerRegistration registration = registry
                .addHandler(handler, "/ws/poker")
                .addInterceptors(handshakeInterceptor);
        if (!properties.allowedOriginPatterns().isEmpty()) {
            registration.setAllowedOriginPatterns(properties.allowedOriginPatterns().toArray(String[]::new));
        }
    }

    @Bean
    public ServletServerContainerFactoryBean webSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(properties.maximumTextMessageBytes());
        return container;
    }
}
