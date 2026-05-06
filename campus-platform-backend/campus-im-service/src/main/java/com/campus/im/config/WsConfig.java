package com.campus.im.config;

import com.campus.im.websocket.WsHandshakeInterceptor;
import com.campus.im.websocket.WsServer;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WsConfig implements WebSocketConfigurer {

    private final WsServer wsServer;
    private final WsHandshakeInterceptor wsHandshakeInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(wsServer, "/ws")
                .addInterceptors(wsHandshakeInterceptor)
                .setAllowedOriginPatterns("*");
    }
}
